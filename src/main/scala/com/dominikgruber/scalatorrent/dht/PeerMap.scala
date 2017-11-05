package com.dominikgruber.scalatorrent.dht

import com.dominikgruber.scalatorrent.dht.message.DhtMessage.{InfoHash, PeerInfo}

//TODO persist
case class PeerMap() {

  private var map: Map[InfoHash, Set[PeerInfo]] = Map.empty

  def get(infoHash: InfoHash): Set[PeerInfo] =
    map.getOrElse(infoHash, Set.empty)

  def add(infoHash: InfoHash, peers: Set[PeerInfo]): Unit = {
    val knownPeers = map.getOrElse(infoHash, Set.empty) & peers
    map += (infoHash -> knownPeers)
  }

}
