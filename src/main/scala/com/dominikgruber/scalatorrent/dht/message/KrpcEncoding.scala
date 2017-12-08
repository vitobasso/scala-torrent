package com.dominikgruber.scalatorrent.dht.message

import com.dominikgruber.scalatorrent.bencode.BencodeParser.{NoSuccess, Success}
import com.dominikgruber.scalatorrent.bencode.{BencodeEncoder, BencodeParser}
import com.dominikgruber.scalatorrent.dht.message.DhtBasicEncoding.{parseNodeInfos, parsePeerInfo, serializeNodeInfo, serializePeerInfo}
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._
import org.slf4j.{Logger, LoggerFactory}

import scala.reflect.ClassTag

object KrpcEncoding {

  val log: Logger = LoggerFactory.getLogger(getClass)

  def encode[M <: Message](msg: M): Either[String, String] =
    BencodeEncoder(toMap(msg))

  def decode(msg: String): Either[String, Message] =
    BencodeParser(msg) match {
      case Success(m: Map[String, Any], _) => fromMap(m)
      case Success(unexpected, _) => Left(s"Expected a Map[String, Any] but was $unexpected")
      case NoSuccess(errorMsg, _) => Left(errorMsg)
    }

  private def toMap(msg: Message): Map[String, Any] = msg match {
    case m: Ping => PingCodec.encode(m)
    case m: Pong => PongCodec.encode(m)
    case m: FindNode => FindNodeCodec.encode(m)
    case m: NodesFound => NodesFoundCodec.encode(m)
    case m: GetPeers => GetPeersCodec.encode(m)
    case m: PeersFound => PeersFoundCodec.encode(m)
    case m: PeersFoundAndNodes => PeersFoundAndNodesCodec.encode(m)
    case m: PeersNotFound => PeersNotFoundCodec.encode(m)
    case m: AnnouncePeer => AnnouncePeerCodec.encode(m)
    case m: PeerReceived => PeerReceivedCodec.encode(m)
  }

  private def fromMap(map: Map[String, Any]): Either[String, _ <: Message] =
    for {
      codec <- chooseCodec(map).right
      msg <- codec.decode(map).right
    } yield msg

  def chooseCodec(map: Map[String, Any]): Either[String, KRPCCodec[_ <: Message]] =
    map.get("y") match {
      case Some("q") => chooseQueryCodec(map)
      case Some("r") => chooseResponseCodec(map)
      case Some(other) => Left(s"Unexpected value for key 'y': $other")
      case None => Left(s"No value for key 'y'")
    }

  private def chooseQueryCodec(map: Map[String, Any]): Either[String, QueryCodec[_ <: Query]] =
    map.get("q") match {
      case Some("ping") => Right(PingCodec)
      case Some("find_node") => Right(FindNodeCodec)
      case Some("get_peers") => Right(GetPeersCodec)
      case Some("announce_peer") => Right(AnnouncePeerCodec)
      case Some(other) => Left(s"Unexpected value for key 'q': $other")
      case None => Left(s"No value for key 'q'")
    }

  private def chooseResponseCodec(map: Map[String, Any]): Either[String, ResponseCodec[_ <: Response]] =
    map.get("r") match {
      case Some(rMap: Map[String, Any]) =>
        val observedKeys = rMap.keySet
        val chooseCodec =
          matchKeys(Set("id", "token", "values", "nodes"), PeersFoundAndNodesCodec) orElse
          matchKeys(Set("id", "token", "values"), PeersFoundCodec) orElse
          matchKeys(Set("id", "token", "nodes"), PeersNotFoundCodec) orElse
          matchKeys(Set("id", "nodes"), NodesFoundCodec) orElse
          matchKeys(Set("id"), PongCodec)
        chooseCodec.andThen(Right(_))
          .applyOrElse(observedKeys, (other: Set[String]) => Left(s"Unexpected value for key 'r': $other"))
      case Some(other) => Left(s"Unexpected value for key 'r': $other")
      case None => Left(s"No value for key 'r'")
    }

  private def matchKeys(requiredKeys: Set[String], codec: ResponseCodec[_ <: Response])
  : PartialFunction[Set[String], ResponseCodec[_ <: Response]] = {
    case observedKeys: Set[String] if requiredKeys.forall(observedKeys.contains) =>
      val unexpectedKeys = observedKeys -- requiredKeys
      if(unexpectedKeys.nonEmpty) log.warn(s"Discarded unknown keys in response: ${unexpectedKeys.mkString(", ")}")
      codec
  }

  implicit class MapOps(map: Map[String, Any]) {
    def getA[T: ClassTag](key: String): Either[String, T] =
      map.get(key) match {
        case Some(value: T) => Right(value)
        case Some(unexpected) =>
          val expectedType = implicitly[ClassTag[T]].runtimeClass.getSimpleName
          val actualType = unexpected.getClass.getSimpleName
          Left(s"Expected $expectedType but $key=$unexpected is a $actualType")
        case None => Left(s"No value for key '$key'")
      }
  }

}

import KrpcEncoding._
import DecoderUtil._

sealed trait KRPCCodec[T <: DhtMessage.Message] {
  def encode(msg: T): Map[String, Any]
  def decode(map: Map[String, Any]): Either[String, T] =
    for {
      trans <- map.getA[String]("t").right
      body <- map.getA[Map[String, Any]](bodyKey).right
      result <- decodeBody(body, TransactionId(trans)).right
    } yield result
  protected def bodyKey: String
  protected def decodeBody(map: Map[String, Any], trans: TransactionId): Either[String, T]
}

sealed trait QueryCodec[T <: DhtMessage.Query] extends KRPCCodec[T]{
  override def bodyKey: String = "a"
  def encode(msg: T): Map[String, Any] =
    Map (
      "t" -> msg.trans.value,
      "y" -> "q",
      "q" -> q,
      "a" -> encodeBody(msg)
    )
  protected val q: String
  protected def encodeBody(msg: T): Map[String, Any]
}

sealed trait ResponseCodec[T <: DhtMessage.Response] extends KRPCCodec[T]{
  override def bodyKey: String = "r"
  def encode(msg: T): Map[String, Any] =
    Map (
      "t" -> msg.trans.value,
      "y" -> "r",
      "r" -> encodeBody(msg)
    )
  protected def encodeBody(msg: T): Map[String, Any]
  protected def decodeBody(map: Map[String, Any], trans: TransactionId): Either[String, T]
}

case object PingCodec extends QueryCodec[Ping] {
  override val q: String = "ping"
  override def encodeBody(ping: Ping) =
    Map("id" -> ping.origin.value.unsized)
  override def decodeBody(args: Map[String, Any], trans: TransactionId): Either[String, Ping] =
    for {
      origin <- decodeOriginId(args).right
    } yield Ping(trans, origin)
}

case object PongCodec extends ResponseCodec[Pong] {
  override def encodeBody(pong: Pong): Map[String, Any] =
    Map("id" -> pong.origin.value.unsized)
  override def decodeBody(args: Map[String, Any], trans: TransactionId) =
    for {
      origin <- decodeOriginId(args).right
    } yield Pong(trans, origin)
}

case object FindNodeCodec extends QueryCodec[FindNode] {
  override val q: String = "find_node"
  override def encodeBody(findQuery: FindNode) =
    Map("id" -> findQuery.origin.value.unsized,
        "target" -> findQuery.target.value.unsized)
  override def decodeBody(args: Map[String, Any], trans: TransactionId): Either[String, FindNode] =
    for {
      origin <- decodeOriginId(args).right
      target <- decodeTargetId(args).right
    } yield FindNode(trans, origin, target)
}

case object NodesFoundCodec extends ResponseCodec[NodesFound] {
  override def encodeBody(findResponse: NodesFound): Map[String, Any] =
    Map("id" -> findResponse.origin.value.unsized,
        "nodes" -> findResponse.nodes
          .map(serializeNodeInfo)
          .mkString(""))
  override def decodeBody(args: Map[String, Any], trans: TransactionId) =
    for {
      origin <- decodeOriginId(args).right
      nodes <- decodeNodeInfos(args).right
    } yield NodesFound(trans, origin, nodes)
}

case object GetPeersCodec extends QueryCodec[GetPeers] {
  override val q: String = "get_peers"
  override def encodeBody(getPeers: GetPeers) =
    Map("id" -> getPeers.origin.value.unsized,
        "info_hash" -> getPeers.infoHash.value.unsized)
  override def decodeBody(args: Map[String, Any], trans: TransactionId): Either[String, GetPeers] =
    for {
      origin <- decodeOriginId(args).right
      hash <- decodeInfoHash(args).right
    } yield GetPeers(trans, origin, hash)
}

case object PeersFoundCodec extends ResponseCodec[PeersFound] {
  override def encodeBody(peersFound: PeersFound) =
    Map("id" -> peersFound.origin.value.unsized,
        "token" -> peersFound.token.value,
        "values" -> peersFound.peers
          .map(serializePeerInfo)
          .toList)
  override def decodeBody(args: Map[String, Any], trans: TransactionId): Either[String, PeersFound] =
    for {
      origin <- decodeOriginId(args).right
      token <- decodeToken(args).right
      peers <- decodePeerInfos(args).right
    } yield PeersFound(trans, origin, token, peers)
}

case object PeersFoundAndNodesCodec extends ResponseCodec[PeersFoundAndNodes] {
  override def encodeBody(peersFound: PeersFoundAndNodes) =
    Map("id" -> peersFound.origin.value.unsized,
        "token" -> peersFound.token.value,
        "values" -> peersFound.peers
          .map(serializePeerInfo)
          .toList,
        "nodes" -> peersFound.closestNodes
          .map(serializeNodeInfo)
          .mkString("")
    )
  override def decodeBody(args: Map[String, Any], trans: TransactionId): Either[String, PeersFoundAndNodes] =
    for {
      origin <- decodeOriginId(args).right
      token <- decodeToken(args).right
      peers <- decodePeerInfos(args).right
      nodes <- decodeNodeInfos(args).right
    } yield PeersFoundAndNodes(trans, origin, token, peers, nodes)
}

case object PeersNotFoundCodec extends ResponseCodec[PeersNotFound] {
  override def encodeBody(peersNotFound: PeersNotFound) =
    Map(
      "id" -> peersNotFound.origin.value.unsized,
      "token" -> peersNotFound.token.value,
      "nodes" -> peersNotFound.closestNodes
        .map(serializeNodeInfo)
        .mkString("")
    )
  override def decodeBody(args: Map[String, Any], trans: TransactionId): Either[String, PeersNotFound] =
    for {
      origin <- decodeOriginId(args).right
      token <- decodeToken(args).right
      nodes <- decodeNodeInfos(args).right
    } yield PeersNotFound(trans, origin, token, nodes)
}

case object AnnouncePeerCodec extends QueryCodec[AnnouncePeer] {
  override val q: String = "announce_peer"
  override def encodeBody(announce: AnnouncePeer) =
    Map(
      "id" -> announce.origin.value.unsized,
      "info_hash" -> announce.infoHash.value.unsized,
      "token" -> announce.token.value
    ).+(encodePort(announce))

  private def encodePort(announce: AnnouncePeer): (String, Int) =
    announce.port.map("port" -> _.toInt)
      .getOrElse("implied_port" -> 1)

  override def decodeBody(args: Map[String, Any], trans: TransactionId): Either[String, AnnouncePeer] =
    for {
      origin <- decodeOriginId(args).right
      hash <- decodeInfoHash(args).right
      port <- decodePort(args).right
      token <- decodeToken(args).right
    } yield AnnouncePeer(trans, origin, hash, port, token)

  private def decodePort(map: Map[String, Any]): Either[String, Option[Port]] =
    map.getA[Long]("implied_port") match {
      case Right(1) => Right(None)
      case _ => for {
          long <- map.getA[Long]("port").right
          port <- Port.parse(long).right
        } yield Some(port)
    }
}

case object PeerReceivedCodec extends ResponseCodec[PeerReceived] {
  override def encodeBody(announce: PeerReceived) =
    Map("id" -> announce.origin.value.unsized)
  override def decodeBody(args: Map[String, Any], trans: TransactionId): Either[String, PeerReceived] =
    for {
      origin <- decodeOriginId(args).right
    } yield PeerReceived(trans, origin)
}

object DecoderUtil {

  type Decoder[A] = Map[String, Any] => Either[String, A]

  val decodeOriginId: Decoder[NodeId] = decodeNodeId("id")
  val decodeTargetId: Decoder[NodeId] = decodeNodeId("target")

  def decodeNodeId(key: String): Decoder[NodeId] =
    decodeString(key, NodeId.validate)

  def decodeNodeInfos: Decoder[Seq[NodeInfo]] =
    decodeString("nodes", parseNodeInfos)

  def decodeInfoHash: Decoder[InfoHash] =
    decodeString("info_hash", InfoHash.validate)

  def decodeToken: Decoder[Token] =
    decodeString("token", raw => Right(Token(raw)))

  type Parser[A] = String => Either[String, A]
  def decodeString[A](key: String, parse: Parser[A]): Decoder[A] =
    args => for {
      raw <- args.getA[String](key).right
      parsed <- parse(raw).right
    } yield parsed

  def decodePeerInfos: Decoder[List[PeerInfo]] =
    args => for {
      raw <- args.getA[List[String]]("values").right
      parsed <- Right(raw.map(parsePeerInfo)).right
    } yield parsed

}