package org.plasmalabs.quivr.algebras

import org.plasmalabs.common.ContextlessValidation
import quivr.models.DigestVerification
import org.plasmalabs.quivr.runtime.QuivrRuntimeError

/** A trait that provides Digest verification for use in a Dynamic Context */
trait DigestVerifier[F[_]] extends ContextlessValidation[F, QuivrRuntimeError, DigestVerification]
