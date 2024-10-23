package org.plasmalabs.sdk.common

import org.plasmalabs.sdk.models.Evidence
import org.plasmalabs.crypto.accumulators.LeafData
import org.plasmalabs.crypto.accumulators.merkle.MerkleTree
import org.plasmalabs.crypto.hash.digest.Digest32
import org.plasmalabs.crypto.hash.implicits._
import org.plasmalabs.crypto.hash.Blake2b
import org.plasmalabs.crypto.hash.blake2b256
import com.google.protobuf.ByteString
import quivr.models.Digest

/**
 * Contains signable bytes and has methods to get evidence of those bytes in the form of a 32 or 64 byte hash.
 */
trait ContainsEvidence[T] {
  def sizedEvidence(t: T): Evidence
}

object ContainsEvidence {
  def apply[T](implicit ev: ContainsEvidence[T]): ContainsEvidence[T] = ev

  implicit class Ops[T: ContainsEvidence](t: T) {
    def sizedEvidence: Evidence = ContainsEvidence[T].sizedEvidence(t)

  }

  implicit def blake2bEvidenceFromImmutable[T: ContainsImmutable]: ContainsEvidence[T] =
    new ContainsEvidence[T] {

      override def sizedEvidence(t: T): Evidence =
        Evidence(
          Digest(
            ByteString.copyFrom(
              blake2b256
                .hash(
                  ContainsImmutable[T].immutableBytes(t).value.toByteArray
                )
                .value
            )
          )
        )
    }

  implicit def merkleRootFromBlake2bEvidence[T: ContainsImmutable]: ContainsEvidence[List[T]] =
    new ContainsEvidence[List[T]] {

      override def sizedEvidence(list: List[T]): Evidence =
        Evidence(
          Digest(
            ByteString.copyFrom(
              MerkleTree
                .apply[Blake2b, Digest32](
                  list.zipWithIndex
                    .map { case (item, _) =>
                      LeafData(ContainsImmutable[T].immutableBytes(item).value.toByteArray)
                    }
                )
                .rootHash
                .value
            )
          )
        )
    }
}
