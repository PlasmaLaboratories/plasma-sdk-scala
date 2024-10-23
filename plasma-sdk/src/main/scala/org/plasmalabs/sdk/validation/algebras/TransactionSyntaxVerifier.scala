package org.plasmalabs.sdk.validation.algebras

import org.plasmalabs.sdk.models.transaction.IoTransaction
import org.plasmalabs.common.ContextlessValidation
import org.plasmalabs.sdk.validation.TransactionSyntaxError
import cats.data.NonEmptyChain

trait TransactionSyntaxVerifier[F[_]]
    extends ContextlessValidation[F, NonEmptyChain[TransactionSyntaxError], IoTransaction]
