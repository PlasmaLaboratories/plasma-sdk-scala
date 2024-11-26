package org.plasmalabs.sdk.validation

import cats.Id
import cats.implicits._
import org.plasmalabs.sdk.MockHelpers
import org.plasmalabs.sdk.constants.NetworkConstants.{MAIN_NETWORK_ID, TEST_NETWORK_ID}
import org.plasmalabs.sdk.models.box.{Attestation, Challenge, Lock, Value}
import org.plasmalabs.sdk.models.transaction.Schedule
import com.google.protobuf.ByteString
import org.plasmalabs.quivr.models.{Int128, Proof, Proposition}
import org.plasmalabs.quivr.api.{Proposer, Prover}
import org.plasmalabs.sdk.models.Datum
import org.plasmalabs.sdk.models.Event
import org.plasmalabs.quivr.models.SmallData
import org.plasmalabs.sdk.models.GroupPolicy
import org.plasmalabs.sdk.syntax._
import org.plasmalabs.sdk.models.TransactionOutputAddress
import org.plasmalabs.sdk.constants.NetworkConstants.MAIN_LEDGER_ID
import org.plasmalabs.sdk.constants.NetworkConstants.PRIVATE_NETWORK_ID
import org.plasmalabs.sdk.models.transaction.SpentTransactionOutput
import org.plasmalabs.sdk.models.transaction.UnspentTransactionOutput

class TransactionSyntaxInterpreterSpec extends munit.FunSuite with MockHelpers {

  test("type 0 tx: validate non-empty inputs") {
    val testTx = txFull.copy(inputs = List())
    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator
      .validate(testTx)
      .swap
      .exists(_.toList.contains(TransactionSyntaxError.EmptyInputs))
    assertEquals(result, true)
  }

  test("type 0 tx: validate distinct inputs") {
    val testTx = txFull.copy(inputs = List(inputFull, inputFull))
    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator
      .validate(testTx)
      .swap
      .exists(_.toList.contains(TransactionSyntaxError.DuplicateInput(inputFull.address)))
    assertEquals(result, true)
  }

  test("type 0 tx: validate maximum outputs count") {
    val testTx = txFull.copy(outputs = Vector.fill(Short.MaxValue)(output))
    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator
      .validate(testTx)
      .swap
      .exists(_.toList.contains(TransactionSyntaxError.ExcessiveOutputsCount))
    assertEquals(result, true)
  }

  test("type 0 tx: validate positive timestamp") {
    val testTx = txFull.copy(datum =
      txDatum
        .copy(
          event = txDatum.event.copy(schedule = Schedule(3, 50, -1))
        )
    )
    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator
      .validate(testTx)
      .swap
      .exists(_.toList.contains(TransactionSyntaxError.InvalidTimestamp(-1)))
    assertEquals(result, true)
  }

  test("type 0 tx: validate schedule") {
    val invalidSchedules = List(Schedule(5, 4, 100), Schedule(-5, -1, 100), Schedule(-1, 0, 100), Schedule(-1, 1, 100))
    val result = invalidSchedules
      .map { schedule =>
        val testTx = txFull.copy(datum =
          txDatum
            .copy(
              event = txDatum.event.copy(schedule = schedule)
            )
        )
        val validator = TransactionSyntaxInterpreter.make[Id]()
        validator
          .validate(testTx)
          .swap
          .exists(_.toList.contains(TransactionSyntaxError.InvalidSchedule(schedule)))
      }
      .forall(identity)
    assertEquals(result, true)
  }

  test("type 0 tx: validate positive output quantities") {
    val negativeValue: Value =
      Value.defaultInstance.withLvl(Value.LVL(Int128(ByteString.copyFrom(BigInt(-1).toByteArray))))
    val testTx = txFull.copy(outputs = Seq(output.copy(value = negativeValue)))
    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator
      .validate(testTx)
      .swap
      .exists(_.toList.contains(TransactionSyntaxError.NonPositiveOutputValue(negativeValue)))
    assertEquals(result, true)
  }

  test("type 0 tx: validate sufficient input funds") {
    val tokenValueIn: Value =
      Value.defaultInstance.withLvl(Value.LVL(Int128(ByteString.copyFrom(BigInt(100).toByteArray))))
    val tokenValueOut: Value =
      Value.defaultInstance.withLvl(Value.LVL(Int128(ByteString.copyFrom(BigInt(101).toByteArray))))

    def testTx(inputValue: Value, outputValue: Value) = TransactionSyntaxInterpreter
      .make[Id]()
      .validate(
        txFull.copy(
          inputs = txFull.inputs.map(_.copy(value = inputValue)),
          outputs = Seq(output.copy(value = outputValue))
        )
      )
      .swap
      .exists(
        _.toList
          .contains(TransactionSyntaxError.InsufficientInputFunds(List(inputValue.value), List(outputValue.value)))
      )

    val result = List(
      testTx(tokenValueIn, tokenValueOut) // Token Test
    ).forall(identity)
    assertEquals(result, true)
  }

  test("type 0 tx: validate proof types: Lock.Predicate") {
    val propositions: Seq[Proposition] = List(
      Proposer.LockedProposer[Id].propose(None),
      Proposer.heightProposer[Id].propose(("header", 0, 100)),
      Proposer.tickProposer[Id].propose((0, 100)),
      Proposer.tickProposer[Id].propose((0, 100))
    )
    val responses: Seq[Proof] = List(
      Prover.heightProver[Id].prove((), fakeMsgBind), // Mismatched
      Prover.heightProver[Id].prove((), fakeMsgBind), // Matched
      Proof() // Empty proof
      // Missing a Proof
    )
    val testTx = txFull.copy(inputs =
      txFull.inputs.map(
        _.copy(attestation =
          Attestation().withPredicate(
            Attestation.Predicate(Lock.Predicate(propositions.map(Challenge().withRevealed), 1), responses)
          )
        )
      )
    )

    def testError(error: TransactionSyntaxError) = error match {
      case TransactionSyntaxError.InvalidProofType(challenge, response) =>
        // First challenge is mismatched so we expect the error
        if (challenge == propositions.head && response == responses.head) true else false
      case _ => false // We don't expect any other errors
    }

    val result = TransactionSyntaxInterpreter
      .make[Id]()
      .validate(testTx)
      .swap
      .forall(_.toList.map(testError).forall(identity))
    assertEquals(result, true)
  }

  test("type 0 tx: Invalid data-length transaction > MaxDataLength ") {
    ByteString.copyFrom(Array.fill(TransactionSyntaxInterpreter.MaxDataLength + 1)(1.toByte))
    val testTx = txFull.copy(outputs = List.fill(5000)(output))

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator
      .validate(testTx)
      .swap
      .exists(_.toList.contains(TransactionSyntaxError.InvalidDataLength))
    assertEquals(result, true)
  }

  test("type 0 tx: Mismatched Network IDs ") {
    val inputs = txFull.inputs.map(in => in.copy(address = in.address.withNetwork(MAIN_NETWORK_ID)))
    val outputs = txFull.outputs.map(out => out.copy(address = out.address.withNetwork(MAIN_NETWORK_ID)))
    val testTx = txFull.copy(
      outputs = outputs :+ outputs.head.copy(address = outputs.head.address.withNetwork(TEST_NETWORK_ID)),
      inputs = inputs.head.copy(address = inputs.head.address.withNetwork(TEST_NETWORK_ID)) +: inputs
    )
    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap
    assertEquals(
      result.exists(
        _.toList.contains(TransactionSyntaxError.InconsistentNetworkIDs(Set(MAIN_NETWORK_ID, TEST_NETWORK_ID)))
      ),
      true
    )
  }

  test("type 1 tx: validate distinct inputs") {
    val testTx = txFull.copy(inputs = List(inputFull, inputFull), outputs = List(outputAccountLedger0))
    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator
      .validate(testTx)
      .swap
      .exists(_.toList.contains(TransactionSyntaxError.DuplicateInput(inputFull.address)))
    assertEquals(result, true)
  }

  test("type 1 tx: validate one output") {
    val testTx =
      txFull.copy(inputs = List(inputFull, inputFull), outputs = List(outputAccountLedger0, outputAccountLedger1))
    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator
      .validate(testTx)
      .swap
      .exists(_.toList.contains(TransactionSyntaxError.InvalidAccountLedgerOutputNumber))
    assertEquals(result, true)
  }

  test("type 1 tx: no statements") {

    val txoAddress_1 = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 1, dummyTxIdentifier)
    val groupPolicy = GroupPolicy(label = "groupLabelA", registrationUtxo = txoAddress_1)
    val value_1_in: Value =
      Value.defaultInstance.withLvl(
        Value.LVL(
          quantity = BigInt(1)
        )
      )

    val value_1_out: Value =
      Value.defaultInstance.withGroup(
        Value.Group(
          groupId = groupPolicy.computeId,
          quantity = BigInt(1)
        )
      )

    val input_1 = SpentTransactionOutput(txoAddress_1, attFull, value_1_in)
    val output_1: UnspentTransactionOutput = UnspentTransactionOutput(trivialLockAddress, value_1_out)

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(groupPolicies = Seq(groupPolicy)))

    val testTx = txFull.copy(inputs = List(input_1), outputs = List(outputAccountLedger0), datum = datum)

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator
      .validate(testTx)
      .swap
      .exists(_.toList.contains(TransactionSyntaxError.NoStatementsAllowed))
    assertEquals(result, true)
  }

  test("type 1 tx: validate right output address") {
    val testTx =
      txFull.copy(inputs = List(inputFull, inputFull), outputs = List(outputAccountLedger2))
    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator
      .validate(testTx)
      .swap
      .exists(_.toList.contains(TransactionSyntaxError.InvalidAccountLedgerAddress))
    assertEquals(result, true)
  }

  test("type 1 tx: validate right output type") {
    val testTx =
      txFull.copy(inputs = List(inputFull, inputFull), outputs = List(outputAccountLedger3))
    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator
      .validate(testTx)
      .swap
      .exists(_.toList.contains(TransactionSyntaxError.InvalidAccountLedgerAsset))
    assertEquals(result, true)
  }

  test("type 1 tx: validate all rules, correct transaction") {
    // no fee
    val testTx0 =
      txFull.copy(inputs = List(inputFullAccountLedger0), outputs = List(outputAccountLedger4))
    val validator = TransactionSyntaxInterpreter.make[Id]()
    assert(validator.validate(testTx0).isRight, true)
    // fee
    val testTx1 =
      txFull.copy(inputs = List(inputFullAccountLedger1), outputs = List(outputAccountLedger4))
    println(validator.validate(testTx1))
    assert(validator.validate(testTx1).isRight, true)
  }

}
