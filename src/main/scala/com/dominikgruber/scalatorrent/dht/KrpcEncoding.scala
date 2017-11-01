package com.dominikgruber.scalatorrent.dht

import com.dominikgruber.scalatorrent.bencode.BencodeParser.{NoSuccess, Success}
import com.dominikgruber.scalatorrent.bencode.{BencodeEncoder, BencodeParser}
import com.dominikgruber.scalatorrent.dht.DhtMessage._
import com.dominikgruber.scalatorrent.dht.DhtBasicEncoding.{parseNodeInfos, serializeNodeInfo, parsePeerInfo, serializePeerInfo}

import scala.reflect.ClassTag

object KrpcEncoding {

  def encode[M <: Message](msg: M): Either[String, String] =
    BencodeEncoder(toMap(msg))

  def decode(msg: String): Either[String, Message] =
    BencodeParser(msg) match {
      case Success(m: Map[String, Any], _) => fromMap(m)
      case Success(unexpected, _) => Left(s"Expected a Map[String, Any] but was $unexpected")
      case NoSuccess(errorMsg, _) => Left(errorMsg)
    }

  def toMap(msg: Message): Map[String, Any] = msg match {
    case m: Ping => PingCodec.encode(m)
    case m: Pong => PongCodec.encode(m)
    case m: FindNode => FindReqCodec.encode(m)
    case m: FindNodeResponse => FindRepCodec.encode(m)
    case m: GetPeers => GetPeersCodec.encode(m)
    case m: PeersFound => PeersFoundCodec.encode(m)
    case m: PeersNotFound => PeersNotFoundCodec.encode(m)
    case m: AnnouncePeer => AnnounceReqCodec.encode(m)
    case m: AnnouncePeerResponse => AnnounceRepCodec.encode(m)
  }

  def fromMap(map: Map[String, Any]): Either[String, _ <: Message] =
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

  private def chooseQueryCodec(map: Map[String, Any]): Either[String, KRPCCodec[_ <: Query]] =
    map.get("q") match {
      case Some("ping") => Right(PingCodec)
      case Some("find_node") => Right(FindReqCodec)
      case Some("get_peers") => Right(GetPeersCodec)
      case Some("announce_peer") => Right(AnnounceReqCodec)
      case Some(other) => Left(s"Unexpected value for key 'q': $other")
      case None => Left(s"No value for key 'q'")
    }

  private def chooseResponseCodec(map: Map[String, Any]): Either[String, KRPCCodec[_ <: Message]] =
    map.get("r") match {
      case Some(rMap: Map[String, Any]) =>
        val keys = rMap.keySet
        if(keys == Set("id")) Right(PongCodec)
        else if(keys == Set("id", "nodes")) Right(FindRepCodec)
        else if(keys == Set("id", "token", "values")) Right(PeersFoundCodec)
        else if(keys == Set("id", "token", "nodes")) Right(PeersNotFoundCodec)
        else Left(s"Unknown response type with keys: $keys")
      case Some(other) => Left(s"Unexpected value for key 'r': $other")
      case None => Left(s"No value for key 'r'")
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

import com.dominikgruber.scalatorrent.dht.KrpcEncoding.MapOps

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
      node <- args.getA[String]("id").right
      validNode <- NodeId.validate(node).right
    } yield Ping(trans, validNode)
}

case object PongCodec extends ResponseCodec[Pong] {
  override def encodeBody(pong: Pong): Map[String, Any] =
    Map("id" -> pong.origin.value.unsized)
  override def decodeBody(args: Map[String, Any], trans: TransactionId) =
    for {
      node <- args.getA[String]("id").right
      validNode <- NodeId.validate(node).right
    } yield Pong(trans, validNode)
}

case object FindReqCodec extends QueryCodec[FindNode] {
  override val q: String = "find_node"
  override def encodeBody(findQuery: FindNode) =
    Map("id" -> findQuery.origin.value.unsized,
        "target" -> findQuery.target.value.unsized)
  override def decodeBody(args: Map[String, Any], trans: TransactionId): Either[String, FindNode] =
    for {
      originNode <- args.getA[String]("id").right
      targetNode <- args.getA[String]("target").right
      validOriginNode <- NodeId.validate(originNode).right
      validTargetNode <- NodeId.validate(targetNode).right
    } yield FindNode(trans, validOriginNode, validTargetNode)
}

case object FindRepCodec extends ResponseCodec[FindNodeResponse] {
  override def encodeBody(findResponse: FindNodeResponse): Map[String, Any] =
    Map("id" -> findResponse.origin.value.unsized,
        "nodes" -> findResponse.nodes
          .map(serializeNodeInfo)
          .mkString(""))
  override def decodeBody(args: Map[String, Any], trans: TransactionId) =
    for {
      node <- args.getA[String]("id").right
      closestNodes <- args.getA[String]("nodes").right
      validNode <- NodeId.validate(node).right
      validClosestNodes <- parseNodeInfos(closestNodes).right
    } yield FindNodeResponse(trans, validNode, validClosestNodes)
}

case object GetPeersCodec extends QueryCodec[GetPeers] {
  override val q: String = "get_peers"
  override def encodeBody(getPeers: GetPeers) =
    Map("id" -> getPeers.origin.value.unsized,
        "info_hash" -> getPeers.infoHash.value.unsized)
  override def decodeBody(args: Map[String, Any], trans: TransactionId): Either[String, GetPeers] =
    for {
      originNode <- args.getA[String]("id").right
      hash <- args.getA[String]("info_hash").right
      validOriginNode <- NodeId.validate(originNode).right
      validHash <- InfoHash.validate(hash).right
    } yield GetPeers(trans, validOriginNode, validHash)
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
      node <- args.getA[String]("id").right
      token <- args.getA[String]("token").right
      peers <- args.getA[List[String]]("values").right
      validNode <- NodeId.validate(node).right
      validToken <- Right(Token(token)).right
      validPeers <- Right(peers.map(parsePeerInfo)).right
    } yield PeersFound(trans, validNode, validToken, validPeers)
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
      node <- args.getA[String]("id").right
      token <- args.getA[String]("token").right
      nodes <- args.getA[String]("nodes").right
      validNode <- NodeId.validate(node).right
      validToken <- Right(Token(token)).right
      validNodes <- parseNodeInfos(nodes).right
    } yield PeersNotFound(trans, validNode, validToken, validNodes)
}

case object AnnounceReqCodec extends QueryCodec[AnnouncePeer] {
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
      origin <- args.getA[String]("id").right
      hash <- args.getA[String]("info_hash").right
      port <- decodePort(args).right
      token <- args.getA[String]("token").right
      validOrigin <- NodeId.validate(origin).right
      validHash <- InfoHash.validate(hash).right
    } yield AnnouncePeer(trans, validOrigin, validHash, port, Token(token))

  private def decodePort(map: Map[String, Any]): Either[String, Option[Port]] =
    map.getA[Long]("implied_port") match {
      case Right(1) => Right(None)
      case _ => for {
          long <- map.getA[Long]("port").right
          port <- Port.parse(long).right
        } yield Some(port)
    }
}

case object AnnounceRepCodec extends ResponseCodec[AnnouncePeerResponse] {
  override def encodeBody(announce: AnnouncePeerResponse) =
    Map("id" -> announce.origin.value.unsized)
  override def decodeBody(args: Map[String, Any], trans: TransactionId): Either[String, AnnouncePeerResponse] =
    for {
      node <- args.getA[String]("id").right
      validNode <- NodeId.validate(node).right
    } yield AnnouncePeerResponse(trans, validNode)
}
