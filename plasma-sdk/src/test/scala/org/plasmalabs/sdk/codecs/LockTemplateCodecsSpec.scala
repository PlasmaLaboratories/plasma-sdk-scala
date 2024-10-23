package org.plasmalabs.sdk.codecs

import cats.Id
import org.plasmalabs.sdk.builders.locks.LockTemplate
import org.plasmalabs.sdk.builders.locks.LockTemplate.PredicateTemplate
import org.plasmalabs.sdk.codecs.LockTemplateCodecs.{decodeLockTemplate, encodeLockTemplate}
import io.circe.Json

class LockTemplateCodecsSpec extends munit.FunSuite with PropositionTemplateCodecsSpecBase {

  def assertEncodeDecode[TemplateType <: LockTemplate[Id]](expectedValue: TemplateType, expectedJson: Json): Unit = {
    // Decode test
    val testPropositionRes = decodeLockTemplate[Id](expectedJson)
    assert(testPropositionRes.isRight)
    val templateInstance = testPropositionRes.toOption.get.asInstanceOf[TemplateType]
    assertEquals(templateInstance, expectedValue)

    // Encode test
    val testJson = encodeLockTemplate(expectedValue)
    assert(!testJson.isNull)
    assertEquals(testJson, expectedJson)

    // Decode then Encode test
    val encodedFromDecoded = encodeLockTemplate(templateInstance)
    assert(!encodedFromDecoded.isNull)
    assertEquals(encodedFromDecoded, expectedJson)

    // Encode then Decode test
    val decodedFromEncoded = decodeLockTemplate[Id](testJson)
    assert(decodedFromEncoded.isRight)
    val templateInstanceFromEncoded = testPropositionRes.toOption.get.asInstanceOf[TemplateType]
    assertEquals(templateInstanceFromEncoded, expectedValue)
  }

  test("Encode and Decode Predicate Lock Template") {
    assertEncodeDecode[PredicateTemplate[Id]](ExpectedPredicateLock.value, ExpectedPredicateLock.json)
  }
}
