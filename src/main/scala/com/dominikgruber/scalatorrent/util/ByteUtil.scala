package com.dominikgruber.scalatorrent.util

import akka.util.ByteString

import scala.collection.mutable.ArrayBuffer

object ByteUtil {

  object Hex {
    def apply(buf: Array[Byte]): String = buf.map("%02X" format _).mkString(" ")
    def apply(buf: Vector[Byte]): String = apply(buf.toArray)
    def apply(buf: ArrayBuffer[Byte]): String = apply(buf.toArray)
    def apply(buf: ByteString): String = apply(buf.toArray)
  }

  def bytes(str: String): Array[Byte] = {
    str.split(" ").map(Integer.parseInt(_, 16).toByte)
  }

}
