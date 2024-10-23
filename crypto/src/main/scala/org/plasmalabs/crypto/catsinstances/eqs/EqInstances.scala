package org.plasmalabs.crypto.catsinstances.eqs

import cats.Eq
import org.plasmalabs.crypto.hash.digest.implicits._
import org.plasmalabs.crypto.hash.digest.Digest

trait EqInstances {
  implicit def digestEq[T: Digest]: Eq[T] = (digestA, digestB) => digestA.bytes.sameElements(digestB.bytes)
}
