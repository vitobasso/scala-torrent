package com.dominikgruber.scalatorrent.dht

/**
  * http://www.bittorrent.org/beps/bep_0005.html
  */
class DhtNode {

}

object DhtNode {

  //routing table
  //  status
  //    good
  //      responded to us in the last 15 min; or
  //      sent us a request in the last 15 min and has ever responded to us anytime in the past
  //    questionable:
  //      15 min inactivity
  //    bad
  //      failed to respond multiple queries in a row
  //  keep only good nodes (keep questionable until replacing by a enw good one?)
  //  priority to good nodes
  //
  //  each bucket
  //    holds K=8 nodes
  //
  //    when full,
  //      if contains our own nodeid, split in half the range
  //      else, discard
  //        1. bad
  //        2. questionable (the one inactive for longer, but first try to ping 2x)
  //        3. the new one
  //    last changed
  //      refresh after 15min
  //
  // begin w/ new table
  //  1 bucket 0 to 2^160
  //  find_node closer and closer till can't find
  // start
  //  > p2p handshake
  //  < port
  //  > ping
  //  save peer


  //find peers
  //  d = distance(infohash, node)  for node in local routing table
  //  select x closest nodes, ask each
  //  stop if
  //    found enough peers; or
  //    can't find closer node
  //  store (in local routing table?) contact of x responding nodes closest to infohash


}
