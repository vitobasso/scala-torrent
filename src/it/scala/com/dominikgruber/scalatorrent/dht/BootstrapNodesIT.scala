package com.dominikgruber.scalatorrent.dht

import org.scalatest.{Matchers, WordSpec}

class BootstrapNodesIT extends WordSpec with Matchers {

  "Bootstrap Nodes" should {

    "Resolve some ips" in {
      BootstrapNodes.addresses should not be empty
    }

  }

}