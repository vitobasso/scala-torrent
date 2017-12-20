package com.dominikgruber.scalatorrent.dht.message

import com.dominikgruber.scalatorrent.dht.SearchManager.Transaction
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._
import com.dominikgruber.scalatorrent.util.ByteUtil.Hex

/**
  * Concise textual representation of [[A]] omitting verbose information
  * Usage: import [[ShortDescription.show]]
  */
trait ShortDescription[A] {
  def apply(a: A): String
}

object ShortDescription {

  //users should import this method only
  def show[A](a: A)(implicit pp: ShortDescription[A]): String = pp.apply(a)

  def inst[A](f: A => String): ShortDescription[A] = new ShortDescription[A] {
    override def apply(a: A): String = f(a)
  }

  implicit def list[A: ShortDescription](implicit elm: ShortDescription[A]): ShortDescription[List[A]] = inst {
    (v: List[A]) => v match {
      case Nil => "ø"
      case first :: Nil => elm(first)
      case first :: rest => elm(first) + s"(+${rest.size})"
    }
  }
  implicit def seq[A: ShortDescription]: ShortDescription[Seq[A]] = inst { (v: Seq[A]) => show(v.toList) }

  implicit def id[A <: Id20B]: ShortDescription[A] = inst { (v: A) => Hex(v.value.unsized.take(2)) + "…" }
  implicit val peerInfo: ShortDescription[PeerInfo] = inst { (v: PeerInfo) => v.ip.toString }
  implicit val nodeInfo: ShortDescription[NodeInfo] = inst { (v: NodeInfo) => v.address.ip.toString }
  implicit val message: ShortDescription[Message] = inst {
    (v: Message) => v match {
      case Ping(t, origin) => show(origin)
      case FindNode(t, origin, target) => s"FindNode(${show(origin)}, ${show(target)})"
      case GetPeers(t, origin, hash) => s"GetPeers(${show(origin)}, ${show(hash)})"
      case NodesFound(t, origin, nodes) => s"NodesFound(${show(origin)}, ${show(nodes)})"
      case PeersFound(t, origin, token, peers, nodes) => s"PeersFound(${show(origin)}, )"
      case PeersNotFound(t, origin, token, nodes) => s"PeersNotFound(${show(origin)}, )"
      case _ => v.toString
    }
  }
  implicit val transactionId: ShortDescription[TransactionId] = inst { v => Hex(v.value) }
  implicit val transaction: ShortDescription[Transaction] = inst {
    (v: Transaction) => v.node match {
      case Left(address) => s"${address.ip.toString}-${show(v.id)}"
      case Right(nodeId) => s"${show(nodeId)}-${show(v.id)}"
    }
  }

}
