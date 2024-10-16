package xyz.stratalab.sdk

import cats.Id
import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import xyz.stratalab.sdk.common.ContainsEvidence.Ops
import xyz.stratalab.sdk.common.ContainsImmutable.ContainsImmutableTOps
import xyz.stratalab.sdk.common.ContainsImmutable.instances._
import xyz.stratalab.sdk.common.ContainsSignable.ContainsSignableTOps
import xyz.stratalab.sdk.common.ContainsSignable.instances._
import xyz.stratalab.sdk.models.Event.{GroupPolicy, SeriesPolicy}
import xyz.stratalab.sdk.models._
import xyz.stratalab.sdk.models.box.Attestation
import xyz.stratalab.sdk.models.box.Challenge
import xyz.stratalab.sdk.models.box.FungibilityType.{GROUP, SERIES}
import xyz.stratalab.sdk.models.box.Lock
import xyz.stratalab.sdk.models.box.QuantityDescriptorType.{ACCUMULATOR, FRACTIONABLE, IMMUTABLE}
import xyz.stratalab.sdk.models.box.Value
import xyz.stratalab.sdk.models.transaction._
import xyz.stratalab.sdk.syntax.{assetAsBoxVal, groupPolicyAsGroupPolicySyntaxOps, seriesPolicyAsSeriesPolicySyntaxOps}
import xyz.stratalab.crypto.hash.Blake2b256
import xyz.stratalab.quivr.api.Proposer
import xyz.stratalab.quivr.api.Prover
import com.google.protobuf.ByteString
import quivr.models.{
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
import xyz.stratalab.sdk.syntax.{cryptoToPbKeyPair, pbKeyPairToCryptoKeyPair}
import xyz.stratalab.crypto.generation.Bip32Indexes
import xyz.stratalab.crypto.hash.implicits.sha256Hash
import xyz.stratalab.crypto.signing.ExtendedEd25519

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
      0,
      0,
      0,
      dummyTxIdentifier
    )

  val quantity: Int128 = Int128(ByteString.copyFrom(BigInt(1).toByteArray))

  val lvlValue: Value = Value.defaultInstance.withLvl(Value.LVL(quantity))

  val trivialOutLock: Lock =
    Lock().withPredicate(Lock.Predicate(List(Challenge().withRevealed(Proposer.tickProposer[Id].propose(5, 15))), 1))

  val trivialLockAddress: LockAddress =
    LockAddress(0, 0, LockId(trivialOutLock.sizedEvidence.digest.value))

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
  val inLockFullAddress: LockAddress = LockAddress(0, 0, LockId(inLockFull.sizedEvidence.digest.value))

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

  val fullOutput: UnspentTransactionOutput = UnspentTransactionOutput(inLockFullAddress, lvlValue)

  val attFull: Attestation = Attestation().withPredicate(
    Attestation.Predicate(inPredicateLockFull, List.fill(inPredicateLockFull.challenges.length)(Proof()))
  )

  val inputFull: SpentTransactionOutput = SpentTransactionOutput(dummyTxoAddress, attFull, lvlValue)

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
