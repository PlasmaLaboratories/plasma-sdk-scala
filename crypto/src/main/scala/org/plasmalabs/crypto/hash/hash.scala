package org.plasmalabs.crypto

import org.plasmalabs.crypto.hash.digest._

/* Forked from https://github.com/input-output-hk/scrypto */

/**
 * This package wraps externally implemented hash algorithms
 */
package object hash {

  /**
   * The type that hash operations use as their input.
   */
  type Message = Array[Byte]

  /**
   * Represents a hashing function with a scheme and digest type.
   *
   * @tparam H the hashing scheme. This is not used internally but allows the type system to enforce the use of the expected hashing scheme.
   * @tparam D the digest type with an implicit Digest implementation
   */
  abstract class Hash[H, D: Digest] {

    /**
     * Hashes a set of messages with an optional prefix.
     *
     * @param prefix   the optional prefix byte of the hashed message
     * @param messages the set of messages to iteratively hash
     * @return the hash digest
     */
    def hash(prefix: Option[Byte], messages: Message*): D

    /**
     * Hashes a set of messages with a given prefix.
     *
     * @param prefix   the prefix byte of the hashed message
     * @param messages the set of messages to iteratively hash
     * @return the hash digest
     */
    def hash(prefix: Byte, messages: Message*): D = hash(Some(prefix), messages: _*)

    /**
     * Hashes a message.
     *
     * @param message the message to hash
     * @return the hash digest
     */
    def hash(message: Message): D = hash(None, message)
  }

  object Hash {
    def apply[H, D: Digest](implicit hash: Hash[H, D]): Hash[H, D] = hash
  }

  /**
   * The scala type for the Blake2b hash scheme.
   */
  type Blake2b

  /**
   * The scala type for SHA2-* hash schemes
   */
  type Sha

  import digest.implicits._

  /**
   * Use this to compute Blake2b-256 (32 byte) hashes.
   */
  val blake2b256: Hash[Blake2b, Digest32] = new Blake2bHash[Digest32] {}

  /**
   * Use this to compute Blake2b-512 (64 byte) hashes.
   */
  val blake2b512: Hash[Blake2b, Digest64] = new Blake2bHash[Digest64] {}

  /**
   * Use this to compute SHA2-256 (32 byte) hashes.
   */
  val sha256: Hash[Sha, Digest32] = new ShaHash[Digest32]("Sha-256") {}

  /**
   * User this to compute SHA2-512 (64 byte) hashes.
   */
  val sha512: Hash[Sha, Digest64] = new ShaHash[Digest64]("Sha-512") {}

  /**
   * Implicit versions of the hash schemes
   */
  trait Instances {
    implicit val sha256Hash: Hash[Sha, Digest32] = sha256
    implicit val sha512Hash: Hash[Sha, Digest64] = sha512
    implicit val blake2b256Hash: Hash[Blake2b, Digest32] = blake2b256
    implicit val blake2b512Hash: Hash[Blake2b, Digest64] = blake2b512
  }

  /**
   * Use this object to get an implicit for a supported hashing scheme.
   */
  object implicits extends Instances with DigestImplicits
}
