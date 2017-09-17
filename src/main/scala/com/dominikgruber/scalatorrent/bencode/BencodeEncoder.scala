package com.dominikgruber.scalatorrent.bencode

import scala.annotation.tailrec

/**
 * Takes an input and transforms it into a bencoded string.
 */
object BencodeEncoder {

  /**
   * @see [[BencodeParser.string]]
   */
  def string(str: String): Either[String, String] = {
    val l = str.length
    if (l > 0) Right(l + ":" + str)
    else Left("empty string")
  }

  /**
   * @see [[BencodeParser.integer]]
   */
  def integer(i: Long): String =
    "i" + i + "e"

  /**
   * @see [[BencodeParser.list]]
   */
  def list(l: List[Any]): Either[String, String] = {
    @tailrec
    def inner(l: List[Any], acc: String): Either[String, String] = l match {
      case head :: tail => encode(head) match {
        case Right(x) => inner(tail, acc + x)
        case Left(e) => Left(e)
      }
      case _ => Right("l" + acc + "e")
    }
    inner(l, "")
  }

  /**
   * @see [[BencodeParser.dictionary]]
   */
  def dictionary(m: Map[String,Any]): Either[String, String] = {
    @tailrec
    def inner(l: List[(String,Any)], acc: String): Either[String, String] = l match {
      case (s, v) :: tail => (apply(s), encode(v)) match {
        case (Right(str1), Right(str2)) => inner(tail, acc + str1 + str2)
        case (Left(e), Right(_)) => Left(e)
        case (Right(_), Left(e)) => Left(e)
        case (Left(e1), Left(e2)) => Left(s"$e1; $e2")
      }
      case _ => Right("d" + acc + "e")
    }
    inner(m.toList.sortBy(_._1), "")
  }

  def encode(input: Any): Either[String, String] = input match {
    case s: String => string(s)
    case l: Long => Right(integer(l))
    case i: Int => Right(integer(i))
    case l: List[Any] => list(l)
    case d: Map[_,_] => d.toList match { // Workaround for type erasure
      case ((s: String, v: Any)) :: tail => dictionary(d.asInstanceOf[Map[String,Any]])
      case other => Left(s"expected a String for key but was $other")
    }
    case other => Left(s"can't encode type of $other")
  }

  def apply(input: String): Either[String, String] =
    string(input)

  def apply(input: Int): String =
    integer(input)

  def apply(input: List[Any]): Either[String, String] =
    list(input)

  def apply(input: Map[String,Any]): Either[String, String] =
    dictionary(input)
}
