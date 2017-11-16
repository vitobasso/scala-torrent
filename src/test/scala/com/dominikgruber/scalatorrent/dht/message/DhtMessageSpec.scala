package com.dominikgruber.scalatorrent.dht.message

import com.dominikgruber.scalatorrent.dht.Util.node
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._
import org.scalatest.{Matchers, WordSpecLike}

class DhtMessageSpec extends WordSpecLike with Matchers {

  "NodeId" must {
    "compare equal NodeIds" in {
      node("01") shouldBe node("01")
    }

    "compare different NodeIds" in {
      node("01") should not be node("02")
    }

    "create a random NodeId" in {
      NodeId.random should not be NodeId.random
    }

    "calculate the distance between NodeIds" in {
      node("00").distance(node("02")) shouldBe node("02").toBigInt
      node("01").distance(node("02")) shouldBe node("03").toBigInt
      node("08").distance(node("01")) shouldBe node("09").toBigInt
    }
  }

  "Ip" must {
    "parse small numbers" in {
      Ip.parse("1.2.3.4") shouldBe Right(Ip(1,2,3,4))
    }
    "convert to string: small numbers" in {
      Ip.parse("1.2.3.4").right.get.toString shouldBe "1.2.3.4"
    }

    "parse numbers only representable as unsigned byte" in {
      Ip.parse("46.39.231.106") shouldBe Right(Ip(46, 39, -25, 106))
    }
    "convert to string: numbers only representable as unsigned byte" in {
      Ip.parse("46.39.231.106").right.get.toString shouldBe "46.39.231.106"
    }

    "not parse number too large for unsigned byte" in {
      Ip.parse("256.257.258.259") shouldBe a[Left[_,_]]
    }

    "not parse negative numbers" in {
      Ip.parse("-1.-2.-3.-4") shouldBe a[Left[_, _]]
    }

    "not parse a string with only 3 dots" in {
      Ip.parse("1.2.3") shouldBe a[Left[_, _]]
    }

    "not parse a string with too many dots" in {
      Ip.parse("1.2.3.4.5") shouldBe a[Left[_, _]]
    }

    "not parse non numeric values" in {
      Ip.parse("1.2.3.a") shouldBe a[Left[_, _]]
    }

  }

  "Port" must {
    "parse a small number" in {
      Port.parse(1) shouldBe Right(Port(1))
    }

    "convert to int: a small number" in {
      Port.parse(1).right.get.toInt shouldBe 1
    }

    "parse a number only representable as unsigned short" in {
      Port.parse(65535) shouldBe Right(Port(-1))
    }

    "convert to int: a number only representable as unsigned short" in {
      Port.parse(65535).right.get.toInt shouldBe 65535
    }
    "not parse a number too large for unsigned short" in {
      Port.parse(65536) shouldBe a[Left[_,_]]
    }

    "not parse a negative number" in {
      Port.parse(-1) shouldBe a[Left[_,_]]
    }

  }

}
