package org.plasmalabs.sdk.syntax

import org.plasmalabs.sdk.common.ContainsImmutable.ContainsImmutableTOps
import org.plasmalabs.sdk.common.ContainsImmutable.instances.seriesPolicyImmutable
import org.plasmalabs.sdk.models.SeriesPolicy
import org.plasmalabs.sdk.models.SeriesId
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
