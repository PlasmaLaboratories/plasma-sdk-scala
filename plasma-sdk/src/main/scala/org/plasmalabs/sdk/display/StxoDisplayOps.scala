package org.plasmalabs.sdk.display

import org.plasmalabs.sdk.display.DisplayOps.DisplayTOps
import org.plasmalabs.sdk.models.TransactionOutputAddress
import org.plasmalabs.sdk.models.transaction.SpentTransactionOutput
import org.plasmalabs.sdk.utils.Encoding
import org.plasmalabs.indexer.services.Txo

trait StxoDisplayOps {

  implicit val stxoDisplay: DisplayOps[SpentTransactionOutput] = (stxo: SpentTransactionOutput) =>
    Seq(
      padLabel("TxoAddress") + stxo.address.display,
      padLabel("Attestation") + "Not implemented",
      stxo.value.value.display
    ).mkString("\n")

  implicit val txoAddressDisplay: DisplayOps[TransactionOutputAddress] = (txoAddress: TransactionOutputAddress) =>
    s"${Encoding.encodeToBase58(txoAddress.id.value.toByteArray())}#${txoAddress.index}"

  implicit val txoDisplay: DisplayOps[Txo] = (txo: Txo) =>
    Seq(
      padLabel("TxoAddress") + txo.outputAddress.display,
      padLabel("LockAddress") + txo.transactionOutput.address.display,
      txo.transactionOutput.value.value.display
    ).mkString("\n")

}
