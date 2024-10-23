package org.plasmalabs.sdk.display

import org.plasmalabs.sdk.codecs.AddressCodecs
import org.plasmalabs.sdk.display.DisplayOps.DisplayTOps
import org.plasmalabs.sdk.models.LockAddress
import org.plasmalabs.sdk.models.transaction.UnspentTransactionOutput

trait UtxoDisplayOps {

  implicit val utxoDisplay: DisplayOps[UnspentTransactionOutput] = (utxo: UnspentTransactionOutput) =>
    Seq(
      padLabel("LockAddress") + utxo.address.display,
      utxo.value.value.display
    ).mkString("\n")

  implicit val lockAddressDisplay: DisplayOps[LockAddress] = (lockAddress: LockAddress) =>
    AddressCodecs.encodeAddress(lockAddress)

}
