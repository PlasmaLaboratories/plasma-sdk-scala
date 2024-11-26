package org.plasmalabs.sdk.constants

import com.google.protobuf.ByteString
import org.plasmalabs.sdk.models.GroupPolicy
import org.plasmalabs.sdk.models.TransactionOutputAddress
import org.plasmalabs.sdk.models.TransactionId
import org.plasmalabs.sdk.models.SeriesPolicy
import org.plasmalabs.sdk.models.box.QuantityDescriptorType
import org.plasmalabs.sdk.models.box.FungibilityType

object EthConstants {

  private val RegistrationUtxoGroupSeries = ByteString.copyFrom(Array.fill[Byte](32)(0))

  val GroupPolicyEthPrivate =
    GroupPolicy(
      label = "Eth Group",
      registrationUtxo = TransactionOutputAddress(
        network = NetworkConstants.PRIVATE_NETWORK_ID,
        ledger = NetworkConstants.MAIN_LEDGER_ID,
        index = 0,
        id = TransactionId(RegistrationUtxoGroupSeries)
      ),
      fixedSeries = None
    )

  val SeriesPolicyEthPrivate =
    SeriesPolicy(
      label = "Eth Series",
      tokenSupply = None,
      registrationUtxo = TransactionOutputAddress(
        network = NetworkConstants.PRIVATE_NETWORK_ID,
        ledger = NetworkConstants.MAIN_LEDGER_ID,
        index = 1,
        id = TransactionId(RegistrationUtxoGroupSeries)
      ),
      quantityDescriptor = QuantityDescriptorType.LIQUID,
      fungibility = FungibilityType.GROUP_AND_SERIES
    )

  val GroupPolicyEthTestnet =
    GroupPolicy(
      label = "Eth Group",
      registrationUtxo = TransactionOutputAddress(
        network = NetworkConstants.TEST_NETWORK_ID,
        ledger = NetworkConstants.MAIN_LEDGER_ID,
        index = 0,
        id = TransactionId(RegistrationUtxoGroupSeries)
      ),
      fixedSeries = None
    )

  val SeriesPolicyEthTestnet =
    SeriesPolicy(
      label = "Eth Series",
      tokenSupply = None,
      registrationUtxo = TransactionOutputAddress(
        network = NetworkConstants.TEST_NETWORK_ID,
        ledger = NetworkConstants.MAIN_LEDGER_ID,
        index = 1,
        id = TransactionId(RegistrationUtxoGroupSeries)
      ),
      quantityDescriptor = QuantityDescriptorType.LIQUID,
      fungibility = FungibilityType.GROUP_AND_SERIES
    )

  val GroupPolicyEthMainnet =
    GroupPolicy(
      label = "Eth Group",
      registrationUtxo = TransactionOutputAddress(
        network = NetworkConstants.MAIN_NETWORK_ID,
        ledger = NetworkConstants.MAIN_LEDGER_ID,
        index = 0,
        id = TransactionId(RegistrationUtxoGroupSeries)
      ),
      fixedSeries = None
    )

  val SeriesPolicyEthMainnet =
    SeriesPolicy(
      label = "Eth Series",
      tokenSupply = None,
      registrationUtxo = TransactionOutputAddress(
        network = NetworkConstants.MAIN_NETWORK_ID,
        ledger = NetworkConstants.MAIN_LEDGER_ID,
        index = 1,
        id = TransactionId(RegistrationUtxoGroupSeries)
      ),
      quantityDescriptor = QuantityDescriptorType.LIQUID,
      fungibility = FungibilityType.GROUP_AND_SERIES
    )

}
