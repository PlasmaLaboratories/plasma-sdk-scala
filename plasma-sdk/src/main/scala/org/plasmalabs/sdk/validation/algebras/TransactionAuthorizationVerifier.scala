package org.plasmalabs.sdk.validation.algebras

import org.plasmalabs.sdk.models.Datum
import org.plasmalabs.sdk.models.transaction.IoTransaction
import org.plasmalabs.common.ContextualValidation
import org.plasmalabs.sdk.validation.TransactionAuthorizationError
import org.plasmalabs.quivr.runtime.DynamicContext

trait TransactionAuthorizationVerifier[F[_]]
    extends ContextualValidation[F, TransactionAuthorizationError, IoTransaction, DynamicContext[F, String, Datum]]
