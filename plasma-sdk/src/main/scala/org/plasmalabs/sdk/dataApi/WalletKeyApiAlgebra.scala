package org.plasmalabs.sdk.dataApi

import org.plasmalabs.sdk.dataApi.WalletKeyApiAlgebra._
import org.plasmalabs.crypto.encryption.VaultStore

/**
 * Defines a storage API for fetching and storing Topl Main Key Vault Store.
 */
trait WalletKeyApiAlgebra[F[_]] {

  /**
   * Persist a VaultStore for the Topl Main Secret Key.
   *
   * @param mainKeyVaultStore The VaultStore to persist
   * @param name              The name identifier of the VaultStore. This is used to manage multiple wallet identities.
   *                          Most commonly, only one wallet identity will be used. It is the responsibility of the dApp
   *                          to manage the names of the wallet identities if multiple will be used.
   * @return nothing if successful. If persisting fails due to an underlying cause, return a DataApiException
   */
  def saveMainKeyVaultStore(mainKeyVaultStore: VaultStore[F], name: String): F[Either[WalletKeyException, Unit]]

  /**
   * Persist a mnemonic used to recover a Topl Main Secret Key.
   *
   * @param mnemonic          The mnemonic to persist
   * @param mnemonicName      The name identifier of the mnemonic.
   * @return nothing if successful. If persisting fails due to an underlying cause, return a WalletKeyException
   */
  def saveMnemonic(mnemonic: IndexedSeq[String], mnemonicName: String): F[Either[WalletKeyException, Unit]]

  /**
   * Return the VaultStore for the Topl Main Secret Key.
   *
   * @param name The name identifier  of the VaultStore. This is used to manage multiple wallet identities.
   *             Most commonly, only one wallet identity will be used. It is the responsibility of the dApp to manage
   *             the names of the wallet identities if multiple will be used.
   * @return The VaultStore for the Topl Main Secret Key if it exists. If retrieving fails due to an underlying cause, return a DataApiException
   */
  def getMainKeyVaultStore(name: String): F[Either[WalletKeyException, VaultStore[F]]]

  /**
   * Update a persisted VaultStore for the Topl Main Secret Key.
   *
   * @param name              The name identifier of the VaultStore to update. This is used to manage multiple wallet identities.
   *                          Most commonly, only one wallet identity will be used. It is the responsibility of the dApp
   *                          to manage the names of the wallet identities if multiple will be used.
   * @return nothing if successful. If the update fails due to an underlying cause (for ex does not exist), return a DataApiException
   */
  def updateMainKeyVaultStore(mainKeyVaultStore: VaultStore[F], name: String): F[Either[WalletKeyException, Unit]]

  /**
   * Delete a persisted VaultStore for the Topl Main Secret Key.
   *
   * @param name The name identifier of the VaultStore to delete. This is used to manage multiple wallet identities.
   *             Most commonly, only one wallet identity will be used. It is the responsibility of the dApp
   *             to manage the names of the wallet identities if multiple will be used.
   * @return nothing if successful. If the deletion fails due to an underlying cause (for ex does not exist), return a DataApiException
   */
  def deleteMainKeyVaultStore(name: String): F[Either[WalletKeyException, Unit]]
}

object WalletKeyApiAlgebra {
  abstract class WalletKeyException(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)
}
