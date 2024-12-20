package org.plasmalabs.sdk.validation

import cats.Id
import cats.implicits._
import org.plasmalabs.sdk.MockHelpers
import org.plasmalabs.sdk.builders.MergingOps
import org.plasmalabs.sdk.common.ContainsEvidence.Ops
import org.plasmalabs.sdk.common.ContainsImmutable.instances.lockImmutable
import org.plasmalabs.sdk.models.box._
import org.plasmalabs.sdk.models.transaction.{IoTransaction, Schedule, SpentTransactionOutput, UnspentTransactionOutput}
import org.plasmalabs.sdk.models.{AssetMergingStatement, Datum, Event, GroupId, LockAddress, LockId, SeriesId}
import org.plasmalabs.indexer.services.Txo
import com.google.protobuf.ByteString
import org.plasmalabs.quivr.models.{Int128, SmallData}
import org.plasmalabs.sdk.constants.NetworkConstants.PRIVATE_NETWORK_ID
import org.plasmalabs.sdk.constants.NetworkConstants.MAIN_LEDGER_ID

/**
 * Test to coverage Asset Merging:
 *    - all UTXOs (inputs and the new output) share the same (non group-and-series) fungibility type
 *    - all UTXOs (inputs and the new output) share the same quantity descriptor type
 *    - all UTXOs (inputs and the new output) have valid series/group alloy and IDs:
 *       - If group fungible, all UTXOs must share the same group ID. The new output must have a valid seriesAlloy and no seriedId
 *       - If series fungible, all UTXOs must share the same series ID. The new output must have a valid groupAlloy and no groupId
 *    - the alloy fields are a valid merkle root of the input UTXO's values (in lexicographic order)
 *    - merging statement contain 2 or more asset inputs that exist in the transaction
 *    - merging statement's output's index does not refer to an asset output
 *    - input UTXOs are not reused in multiple merging statements
 *    - input UTXOs are not present in the transaction outputs
 *    - quantity in the merged output is the sum of the inputs
 */
class TransactionSyntaxInterpreterAssetMergingSpec extends munit.FunSuite with MockHelpers {

  private val validator = TransactionSyntaxInterpreter.make[Id]()

  private val mockTransaction = IoTransaction.defaultInstance.withDatum(txDatum)

  // The following mock lock address and attestation are used for all the UTXOs in the tests
  private val mockLock = Lock.Predicate(List(MockHeightProposition).map(Challenge().withRevealed), 1)

  private val mockLockAddress =
    LockAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, LockId(Lock().withPredicate(mockLock).sizedEvidence.digest.value))
  private val mockAttestation = Attestation().withPredicate(Attestation.Predicate(mockLock, List(MockHeightProof)))

  private val mockAsset =
    Value.Asset(quantity = Int128(ByteString.copyFrom(BigInt(1).toByteArray)), fungibility = FungibilityType.GROUP)

  private val dummyBytes = ByteString.copyFrom(Array.fill(32)(0.toByte))

  private val groupTxos =
    for (i <- 1 to 3)
      yield Txo(
        transactionOutput = UnspentTransactionOutput(
          mockLockAddress,
          Value.defaultInstance.withAsset(
            mockAsset
              .withSeriesId(SeriesId(ByteString.copyFrom(Array.fill(32)(i.toByte))))
              .withGroupId(GroupId(dummyBytes))
          )
        ),
        outputAddress = dummyTxoAddress.withIndex(i)
      )
  private val mergedGroup = MergingOps.merge(groupTxos, mockLockAddress, None, None)

  private val seriesTxos =
    for (i <- 4 to 6)
      yield Txo(
        transactionOutput = UnspentTransactionOutput(
          mockLockAddress,
          Value.defaultInstance.withAsset(
            mockAsset
              .withFungibility(FungibilityType.SERIES)
              .withSeriesId(SeriesId(dummyBytes))
              .withGroupId(GroupId(ByteString.copyFrom(Array.fill(32)(i.toByte))))
          )
        ),
        outputAddress = dummyTxoAddress.withIndex(i)
      )
  private val mergedSeries = MergingOps.merge(seriesTxos, mockLockAddress, None, None)

  /**
   * Valid complex case:
   * - 2 asset merging statements, one for group fungible and one for series fungible (3 assets each)
   * - Transaction contains other UTXOs that are not used in the merging statements
   */
  test("Valid complex case") {
    val asmGroup = AssetMergingStatement(groupTxos.map(_.outputAddress), 1)
    val asmSeries = AssetMergingStatement(seriesTxos.map(_.outputAddress), 2)
    val inputs = (for (txo <- groupTxos ++ seriesTxos)
      yield SpentTransactionOutput(
        txo.outputAddress,
        mockAttestation,
        txo.transactionOutput.value
      )) :+ SpentTransactionOutput(dummyTxoAddress, mockAttestation, lvlValue)
    val outputs = Seq(UnspentTransactionOutput(mockLockAddress, lvlValue), mergedGroup, mergedSeries)

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup, asmSeries))
            )
        )

    val result = validator.validate(testTx)

    assert(result.toOption.isDefined) // If the result is defined, the validation passed
  }

  /**
   * input UTXOs are not reused in multiple merging statements
   */
  test("Invalid case, an input UTXO is present in multiple merging statements") {
    val asmGroup = AssetMergingStatement(groupTxos.map(_.outputAddress), 0)
    val asmGroupDup = AssetMergingStatement(groupTxos.map(_.outputAddress), 1)
    val inputs =
      for (txo <- groupTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(mergedGroup, mergedGroup)

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup, asmGroupDup))
            )
        )

    val result = validator.validate(testTx).swap

    val expectedErrors = groupTxos.map(txo => TransactionSyntaxError.NonDistinctMergingInput(txo.outputAddress))
    val assertError = result.exists(_.forall(expectedErrors.contains))
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), expectedErrors.length)
  }

  /**
   * input UTXOs are not reused in multiple merging statements
   */
  test("Invalid case, an input UTXO is present multiple times in the same merging statement") {
    val asmGroup = AssetMergingStatement((groupTxos :+ groupTxos.head).map(_.outputAddress), 0)
    val inputs =
      for (txo <- groupTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(MergingOps.merge(groupTxos :+ groupTxos.head, mockLockAddress, None, None))

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(_.toList.contains(TransactionSyntaxError.InvalidMergingStatement(asmGroup)))
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * merging statement contain 2 or more asset inputs that exist in the transaction
   */
  test("Invalid case, merging statement only contains 1 input") {
    val asmGroup = AssetMergingStatement(Seq(groupTxos.head.outputAddress), 0)
    val inputs =
      Seq(SpentTransactionOutput(groupTxos.head.outputAddress, mockAttestation, groupTxos.head.transactionOutput.value))
    val outputs = Seq(MergingOps.merge(Seq(groupTxos.head), mockLockAddress, None, None))

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(_.toList.contains(TransactionSyntaxError.InvalidMergingStatement(asmGroup)))
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * merging statement contain 2 or more asset inputs that exist in the transaction
   */
  test("Invalid case, merging statement contains a txo that does not exist in the transaction inputs") {
    val asmGroup = AssetMergingStatement(groupTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- groupTxos.tail)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(MergingOps.merge(groupTxos.tail, mockLockAddress, None, None))

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(_.toList.contains(TransactionSyntaxError.InvalidMergingStatement(asmGroup)))
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * merging statement's output's index does not refer to an asset output
   */
  test("Invalid case, merging statement's output index is out of bounds") {
    val asmGroup = AssetMergingStatement(groupTxos.map(_.outputAddress), 1)
    val inputs =
      for (txo <- groupTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq()

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(_.toList.contains(TransactionSyntaxError.InvalidMergingStatement(asmGroup)))
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * input UTXOs are not present in the transaction outputs
   */
  test("Invalid case, an input UTXO to merge is also present in the transaction outputs (non sufficient funds)") {
    val asmGroup = AssetMergingStatement(groupTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- groupTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(mergedGroup, UnspentTransactionOutput(mockLockAddress, groupTxos.head.transactionOutput.value))

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.InsufficientInputFunds(
          inputs.map(_.value.value).toList,
          outputs.map(_.value.value).toList
        )
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * merging statement contain 2 or more asset inputs that exist in the transaction
   */
  test("Invalid case, merging statement's inputs are not assets") {
    val asmGroup = AssetMergingStatement(groupTxos.map(_.outputAddress), 0)
    val inputs = for (txo <- groupTxos) yield SpentTransactionOutput(txo.outputAddress, mockAttestation, lvlValue)
    val outputs = for (_ <- groupTxos.indices) yield UnspentTransactionOutput(mockLockAddress, lvlValue)

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value))
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * merging statement's output's index does not refer to an asset output
   */
  test("Invalid case, merging statement's output index refers to a non-asset") {
    val asmGroup = AssetMergingStatement(groupTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- groupTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(UnspentTransactionOutput(mockLockAddress, lvlValue))

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value))
    )
    assertEquals(assertError, true)
    assertEquals(
      result.map(_.toList.size).getOrElse(0),
      2
    ) // 2 errors, one for the input incompatible merge, the other for insufficient funds (lvl has no input)
  }

  /**
   * all UTXOs (inputs and the new output) share the same (non group-and-series) fungibility type
   */
  test("Invalid case, UTXOs share GROUP_AND_SERIES fungibility type") {
    val groupSeriesTxos =
      for (i <- 1 to 3)
        yield Txo(
          transactionOutput = UnspentTransactionOutput(
            mockLockAddress,
            Value.defaultInstance.withAsset(
              mockAsset
                .withFungibility(FungibilityType.GROUP_AND_SERIES)
                .withSeriesId(SeriesId(ByteString.copyFrom(Array.fill(32)(i.toByte))))
                .withGroupId(GroupId(ByteString.copyFrom(Array.fill(32)(i.toByte))))
            )
          ),
          outputAddress = dummyTxoAddress.withIndex(i)
        )
    val mergedGroupSeries = MergingOps.merge(groupSeriesTxos, mockLockAddress, None, None)
    val asmGroupSeries = AssetMergingStatement(groupSeriesTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- groupSeriesTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(mergedGroupSeries)

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroupSeries))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * all UTXOs (inputs and the new output) share the same (non group-and-series) fungibility type
   */
  test("Invalid case, all inputs do not share the same fungibility type") {
    val mockTxos = groupTxos :+ seriesTxos.head
    val mockOutput = MergingOps.merge(mockTxos, mockLockAddress, None, None)
    val asm = AssetMergingStatement(mockTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- mockTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(mockOutput)

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asm))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * all UTXOs (inputs and the new output) share the same (non group-and-series) fungibility type
   */
  test("Invalid case, the output does not share the same fungibility type as the inputs") {
    val asmGroup = AssetMergingStatement(groupTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- groupTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(
      mergedGroup.copy(value =
        mergedGroup.value.withAsset(mergedGroup.value.getAsset.withFungibility(FungibilityType.SERIES))
      )
    )

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * all UTXOs (inputs and the new output) share the same quantity descriptor type
   */
  test("Invalid case, all inputs do not share the same quantity descriptor type") {
    val mockTxos = groupTxos.tail :+ groupTxos.head.copy(transactionOutput =
      groupTxos.head.transactionOutput.copy(value =
        groupTxos.head.transactionOutput.value.withAsset(
          groupTxos.head.transactionOutput.value.getAsset.withQuantityDescriptor(QuantityDescriptorType.ACCUMULATOR)
        )
      )
    )
    val mockOutput = MergingOps.merge(mockTxos, mockLockAddress, None, None)
    val asm = AssetMergingStatement(mockTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- mockTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(mockOutput)

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asm))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * all UTXOs (inputs and the new output) share the same quantity descriptor type
   */
  test("Invalid case, the output does not share the same quantity descriptor type as the inputs") {
    val asmGroup = AssetMergingStatement(groupTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- groupTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(
      mergedGroup.copy(value =
        mergedGroup.value.withAsset(
          mergedGroup.value.getAsset.withQuantityDescriptor(QuantityDescriptorType.ACCUMULATOR)
        )
      )
    )

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * If group fungible, all UTXOs must share the same group ID.
   */
  test("Invalid case, all inputs do not share the same group id") {
    val mockTxos = groupTxos.tail :+ groupTxos.head.copy(transactionOutput =
      groupTxos.head.transactionOutput.copy(value =
        groupTxos.head.transactionOutput.value.withAsset(
          groupTxos.head.transactionOutput.value.getAsset
            .withGroupId(GroupId(ByteString.copyFrom(Array.fill(32)(99.toByte))))
        )
      )
    )
    val mockOutput = MergingOps.merge(mockTxos, mockLockAddress, None, None)
    val asm = AssetMergingStatement(mockTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- mockTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(mockOutput)

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asm))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * If group fungible, all UTXOs must share the same group ID.
   */
  test("Invalid case, the output does not share the group id as the inputs") {
    val asmGroup = AssetMergingStatement(groupTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- groupTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(
      mergedGroup.copy(value =
        mergedGroup.value.withAsset(
          mergedGroup.value.getAsset.withGroupId(GroupId(ByteString.copyFrom(Array.fill(32)(99.toByte))))
        )
      )
    )

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * If group fungible, The new output must have a valid seriesAlloy/seriesId.
   */
  test("Invalid case, the output does not have a series alloy") {
    val asmGroup = AssetMergingStatement(groupTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- groupTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs =
      Seq(mergedGroup.copy(value = mergedGroup.value.withAsset(mergedGroup.value.getAsset.clearSeriesAlloy)))

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * If group fungible, The new output must have a valid seriesAlloy/seriesId.
   */
  test("Invalid case, the output has a seriesId defined") {
    val asmGroup = AssetMergingStatement(groupTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- groupTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(
      mergedGroup.copy(value =
        mergedGroup.value.withAsset(
          mergedGroup.value.getAsset.withSeriesId(SeriesId(ByteString.copyFrom(Array.fill(32)(99.toByte))))
        )
      )
    )
    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )
    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * If group fungible, The new output must have a valid seriesAlloy.
   */
  test("Invalid case, the output's series alloy is not a valid merkle root of the inputs") {
    val asmGroup = AssetMergingStatement(groupTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- groupTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(
      mergedGroup.copy(value =
        mergedGroup.value.withAsset(
          mergedGroup.value.getAsset.withSeriesAlloy(mergedSeries.value.getAsset.getGroupAlloy)
        )
      )
    )

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )
    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * If series fungible, all UTXOs must share the same series ID.
   */
  test("Invalid case, all inputs do not share the same series id") {
    val mockTxos = seriesTxos.tail :+ seriesTxos.head.copy(transactionOutput =
      seriesTxos.head.transactionOutput.copy(value =
        seriesTxos.head.transactionOutput.value.withAsset(
          seriesTxos.head.transactionOutput.value.getAsset
            .withSeriesId(SeriesId(ByteString.copyFrom(Array.fill(32)(99.toByte))))
        )
      )
    )
    val mockOutput = MergingOps.merge(mockTxos, mockLockAddress, None, None)
    val asm = AssetMergingStatement(mockTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- mockTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(mockOutput)

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asm))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * If series fungible, all UTXOs must share the same series ID.
   */
  test("Invalid case, the output does not share the series id as the inputs") {
    val asmGroup = AssetMergingStatement(seriesTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- seriesTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(
      mergedSeries.copy(value =
        mergedSeries.value.withAsset(
          mergedSeries.value.getAsset.withSeriesId(SeriesId(ByteString.copyFrom(Array.fill(32)(99.toByte))))
        )
      )
    )

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * If series fungible, The new output must have a valid groupAlloy/groupId .
   */
  test("Invalid case, the output does not have a group alloy") {
    val asmGroup = AssetMergingStatement(seriesTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- seriesTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs =
      Seq(mergedSeries.copy(value = mergedSeries.value.withAsset(mergedSeries.value.getAsset.clearGroupAlloy)))

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * If series fungible, The new output must have a valid groupAlloy/groupId .
   */
  test("Invalid case, the output has a group id defined") {
    val asmGroup = AssetMergingStatement(seriesTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- seriesTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(
      mergedSeries.copy(value =
        mergedSeries.value.withAsset(
          mergedSeries.value.getAsset.withGroupId(GroupId(ByteString.copyFrom(Array.fill(32)(99.toByte))))
        )
      )
    )

    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * If series fungible, The new output must have a valid groupAlloy.
   */
  test("Invalid case, the output's group alloy is not a valid merkle root of the inputs") {
    val asmGroup = AssetMergingStatement(seriesTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- seriesTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(
      mergedSeries.copy(value =
        mergedSeries.value.withAsset(
          mergedSeries.value.getAsset.withGroupAlloy(mergedGroup.value.getAsset.getSeriesAlloy)
        )
      )
    )
    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }

  /**
   * quantity in the merged output is the sum of the inputs
   */
  test("Invalid case, the output's quantity is not the sum of the inputs") {
    val asmGroup = AssetMergingStatement(seriesTxos.map(_.outputAddress), 0)
    val inputs =
      for (txo <- seriesTxos)
        yield SpentTransactionOutput(txo.outputAddress, mockAttestation, txo.transactionOutput.value)
    val outputs = Seq(
      mergedSeries.copy(value =
        mergedSeries.value.withAsset(
          mergedSeries.value.getAsset.withQuantity(Int128(ByteString.copyFrom(BigInt(1).toByteArray)))
        )
      )
    )
    val testTx =
      IoTransaction.defaultInstance
        .withInputs(inputs)
        .withOutputs(outputs)
        .withDatum(
          Datum.IoTransaction.defaultInstance
            .withEvent(
              Event.IoTransaction.defaultInstance
                .withSchedule(Schedule(0, Long.MaxValue, System.currentTimeMillis))
                .withMetadata(SmallData.defaultInstance)
                .withMergingStatements(Seq(asmGroup))
            )
        )

    val result = validator.validate(testTx).swap

    val assertError = result.exists(
      _.toList.contains(
        TransactionSyntaxError.IncompatibleMerge(inputs.map(_.value), outputs.head.value)
      )
    )
    assertEquals(assertError, true)
    assertEquals(result.map(_.toList.size).getOrElse(0), 1)
  }
}
