package com.dominikgruber.scalatorrent.dht

import java.net.InetAddress

import org.scalatest.{Matchers, WordSpec}

/**
  * Other integration tests depend on those assumptions
  */
class PreconditionsIT extends WordSpec with Matchers {

  "Internet connection" should {
    "ping google" in {
      val addr = InetAddress.getByName("google.com")
      addr.isReachable(10 * 1000)
    }
  }

  "Bootstrap Nodes" should {
    "have host names resolved" in {
      Bootstrap.addresses should not be empty
    }
  }

}
