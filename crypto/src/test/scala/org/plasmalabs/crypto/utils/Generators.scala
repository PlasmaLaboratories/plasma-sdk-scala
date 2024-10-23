package org.plasmalabs.crypto.utils

import org.scalacheck.{Arbitrary, Gen}
import org.plasmalabs.crypto.generation.mnemonic.{MnemonicSize, MnemonicSizes}

object Generators {
  def genRandomlySizedByteArray: Gen[Array[Byte]] = Gen.listOf(Arbitrary.arbitrary[Byte]).map(_.toArray)

  def genByteArrayWithBoundedSize(minSize: Int, maxSize: Int): Gen[Array[Byte]] =
    Gen
      .choose(minSize, maxSize)
      .flatMap(sz => Gen.listOfN(sz, Arbitrary.arbitrary[Byte]))
      .retryUntil(list => list.length >= minSize && list.length <= maxSize)
      .map(_.toArray)

  def genByteArrayOfSize(n: Int): Gen[Array[Byte]] =
    Gen.listOfN(n, Arbitrary.arbitrary[Byte]).retryUntil(_.length == n).map(_.toArray)

  lazy val stringGen: Gen[String] = Gen.alphaNumStr.suchThat(_.nonEmpty)

  val mnemonicSizeGen: Gen[MnemonicSize] =
    Gen.oneOf(
      MnemonicSizes.words12,
      MnemonicSizes.words15,
      MnemonicSizes.words18,
      MnemonicSizes.words21,
      MnemonicSizes.words24
    )

}
