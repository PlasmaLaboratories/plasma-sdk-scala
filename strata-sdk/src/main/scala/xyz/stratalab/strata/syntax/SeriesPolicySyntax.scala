package xyz.stratalab.sdk.syntax

import xyz.stratalab.sdk.common.ContainsImmutable.ContainsImmutableTOps
import xyz.stratalab.sdk.common.ContainsImmutable.instances.seriesPolicyEventImmutable
import xyz.stratalab.sdk.models.Event.SeriesPolicy
import xyz.stratalab.sdk.models.SeriesId
import com.google.protobuf.ByteString
import java.security.MessageDigest
import scala.language.implicitConversions

trait SeriesPolicySyntax {

  implicit def seriesPolicyAsSeriesPolicySyntaxOps(SeriesPolicy: SeriesPolicy): SeriesPolicyAsSeriesPolicySyntaxOps =
    new SeriesPolicyAsSeriesPolicySyntaxOps(SeriesPolicy)
}

class SeriesPolicyAsSeriesPolicySyntaxOps(val seriesPolicy: SeriesPolicy) extends AnyVal {

  def computeId: SeriesId = {
    val digest: Array[Byte] = seriesPolicy.immutable.value.toByteArray
    val sha256 = MessageDigest.getInstance("SHA-256").digest(digest)
    SeriesId(ByteString.copyFrom(sha256))
  }
}
