package org.plasmalabs.sdk

import cats.Id
import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import org.plasmalabs.sdk.common.ContainsEvidence.Ops
import org.plasmalabs.sdk.common.ContainsImmutable.ContainsImmutableTOps
import org.plasmalabs.sdk.common.ContainsImmutable.instances._
import org.plasmalabs.sdk.common.ContainsSignable.ContainsSignableTOps
import org.plasmalabs.sdk.common.ContainsSignable.instances._
import org.plasmalabs.sdk.models.{GroupPolicy, SeriesPolicy}
import org.plasmalabs.sdk.models._
import org.plasmalabs.sdk.models.box.Attestation
import org.plasmalabs.sdk.models.box.Challenge
import org.plasmalabs.sdk.models.box.FungibilityType.{GROUP, SERIES}
import org.plasmalabs.sdk.models.box.Lock
import org.plasmalabs.sdk.models.box.QuantityDescriptorType.{ACCUMULATOR, FRACTIONABLE, IMMUTABLE}
import org.plasmalabs.sdk.models.box.Value
import org.plasmalabs.sdk.models.transaction._
import org.plasmalabs.sdk.syntax.{assetAsBoxVal, groupPolicyAsGroupPolicySyntaxOps, seriesPolicyAsSeriesPolicySyntaxOps}
import org.plasmalabs.quivr.api.Proposer
import org.plasmalabs.quivr.api.Prover
import com.google.protobuf.ByteString
import org.plasmalabs.quivr.models.{
  Digest,
  Int128,
  KeyPair,
  Preimage,
  Proof,
  Proposition,
  SignableBytes,
  SmallData,
  VerificationKey,
  Witness
}
import org.plasmalabs.sdk.syntax.{cryptoToPbKeyPair, pbKeyPairToCryptoKeyPair}
import org.plasmalabs.crypto.hash.implicits.sha256Hash
import org.plasmalabs.crypto.generation.Bip32Indexes
import org.plasmalabs.crypto.hash.Blake2b256
import org.plasmalabs.crypto.signing.ExtendedEd25519
import org.plasmalabs.sdk.constants.NetworkConstants.PRIVATE_NETWORK_ID
import org.plasmalabs.sdk.constants.NetworkConstants.MAIN_LEDGER_ID
import org.plasmalabs.sdk.constants.NetworkConstants.ACCOUNT_LEDGER_ID
import org.plasmalabs.sdk.constants.AccountLedgerConstants.GroupPolicyAccountLedgerPrivate
import org.plasmalabs.sdk.constants.AccountLedgerConstants.SeriesPolicyAccountLedgerPrivate

trait MockHelpers {
  type F[A] = IO[A]

  val fakeMsgBind: SignableBytes = "transaction binding".getBytes.immutable.signable

  val MockIndices: Indices = Indices(0, 0, 0)
  // Hardcoding ExtendedEd25519
  val MockMainKeyPair: KeyPair = (new ExtendedEd25519).deriveKeyPairFromSeed(Array.fill(96)(0: Byte))

  val MockChildKeyPair: KeyPair = (new ExtendedEd25519).deriveKeyPairFromChildPath(
    pbKeyPairToCryptoKeyPair(MockMainKeyPair).signingKey,
    List(
      Bip32Indexes.HardenedIndex(MockIndices.x),
      Bip32Indexes.SoftIndex(MockIndices.y),
      Bip32Indexes.SoftIndex(MockIndices.z)
    )
  )

  val MockSigningRoutine: String = "ExtendedEd25519"

  val MockSignatureProposition: Id[Proposition] =
    Proposer.signatureProposer[Id].propose((MockSigningRoutine, MockChildKeyPair.vk))

  val MockSignature: Witness = Witness(
    ByteString.copyFrom((new ExtendedEd25519).sign(MockChildKeyPair.signingKey, fakeMsgBind.value.toByteArray))
  )
  val MockSignatureProof: Id[Proof] = Prover.signatureProver[Id].prove(MockSignature, fakeMsgBind)

  val MockPreimage: Preimage = Preimage(ByteString.copyFrom("secret".getBytes), ByteString.copyFromUtf8("salt"))

  // Hardcoding Blake2b256
  val MockDigestRoutine: String = "Blake2b256"
  val MockSha256DigestRoutine: String = "Sha256"

  val MockDigest: Digest =
    Digest(ByteString.copyFrom((new Blake2b256).hash(MockPreimage.input.toByteArray ++ MockPreimage.salt.toByteArray)))

  val MockSha256Digest: Digest =
    Digest(ByteString.copyFrom(sha256Hash.hash(MockPreimage.input.toByteArray ++ MockPreimage.salt.toByteArray).value))
  val MockDigestProposition: Id[Proposition] = Proposer.digestProposer[Id].propose((MockDigestRoutine, MockDigest))

  val MockSha256DigestProposition: Id[Proposition] =
    Proposer.digestProposer[Id].propose((MockSha256DigestRoutine, MockSha256Digest))
  val MockDigestProof: Id[Proof] = Prover.digestProver[Id].prove(MockPreimage, fakeMsgBind)

  val MockMin: Long = 0L
  val MockMax: Long = 100L
  val MockChain: String = "header"
  val MockTickProposition: Id[Proposition] = Proposer.tickProposer[Id].propose((MockMin, MockMax))
  val MockTickProof: Id[Proof] = Prover.tickProver[Id].prove((), fakeMsgBind)

  val MockHeightProposition: Id[Proposition] = Proposer.heightProposer[Id].propose((MockChain, MockMin, MockMax))
  val MockHeightProof: Id[Proof] = Prover.heightProver[Id].prove((), fakeMsgBind)

  val MockLockedProposition: Id[Proposition] = Proposer.LockedProposer[Id].propose(None)
  val MockLockedProof: Id[Proof] = Prover.lockedProver[Id].prove((), fakeMsgBind)

  val txDatum: Datum.IoTransaction = Datum.IoTransaction(
    Event
      .IoTransaction(
        Schedule(0, Long.MaxValue, System.currentTimeMillis),
        SmallData.defaultInstance
      )
  )

  // Arbitrary Transaction that any new transaction can reference
  val dummyTx: IoTransaction = IoTransaction(datum = txDatum)

  val dummyTxIdentifier: TransactionId = TransactionId(dummyTx.sizedEvidence.digest.value)

  val dummyTxoAddress: TransactionOutputAddress =
    TransactionOutputAddress(
      PRIVATE_NETWORK_ID,
      MAIN_LEDGER_ID,
      0,
      dummyTxIdentifier
    )

  val quantity: Int128 = Int128(ByteString.copyFrom(BigInt(1).toByteArray))

  val lvlValue: Value = Value.defaultInstance.withLvl(Value.LVL(quantity))

  val assetValue1: Value = Value.defaultInstance.withAsset(
    Value.Asset(
      Some(GroupId(ByteString.copyFrom(Array.fill[Byte](12)(0) ++ Array.fill[Byte](20)(1)))),
      Some(SeriesId(ByteString.copyFrom(Array.fill[Byte](12)(0) ++ Array.fill[Byte](20)(1)))),
      quantity
    )
  )

  val assetValue2: Value = Value.defaultInstance.withAsset(
    Value.Asset(
      Some(GroupPolicyAccountLedgerPrivate.computeId),
      Some(SeriesPolicyAccountLedgerPrivate.computeId),
      quantity
    )
  )

  val assetValue3: Value = Value.defaultInstance.withAsset(
    Value.Asset(
      Some(GroupPolicyAccountLedgerPrivate.computeId),
      Some(SeriesPolicyAccountLedgerPrivate.computeId),
      Int128(ByteString.copyFrom(BigInt(2).toByteArray))
    )
  )

  val trivialOutLock: Lock =
    Lock().withPredicate(Lock.Predicate(List(Challenge().withRevealed(Proposer.tickProposer[Id].propose(5, 15))), 1))

  val trivialLockAddress: LockAddress =
    LockAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, LockId(trivialOutLock.sizedEvidence.digest.value))

  val trivialAccountLedgerLockAddress0: LockAddress =
    LockAddress(PRIVATE_NETWORK_ID, ACCOUNT_LEDGER_ID, LockId(trivialOutLock.sizedEvidence.digest.value))

  val trivialAccountLedgerLockAddress1: LockAddress =
    LockAddress(PRIVATE_NETWORK_ID, ACCOUNT_LEDGER_ID, LockId(trivialOutLock.sizedEvidence.digest.value))

  val trivialAccountLockAddress2: LockAddress =
    LockAddress(
      PRIVATE_NETWORK_ID,
      ACCOUNT_LEDGER_ID,
      LockId(ByteString.copyFrom(Array.fill[Byte](12)(0) ++ Array.fill[Byte](20)(1)))
    )

  val inPredicateLockFull: Lock.Predicate = Lock.Predicate(
    List(
      MockLockedProposition,
      MockDigestProposition,
      MockSignatureProposition,
      MockHeightProposition,
      MockTickProposition
    )
      .map(Challenge().withRevealed),
    3
  )

  val inLockFull: Lock = Lock().withPredicate(inPredicateLockFull)

  val inLockFullAddress: LockAddress =
    LockAddress(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, LockId(inLockFull.sizedEvidence.digest.value))

  val inPredicateLockFullAttestation: Attestation.Predicate = Attestation.Predicate(
    inPredicateLockFull,
    List(
      MockLockedProof,
      MockDigestProof,
      MockSignatureProof,
      MockHeightProof,
      MockTickProof
    )
  )

  val nonEmptyAttestation: Attestation = Attestation().withPredicate(inPredicateLockFullAttestation)

  val output: UnspentTransactionOutput = UnspentTransactionOutput(trivialLockAddress, lvlValue)

  val outputAccountLedger0: UnspentTransactionOutput =
    UnspentTransactionOutput(trivialAccountLedgerLockAddress0, lvlValue)

  val outputAccountLedger1: UnspentTransactionOutput =
    UnspentTransactionOutput(trivialAccountLedgerLockAddress1, lvlValue)

  val outputAccountLedger2: UnspentTransactionOutput =
    UnspentTransactionOutput(trivialAccountLedgerLockAddress0, lvlValue)

  val outputAccountLedger3: UnspentTransactionOutput =
    UnspentTransactionOutput(trivialAccountLedgerLockAddress0, assetValue1)

  val outputAccountLedger4: UnspentTransactionOutput = UnspentTransactionOutput(trivialAccountLockAddress2, assetValue2)

  val fullOutput: UnspentTransactionOutput = UnspentTransactionOutput(inLockFullAddress, lvlValue)

  val attFull: Attestation = Attestation().withPredicate(
    Attestation.Predicate(inPredicateLockFull, List.fill(inPredicateLockFull.challenges.length)(Proof()))
  )

  val inputFull: SpentTransactionOutput = SpentTransactionOutput(dummyTxoAddress, attFull, lvlValue)

  val inputFullAccountLedger0: SpentTransactionOutput = SpentTransactionOutput(dummyTxoAddress, attFull, assetValue2)

  val inputFullAccountLedger1: SpentTransactionOutput = SpentTransactionOutput(dummyTxoAddress, attFull, assetValue3)

  val txFull: IoTransaction =
    IoTransaction.defaultInstance.withInputs(List(inputFull)).withOutputs(List(output)).withDatum(txDatum)

  val mockVks: List[VerificationKey] = List(
    MockChildKeyPair.vk,
    (new ExtendedEd25519).deriveKeyPairFromSeed(Array.fill(96)(1: Byte)).vk
  )

  val mockSeriesPolicy: SeriesPolicy = SeriesPolicy("Mock Series Policy", None, dummyTxoAddress)
  val mockSeriesPolicyImmutable: SeriesPolicy = mockSeriesPolicy.copy(quantityDescriptor = IMMUTABLE)
  val mockSeriesPolicyFractionable: SeriesPolicy = mockSeriesPolicy.copy(quantityDescriptor = FRACTIONABLE)
  val mockSeriesPolicyAccumulator: SeriesPolicy = mockSeriesPolicy.copy(quantityDescriptor = ACCUMULATOR)
  val mockGroupPolicy: GroupPolicy = GroupPolicy("Mock Group Policy", dummyTxoAddress)

  val toplValue: Value = Value.defaultInstance.withTopl(Value.TOPL(quantity, None))
  val seriesValue: Value = Value.defaultInstance.withSeries(Value.Series(mockSeriesPolicy.computeId, quantity, None))
  val groupValue: Value = Value.defaultInstance.withGroup(Value.Group(mockGroupPolicy.computeId, quantity))

  val assetGroupSeries: Value = Value.defaultInstance.withAsset(
    Value.Asset(mockGroupPolicy.computeId.some, mockSeriesPolicy.computeId.some, quantity)
  )

  val assetGroupSeriesImmutable: Value =
    assetGroupSeries.copy(
      assetGroupSeries.getAsset
        .copy(quantityDescriptor = IMMUTABLE, seriesId = mockSeriesPolicyImmutable.computeId.some)
    )

  val assetGroupSeriesFractionable: Value =
    assetGroupSeries.copy(
      assetGroupSeries.getAsset
        .copy(quantityDescriptor = FRACTIONABLE, seriesId = mockSeriesPolicyFractionable.computeId.some)
    )

  val assetGroupSeriesAccumulator: Value =
    assetGroupSeries.copy(
      assetGroupSeries.getAsset
        .copy(quantityDescriptor = ACCUMULATOR, seriesId = mockSeriesPolicyAccumulator.computeId.some)
    )

  val assetGroup: Value = assetGroupSeries.copy(
    assetGroupSeries.getAsset
      .copy(fungibility = GROUP, seriesId = mockSeriesPolicy.copy(fungibility = GROUP).computeId.some)
  )

  val assetGroupImmutable: Value = assetGroup.copy(
    assetGroup.getAsset.copy(
      quantityDescriptor = IMMUTABLE,
      seriesId = mockSeriesPolicyImmutable.copy(fungibility = GROUP).computeId.some
    )
  )

  val assetGroupFractionable: Value = assetGroup.copy(
    assetGroup.getAsset.copy(
      quantityDescriptor = FRACTIONABLE,
      seriesId = mockSeriesPolicyFractionable.copy(fungibility = GROUP).computeId.some
    )
  )

  val assetGroupAccumulator: Value = assetGroup.copy(
    assetGroup.getAsset.copy(
      quantityDescriptor = ACCUMULATOR,
      seriesId = mockSeriesPolicyAccumulator.copy(fungibility = GROUP).computeId.some
    )
  )

  val assetSeries: Value = assetGroupSeries.copy(
    assetGroupSeries.getAsset
      .copy(fungibility = SERIES, seriesId = mockSeriesPolicy.copy(fungibility = SERIES).computeId.some)
  )

  val assetSeriesImmutable: Value = assetSeries.copy(
    assetSeries.getAsset.copy(
      quantityDescriptor = IMMUTABLE,
      seriesId = mockSeriesPolicyImmutable.copy(fungibility = SERIES).computeId.some
    )
  )

  val assetSeriesFractionable: Value = assetSeries.copy(
    assetSeries.getAsset.copy(
      quantityDescriptor = FRACTIONABLE,
      seriesId = mockSeriesPolicyFractionable.copy(fungibility = SERIES).computeId.some
    )
  )

  val assetSeriesAccumulator: Value = assetSeries.copy(
    assetSeries.getAsset.copy(
      quantityDescriptor = ACCUMULATOR,
      seriesId = mockSeriesPolicyAccumulator.copy(fungibility = SERIES).computeId.some
    )
  )
}
