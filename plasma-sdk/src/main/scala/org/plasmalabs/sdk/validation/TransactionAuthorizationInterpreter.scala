package org.plasmalabs.sdk.validation

import cats.Monad
import cats.implicits._
import org.plasmalabs.sdk.models.{AccumulatorRootId, Datum, LockId}
import org.plasmalabs.sdk.models.box.Attestation
import org.plasmalabs.sdk.models.transaction.IoTransaction
import org.plasmalabs.sdk.validation.algebras.TransactionAuthorizationVerifier
import org.plasmalabs.quivr.models.{Proof, Proposition}
import org.plasmalabs.quivr.api.Verifier
import org.plasmalabs.quivr.runtime.DynamicContext

/**
 * Validates that each Input within a Transaction is properly "authorized".  "Authorized" simply means "does the given
 * Proof satisfy the given Proposition?".
 */
object TransactionAuthorizationInterpreter {

  def make[F[_]: Monad]()(implicit verifier: Verifier[F, Datum]): TransactionAuthorizationVerifier[F] =
    new TransactionAuthorizationVerifier[F] {

      // TODO: Fix cases of `challenges.map(_.getRevealed)`

      /**
       * Verifies each (Proposition, Proof) pair in the given Transaction
       */
      override def validate(context: DynamicContext[F, String, Datum])(
        transaction: IoTransaction
      ): F[Either[TransactionAuthorizationError, IoTransaction]] =
        transaction.inputs
          .foldLeftM(Either.right[TransactionAuthorizationError, IoTransaction](transaction)) {
            case (Left(error), _) => error.asLeft[IoTransaction].pure[F]
            case (_, input) =>
              input.attestation.value match {
                case Attestation.Value.Predicate(p) =>
                  predicateValidate(p.lock.challenges.map(_.getRevealed), p.lock.threshold, p.responses, context).map(
                    r => r.map(_ => transaction)
                  )

                case Attestation.Value.Image(p) =>
                  imageValidate(p.lock.leaves, p.lock.threshold, p.known.map(_.getRevealed), p.responses, context)
                    .map(r => r.map(_ => transaction))

                case Attestation.Value.Commitment(p) =>
                  commitmentValidate(
                    p.lock.root.get,
                    p.lock.threshold,
                    p.known.map(_.getRevealed),
                    p.responses,
                    context
                  ).map(r => r.map(_ => transaction))
                case _ =>
                  (TransactionAuthorizationError.AuthorizationFailed(): TransactionAuthorizationError)
                    .asLeft[IoTransaction]
                    .pure[F]
              }
          }

      private def predicateValidate(
        challenges: Seq[Proposition],
        threshold:  Int,
        responses:  Seq[Proof],
        context:    DynamicContext[F, String, Datum]
      ): F[Either[TransactionAuthorizationError, Boolean]] =
        thresholdVerifier(challenges, responses, threshold, context)

      private def imageValidate(
        leaves:    Seq[LockId],
        threshold: Int,
        known:     Seq[Proposition],
        responses: Seq[Proof],
        context:   DynamicContext[F, String, Datum]
      ): F[Either[TransactionAuthorizationError, Boolean]] =
        // check that the known Propositions match the leaves?
        thresholdVerifier(known, responses, threshold, context)

      // commitments need an additional proof of membership to be provided with the proposition
      private def commitmentValidate(
        root:      AccumulatorRootId,
        threshold: Int,
        known:     Seq[Proposition],
        responses: Seq[Proof],
        context:   DynamicContext[F, String, Datum]
      ): F[Either[TransactionAuthorizationError, Boolean]] =
        thresholdVerifier(known, responses, threshold, context)

      /**
       * *
       * Verifies that at least threshold number of proofs satisfy their associated propositions
       * @param propositions the propositions to be verified
       * @param proofs the proofs to be verified
       * @param threshold the threshold of proofs that must be satisfied
       * @param context the context in which the proofs are to be verified
       * @param verifier the verifier to be used to verify the proofs
       * @return
       */
      private def thresholdVerifier(
        propositions: Seq[Proposition],
        proofs:       Seq[Proof],
        threshold:    Int,
        context:      DynamicContext[F, String, Datum]
      )(implicit verifier: Verifier[F, Datum]): F[Either[TransactionAuthorizationError, Boolean]] =
        if (threshold === 0) true.asRight[TransactionAuthorizationError].pure[F]
        else if (threshold > propositions.size)
          Either
            .left[TransactionAuthorizationError, Boolean](TransactionAuthorizationError.AuthorizationFailed())
            .pure[F]
        else if (proofs.isEmpty)
          Either
            .left[TransactionAuthorizationError, Boolean](TransactionAuthorizationError.AuthorizationFailed())
            .pure[F]
        // We assume a one-to-one pairing of sub-proposition to sub-proof with the assumption that some of the proofs
        // may be Proof.Value.Empty
        else if (proofs.size =!= propositions.size)
          Either
            .left[TransactionAuthorizationError, Boolean](TransactionAuthorizationError.AuthorizationFailed())
            .pure[F]
        else
          propositions
            .zip(proofs)
            .map(p => verifier.evaluate(p._1, p._2, context)) // Evaluate all the (proposition, proof) pairs
            .sequence
            .map(_.partitionMap(identity))
            .map { res =>
              // If at least threshold number of pairs are valid, authorization is successful
              if (res._2.count(identity) >= threshold)
                true.asRight[TransactionAuthorizationError]
              // If authorization fails, return the QuivrRuntimeErrors that were encountered
              else
                TransactionAuthorizationError.AuthorizationFailed(res._1.toList).asLeft[Boolean]
            }
    }
}
