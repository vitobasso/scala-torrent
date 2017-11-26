scala-torrent
=============

A [BitTorrent](http://www.bittorrent.com) client under development in
[Scala](http://www.scala-lang.org).

While looking for a good case to learn [Akka](http://akka.io) I came
across this project started by [TheDom](https://github.com/TheDom/scala-torrent),
who did the work from Bencode to Handshakes.

I've been having fun with it since then. Learned some Akka, networking
protocols and DHT's. Was able to download a whole movie and watch it :)

### Progress

* [✔] Bencode
* [✔] Talk with HTTP trackers
* Peer Wire Protocol
  * [✔] Message models & encoding
  * [✔] Handshake
  * [✔] TCP buffering
  * [✔] Leeching
  * [TODO] Seeding
  * [✔] Pipelining
  * [TODO] Piece selection strategies
  * [TODO] Choking
* [✔] Reconstruct files
* [✔] Talk with UDP trackers
* [in progress] Distributed Hash Table (Kademlia)
* [TODO] Add new torrents via cli

