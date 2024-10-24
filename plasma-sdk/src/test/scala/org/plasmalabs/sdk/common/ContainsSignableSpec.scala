package org.plasmalabs.sdk.common

import org.plasmalabs.sdk.MockHelpers
import org.plasmalabs.sdk.common.ContainsImmutable.ContainsImmutableTOps
import org.plasmalabs.sdk.common.ContainsImmutable.instances._
import org.plasmalabs.sdk.common.ContainsSignable.ContainsSignableTOps
import org.plasmalabs.sdk.common.ContainsSignable.instances._
import org.plasmalabs.sdk.models.box.Attestation

class ContainsSignableSpec extends munit.FunSuite with MockHelpers {

  test("IoTransaction.signable should return the same bytes as IoTransaction.immutable minus the Proofs") {
    // withProofs has non-empty proofs for all the proofs. noProofs has proofs stripped away
    val withProofs = txFull.copy(inputs = txFull.inputs.map(stxo => stxo.copy(attestation = nonEmptyAttestation)))
    val emptyAttestation = Attestation().withPredicate(inPredicateLockFullAttestation.copy(responses = Seq.empty))
    val noProofs = withProofs.copy(inputs = withProofs.inputs.map(stxo => stxo.copy(attestation = emptyAttestation)))
    val signableFull = withProofs.signable.value
    val immutableFull = withProofs.immutable.value
    val immutableNoProofs = noProofs.immutable.value
    // The only difference between immutableFull and immutableEmpty is the Proofs
    val proofsImmutableSize = immutableFull.size - immutableNoProofs.size
    assertEquals(proofsImmutableSize > 0, true)
    assertEquals(signableFull.size, immutableFull.size - proofsImmutableSize)
    assertEquals(signableFull.size, immutableNoProofs.size)
  }

  test("The Proofs in an IoTransaction changing should not alter the transaction's signable bytes") {
    val withProofs = txFull.copy(inputs = txFull.inputs.map(stxo => stxo.copy(attestation = nonEmptyAttestation)))
    val signableFull = withProofs.signable.value
    val signableEmpty = txFull.signable.value
    // The only difference between signableFull and signableEmpty is the Proofs
    assertEquals(signableFull.size, signableEmpty.size)
  }
}
