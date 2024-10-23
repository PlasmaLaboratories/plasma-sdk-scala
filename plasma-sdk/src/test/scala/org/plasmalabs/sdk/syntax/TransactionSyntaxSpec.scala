package org.plasmalabs.sdk.syntax

import org.plasmalabs.sdk.MockHelpers
import org.plasmalabs.sdk.common.ContainsEvidence
import org.plasmalabs.sdk.common.ContainsImmutable
import org.plasmalabs.sdk.common.ContainsSignable
import org.plasmalabs.sdk.common.ContainsSignable.instances.ioTransactionSignable
import org.plasmalabs.sdk.models.TransactionId
import org.plasmalabs.sdk.models.common.ImmutableBytes
import org.plasmalabs.sdk.models.transaction.IoTransaction

class TransactionSyntaxSpec extends munit.FunSuite with MockHelpers {

  test("TransactionSyntax creates and embeds IDs") {
    val transaction = dummyTx
    assertEquals(transaction.transactionId, None)
    val expectedId = {
      implicit val immutableContainsImmutable: ContainsImmutable[ImmutableBytes] = identity
      val signableBytes = ContainsSignable[IoTransaction].signableBytes(transaction)
      val immutable = ImmutableBytes(signableBytes.value)
      val evidence = ContainsEvidence[ImmutableBytes].sizedEvidence(immutable)
      TransactionId(evidence.digest.value)
    }
    assertEquals(transaction.computeId, expectedId)
    assertEquals(transaction.id, expectedId)
    val withEmbeddedId = transaction.embedId
    assertEquals(withEmbeddedId.transactionId, Some(expectedId))
    assertEquals(withEmbeddedId.id, expectedId)
    assertEquals(withEmbeddedId.containsValidId, true)
  }
}
