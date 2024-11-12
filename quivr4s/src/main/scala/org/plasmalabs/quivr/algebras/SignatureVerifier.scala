package org.plasmalabs.quivr.algebras

import org.plasmalabs.common.ContextlessValidation
import org.plasmalabs.quivr.models.SignatureVerification
import org.plasmalabs.quivr.runtime.QuivrRuntimeError

/** A trait that provides Signature verification for use in a Dynamic Context */
trait SignatureVerifier[F[_]] extends ContextlessValidation[F, QuivrRuntimeError, SignatureVerification]
