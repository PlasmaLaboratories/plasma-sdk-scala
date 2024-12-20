package org.plasmalabs.sdk.validation

import cats.Id
import cats.implicits._
import org.plasmalabs.sdk.MockHelpers
import org.plasmalabs.sdk.models.{Event, GroupPolicy, SeriesPolicy, TransactionOutputAddress}
import org.plasmalabs.sdk.models.AssetMintingStatement
import org.plasmalabs.sdk.models.box.Value
import org.plasmalabs.sdk.models.transaction.SpentTransactionOutput
import org.plasmalabs.sdk.models.transaction.UnspentTransactionOutput
import org.plasmalabs.sdk.syntax._
import org.plasmalabs.sdk.constants.NetworkConstants.PRIVATE_NETWORK_ID
import org.plasmalabs.sdk.constants.NetworkConstants.MAIN_LEDGER_ID

/**
 * Test to coverage this specific syntax validation:
 *  - Validations only for minting, After projection  (only if for all inputs and outputs isMint == true)
 *    Case 3: Series
 *  - asset minted correspond to token supply in series policy
 */
class TransactionSyntaxInterpreterMintingCaseCSpec extends munit.FunSuite with MockHelpers {

  private val txoAddress_1 = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 1, dummyTxIdentifier)
  private val txoAddress_2 = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 2, dummyTxIdentifier)
  private val txoAddress_3 = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 3, dummyTxIdentifier)
  private val txoAddress_4 = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 4, dummyTxIdentifier)

  test("Valid data-input case 1, minting a Asset Token Unlimited") {
    val groupPolicy = GroupPolicy(label = "policyG", registrationUtxo = txoAddress_1)
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_2, tokenSupply = None)

    val value_1_in: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = BigInt(1)))

    val value_2_in: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(1), tokenSupply = None)
      )

    // case 1 token supply Unlimited
    // quantity could be any val 18, 1888, 999, and should be valid
    val value_1_out: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(
          groupId = Some(groupPolicy.computeId),
          seriesId = Some(seriesPolicy.computeId),
          quantity = BigInt(1)
        )
      )

    val value_2_out: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = BigInt(1)))

    val value_3_out: Value =
      Value.defaultInstance.withSeries(Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(1)))

    val input_1 = SpentTransactionOutput(txoAddress_1, attFull, value_1_in)
    val input_2 = SpentTransactionOutput(txoAddress_2, attFull, value_2_in)

    val output_1 = UnspentTransactionOutput(trivialLockAddress, value_1_out)
    val output_2 = UnspentTransactionOutput(trivialLockAddress, value_2_out)
    val output_3 = UnspentTransactionOutput(trivialLockAddress, value_3_out)

    val mintingStatement_1 = Seq(
      AssetMintingStatement(
        groupTokenUtxo = txoAddress_1,
        seriesTokenUtxo = txoAddress_2,
        quantity = BigInt(1)
      )
    )

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatement_1))
    val testTx =
      txFull.copy(inputs = List(input_1, input_2), outputs = List(output_1, output_2, output_3), datum = datum)

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    assertEquals(result.map(_.toList.size).getOrElse(0), 0)

  }

  test("Valid data-input case 1, minting a Asset Token Limited") {
    val groupPolicy = GroupPolicy(label = "policyG", registrationUtxo = txoAddress_1)
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_2, tokenSupply = Some(10))

    val value_1_in: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = 1))

    val value_2_in: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = seriesPolicy.computeId, quantity = 1, tokenSupply = Some(10))
      )

    // case 1 token supply Limited
    // only possible value is 10, 1*10.  if quantity is 2 = could be 10 or 20. 1*10, 2*10,
    val value_1_out: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(groupId = Some(groupPolicy.computeId), seriesId = Some(seriesPolicy.computeId), quantity = 10)
      )

    // quantity should be equal value_1_in
    val value_2_out: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = 1))

    // Here we burning the series, burned == 1 keep comment to understand the test
    // when we burn a series, means quantity =0, but this output is not produced
    //    val value_3_out: Value =
    //      Value.defaultInstance.withSeries(
    //        Value.Series(
    //          seriesId = seriesPolicy.computeId,
    //          quantity = BigInt(0) //  (assetQuantity(10) / tokenSupply(10)) - seriesQuantity(1) = 0
    //        )
    //      )

    val input_1 = SpentTransactionOutput(txoAddress_1, attFull, value_1_in)
    val input_2 = SpentTransactionOutput(txoAddress_2, attFull, value_2_in)
    val output_1 = UnspentTransactionOutput(trivialLockAddress, value_1_out)
    val output_2 = UnspentTransactionOutput(trivialLockAddress, value_2_out)

    val mintingStatement_1 =
      Seq(AssetMintingStatement(groupTokenUtxo = txoAddress_1, seriesTokenUtxo = txoAddress_2, quantity = 10))

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatement_1))
    val testTx = txFull.copy(
      inputs = List(input_1, input_2),
      outputs = List(output_1, output_2),
      datum = datum
    )

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    assertEquals(result.map(_.toList.size).getOrElse(0), 0)

  }

  test("Valid data-input case 2, minting a Asset Token Limited") {
    val groupPolicy = GroupPolicy(label = "policyG", registrationUtxo = txoAddress_1)
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_2, tokenSupply = Some(10))

    val value_1_in: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = BigInt(1)))

    val value_2_in: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(2), tokenSupply = Some(10))
      )

    // case 1 token supply Limited
    // only possible value is 10, 1*10.  if quantity is 2 = could be 10 or 20. 1*10, 2*10,
    val value_1_out: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(
          groupId = Some(groupPolicy.computeId),
          seriesId = Some(seriesPolicy.computeId),
          quantity = BigInt(20)
        )
      )

    // quantity should be equal value_1_in
    val value_2_out: Value =
      Value.defaultInstance.withGroup(
        Value.Group(
          groupId = groupPolicy.computeId,
          quantity = BigInt(1)
        )
      )

    // Here we burning the series, keep comment to understand the test
    // when we burn a 1 series, means quantity =0, but this output is not produced
    //    val value_3_out: Value =
    //      Value.defaultInstance.withSeries(
    //        Value.Series(
    //          seriesId = seriesPolicy.computeId,
    //          quantity = BigInt(0) //  (assetQuantity(10) / tokenSupply(10)) - seriesQuantity(1) = 0
    //        )
    //      )

    val inputs = List(
      SpentTransactionOutput(txoAddress_1, attFull, value_1_in),
      SpentTransactionOutput(txoAddress_2, attFull, value_2_in)
    )
    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, value_1_out),
      UnspentTransactionOutput(trivialLockAddress, value_2_out)
    )

    val mintingStatements = List(
      AssetMintingStatement(
        groupTokenUtxo = txoAddress_1,
        seriesTokenUtxo = txoAddress_2,
        quantity = BigInt(20)
      )
    )

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatements))
    val testTx = txFull.copy(inputs = inputs, outputs = outputs, datum = datum)

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    assertEquals(result.map(_.toList.size).getOrElse(0), 0)

  }

  test("Valid data-input case 3, minting a Asset Token Limited") {
    val groupPolicy = GroupPolicy(label = "policyG", registrationUtxo = txoAddress_1)
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_2, tokenSupply = Some(10))

    val value_1_in: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = BigInt(1)))

    val value_2_in: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(2), tokenSupply = Some(10))
      )

    // case 1 token supply Limited
    // only possible value is 10, 1*10.  if quantity is 2 = could be 10 or 20. 1*10, 2*10,
    val value_1_out: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(
          groupId = Some(groupPolicy.computeId),
          seriesId = Some(seriesPolicy.computeId),
          quantity = BigInt(10)
        )
      )

    // quantity should be equal value_1_in
    val value_2_out: Value =
      Value.defaultInstance.withGroup(
        Value.Group(
          groupId = groupPolicy.computeId,
          quantity = BigInt(1)
        )
      )

    // Here we are not burning the series, we spend 1.
    val value_3_out: Value =
      Value.defaultInstance.withSeries(
        Value.Series(
          seriesId = seriesPolicy.computeId,
          quantity = BigInt(1),
          tokenSupply = Some(10)
        )
      )

    val input_1 = SpentTransactionOutput(txoAddress_1, attFull, value_1_in)
    val input_2 = SpentTransactionOutput(txoAddress_2, attFull, value_2_in)
    val output_1 = UnspentTransactionOutput(trivialLockAddress, value_1_out)
    val output_2 = UnspentTransactionOutput(trivialLockAddress, value_2_out)
    val output_3 = UnspentTransactionOutput(trivialLockAddress, value_3_out)

    val mintingStatement_1 = Seq(
      AssetMintingStatement(
        groupTokenUtxo = txoAddress_1,
        seriesTokenUtxo = txoAddress_2,
        quantity = BigInt(10)
      )
    )

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatement_1))
    val testTx = txFull.copy(
      inputs = List(input_1, input_2),
      outputs = List(output_1, output_2, output_3),
      datum = datum
    )

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    assertEquals(result.map(_.toList.size).getOrElse(0), 0)

  }

  test("Valid data-input case 4, minting a Asset Token Limited") {
    val groupPolicy = GroupPolicy(label = "policyG", registrationUtxo = txoAddress_1)
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_2, tokenSupply = Some(10))

    val value_1_in: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = BigInt(1)))

    val value_2_in: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(2), tokenSupply = Some(10))
      )

    // case 1 token supply Limited
    // only possible value is 10, 1*10.  if quantity is 2 = could be 10 or 20. 1*10, 2*10,
    val value_1_out: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(
          groupId = Some(groupPolicy.computeId),
          seriesId = Some(seriesPolicy.computeId),
          quantity = BigInt(20)
        )
      )

    // quantity should be equal value_1_in
    val value_2_out: Value =
      Value.defaultInstance.withGroup(
        Value.Group(
          groupId = groupPolicy.computeId,
          quantity = BigInt(1)
        )
      )

    // Here we burning the series, keep comment to understand the test, we burn 2
//    val value_3_out: Value =
//      Value.defaultInstance.withSeries(
//        Value.Series(
//          seriesId = seriesPolicy.computeId,
//          quantity = BigInt(0), // 2-2= 0
//          tokenSupply = Some(10)
//        )
//      )

    val input_1 = SpentTransactionOutput(txoAddress_1, attFull, value_1_in)
    val input_2 = SpentTransactionOutput(txoAddress_2, attFull, value_2_in)
    val output_1 = UnspentTransactionOutput(trivialLockAddress, value_1_out)
    val output_2 = UnspentTransactionOutput(trivialLockAddress, value_2_out)

    val mintingStatement_1 = Seq(
      AssetMintingStatement(
        groupTokenUtxo = txoAddress_1,
        seriesTokenUtxo = txoAddress_2,
        quantity = BigInt(20)
      )
    )

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatement_1))
    val testTx = txFull.copy(
      inputs = List(input_1, input_2),
      outputs = List(output_1, output_2),
      datum = datum
    )

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    assertEquals(result.map(_.toList.size).getOrElse(0), 0)

  }

  test("Valid data-input case 5, minting a Asset Token Limited") {
    val groupPolicy = GroupPolicy(label = "policyG", registrationUtxo = txoAddress_1)
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_2, tokenSupply = Some(10))

    val value_1_in: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = BigInt(1)))

    val value_2_in: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(2), tokenSupply = Some(10))
      )

    // case 1 token supply Limited
    // only possible value is 10, 1*10.  if quantity is 2 = could be 10 or 20. 1*10, 2*10,
    val value_1_out: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(
          groupId = Some(groupPolicy.computeId),
          seriesId = Some(seriesPolicy.computeId),
          quantity = BigInt(20)
        )
      )

    // quantity should be equal value_1_in
    val value_2_out: Value =
      Value.defaultInstance.withGroup(
        Value.Group(
          groupId = groupPolicy.computeId,
          quantity = BigInt(1)
        )
      )

    val inputs = List(
      SpentTransactionOutput(txoAddress_1, attFull, value_1_in),
      SpentTransactionOutput(txoAddress_2, attFull, value_2_in)
    )

    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, value_1_out),
      UnspentTransactionOutput(trivialLockAddress, value_2_out)
    )

    val mintingStatements = List(
      AssetMintingStatement(
        groupTokenUtxo = txoAddress_1,
        seriesTokenUtxo = txoAddress_2,
        quantity = BigInt(20)
      )
    )

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatements))
    val testTx = txFull.copy(inputs = inputs, outputs = outputs, datum = datum)

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    assertEquals(result.map(_.toList.size).getOrElse(0), 0)

  }

  test("Valid data-input case 6, minting a Asset Token Limited and transfer Other Asset") {
    val groupPolicy =
      GroupPolicy(label = "policy", registrationUtxo = txoAddress_1)

    val groupPolicy_A =
      GroupPolicy(label = "policy_A", registrationUtxo = txoAddress_3)

    val seriesPolicy =
      SeriesPolicy(label = "series", registrationUtxo = txoAddress_2, tokenSupply = Some(10))

    val seriesPolicy_A =
      SeriesPolicy(label = "series_A", registrationUtxo = txoAddress_4, tokenSupply = Some(10))

    val value_1_in: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = BigInt(1)))

    val value_2_in: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(2), tokenSupply = Some(10))
      )

    // transfer asset with output value_4_out
    val value_3_in: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(
          groupId = Some(groupPolicy_A.computeId),
          seriesId = Some(seriesPolicy_A.computeId),
          quantity = BigInt(1)
        )
      )

    // minting asset case 1 token supply Limited
    // only possible value is 10, 1*10.  if quantity is 2 = could be 10 or 20. 1*10, 2*10,
    val value_1_out: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(
          groupId = Some(groupPolicy.computeId),
          seriesId = Some(seriesPolicy.computeId),
          quantity = BigInt(10)
        )
      )

    // quantity should be equal value_1_in
    val value_2_out: Value =
      Value.defaultInstance.withGroup(
        Value.Group(
          groupId = groupPolicy.computeId,
          quantity = BigInt(1)
        )
      )

    // Here we are not burning the series, we spend 1 quantity.
    val value_3_out: Value =
      Value.defaultInstance.withSeries(
        Value.Series(
          seriesId = seriesPolicy.computeId,
          quantity = BigInt(1),
          tokenSupply = Some(10)
        )
      )

    // transfer asset with input value_3_in
    val value_4_out: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(
          groupId = Some(groupPolicy_A.computeId),
          seriesId = Some(seriesPolicy_A.computeId),
          quantity = BigInt(1)
        )
      )

    val inputs = List(
      SpentTransactionOutput(txoAddress_1, attFull, value_1_in),
      SpentTransactionOutput(txoAddress_2, attFull, value_2_in),
      SpentTransactionOutput(txoAddress_3, attFull, value_3_in)
    )

    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, value_1_out),
      UnspentTransactionOutput(trivialLockAddress, value_2_out),
      UnspentTransactionOutput(trivialLockAddress, value_3_out),
      UnspentTransactionOutput(trivialLockAddress, value_4_out)
    )

    val mintingStatements = List(
      AssetMintingStatement(
        groupTokenUtxo = txoAddress_1,
        seriesTokenUtxo = txoAddress_2,
        quantity = BigInt(10)
      )
    )

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatements))
    val testTx = txFull.copy(inputs = inputs, outputs = outputs, datum = datum)

    val validator = TransactionSyntaxInterpreter.make[Id]()
    val result = validator.validate(testTx).swap

    assertEquals(result.map(_.toList.size).getOrElse(0), 0)

  }

  test("Invalid data-input case 1, minting a Asset Token Limited") {
    val groupPolicy = GroupPolicy(label = "policyG", registrationUtxo = txoAddress_1)
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_2, tokenSupply = Some(10))

    val value_1_in: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = BigInt(1)))

    val value_2_in: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(1), tokenSupply = Some(10))
      )

    // only possible value is 10, 1*10.  if quantity is 2 = could be 10 or 20. 1*10, 2*10,
    val value_1_out: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(
          groupId = Some(groupPolicy.computeId),
          seriesId = Some(seriesPolicy.computeId),
          quantity = BigInt(10)
        )
      )

    // quantity should be equal value_1_in
    val value_2_out: Value =
      Value.defaultInstance.withGroup(
        Value.Group(
          groupId = groupPolicy.computeId,
          quantity = BigInt(1)
        )
      )

    val input_1 = SpentTransactionOutput(txoAddress_1, attFull, value_1_in)
    val input_2 = SpentTransactionOutput(txoAddress_2, attFull, value_2_in)
    val output_1 = UnspentTransactionOutput(trivialLockAddress, value_1_out)
    val output_2 = UnspentTransactionOutput(trivialLockAddress, value_2_out)

    val mintingStatements = Seq(
      AssetMintingStatement(
        groupTokenUtxo = txoAddress_1,
        seriesTokenUtxo = txoAddress_2,
        quantity = BigInt(11) // Invalid data
      )
    )

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatements))
    val testTx = txFull.copy(inputs = List(input_1, input_2), outputs = List(output_1, output_2), datum = datum)

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
    // 1 InsufficientInputFunds validation rules are catch
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)

  }

  test("Invalid data-input case 2, minting a Asset Token Limited") {
    val groupPolicy = GroupPolicy(label = "policyG", registrationUtxo = txoAddress_1)
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_2, tokenSupply = Some(10))

    val value_1_in: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = BigInt(1)))

    val value_2_in: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(1), tokenSupply = Some(10))
      )

    // only possible value is 10, 1*10.  if quantity is 2 = could be 10 or 20. 1*10, 2*10,
    val value_1_out: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(
          groupId = Some(groupPolicy.computeId),
          seriesId = Some(seriesPolicy.computeId),
          quantity = BigInt(11) // Invalid Data
        )
      )

    // quantity should be equal value_1_in
    val value_2_out: Value =
      Value.defaultInstance.withGroup(
        Value.Group(
          groupId = groupPolicy.computeId,
          quantity = BigInt(1)
        )
      )

    val input_1 = SpentTransactionOutput(txoAddress_1, attFull, value_1_in)
    val input_2 = SpentTransactionOutput(txoAddress_2, attFull, value_2_in)
    val output_1 = UnspentTransactionOutput(trivialLockAddress, value_1_out)
    val output_2 = UnspentTransactionOutput(trivialLockAddress, value_2_out)

    val mintingStatements = Seq(
      AssetMintingStatement(
        groupTokenUtxo = txoAddress_1,
        seriesTokenUtxo = txoAddress_2,
        quantity = BigInt(10)
      )
    )

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatements))
    val testTx = txFull.copy(inputs = List(input_1, input_2), outputs = List(output_1, output_2), datum = datum)

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

  test("Invalid data-input case 3, minting a Asset Token Limited") {
    val groupPolicy = GroupPolicy(label = "policyG", registrationUtxo = txoAddress_1)
    val seriesPolicy = SeriesPolicy(label = "seriesLabelB", registrationUtxo = txoAddress_2, tokenSupply = Some(3))

    val value_1_in: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = groupPolicy.computeId, quantity = BigInt(1)))

    val value_2_in: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = seriesPolicy.computeId, quantity = BigInt(2), tokenSupply = Some(3))
      )

    // possible value is 3, 6.
    val value_1_out: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(
          groupId = Some(groupPolicy.computeId),
          seriesId = Some(seriesPolicy.computeId),
          quantity = BigInt(2) // Invalid Data
        )
      )

    // quantity should be equal value_1_in
    val value_2_out: Value =
      Value.defaultInstance.withGroup(
        Value.Group(
          groupId = groupPolicy.computeId,
          quantity = BigInt(1)
        )
      )

    val input_1 = SpentTransactionOutput(txoAddress_1, attFull, value_1_in)
    val input_2 = SpentTransactionOutput(txoAddress_2, attFull, value_2_in)
    val output_1 = UnspentTransactionOutput(trivialLockAddress, value_1_out)
    val output_2 = UnspentTransactionOutput(trivialLockAddress, value_2_out)

    val mintingStatements = Seq(
      AssetMintingStatement(
        groupTokenUtxo = txoAddress_1,
        seriesTokenUtxo = txoAddress_2,
        quantity = BigInt(2)
      )
    )

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatements))
    val testTx = txFull.copy(
      inputs = List(input_1, input_2),
      outputs = List(output_1, output_2),
      datum = datum
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
   *  A transaction with 2 minting statements that point to distinct UTXOs, but their series UTXOs have the same seriesId.
   */
  test("Valid data-input case 4, minting a Asset Token Limited") {

    val utxo_xyz = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 0, dummyTxIdentifier)
    val utxo_abc = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 1, dummyTxIdentifier)
    val utxo_def = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 2, dummyTxIdentifier)
    val utxo_uvw = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 3, dummyTxIdentifier)

    val dummyTxoAddress = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 0, dummyTxIdentifier)

    val g1 = GroupPolicy(label = "policyG1", registrationUtxo = utxo_xyz)
    val g2 = GroupPolicy(label = "policyG2", registrationUtxo = utxo_uvw)

    val s1 = SeriesPolicy(label = "policyS1", registrationUtxo = dummyTxoAddress, tokenSupply = Some(1))

    val value_abc_in: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = s1.computeId, quantity = BigInt(2), tokenSupply = Some(1))
      )

    val value_def_in: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = s1.computeId, quantity = BigInt(1), tokenSupply = Some(1))
      )

    val value_xyz_in: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = g1.computeId, quantity = BigInt(1)))

    val value_uvw_in: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = g2.computeId, quantity = BigInt(1)))

    // minted asset
    val value_1_out: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(groupId = Some(g1.computeId), seriesId = Some(s1.computeId), quantity = BigInt(1))
      )

    // series change
    val value_2_out: Value =
      Value.defaultInstance.withSeries(Value.Series(seriesId = s1.computeId, quantity = BigInt(1)))

    // minted asset
    val value_3_out: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(groupId = Some(g2.computeId), seriesId = Some(s1.computeId), quantity = BigInt(1))
      )

    // group change
    val value_4_out: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = g1.computeId, quantity = BigInt(1)))

    // group change
    val value_5_out: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = g2.computeId, quantity = BigInt(1)))

    val input_abc = SpentTransactionOutput(utxo_abc, attFull, value_abc_in)
    val input_def = SpentTransactionOutput(utxo_def, attFull, value_def_in)
    val input_xyz = SpentTransactionOutput(utxo_xyz, attFull, value_xyz_in)
    val input_uvw = SpentTransactionOutput(utxo_uvw, attFull, value_uvw_in)
    val output_1 = UnspentTransactionOutput(trivialLockAddress, value_1_out)
    val output_2 = UnspentTransactionOutput(trivialLockAddress, value_2_out)
    val output_3 = UnspentTransactionOutput(trivialLockAddress, value_3_out)
    val output_4 = UnspentTransactionOutput(trivialLockAddress, value_4_out)
    val output_5 = UnspentTransactionOutput(trivialLockAddress, value_5_out)

    // A transaction with 2 minting statements that point to distinct UTXOs, but their series UTXOs have the same seriesId.
    val mintingStatement_1 = AssetMintingStatement(
      seriesTokenUtxo = utxo_abc,
      groupTokenUtxo = utxo_xyz,
      quantity = BigInt(1)
    )

    val mintingStatement_2 = AssetMintingStatement(
      seriesTokenUtxo = utxo_def,
      groupTokenUtxo = utxo_uvw,
      quantity = BigInt(1)
    )

    val datum = txFull.datum.copy(event =
      txFull.datum.event.copy(mintingStatements = List(mintingStatement_1, mintingStatement_2))
    )
    val testTx = txFull.copy(
      inputs = List(input_abc, input_def, input_xyz, input_uvw),
      outputs = List(output_1, output_2, output_3, output_4, output_5),
      datum = datum
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
    println("result: " + result)
    assertEquals(assertError, false)

  }

  /**
   * A transaction with 2 minting statements that point to distinct UTXOs, but their series UTXOs have the same seriesId.
   */
  test("Invalid data-input case 4, minting a Asset Token Limited") {

    val utxo_xyz = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 1, dummyTxIdentifier)
    val utxo_abc = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 2, dummyTxIdentifier)
    val utxo_def = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 3, dummyTxIdentifier)
    val utxo_uvw = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 4, dummyTxIdentifier)

    val dummyTxoAddress = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 0, dummyTxIdentifier)

    val g1 = GroupPolicy(label = "policyG1", registrationUtxo = utxo_xyz)
    val g2 = GroupPolicy(label = "policyG2", registrationUtxo = utxo_uvw)

    val s1 = SeriesPolicy(label = "policyS1", registrationUtxo = dummyTxoAddress, tokenSupply = Some(1))

    val value_abc_in: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = s1.computeId, quantity = BigInt(2), tokenSupply = Some(1))
      )

    val value_def_in: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = s1.computeId, quantity = BigInt(1), tokenSupply = Some(1))
      )

    val value_xyz_in: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = g1.computeId, quantity = BigInt(1)))

    val value_uvw_in: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = g2.computeId, quantity = BigInt(1)))

    // minted asset
    val value_1_out: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(groupId = Some(g1.computeId), seriesId = Some(s1.computeId), quantity = BigInt(1))
      )

    // series change, invalid case, because quantity
    val value_2_out: Value =
      Value.defaultInstance.withSeries(Value.Series(seriesId = s1.computeId, quantity = BigInt(10)))

    // minted asset
    val value_3_out: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(groupId = Some(g2.computeId), seriesId = Some(s1.computeId), quantity = BigInt(1))
      )

    // group change
    val value_4_out: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = g1.computeId, quantity = BigInt(1)))

    // group change
    val value_5_out: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = g2.computeId, quantity = BigInt(1)))

    val input_abc = SpentTransactionOutput(utxo_abc, attFull, value_abc_in)
    val input_def = SpentTransactionOutput(utxo_def, attFull, value_def_in)
    val input_xyz = SpentTransactionOutput(utxo_xyz, attFull, value_xyz_in)
    val input_uvw = SpentTransactionOutput(utxo_uvw, attFull, value_uvw_in)

    val output_1 = UnspentTransactionOutput(trivialLockAddress, value_1_out)
    val output_2 = UnspentTransactionOutput(trivialLockAddress, value_2_out)
    val output_3 = UnspentTransactionOutput(trivialLockAddress, value_3_out)
    val output_4 = UnspentTransactionOutput(trivialLockAddress, value_4_out)
    val output_5 = UnspentTransactionOutput(trivialLockAddress, value_5_out)

    // A transaction with 2 minting statements that point to distinct UTXOs, but their series UTXOs have the same seriesId.
    val mintingStatement_1 = AssetMintingStatement(
      seriesTokenUtxo = utxo_abc,
      groupTokenUtxo = utxo_xyz,
      quantity = BigInt(1)
    )

    val mintingStatement_2 = AssetMintingStatement(
      seriesTokenUtxo = utxo_def,
      groupTokenUtxo = utxo_uvw,
      quantity = BigInt(1)
    )

    val datum = txFull.datum.copy(event =
      txFull.datum.event.copy(mintingStatements = List(mintingStatement_1, mintingStatement_2))
    )
    val testTx = txFull.copy(
      inputs = List(input_abc, input_def, input_xyz, input_uvw),
      outputs = List(output_1, output_2, output_3, output_4, output_5),
      datum = datum
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
   * A transaction with 2 minting statements that point to distinct UTXOs, but their series UTXOs have the same seriesId.
   * @see [[co.topl.brambl.validation.pumls.valid_case_5.puml]]
   */
  test("Valid data-input case 5, minting 2 Asset Token Limited, with 2 groups and 2 series") {
    val utxo = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 0, dummyTxIdentifier)
    val utxo1 = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 1, dummyTxIdentifier)
    val utxo2 = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 2, dummyTxIdentifier)
    val utxo3 = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 3, dummyTxIdentifier)
    val utxo4 = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 4, dummyTxIdentifier)

    val gA = GroupPolicy(label = "policyGA", registrationUtxo = utxo1)
    val gB = GroupPolicy(label = "policyGB", registrationUtxo = utxo2)

    val sC = SeriesPolicy(label = "policyS1", registrationUtxo = utxo, tokenSupply = Some(5))

    // inputs
    val inValue1_GA: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = gA.computeId, quantity = BigInt(1)))

    val inValue2_GB: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = gB.computeId, quantity = BigInt(1)))

    val inValue3_SC: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = sC.computeId, quantity = BigInt(3), tokenSupply = Some(5))
      )

    val inValue4_SC: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = sC.computeId, quantity = BigInt(3), tokenSupply = Some(5))
      )

    // outputs
    val outValue1_GA: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = gA.computeId, quantity = BigInt(1)))

    val outValue2_GB: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = gB.computeId, quantity = BigInt(1)))

    val outValue3_SC: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = sC.computeId, quantity = BigInt(1), tokenSupply = Some(5))
      )

    val outValue4_SC: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = sC.computeId, quantity = BigInt(2), tokenSupply = Some(5))
      )

    val outValue5_A1: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(groupId = Some(gA.computeId), seriesId = Some(sC.computeId), quantity = BigInt(10))
      )

    val outValue6_A2: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(groupId = Some(gB.computeId), seriesId = Some(sC.computeId), quantity = BigInt(5))
      )

    val inputs = List(
      SpentTransactionOutput(utxo1, attFull, inValue1_GA),
      SpentTransactionOutput(utxo2, attFull, inValue2_GB),
      SpentTransactionOutput(utxo3, attFull, inValue3_SC),
      SpentTransactionOutput(utxo4, attFull, inValue4_SC)
    )

    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, outValue1_GA),
      UnspentTransactionOutput(trivialLockAddress, outValue2_GB),
      UnspentTransactionOutput(trivialLockAddress, outValue3_SC),
      UnspentTransactionOutput(trivialLockAddress, outValue4_SC),
      UnspentTransactionOutput(trivialLockAddress, outValue5_A1),
      UnspentTransactionOutput(trivialLockAddress, outValue6_A2)
    )

    val mintingStatements = List(
      AssetMintingStatement(groupTokenUtxo = utxo1, seriesTokenUtxo = utxo3, quantity = BigInt(10)),
      AssetMintingStatement(groupTokenUtxo = utxo2, seriesTokenUtxo = utxo4, quantity = BigInt(5))
    )

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatements))
    val testTx = txFull.copy(inputs = inputs, outputs = outputs, datum = datum)

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

  /**
   * A transaction with 2 minting statements that point to distinct UTXOs, but their series UTXOs have the same seriesId.
   *
   * @see [[co.topl.brambl.validation.pumls.invalid_case_6.puml]]
   */
  test("Invalid data-input case 6, minting 2 Asset Token Limited, with 2 groups and 2 series") {
    val utxo = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 0, dummyTxIdentifier)
    val utxo1 = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 1, dummyTxIdentifier)
    val utxo2 = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 2, dummyTxIdentifier)
    val utxo3 = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 3, dummyTxIdentifier)
    val utxo4 = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 4, dummyTxIdentifier)

    val gA = GroupPolicy(label = "policyGA", registrationUtxo = utxo1)
    val gB = GroupPolicy(label = "policyGB", registrationUtxo = utxo2)

    val sC = SeriesPolicy(label = "policyS1", registrationUtxo = utxo, tokenSupply = Some(5))

    // inputs
    val inValue1_GA: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = gA.computeId, quantity = BigInt(1)))

    val inValue2_GB: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = gB.computeId, quantity = BigInt(1)))

    val inValue3_SC: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = sC.computeId, quantity = BigInt(4), tokenSupply = Some(5))
      )

    val inValue4_SC: Value =
      Value.defaultInstance.withSeries(
        Value.Series(seriesId = sC.computeId, quantity = BigInt(3), tokenSupply = Some(5))
      )

    // outputs
    val outValue1_GA: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = gA.computeId, quantity = BigInt(1)))

    val outValue2_GB: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = gB.computeId, quantity = BigInt(1)))

    val outValue5_A1: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(groupId = Some(gA.computeId), seriesId = Some(sC.computeId), quantity = BigInt(25))
      )

    val outValue6_A2: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(groupId = Some(gB.computeId), seriesId = Some(sC.computeId), quantity = BigInt(10))
      )

    val inputs = List(
      SpentTransactionOutput(utxo1, attFull, inValue1_GA),
      SpentTransactionOutput(utxo2, attFull, inValue2_GB),
      SpentTransactionOutput(utxo3, attFull, inValue3_SC),
      SpentTransactionOutput(utxo4, attFull, inValue4_SC)
    )

    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, outValue1_GA),
      UnspentTransactionOutput(trivialLockAddress, outValue2_GB),
      UnspentTransactionOutput(trivialLockAddress, outValue5_A1),
      UnspentTransactionOutput(trivialLockAddress, outValue6_A2)
    )

    val mintingStatements = List(
      AssetMintingStatement(groupTokenUtxo = utxo1, seriesTokenUtxo = utxo3, quantity = BigInt(25)),
      AssetMintingStatement(groupTokenUtxo = utxo2, seriesTokenUtxo = utxo4, quantity = BigInt(10))
    )

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatements))
    val testTx = txFull.copy(inputs = inputs, outputs = outputs, datum = datum)

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
   * Invalid since seriesUtxo does not refer to a series token
   *
   * @see [[co.topl.brambl.validation.pumls.invalid_case_7.puml]]
   */
  test("Invalid data-input case 7, minting 1 Asset, series Utxo does not refer to a series token") {
    val utxo = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 0, dummyTxIdentifier)
    val utxo_abc = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 1, dummyTxIdentifier)
    val utxo_xyz = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 2, dummyTxIdentifier)

    val g1 = GroupPolicy(label = "policyG1", registrationUtxo = utxo_xyz)

    val sC = SeriesPolicy(label = "policyS1", registrationUtxo = utxo, tokenSupply = Some(5))

    // inputs
    val inValue1_abc: Value =
      Value.defaultInstance.withLvl(Value.LVL(quantity = 1))

    val inValue2_xyz: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = g1.computeId, quantity = 1))

    // outputs
    val outValue_mintedAsset: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(groupId = Some(g1.computeId), seriesId = Some(sC.computeId), quantity = 1)
      )
    val outValue_groupChange: Value =
      Value.defaultInstance.withGroup(Value.Group(groupId = g1.computeId, quantity = 1))

    val inputs = List(
      SpentTransactionOutput(utxo_abc, attFull, inValue1_abc),
      SpentTransactionOutput(utxo_xyz, attFull, inValue2_xyz)
    )

    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, outValue_mintedAsset),
      UnspentTransactionOutput(trivialLockAddress, outValue_groupChange)
    )

    val mintingStatements = List(
      AssetMintingStatement(groupTokenUtxo = utxo_xyz, seriesTokenUtxo = utxo_abc, quantity = 1)
    )

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatements))
    val testTx = txFull.copy(inputs = inputs, outputs = outputs, datum = datum)

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
    // InsufficientInputFunds twice
    assertEquals(result.map(_.toList.size).getOrElse(0), 2)
  }

  /**
   * Invalid since groupUtxo does not refer to a group token
   *
   * @see [[co.topl.brambl.validation.pumls.invalid_case_8.puml]]
   */
  test("Invalid data-input case 8, minting 1 Asset, group Utxo does not refer to a series token") {
    val utxo = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 0, dummyTxIdentifier)
    val utxo_abc = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 1, dummyTxIdentifier)
    val utxo_xyz = TransactionOutputAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, 2, dummyTxIdentifier)

    val g1 = GroupPolicy(label = "g1", registrationUtxo = utxo_xyz)
    val s1 = SeriesPolicy(label = "s1", registrationUtxo = utxo, tokenSupply = Some(5))

    // inputs
    val inValue1_abc: Value =
      Value.defaultInstance.withSeries(Value.Series(seriesId = s1.computeId, quantity = 1))

    val inValue2_xyz: Value =
      Value.defaultInstance.withLvl(Value.LVL(quantity = 1))

    // outputs
    val outValue_mintedAsset: Value =
      Value.defaultInstance.withAsset(
        Value.Asset(groupId = Some(g1.computeId), seriesId = Some(s1.computeId), quantity = 1)
      )
    val outValue_seriesChange: Value =
      Value.defaultInstance.withSeries(Value.Series(seriesId = s1.computeId, quantity = 1))

    val inputs = List(
      SpentTransactionOutput(utxo_abc, attFull, inValue1_abc),
      SpentTransactionOutput(utxo_xyz, attFull, inValue2_xyz)
    )

    val outputs = List(
      UnspentTransactionOutput(trivialLockAddress, outValue_mintedAsset),
      UnspentTransactionOutput(trivialLockAddress, outValue_seriesChange)
    )

    val mintingStatements = List(
      AssetMintingStatement(groupTokenUtxo = utxo_xyz, seriesTokenUtxo = utxo_abc, quantity = 1)
    )

    val datum = txFull.datum.copy(event = txFull.datum.event.copy(mintingStatements = mintingStatements))
    val testTx = txFull.copy(inputs = inputs, outputs = outputs, datum = datum)

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
