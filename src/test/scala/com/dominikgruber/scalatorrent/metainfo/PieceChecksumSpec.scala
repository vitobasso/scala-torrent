package com.dominikgruber.scalatorrent.metainfo

import com.dominikgruber.scalatorrent.util.{ByteUtil, UnitSpec}
import java.nio.charset.StandardCharsets.ISO_8859_1

import com.dominikgruber.scalatorrent.util.ByteUtil._

class PieceChecksumSpec extends UnitSpec {

  it should "use the correct sha1 function" in {
    val input = "test".getBytes(ISO_8859_1)
    sha1Hash(input) shouldBe bytes("a9 4a 8f e5 cc b1 9b a6 1c 4c 08 73 d3 91 e9 87 98 2f bb d3")
  }

}
