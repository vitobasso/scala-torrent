package com.dominikgruber.scalatorrent.dht

import com.dominikgruber.scalatorrent.dht.message.DhtMessage.NodeId
import com.dominikgruber.scalatorrent.dht.Util.node
import com.dominikgruber.scalatorrent.util.UnitSpec

class NodeIdSpec extends UnitSpec {

  it should "compare equal NodeIds" in {
    node("01") shouldBe node("01")
  }

  it should "compare different NodeIds" in {
    node("01") should not be node("02")
  }

  it should "create a random NodeId" in {
    NodeId.random should not be NodeId.random
  }

  it should "calculate the distance between NodeIds" in {
    node("00").distance(node("02")) shouldBe node("02").toBigInt
    node("01").distance(node("02")) shouldBe node("03").toBigInt
    node("08").distance(node("01")) shouldBe node("09").toBigInt
  }

}
