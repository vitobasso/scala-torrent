package com.dominikgruber.scalatorrent.peerwireprotocol.message

import com.dominikgruber.scalatorrent.util.UnitSpec

class PortSpec extends UnitSpec  {

  "marshal" should "work" in {
    val msg = Port(1)
    msg.marshal should be (Vector[Byte](0, 0, 0, 3, 9, 0, 1))
  }

  "unmarshal" should "work" in {
    val msg = Port(1)
    Port.unmarshal(msg.marshal) should be (Some(msg))
  }

  it should "fail" in {
    Port.unmarshal(Vector[Byte](0, 0, 0, 3, 9, 1, 2, 3)) should be (None)
  }
}