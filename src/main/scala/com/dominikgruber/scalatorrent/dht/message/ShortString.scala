package com.dominikgruber.scalatorrent.dht.message

import com.dominikgruber.scalatorrent.dht.SearchManager.Transaction
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._

/**
  * Concise textual representation of [[A]] with information omitted
  */
trait ShortString[A] {
  def apply(a: A): String
}

object ShortString {

  //users should import this method only
  def shortString[A](a: A)(implicit pp: ShortString[A]): String = pp.apply(a)

  def inst[A](f: A => String): ShortString[A] = new ShortString[A] {
    override def apply(a: A): String = f(a)
  }

  implicit def list[A: ShortString](implicit elm: ShortString[A]): ShortString[List[A]] = inst {
    (v: List[A]) => v match {
      case Nil => "ø"
      case first :: Nil => elm(first)
      case first :: rest => elm(first) + s"…+${rest.size}"
    }
  }
  implicit def seq[A: ShortString]: ShortString[Seq[A]] = inst { (v: Seq[A]) => shortString(v.toList) }

  implicit def id[A <: Id20B]: ShortString[A] = inst { (v: A) => v.value.unsized.take(2) + "…" }
  implicit val peer: ShortString[PeerInfo] = inst { (v: PeerInfo) => v.ip.toString }
  implicit val node: ShortString[NodeInfo] = inst { (v: NodeInfo) => v.address.ip.toString }
  implicit val message: ShortString[Message] = inst {
    (v: Message) => v match {
      case Ping(t, origin) => shortString(origin)
      case FindNode(t, origin, target) => s"FindNode(${shortString(origin)}, ${shortString(target)})"
      case GetPeers(t, origin, hash) => s"GetPeers(${shortString(origin)}, ${shortString(hash)})"
      case NodesFound(t, origin, nodes) => s"NodesFound(${shortString(origin)}, ${shortString(nodes)})"
      case PeersFound(t, origin, token, peers) => s"PeersFound(${shortString(origin)}, )"
      case PeersNotFound(t, origin, token, nodes) => s"PeersNotFound(${shortString(origin)}, )"
      case _ => v.toString
    }
  }
  implicit val transactionId: ShortString[TransactionId] = inst { _.toString }
  implicit val transaction: ShortString[Transaction] = inst {
    (v: Transaction) => v.node match {
      case Left(address) => address.ip.toString
      case Right(id) => shortString(id)
    }
  }

}
