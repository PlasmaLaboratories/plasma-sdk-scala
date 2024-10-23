package org.plasmalabs.crypto.generation.mnemonic

import cats.implicits._
import org.plasmalabs.crypto.generation.mnemonic.Language._
import org.plasmalabs.crypto.utils.Generators
import org.scalatest.matchers.should.Matchers._
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.{ScalaCheckDrivenPropertyChecks, ScalaCheckPropertyChecks}
import org.plasmalabs.crypto.generation.mnemonic.{Entropy, Language, Phrase}

class LanguageSpec extends AnyPropSpec with ScalaCheckPropertyChecks with ScalaCheckDrivenPropertyChecks {

  val languages: Seq[Language] = Seq(
    English,
    ChineseSimplified,
    ChineseTraditional,
    Portuguese,
    Czech,
    Spanish,
    Italian,
    French,
    Japanese,
    Korean
  )

  languages.foreach { lang =>
    property(s"${lang.toString} should have a valid checksum with included word list") {
      LanguageWordList.validated(lang).valueOr(err => throw new Error(s"Invalid word list: $err"))
    }

    property(s"phrases should be generated in ${lang.toString}") {
      forAll(Generators.mnemonicSizeGen) { mnemonicSize =>
        val entropy = Entropy.generate(mnemonicSize)

        Phrase.fromEntropy(entropy, mnemonicSize, lang).isRight shouldBe true
      }
    }
  }
}
