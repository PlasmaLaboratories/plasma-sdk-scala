package xyz.stratalab.sdk

import cats.Monad
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxOptionId}
import xyz.stratalab.sdk.models.Datum
import xyz.stratalab.sdk.models.transaction.IoTransaction
import xyz.stratalab.sdk.common.ContainsSignable.ContainsSignableTOps
import xyz.stratalab.sdk.common.ContainsSignable.instances._
import xyz.stratalab.sdk.validation.{
  Blake2b256DigestInterpreter,
  ExtendedEd25519SignatureInterpreter,
  Sha256DigestInterpreter
}
import xyz.stratalab.common.ParsableDataInterface
import xyz.stratalab.quivr.runtime.DynamicContext
import xyz.stratalab.quivr.algebras.{DigestVerifier, SignatureVerifier}
import quivr.models.SignableBytes

// A Verification Context opinionated to the Topl context.
// signableBytes, currentTick and the datums are dynamic
case class Context[F[_]: Monad](tx: IoTransaction, curTick: Long, heightDatums: String => Option[Datum])
    extends DynamicContext[F, String, Datum] {

  override val hashingRoutines: Map[String, DigestVerifier[F]] = Map(
    "Blake2b256" -> Blake2b256DigestInterpreter.make(),
    "Sha256"     -> Sha256DigestInterpreter.make()
  )

  override val signingRoutines: Map[String, SignatureVerifier[F]] =
    Map("ExtendedEd25519" -> ExtendedEd25519SignatureInterpreter.make())

  override val interfaces: Map[String, ParsableDataInterface] = Map() // Arbitrary

  override def signableBytes: F[SignableBytes] = tx.signable.pure[F]

  override def currentTick: F[Long] = curTick.pure[F]

  // Needed for height
  override val datums: String => Option[Datum] = heightDatums

  def heightOf(label: String): F[Option[Long]] = heightDatums(label)
    .flatMap(_.value match {
      case Datum.Value.Header(h) => h.event.height.some
      case _                     => None
    })
    .pure[F]
}
