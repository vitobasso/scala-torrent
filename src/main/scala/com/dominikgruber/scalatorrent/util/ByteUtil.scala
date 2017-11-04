package com.dominikgruber.scalatorrent.util

import java.nio.charset.StandardCharsets.ISO_8859_1

import akka.util.ByteString

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

object ByteUtil {

  type Bytes = Array[Byte]

  object Hex {
    def apply(buf: Bytes): String = buf.map("%02X" format _).mkString(" ")
    def apply(buf: Vector[Byte]): String = apply(buf.toArray)
    def apply(buf: ArrayBuffer[Byte]): String = apply(buf.toArray)
    def apply(buf: ByteString): String = apply(buf.toArray)
  }

  def bytes(str: String): Bytes =
    str.split(" ").map(Integer.parseInt(_, 16).toByte)

  def unsignedByte(b: Byte): Short = (0xff & b).toShort
  def unsignedShort(s: Short): Int = 0xffff & s

  def sha1Hash(bytes: Bytes): Bytes = {
    val md = java.security.MessageDigest.getInstance("SHA-1")
    md.digest(bytes)
  }

  def randomString(nBytes: Int): String = {
    val bytes = Array.fill[Byte](nBytes)(0)
    Random.nextBytes(bytes)
    new String(bytes, ISO_8859_1)
  }

}
