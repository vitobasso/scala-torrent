package com.dominikgruber.scalatorrent.peerwireprotocol

import java.lang.System.currentTimeMillis

import com.dominikgruber.scalatorrent.peerwireprotocol.message.Request
import com.dominikgruber.scalatorrent.tracker.PeerAddress

import scala.concurrent.duration._
import com.dominikgruber.scalatorrent.Torrent.BlockSize
import PendingRequests.RequestTTL
import com.dominikgruber.scalatorrent.AppConfig

class PendingRequests() {
  private var pendingRequests = Map.empty[PeerAddress, Map[Request, Long]]

  def drop(piece: Int): Unit =
    dropTemplate { case (req, _) => req.index != piece }

  def drop(piece: Int, block: Int): Unit =
    dropTemplate { case (req, _) => req.index != piece || req.begin/BlockSize != block }

  private def dropTemplate(f: (Request, Long) => Boolean): Unit = {
    pendingRequests =
      pendingRequests.mapValues {
        _.filter{ case (k, v) => f(k, v) }
      }.filter {
        case (_, requests) => requests.nonEmpty
      }
  }

  def add(peer: PeerAddress, request: Request) = {
    val requests = pendingRequests.getOrElse(peer, Map.empty)
    val updatedRequests = requests.updated(request, currentTimeMillis)
    pendingRequests = pendingRequests.updated(peer, updatedRequests)
  }

  def size(peer: PeerAddress): Int = {
    val stillPending = dropOld(peer)
    stillPending.size
  }

  private def dropOld(peer: PeerAddress): Map[Request, Long] = {
    val stillPending =
      pendingRequests.getOrElse(peer, Map.empty)
        .filterNot{ case (_, sinceTime) => tooOld(sinceTime) }

    pendingRequests = pendingRequests.updated(peer, stillPending)
    stillPending
  }

  private def tooOld(sinceTime: Long): Boolean = {
    val lifeTime: Duration = (currentTimeMillis - sinceTime).millis
    lifeTime > RequestTTL
  }

}

object PendingRequests {
  val RequestTTL: Duration = AppConfig.bittorrentRequestTtl
}

