package org.plasmalabs.sdk.validation

import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxEitherObject}
import cats.Monad
import org.plasmalabs.quivr.algebras.SignatureVerifier
import org.plasmalabs.quivr.runtime.QuivrRuntimeError
import org.plasmalabs.quivr.runtime.QuivrRuntimeErrors.ValidationError
import org.plasmalabs.quivr.models.{Message, SignatureVerification, VerificationKey, Witness}
import org.plasmalabs.quivr.models.VerificationKey._
import org.plasmalabs.crypto.signing.{Ed25519, ExtendedEd25519}

/**
 * Validates that an Ed25519 signature is valid.
 */
object ExtendedEd25519SignatureInterpreter {

  def make[F[_]: Monad](): SignatureVerifier[F] = new SignatureVerifier[F] {

    /**
     * Validates that an Ed25519 signature is valid.
     * @param t SignatureVerification object containing the message, verification key, and signature
     * @return The SignatureVerification object if the signature is valid, otherwise an error
     */
    override def validate(t: SignatureVerification): F[Either[QuivrRuntimeError, SignatureVerification]] = t match {
      case SignatureVerification(
            VerificationKey(Vk.ExtendedEd25519(ExtendedEd25519Vk(Ed25519Vk(vk, _), chainCode, _)), _),
            Witness(sig, _),
            Message(msg, _),
            _
          ) =>
        val extendedVk = ExtendedEd25519.PublicKey(Ed25519.PublicKey(vk.toByteArray), chainCode.toByteArray)
        if ((new ExtendedEd25519).verify(sig.toByteArray, msg.toByteArray, extendedVk))
          Either.right[QuivrRuntimeError, SignatureVerification](t).pure[F]
        else // TODO: replace with correct error. Verification failed.
          Either
            .left[QuivrRuntimeError, SignatureVerification](ValidationError.LockedPropositionIsUnsatisfiable)
            .pure[F]
      // TODO: replace with correct error. SignatureVerification is malformed
      case _ =>
        Either.left[QuivrRuntimeError, SignatureVerification](ValidationError.LockedPropositionIsUnsatisfiable).pure[F]
    }
  }
}
