scala-torrent
=============

A [BitTorrent](http://www.bittorrent.com) client under development in
[Scala](http://www.scala-lang.org).

![screenshot](screenshot.png)

While looking for a good case to learn [Akka](http://akka.io) I came
across this project started by [TheDom](https://github.com/TheDom/scala-torrent).
They did the work from Bencode to Handshakes.

I've been having fun with it since then. Learned some Akka, network
protocols and Distributed Hash Tables.
I was also able to download a movie and watch it :)

### Progress

* [✔] Bencode
* [✔] Talk to HTTP trackers
* Peer Wire Protocol
  * [✔] Message models & encoding
  * [✔] Handshake
  * [✔] TCP buffering
  * [✔] Leeching
  * [TODO] Seeding
  * [✔] Pipelining
  * [TODO] Piece selection strategies
  * [TODO] Choking
* [✔] Reconstruct & persist files
* [✔] Talk to UDP trackers
* Distributed Hash Table (Kademlia)
  * [✔] Message models & encoding (KRPC)
  * [✔] Routing table
  * [✔] Bootstrap nodes
  * [✔] Search peers & nodes
  * [TODO] Persist peer infos
  * [TODO] Announce peer
* [✔] Manage known peers
* CLI
  * [✔] Show progress
  * [TODO] Choose torrent file
  * [TODO] Choose magnet link


