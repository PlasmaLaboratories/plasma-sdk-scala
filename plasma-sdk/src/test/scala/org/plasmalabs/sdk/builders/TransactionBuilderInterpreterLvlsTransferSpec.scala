package org.plasmalabs.sdk.builders

import org.plasmalabs.sdk.models.box.Value
import org.plasmalabs.sdk.models.transaction.IoTransaction
import org.plasmalabs.sdk.syntax.LvlType
import org.plasmalabs.sdk.syntax.ioTransactionAsTransactionSyntaxOps
import org.plasmalabs.sdk.syntax.valueToTypeIdentifierSyntaxOps

class TransactionBuilderInterpreterLvlsTransferSpec extends TransactionBuilderInterpreterSpecBase {

  test("unsupported token type in txos is filtered out/ignored") {
    val testTx = buildTransferAmountTransaction
      .withTxos(mockTxos :+ valToTxo(Value.defaultInstance)) // Value.empty
      .run
    val expectedTx = buildTransferAmountTransaction
      .withTxos(mockTxos) // The only difference is the unsupported txo is not present
      .run
    assert(
      (testTx.isRight && expectedTx.isRight) &&
      sortedTx(testTx.toOption.get).computeId == sortedTx(expectedTx.toOption.get).computeId
    )
  }

  test("buildTransferAmountTransaction > quantity to transfer is non positive") {
    val testTx = buildTransferAmountTransaction
      .withAmount(0)
      .run
    assertEquals(testTx, Left(UserInputErrors(Seq(UserInputError(s"quantity to transfer must be positive")))))
  }

  test("buildTransferAmountTransaction > a txo isnt tied to lockPredicateFrom") {
    val testTx = buildTransferAmountTransaction
      .withTxos(mockTxos :+ valToTxo(lvlValue, trivialLockAddress))
      .run
    assertEquals(
      testTx,
      Left(UserInputErrors(Seq(UserInputError(s"every lock in the txos must correspond to lockPredicateFrom"))))
    )
  }

  test("buildTransferAmountTransaction > non sufficient funds") {
    val testTx = buildTransferAmountTransaction
      .withAmount(4)
      .run
    assertEquals(
      testTx,
      Left(
        UserInputErrors(
          Seq(
            UserInputError(
              s"All tokens selected to transfer do not have enough funds to transfer. The desired quantity to transfer is 4 but the 2 tokens selected to transfer only have a combined quantity of 2."
            ),
            UserInputError(s"Not enough LVLs in input to satisfy fee")
          )
        )
      )
    )
  }

  test("buildTransferAmountTransaction > fee not satisfied") {
    val testTx = buildTransferAmountTransaction
      .withFee(2)
      .run
    assertEquals(
      testTx,
      Left(
        UserInputErrors(
          Seq(UserInputError(s"Not enough LVLs in input to satisfy fee"))
        )
      )
    )
  }

  test("buildTransferAmountTransaction > [complex] duplicate inputs are merged and split correctly") {
    val testTx = buildTransferAmountTransaction.run
    val expectedTx = IoTransaction.defaultInstance
      .withDatum(txDatum)
      .withInputs(buildStxos())
      .withOutputs(
        // to recipient
        buildRecipientUtxos(List(lvlValue))
        ++
        // change values unaffected by recipient transfer and fee
        buildChangeUtxos(mockChange.filterNot(_.value.typeIdentifier == LvlType))
      )
    assertEquals(
      sortedTx(testTx.toOption.get).computeId,
      sortedTx(expectedTx).computeId
    )
  }

  test("buildTransferAmountTransaction > [simplest case] no change, only 1 output") {
    val txos = Seq(valToTxo(lvlValue))
    val testTx = buildTransferAmountTransaction
      .withTxos(txos)
      .withFee(0)
      .run
    val expectedTx = IoTransaction.defaultInstance
      .withDatum(txDatum)
      .withInputs(buildStxos(txos))
      .withOutputs(buildRecipientUtxos(List(lvlValue)))
    assertEquals(testTx.toOption.get.computeId, expectedTx.computeId)
  }
}
