package org.plasmalabs.sdk.constants

import com.google.protobuf.ByteString
import org.plasmalabs.sdk.models.GroupPolicy
import org.plasmalabs.sdk.models.TransactionOutputAddress
import org.plasmalabs.sdk.models.TransactionId
import org.plasmalabs.sdk.models.SeriesPolicy
import org.plasmalabs.sdk.models.box.QuantityDescriptorType
import org.plasmalabs.sdk.models.box.FungibilityType

object AccountLedgerConstants {

  private val RegistrationUtxoGroupSeries = ByteString.copyFrom(Array.fill[Byte](32)(0))

  val GroupPolicyAccountLedgerPrivate =
    GroupPolicy(
      label = "Account Ledger Group",
      registrationUtxo = TransactionOutputAddress(
        network = NetworkConstants.PRIVATE_NETWORK_ID,
        ledger = NetworkConstants.MAIN_LEDGER_ID,
        index = 0,
        id = TransactionId(RegistrationUtxoGroupSeries)
      ),
      fixedSeries = None
    )

  val SeriesPolicyAccountLedgerPrivate =
    SeriesPolicy(
      label = "Account Ledger Series",
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

  val GroupPolicyAccountLedgerTestnet =
    GroupPolicy(
      label = "Account Ledger Group",
      registrationUtxo = TransactionOutputAddress(
        network = NetworkConstants.TEST_NETWORK_ID,
        ledger = NetworkConstants.MAIN_LEDGER_ID,
        index = 0,
        id = TransactionId(RegistrationUtxoGroupSeries)
      ),
      fixedSeries = None
    )

  val SeriesPolicyAccountLedgerTestnet =
    SeriesPolicy(
      label = "Account Ledger Series",
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

  val GroupPolicyAccountLedgerMainnet =
    GroupPolicy(
      label = "Account Ledger Group",
      registrationUtxo = TransactionOutputAddress(
        network = NetworkConstants.MAIN_NETWORK_ID,
        ledger = NetworkConstants.MAIN_LEDGER_ID,
        index = 0,
        id = TransactionId(RegistrationUtxoGroupSeries)
      ),
      fixedSeries = None
    )

  val SeriesPolicyAccountLedgerMainnet =
    SeriesPolicy(
      label = "Account Ledger Series",
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
