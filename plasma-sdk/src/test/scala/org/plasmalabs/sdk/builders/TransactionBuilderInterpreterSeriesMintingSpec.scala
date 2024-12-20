package org.plasmalabs.sdk.builders

import org.plasmalabs.quivr.models.SmallData
import org.plasmalabs.sdk.models.transaction.{IoTransaction, Schedule}
import org.plasmalabs.sdk.models.{Datum, Event}
import org.plasmalabs.sdk.models.box.Value
import org.plasmalabs.sdk.syntax.{ioTransactionAsTransactionSyntaxOps, valueToTypeIdentifierSyntaxOps, LvlType}

class TransactionBuilderInterpreterSeriesMintingSpec extends TransactionBuilderInterpreterSpecBase {

  test("Success") {
    val txRes = buildMintSeriesTransaction.run
    val expectedTx = IoTransaction.defaultInstance
      .withDatum(
        Datum.IoTransaction(
          Event
            .IoTransaction(
              Schedule(0, Long.MaxValue, System.currentTimeMillis),
              SmallData.defaultInstance
            )
            .withSeriesPolicies(Seq(mockSeriesPolicyAlt))
        )
      )
      .withInputs(buildStxos(mockTxos :+ valToTxo(lvlValue, txAddr = mockSeriesPolicyAlt.registrationUtxo)))
      .withOutputs(
        // minted output
        buildRecipientUtxos(List(seriesValueAlt))
        ++
        // fee change
        buildChangeUtxos(List(lvlValue))
        ++
        // non-lvl change (i.e, unaffected by fee and registration)
        buildChangeUtxos(mockChange.filterNot(_.value.typeIdentifier == LvlType))
      )
    assert(txRes.isRight && sortedTx(txRes.toOption.get).computeId == sortedTx(expectedTx).computeId)
  }

  test("input txos do not contain registrationUtxo") {
    val testTx = buildMintSeriesTransaction
      .withPolicy(mockSeriesPolicy.copy(registrationUtxo = dummyTxoAddress.copy(network = 10)))
      .run
    assertEquals(
      testTx,
      Left(
        UserInputErrors(
          Seq(UserInputError("Input TXOs need to contain exactly one txo matching the registrationUtxo"))
        )
      )
    )
  }

  test("unsupported token type in txos is filtered out/ignored") {
    val testTx = buildMintSeriesTransaction
      .addTxo(valToTxo(Value.defaultInstance)) // Value.empty
      .run
    val expectedTx = buildMintSeriesTransaction.run // The only difference is the unsupported txo is not present
    assert(
      (testTx.isRight && expectedTx.isRight) &&
      sortedTx(testTx.toOption.get).computeId == sortedTx(expectedTx.toOption.get).computeId
    )
  }

  test("registrationUtxo does not contain lvls") {
    val newAddr = dummyTxoAddress.copy(network = 10)
    val testTx = buildMintSeriesTransaction
      .addTxo(valToTxo(groupValue, txAddr = newAddr))
      .withPolicy(mockSeriesPolicy.copy(registrationUtxo = newAddr))
      .run
    assertEquals(
      testTx,
      Left(
        UserInputErrors(
          Seq(UserInputError("registrationUtxo does not contain LVLs"))
        )
      )
    )
  }

  test("all txos do not have the right lock predicate") {
    val testTx = buildMintSeriesTransaction
      .addTxo(valToTxo(groupValue, trivialLockAddress))
      .run
    assertEquals(
      testTx,
      Left(
        UserInputErrors(
          Seq(UserInputError("every lock in the txos must correspond to lockPredicateFrom"))
        )
      )
    )
  }

  test("quantity to mint is non-positive") {
    val testTx = buildMintSeriesTransaction
      .withMintAmount(0)
      .run
    assertEquals(
      testTx,
      Left(
        UserInputErrors(
          Seq(UserInputError("quantityToMint must be positive"))
        )
      )
    )
  }

  test("not enough lvls for fee") {
    val testTx = buildMintSeriesTransaction
      .withFee(4)
      .run
    assertEquals(
      testTx,
      Left(
        UserInputErrors(
          Seq(UserInputError("Not enough LVLs in input to satisfy fee"))
        )
      )
    )
  }
}
