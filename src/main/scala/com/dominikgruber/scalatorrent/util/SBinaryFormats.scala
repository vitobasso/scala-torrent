package com.dominikgruber.scalatorrent.util

import sbinary.Operations.{fromByteArray, read, toByteArray, write}
import sbinary._
import shapeless._
import shapeless.ops.function.FnToProduct

object SBinaryFormats extends ByteFormats with GenericFormats with HListFormats {

  def fromBytesVia[A, F, L <: HList](assemble: F)(b: Array[Byte])(
    implicit fp: FnToProduct.Aux[F, L => A], formatL: Reads[L]): A =
    fromByteArray(b)(readsVia[F, A, L](assemble))

  def toBytesVia[A, Raw, L <: HList](disassemble: A => Raw)(v: A)(
    implicit gen: Generic.Aux[Raw, L], formatL: Writes[L]): Array[Byte] =
    toByteArray(v)(writesVia[Raw, A, L](disassemble))

}

trait ByteFormats {

  type Bytes = Array[Byte]

  def formatAsBytes[A](nBytes: Int)(disassemble: A => Bytes, assemble: Bytes => A) = new Format[A] {
    override def reads(in: Input): A = readsAsBytes(nBytes)(assemble).reads(in)
    override def writes(out: Output, value: A): Unit = writesAsBytes(nBytes)(disassemble).writes(out, value)
  }

  def readsAsBytes[A](nBytes: Int)(assemble: Bytes => A) = new Reads[A] {
    override def reads(in: Input): A =
      assemble(read(in)(readsNBytes(nBytes)))
  }
  def writesAsBytes[A](nBytes: Int)(disassemble: A => Bytes) = new Writes[A] {
    override def writes(out: Output, value: A): Unit =
      write(out, disassemble(value))(writesNBytes(nBytes))
  }

  def writesNBytes(n: Int) = new Writes[Bytes] {
    override def writes(out: Output, value: Bytes): Unit = writeNBytes(n, out, value)
  }
  def readsNBytes(n: Int) = new Reads[Bytes] {
    override def reads(in: Input): Bytes = readNBytes(n, in)
  }

  import shapeless.ops.nat._
  def readsSizedBytes[N <: Nat: ToInt] = new Reads[Sized[Bytes, N]] {
    override def reads(in: Input): Sized[Bytes, N] = Sized.wrap(readNBytes(Nat.toInt[N], in))
  }
  def writesSizedBytes[N <: Nat: ToInt] = new Writes[Sized[Bytes, N]] {
    override def writes(out: Output, value: Sized[Bytes, N]): Unit = writeNBytes(Nat.toInt[N], out, value)
  }

  private def readNBytes(n: Int, in: Input): Bytes =
    (Array.emptyByteArray /: (0 until n) ) {
      (acc, _) => acc :+ in.readByte
    }
  private def writeNBytes(n: Int, out: Output, value: Bytes): Unit = {
    require(value.length == n)
    out.writeAll(value)
  }

}

trait GenericFormats {

  def readsVia[F, R, L <: HList](assemble: F)(
    implicit fp: FnToProduct.Aux[F, L => R], formatL: Reads[L]) = new Reads[R] {
    override def reads(in: Input): R = fp(assemble)(formatL.reads(in))
  }

  def writesVia[P, R, L <: HList](disassemble: R => P)(
    implicit gen: Generic.Aux[P, L], formatL: Writes[L]) = new Writes[R] {
    override def writes(out: Output, value: R): Unit =
      formatL.writes(out, gen.to(disassemble(value)))
  }

  def readsToEnd[A](implicit readsA: Reads[A]) = new Reads[List[A]] {
    override def reads(in: Input): List[A] =
      try {
        readsA.reads(in) :: reads(in)
      } catch {
        case EOF() => Nil
      }
  }

}

trait HListFormats extends LowPriorityHListFormats {

  implicit def readsHList[Head, Tail <: HList](implicit
                                               readsHead: Lazy[Reads[Head]],
                                               readsTail: Reads[Tail]) =
    new Reads[Head :: Tail] {
      override def reads(in: Input): Head :: Tail =
        readsHead.value.reads(in) :: readsTail.reads(in)
    }

  implicit def writesHList[Head, Tail <: HList](implicit
                                                writesHead: Lazy[Writes[Head]],
                                                writesTail: Writes[Tail]) =
    new Writes[Head :: Tail] {
      override def writes(out: Output, value: Head :: Tail): Unit = {
        writesHead.value.writes(out, value.head)
        writesTail.writes(out, value.tail)
      }
    }

}

/**
  * Lower priority than Reads/Writes[HList], to avoid ambiguity
  */
trait LowPriorityHListFormats {

  implicit val formatHNil: Format[HNil] = new Format[HNil] {
    override def reads(in: Input): HNil = HNil
    override def writes(out: Output, value: HNil): Unit = ()
  }

  def formatHList[Head, Tail <: HList](implicit
                                        r: Reads[HList],
                                        w: Writes[HList]
                                       ) = format(r,w)

  def format[A](r: Reads[A], w: Writes[A]): Format[A] =
    new Format[A] {
      override def reads(in: Input): A = r.reads(in)
      override def writes(out: Output, value: A): Unit = w.writes(out, value)
    }

}
