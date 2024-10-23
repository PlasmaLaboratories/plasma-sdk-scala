package org.plasmalabs.sdk.validation

import cats.Id
import cats.implicits._
import org.plasmalabs.sdk.MockHelpers
import org.plasmalabs.sdk.models.Datum
import org.plasmalabs.sdk.models.Event
import org.plasmalabs.sdk.models.TransactionOutputAddress
import org.plasmalabs.sdk.models.box.Value
import org.plasmalabs.sdk.models.transaction.SpentTransactionOutput
import org.plasmalabs.sdk.models.transaction.UnspentTransactionOutput
import org.plasmalabs.sdk.syntax._

/**
 * Test to coverage this specific syntax validation:
 *  - Validations only for minting, After projection  (only if for all inputs and outputs isMint == true)
 *    Case 2: Series
 *  - policy is attached to transaction
 *  - at least 1 series token is minted
 *  - reference in policy contains LVLs (> 0)
 */
class TransactionSyntaxInterpreterMintingCaseBSpec extends munit.FunSuite with MockHelpers {

  private val txoAddress_1 = TransactionOutputAddress(0, 0, 1, dummyTxIdentifier)
  private val txoAddress_2 = TransactionOutputAddress(0, 0, 2, dummyTxIdentifier)

  test("Valid data-input case 1, minting a Series constructor Token") {
    val seriesPolicy = Event.SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_1)
    val value_1_in: Value =
      Value.defaultInstance.withLvl(
        Value.LVL(
          quantity = BigInt(1)
        )
      )

    val value_1_out: Value =
      Value.defaultInstance.withSeries(
        Value.Series(
          seriesId = seriesPolicy.computeId,
          quantity = BigInt(1)
        )
      )

    val input_1 = SpentTransactionOutput(txoAddress_1, attFull, value_1_in)
    val output_1: UnspentTransactionOutput = UnspentTransactionOutput(trivialLockAddress, value_1_out)

    val testTx = txFull.copy(
      inputs = List(input_1),
      outputs = List(output_1),
      seriesPolicies = List(Datum.SeriesPolicy(seriesPolicy))
    )

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.DuplicateInput(txoAddress_1)
      )
    )
    assertEquals(assertError, false)
    assertEquals(result.map(_.toList.size).getOrElse(0), 0)

  }

  /**
   * Case 2 validations that are failing;
   * reference in policy contains LVLs (> 0)
   */
  test("Invalid data-input case 2, minting a Series constructor Token") {
    val seriesPolicy = Event.SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_1)
    val value_1_in: Value =
      Value.defaultInstance.withLvl(
        Value.LVL(
          quantity = BigInt(0)
        )
      )

    val value_1_out: Value =
      Value.defaultInstance.withSeries(
        Value.Series(
          seriesId = seriesPolicy.computeId,
          quantity = BigInt(1)
        )
      )

    val input_1 = SpentTransactionOutput(txoAddress_1, attFull, value_1_in)
    val output_1: UnspentTransactionOutput = UnspentTransactionOutput(trivialLockAddress, value_1_out)

    val testTx = txFull.copy(
      inputs = List(input_1),
      outputs = List(output_1),
      seriesPolicies = List(Datum.SeriesPolicy(seriesPolicy))
    )

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.InsufficientInputFunds(
          testTx.inputs.map(_.value.value).toList,
          testTx.outputs.map(_.value.value).toList
        )
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)

  }

  /**
   * Case 2 validations that are failing;
   * reference in policy contains LVLs (> 0), registrationUtxo on policy is different that input_1
   */
  test("Invalid data-input case 3, minting a Series constructor Token") {
    val seriesPolicy = Event.SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_2)
    val value_1_in: Value =
      Value.defaultInstance.withLvl(
        Value.LVL(
          quantity = BigInt(0)
        )
      )

    val value_1_out: Value =
      Value.defaultInstance.withSeries(
        Value.Series(
          seriesId = seriesPolicy.computeId,
          quantity = BigInt(1)
        )
      )

    val input_1 = SpentTransactionOutput(txoAddress_1, attFull, value_1_in)
    val output_1: UnspentTransactionOutput = UnspentTransactionOutput(trivialLockAddress, value_1_out)

    val testTx = txFull.copy(
      inputs = List(input_1),
      outputs = List(output_1),
      seriesPolicies = List(Datum.SeriesPolicy(seriesPolicy))
    )

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.InsufficientInputFunds(
          testTx.inputs.map(_.value.value).toList,
          testTx.outputs.map(_.value.value).toList
        )
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)

  }

}
