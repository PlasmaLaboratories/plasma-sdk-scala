package org.plasmalabs.sdk.validation

import cats.Id
import cats.implicits._
import org.plasmalabs.sdk.MockHelpers
import org.plasmalabs.sdk.models.{Event, SeriesPolicy, TransactionOutputAddress}
import org.plasmalabs.sdk.models.box.Value
import org.plasmalabs.sdk.models.transaction.SpentTransactionOutput
import org.plasmalabs.sdk.models.transaction.UnspentTransactionOutput
import org.plasmalabs.sdk.syntax._

/**
 * Test to coverage this specific syntax validation: Transfer series
 *  - seriesEqualFundsValidation
 */
class TransactionSyntaxInterpreterTransferSeriesSpec extends munit.FunSuite with MockHelpers {

  private val txoAddress_1 = TransactionOutputAddress(0, 0, 1, dummyTxIdentifier)
  TransactionOutputAddress(2, 0, 0, dummyTxIdentifier)

  test("Valid data-input case, transfer a simple series ") {
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_1)

    val value_1_in: Value =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(1)))

    val value_1_out: Value =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(1)))

    val inputs = List(
      SpentTransactionOutput(txoAddress_1, attFull, value_1_in)
    )

    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, value_1_out)
    )

    val testTx = txFull.copy(inputs = inputs, outputs = outputs)

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

    assertEquals(assertError, false)
    assertEquals(result.map(_.toList.size).getOrElse(0), 0)

  }

  test("Valid data-input case 2, transfer a simple series ") {
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_1)

    val value_1_in: Value =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(2)))

    val value_1_out: Value =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(1)))

    val value_2_out: Value =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(1)))

    val inputs = List(
      SpentTransactionOutput(txoAddress_1, attFull, value_1_in)
    )

    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, value_1_out),
      UnspentTransactionOutput(trivialLockAddress, value_2_out)
    )

    val testTx = txFull.copy(inputs = inputs, outputs = outputs)

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

    assertEquals(assertError, false)
    assertEquals(result.map(_.toList.size).getOrElse(0), 0)

  }

  test("InValid data-input case 2, transfer a simple series ") {
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_1)

    val value_1_in: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(3))
      ) // check quantity

    val value_1_out: Value =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(1)))

    val value_2_out: Value =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(1)))

    val inputs = List(
      SpentTransactionOutput(txoAddress_1, attFull, value_1_in)
    )

    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, value_1_out),
      UnspentTransactionOutput(trivialLockAddress, value_2_out)
    )

    val testTx = txFull.copy(inputs = inputs, outputs = outputs)

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
