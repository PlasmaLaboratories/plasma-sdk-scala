package org.plasmalabs.sdk.validation

import cats.Id
import cats.implicits._
import org.plasmalabs.sdk.MockHelpers
import org.plasmalabs.sdk.models.{
  AssetMintingStatement,
  Datum,
  Event,
  GroupPolicy,
  SeriesPolicy,
  TransactionOutputAddress
}
import org.plasmalabs.sdk.models.box.Value
import org.plasmalabs.sdk.models.transaction.SpentTransactionOutput
import org.plasmalabs.sdk.models.transaction.UnspentTransactionOutput
import org.plasmalabs.sdk.syntax._
import org.plasmalabs.sdk.constants.NetworkConstants.PRIVATE_NETWORK_ID
import org.plasmalabs.sdk.constants.NetworkConstants.MAIN_LEDGER_ID

/**
 * Test to coverage this specific syntax validation:
 *  - Rule B - For all assets minting statement ams1, ams2, ...,  Should not contain repeated UTXOs
 *  - For all group/series policies gp1, gp2, ..., ++ sp1, sp2, ..., Should not contain repeated UTXOs
 */
class TransactionSyntaxInterpreterRuleBSpec extends munit.FunSuite with MockHelpers {

  private val txoAddress_1 = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 1, dummyTxIdentifier)
  private val txoAddress_2 = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 2, dummyTxIdentifier)

  /**
   * In this case there 2 validations that are failing;
   * DuplicateInput because input contains the same txoAddress
   * DuplicateInput because minting statements contains the same txoAddress
   */
  test("Invalid data-input case, input(0) + minted1 == output1, input and asset mining statements are duplicated") {
    val groupPolicy = GroupPolicy(label = "groupLabelA", registrationUtxo = txoAddress_1)
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_1)
    val value_1_in =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = 1))

    val value_2_in =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy.computeId, quantity = 1))

    val value_1_out =
      Value.defaultInstance.withAsset(
        Value.Asset(groupId = Some(groupPolicy.computeId), seriesId = Some(seriesPolicy.computeId), quantity = 1)
      )
    val value_2_out =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = 1))

    val value_3_out =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy.computeId, quantity = 1))

    // Note: duplicate sto address
    val inputs = List(
      SpentTransactionOutput(txoAddress_1, attFull, value_1_in),
      SpentTransactionOutput(txoAddress_1, attFull, value_2_in)
    )

    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, value_1_out),
      UnspentTransactionOutput(trivialLockAddress, value_2_out),
      UnspentTransactionOutput(trivialLockAddress, value_3_out)
    )

    // Note: duplicate minting statements
    val mintingStatements = List(
      AssetMintingStatement(groupTokenUtxo = txoAddress_1, seriesTokenUtxo = txoAddress_1, quantity = 1)
    )

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatements))
    val testTx = txFull.copy(inputs = inputs, outputs = outputs, datum = datum)

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    val assertError = result.exists(_.toList.contains(TransactionSyntaxError.DuplicateInput(txoAddress_1)))
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 2)

  }

  /**
   * In this case there 2 validations that are failing;
   * InsufficientInputFunds, because is not able to pass assetEqualFundsValidation
   * InsufficientInputFunds, because is not able to pass mintingValidation
   * DuplicateInput because minting statements contains the same txoAddress
   */
  test("Invalid data-input case, input(0) + minted1 == output1, asset mining statements are duplicated") {
    val groupPolicy = GroupPolicy(label = "groupLabelA", registrationUtxo = txoAddress_1)
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_2)
    val value_1_in =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = 1))

    val value_2_in =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy.computeId, quantity = 1))

    val value_1_out =
      Value.defaultInstance.withAsset(
        Value.Asset(groupId = Some(groupPolicy.computeId), seriesId = Some(seriesPolicy.computeId), quantity = 1)
      )

    val value_2_out =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = 1))

    val value_3_out =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy.computeId, quantity = 1))

    val inputs = List(
      SpentTransactionOutput(txoAddress_1, attFull, value_1_in),
      SpentTransactionOutput(txoAddress_2, attFull, value_2_in)
    )
    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, value_1_out),
      UnspentTransactionOutput(trivialLockAddress, value_2_out),
      UnspentTransactionOutput(trivialLockAddress, value_3_out)
    )

    // Note: duplicate minting statements
    val mintingStatements = List(
      AssetMintingStatement(
        groupTokenUtxo = txoAddress_1,
        seriesTokenUtxo = txoAddress_1,
        quantity = 1
      )
    )

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatements))
    val testTx = txFull.copy(inputs = inputs, outputs = outputs, datum = datum)

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    val assertError = result.exists(_.toList.contains(TransactionSyntaxError.DuplicateInput(txoAddress_1)))

    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 3)

  }

  /**
   * In this case there only 1 validation is failing, but contains 2 items;
   * DuplicateInput because minting statements contains the same txoAddress
   */
  test("Invalid data-input case, input(0) + minted1 == output1, asset mining statements are duplicated, case 2") {
    val groupPolicy = GroupPolicy(label = "groupLabelA", registrationUtxo = txoAddress_1)
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_2)
    val value_1_in =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = 1))

    val value_2_in =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy.computeId, quantity = 1))

    val value_1_out =
      Value.defaultInstance.withAsset(
        Value.Asset(groupId = Some(groupPolicy.computeId), seriesId = Some(seriesPolicy.computeId), quantity = 1)
      )

    val value_2_out =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = 1))

    val value_3_out =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy.computeId, quantity = 1))

    val inputs = List(
      SpentTransactionOutput(txoAddress_1, attFull, value_1_in),
      SpentTransactionOutput(txoAddress_2, attFull, value_2_in)
    )

    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, value_1_out),
      UnspentTransactionOutput(trivialLockAddress, value_2_out),
      UnspentTransactionOutput(trivialLockAddress, value_3_out)
    )

    // Note: duplicate minting statements, but in two statements mintingStatement_1 and mintingStatement_2
    val mintingStatement_1 =
      AssetMintingStatement(groupTokenUtxo = txoAddress_1, seriesTokenUtxo = txoAddress_2, quantity = 1)

    val mintingStatement_2 =
      AssetMintingStatement(groupTokenUtxo = txoAddress_1, seriesTokenUtxo = txoAddress_2, quantity = 1)

    val mintingStatements = List(mintingStatement_1, mintingStatement_2)

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatements))
    val testTx = txFull.copy(inputs = inputs, outputs = outputs, datum = datum)

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    val assertError = result.exists(_.toList.contains(TransactionSyntaxError.DuplicateInput(txoAddress_1)))

    val assertError_2 = result.exists(_.toList.contains(TransactionSyntaxError.DuplicateInput(txoAddress_2)))

    assertEquals(assertError, true)
    assertEquals(assertError_2, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 2)

  }

  test("Invalid data-input, minting a Group constructor Token") {
    val groupPolicy = GroupPolicy(label = "groupLabelA", registrationUtxo = txoAddress_1)
    val value_1_in =
      Value.defaultInstance.withLvl(Value.LVL(quantity = 1))

    val value_1_out =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = 1))

    val inputs = List(SpentTransactionOutput(txoAddress_1, attFull, value_1_in))
    val outputs = List(UnspentTransactionOutput(trivialLockAddress, value_1_out))

    // policies contains same referenced Utxos
    val groupPolicies = List(groupPolicy, groupPolicy)

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(groupPolicies = groupPolicies))
    val testTx = txFull.copy(inputs = inputs, outputs = outputs, datum = datum)

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    val assertError = result.exists(_.toList.contains(TransactionSyntaxError.DuplicateInput(txoAddress_1)))
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)

  }

  test("Invalid data-input, minting a Group constructor Token") {
    val groupPolicy_A = GroupPolicy(label = "groupLabelA", registrationUtxo = txoAddress_1)
    val groupPolicy_B = GroupPolicy(label = "groupLabelB", registrationUtxo = txoAddress_1)
    val value_1_in =
      Value.defaultInstance.withLvl(Value.LVL(quantity = 1))

    val value_1_out =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy_A.computeId, quantity = 1))

    val value_2_out =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy_B.computeId, quantity = 1))

    val inputs = List(SpentTransactionOutput(txoAddress_1, attFull, value_1_in))
    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, value_1_out),
      UnspentTransactionOutput(trivialLockAddress, value_2_out)
    )

    // policies contains same referenced Utxos
    val groupPolicies = List(groupPolicy_A, groupPolicy_B)

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(groupPolicies = groupPolicies))
    val testTx = txFull.copy(inputs = inputs, outputs = outputs, datum = datum)

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.DuplicateInput(txoAddress_1)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)

  }

  test("Invalid data-input, minting a Series constructor Token") {
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_1)
    val value_1_in =
      Value.defaultInstance.withLvl(Value.LVL(quantity = 1))

    val value_1_out =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy.computeId, quantity = 1))

    val inputs = List(SpentTransactionOutput(txoAddress_1, attFull, value_1_in))
    val outputs = List(UnspentTransactionOutput(trivialLockAddress, value_1_out))
    // policies contains same referenced Utxos
    val seriesPolicies = List(seriesPolicy, seriesPolicy)

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(seriesPolicies = seriesPolicies))
    val testTx = txFull.copy(inputs = inputs, outputs = outputs, datum = datum)

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    val assertError = result.exists(_.toList.contains(TransactionSyntaxError.DuplicateInput(txoAddress_1)))
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)

  }

  test("Invalid data-input, minting a Series constructor Token") {
    val seriesPolicy_A = SeriesPolicy(label = "seriesLabelA", registrationUtxo = txoAddress_1)
    val seriesPolicy_B = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_1)

    val value_1_in =
      Value.defaultInstance.withLvl(Value.LVL(quantity = 1))

    val value_1_out =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy_A.computeId, quantity = 1))

    val value_2_out =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy_B.computeId, quantity = 1))

    val inputs = List(SpentTransactionOutput(txoAddress_1, attFull, value_1_in))
    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, value_1_out),
      UnspentTransactionOutput(trivialLockAddress, value_2_out)
    )
    // policies contains same referenced Utxos
    val seriesPolicies = List(seriesPolicy_A, seriesPolicy_B)

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(seriesPolicies = seriesPolicies))
    val testTx = txFull.copy(inputs = inputs, outputs = outputs, datum = datum)

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    val assertError = result.exists(_.toList.contains(TransactionSyntaxError.DuplicateInput(txoAddress_1)))
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)

  }

  test("Invalid data-input, minting a Group and Series constructor Token") {
    val g1 = GroupPolicy(label = "g1", registrationUtxo = txoAddress_1)
    val s1 = SeriesPolicy(label = "s1", registrationUtxo = txoAddress_1)

    val value_abc_in =
      Value.defaultInstance.withLvl(Value.LVL(quantity = 1))

    val value_1_out =
      Value.defaultInstance.withGroup(Value.Group(groupId = g1.computeId, quantity = 1))

    val value_2_out =
      Value.defaultInstance.withSeries(Value.Series(seriesId = s1.computeId, quantity = 1))

    val inputs = List(SpentTransactionOutput(txoAddress_1, attFull, value_abc_in))
    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, value_1_out),
      UnspentTransactionOutput(trivialLockAddress, value_2_out)
    )
    // policies contains same referenced Utxos
    val groupPolicies = List(g1)
    val seriesPolicies = List(s1)

    val datum =
      txFull.datum.copy(event = txFull.datum.event.copy(seriesPolicies = seriesPolicies, groupPolicies = groupPolicies))
    val testTx =
      txFull.copy(inputs = inputs, outputs = outputs, datum = datum)

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    val assertError = result.exists(_.toList.contains(TransactionSyntaxError.DuplicateInput(txoAddress_1)))
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)

  }

}
