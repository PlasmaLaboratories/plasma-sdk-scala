package org.plasmalabs.sdk.syntax

import org.plasmalabs.sdk.common.ContainsImmutable.ContainsImmutableTOps
import org.plasmalabs.sdk.common.ContainsImmutable.instances.groupPolicyEventImmutable
import org.plasmalabs.sdk.models.Event.GroupPolicy
import org.plasmalabs.sdk.models.GroupId
import com.google.protobuf.ByteString
import java.security.MessageDigest
import scala.language.implicitConversions

trait GroupPolicySyntax {

  implicit def groupPolicyAsGroupPolicySyntaxOps(groupPolicy: GroupPolicy): GroupPolicyAsGroupPolicySyntaxOps =
    new GroupPolicyAsGroupPolicySyntaxOps(groupPolicy)
}

class GroupPolicyAsGroupPolicySyntaxOps(val groupPolicy: GroupPolicy) extends AnyVal {

  def computeId: GroupId = {
    val digest: Array[Byte] = groupPolicy.immutable.value.toByteArray
    val sha256 = MessageDigest.getInstance("SHA-256").digest(digest)
    GroupId(ByteString.copyFrom(sha256))
  }
}
