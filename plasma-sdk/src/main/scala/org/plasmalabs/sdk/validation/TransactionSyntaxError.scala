package org.plasmalabs.sdk.validation

import org.plasmalabs.sdk.models.TransactionOutputAddress
import org.plasmalabs.sdk.models.box.Value
import org.plasmalabs.sdk.models.AssetMergingStatement
import org.plasmalabs.sdk.models.transaction.Schedule
import org.plasmalabs.quivr.models.{Proof, Proposition}

sealed abstract class TransactionSyntaxError extends ValidationError

object TransactionSyntaxError {

  /**
   * A Syntax error indicating that this transaction does not contain at least 1 input.
   */
  case object EmptyInputs extends TransactionSyntaxError

  /**
   * A Syntax error indicating that this transaction multiple inputs referring to the same KnownIdentifier.
   */
  case class DuplicateInput(knownIdentifier: TransactionOutputAddress) extends TransactionSyntaxError

  /**
   * A Syntax error indicating that this transaction contains too many outputs.
   */
  case object ExcessiveOutputsCount extends TransactionSyntaxError

  /**
   * A Syntax error indicating that this transaction contains an invalid timestamp.
   */
  case class InvalidTimestamp(timestamp: Long) extends TransactionSyntaxError

  /**
   * A Syntax error indicating that this transaction contains an invalid schedule.
   */
  case class InvalidSchedule(schedule: Schedule) extends TransactionSyntaxError

  /**
   * A Syntax error indicating that this transaction contains an output with a non-positive quantity value.
   */
  case class NonPositiveOutputValue(outputValue: Value) extends TransactionSyntaxError

  /**
   * A Syntax error indicating that the inputs of this transaction cannot satisfy the outputs.
   */
  case class InsufficientInputFunds(inputs: List[Value.Value], outputs: List[Value.Value])
      extends TransactionSyntaxError

  /**
   * A Syntax error indicating that this transaction contains a proof whose type does not match its corresponding proposition.
   */
  case class InvalidProofType(proposition: Proposition, proof: Proof) extends TransactionSyntaxError

  /**
   * A Syntax error indicating that the size of this transaction is invalid.
   */
  case object InvalidDataLength extends TransactionSyntaxError

  /**
   * A Syntax error indicating that this transaction contains invalid UpdateProposals
   */
  case class InvalidUpdateProposal(outputs: Seq[Value.UpdateProposal]) extends TransactionSyntaxError

  /**
   * A Syntax error indicating that this transaction contains an invalid MergingStatement
   */
  case class InvalidMergingStatement(statement: AssetMergingStatement) extends TransactionSyntaxError

  /**
   * A Syntax error indicating that a merging input in a transaction is non-distinct
   */
  case class NonDistinctMergingInput(input: TransactionOutputAddress) extends TransactionSyntaxError

  /**
   * A Syntax error indicating that the request merging operation is invalid
   */
  case class IncompatibleMerge(inputs: Seq[Value], output: Value) extends TransactionSyntaxError

  /**
   * A Syntax error indicating that the lock addresses in the transaction do not all share the same network ID
   */
  case class InconsistentNetworkIDs(networkIDs: Set[Int]) extends TransactionSyntaxError
}
