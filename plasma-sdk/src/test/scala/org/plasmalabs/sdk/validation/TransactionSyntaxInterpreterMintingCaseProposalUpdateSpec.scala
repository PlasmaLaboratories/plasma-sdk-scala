package org.plasmalabs.sdk.validation

import cats.Id
import cats.implicits._
import org.plasmalabs.sdk.MockHelpers
import org.plasmalabs.sdk.models.TransactionOutputAddress
import org.plasmalabs.sdk.models.box.Value
import org.plasmalabs.sdk.models.transaction.SpentTransactionOutput
import org.plasmalabs.sdk.models.transaction.UnspentTransactionOutput
import org.plasmalabs.sdk.syntax._
import org.plasmalabs.sdk.constants.NetworkConstants

/**
 * Test to coverage this specific syntax validation:
 *  - Validations only for minting a Proposal Updated
 *  -
 */
class TransactionSyntaxInterpreterMintingCaseProposalUpdateSpec extends munit.FunSuite with MockHelpers {

  private val txoAddress_1 =
    TransactionOutputAddress(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_LEDGER_ID, 1, dummyTxIdentifier)

  test("Valid data-input case 1, minting a proposal updated Token") {
    val value_1_in: Value =
      Value.defaultInstance.withTopl(Value.TOPL(quantity = BigInt(1), registration = None))

    val value_1_out: Value =
      Value.defaultInstance.withUpdateProposal(
        Value.UpdateProposal(label = "Proposal update 1", vrfPrecision = Some(1))
      )

    // do define if a topl should be burned or not when minting UpdateProposal
    val value_2_out: Value =
      Value.defaultInstance.withTopl(Value.TOPL(quantity = BigInt(1), registration = None))

    val inputs = List(SpentTransactionOutput(txoAddress_1, attFull, value_1_in))
    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, value_1_out),
      UnspentTransactionOutput(trivialLockAddress, value_2_out)
    )

    val testTx = txFull.copy(inputs = inputs, outputs = outputs)

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    assertEquals(result.map(_.toList.size).getOrElse(0), 0)
  }

  test("Invalid data-input case 2, minting a proposal updated Token") {
    val value_1_in: Value =
      Value.defaultInstance.withTopl(Value.TOPL(quantity = BigInt(1), registration = None))

    val value_1_out: Value =
      Value.defaultInstance.withUpdateProposal(
        Value.UpdateProposal(label = "Proposal update 1", vrfPrecision = Some(-1))
      )

    val inputs = List(SpentTransactionOutput(txoAddress_1, attFull, value_1_in))
    val outputs = List(UnspentTransactionOutput(trivialLockAddress, value_1_out))

    val testTx = txFull.copy(inputs = inputs, outputs = outputs)

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(TransactionSyntaxError.InvalidUpdateProposal(Seq(value_1_out.getUpdateProposal)))
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

}
