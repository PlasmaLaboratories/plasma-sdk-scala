package org.plasmalabs.sdk.validation

import cats.Monad
import org.plasmalabs.quivr.algebras.DigestVerifier
import org.plasmalabs.quivr.runtime.QuivrRuntimeError
import org.plasmalabs.quivr.runtime.QuivrRuntimeErrors.ValidationError
import org.plasmalabs.quivr.models.{Digest, DigestVerification, Preimage}
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxEitherObject}
import org.plasmalabs.crypto.hash.Blake2b256

/**
 * Validates that a Blake2b256 digest is valid.
 */
object Blake2b256DigestInterpreter {

  def make[F[_]: Monad](): DigestVerifier[F] = new DigestVerifier[F] {

    /**
     * Validates that an Blake2b256 digest is valid.
     * @param t DigestVerification object containing the digest and preimage
     * @return The DigestVerification object if the digest is valid, otherwise an error
     */
    override def validate(t: DigestVerification): F[Either[QuivrRuntimeError, DigestVerification]] = t match {
      case DigestVerification(Digest(d, _), Preimage(p, salt, _), _) =>
        val testHash: Array[Byte] = (new Blake2b256).hash(p.toByteArray ++ salt.toByteArray)
        val expectedHash: Array[Byte] = d.toByteArray
        if (java.util.Arrays.equals(testHash, expectedHash))
          Either.right[QuivrRuntimeError, DigestVerification](t).pure[F]
        else // TODO: replace with correct error. Verification failed.
          Either.left[QuivrRuntimeError, DigestVerification](ValidationError.LockedPropositionIsUnsatisfiable).pure[F]
      // TODO: replace with correct error. SignatureVerification is malformed
      case _ =>
        Either.left[QuivrRuntimeError, DigestVerification](ValidationError.LockedPropositionIsUnsatisfiable).pure[F]
    }
  }
}
