package org.plasmalabs.sdk.utils

import cats.effect.IO
import cats.effect.Sync
import cats.implicits._
import munit.CatsEffectSuite

class CatsUnsafeResourceSpec extends CatsEffectSuite {

  import cats.effect.implicits._

  type F[A] = IO[A]

  test("give thread safety to mutable data") {
    val a1 = Array.fill(16)(0: Byte)
    val a2 = Array.fill(16)(1: Byte)
    for {
      underTest <- CatsUnsafeResource.make[F, MutableResource](new MutableResource(16), 1)
      r1IO = underTest.use(d => Sync[F].delay { d.setBytesSlowly(a1); d.getArrayCopy })
      r2IO = underTest.use(d => Sync[F].delay { d.setBytesSlowly(a2); d.getArrayCopy })
      entry <- (r1IO, r2IO).parTupled
      (r1, r2) = entry
      _ = assert(r1 sameElements a1)
      _ = assert(r2 sameElements a2)
    } yield ()
  }

}

private class MutableResource(length: Int) {
  private val array = new Array[Byte](length)
  def set(index: Int, byte: Byte): Unit = array(index) = byte

  def setBytesSlowly(newBytes: Array[Byte]): Unit =
    newBytes.zipWithIndex.foreach { case (byte, idx) =>
      set(idx, byte)
      Thread.sleep(100)
    }
  def getArrayCopy: Array[Byte] = array.clone()
}
