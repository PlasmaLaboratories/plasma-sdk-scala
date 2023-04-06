package co.topl.brambl

import cats.Id
import co.topl.brambl.common.ContainsEvidence.Ops
import co.topl.brambl.common.ContainsImmutable.ContainsImmutableTOps
import co.topl.brambl.common.ContainsImmutable.instances._
import co.topl.brambl.common.ContainsSignable.ContainsSignableTOps
import co.topl.brambl.common.ContainsSignable.instances._
import co.topl.brambl.models._
import co.topl.brambl.models.box.Attestation
import co.topl.brambl.models.box.Challenge
import co.topl.brambl.models.box.Lock
import co.topl.brambl.models.box.Value
import co.topl.brambl.models.transaction._
import co.topl.brambl.routines.digests.Blake2b256Digest
import co.topl.brambl.routines.signatures.Ed25519Signature
import co.topl.quivr.api.Proposer
import co.topl.quivr.api.Prover
import com.google.protobuf.ByteString
import quivr.models.Int128
import quivr.models.Preimage
import quivr.models.SignableBytes
import quivr.models.SmallData

trait MockHelpers {

  val MockSecret: Array[Byte] = "A mock secret".getBytes
  val MockPreimage: Preimage = Preimage(ByteString.copyFrom(MockSecret), ByteString.copyFromUtf8("salt"))

  val txDatum: Datum.IoTransaction = Datum.IoTransaction(
    Event
      .IoTransaction(
        Schedule(3, 50, 100),
        SmallData(ByteString.copyFrom("metadata".getBytes))
      )
  )

  // Arbitrary Transaction that any new transaction can reference
  val dummyTx: IoTransaction = IoTransaction(datum = txDatum)

  val dummyTxIdentifier: Identifier.IoTransaction32 = Identifier.IoTransaction32(dummyTx.sized32Evidence)

  val dummyTxoAddress: TransactionOutputAddress =
    TransactionOutputAddress(
      0,
      0,
      0,
      TransactionOutputAddress.Id.IoTransaction32(dummyTxIdentifier)
    )

  val value: Value =
    Value.defaultInstance.withLvl(Value.LVL(Int128(ByteString.copyFrom(BigInt(1).toByteArray))))

  val trivialOutLock: Lock =
    Lock().withPredicate(Lock.Predicate(List(Challenge().withRevealed(Proposer.tickProposer[Id].propose(5, 15))), 1))

  val trivialLockAddress: LockAddress =
    LockAddress(0, 0, LockAddress.Id.Lock32(Identifier.Lock32(trivialOutLock.sized32Evidence)))

  val inLockFull: Lock.Predicate = Lock.Predicate(
    List(
      Proposer.LockedProposer[Id].propose(None),
      Proposer
        .digestProposer[Id]
        .propose(
          (
            // Hardcoding Blake2b256Digest
            Blake2b256Digest.routine,
            Blake2b256Digest.hash(MockPreimage)
          )
        ),
      Proposer
        .signatureProposer[Id]
        .propose(
          (
            // Hardcoding Ed25519Signature
            Ed25519Signature.routine,
            Ed25519Signature.createKeyPair(MockSecret).vk
          )
        ),
      Proposer.heightProposer[Id].propose(("header", 0, 100)),
      Proposer.tickProposer[Id].propose((0, 100))
    )
      .map(Challenge().withRevealed),
    3
  )

  val trivialInLockFullAddress: LockAddress =
    LockAddress(0, 0, LockAddress.Id.Lock32(Identifier.Lock32(inLockFull.sized32Evidence)))

  val fakeMsgBind: SignableBytes = "transaction binding".getBytes.immutable.signable

  val nonEmptyAttestation: Attestation = Attestation().withPredicate(
    Attestation.Predicate(
      inLockFull,
      List(
        Prover.lockedProver[Id].prove((), fakeMsgBind),
        Prover.heightProver[Id].prove((), fakeMsgBind),
        Prover.tickProver[Id].prove((), fakeMsgBind)
      )
    )
  )

  val output: UnspentTransactionOutput = UnspentTransactionOutput(trivialLockAddress, value)

  val attFull: Attestation = Attestation().withPredicate(Attestation.Predicate(inLockFull, List()))

  val inputFull: SpentTransactionOutput = SpentTransactionOutput(dummyTxoAddress, attFull, value)

  val txFull: IoTransaction = IoTransaction(List(inputFull), List(output), txDatum)
}