package com.dominikgruber.scalatorrent.dht

import java.nio.charset.StandardCharsets.ISO_8859_1

import com.dominikgruber.scalatorrent.dht.DhtMessage.NodeId
import com.dominikgruber.scalatorrent.util.ByteUtil._
import com.dominikgruber.scalatorrent.util.{ByteUtil, UnitSpec}

class DhtNodeSpec extends UnitSpec {

  it should "compare equal NodeIds" in {
    nodeId("01") shouldBe nodeId("01")
  }

  it should "compare different NodeIds" in {
    nodeId("01") should not be nodeId("02")
  }

  it should "create a random NodeId" in {
    NodeId.random should not be NodeId.random
  }

  it should "calculate the distance between NodeIds" in {
    nodeId("00").distance(nodeId("02")) shouldBe 2
    nodeId("01").distance(nodeId("02")) shouldBe 3
    nodeId("08").distance(nodeId("01")) shouldBe 9
  }

  def nodeId(hexByte: String): NodeId = {
    require(hexByte.length == 2)
    val b: Bytes = Array.fill(19)(0.toByte) ++ bytes(hexByte)
    val str: String = new String(b, ISO_8859_1)
    NodeId.validate(str).right.get
  }
}
