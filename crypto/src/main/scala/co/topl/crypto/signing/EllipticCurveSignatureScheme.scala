package co.topl.crypto.signing

import co.topl.crypto.generation.EntropyToSeed
import co.topl.crypto.generation.mnemonic.Entropy
import co.topl.models.utility.HasLength.instances.bytesLength
import co.topl.models.utility.{Length, Sized}
import co.topl.models.{Bytes, Proof, SecretKey, VerificationKey}

import java.security.SecureRandom

/* Forked from https://github.com/input-output-hk/scrypto */

abstract private[signing] class EllipticCurveSignatureScheme[
  SK <: SecretKey,
  VK <: VerificationKey,
  SIG <: Proof,
  SeedLength <: Length
](implicit
  seedLength: SeedLength
) {

  val SignatureLength: Int
  val KeyLength: Int

  def deriveKeyPairFromEntropy(entropy: Entropy, password: Option[String])(implicit
    entropyToSeed:                      EntropyToSeed[SeedLength] = EntropyToSeed.instances.pbkdf2Sha512[SeedLength]
  ): (SK, VK) = {
    val seed = entropyToSeed.toSeed(entropy, password)
    deriveKeyPairFromSeed(seed)
  }

  def deriveKeyPairFromSeed(seed: Sized.Strict[Bytes, SeedLength]): (SK, VK)

  def sign(privateKey: SK, message: Bytes): SIG

  def verify(signature: SIG, message: Bytes, verifyKey: VK): Boolean

  def getVerificationKey(privateKey: SK): VK
}
