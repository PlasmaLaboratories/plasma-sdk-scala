package org.plasmalabs.sdk.codecs

import org.plasmalabs.sdk.constants.NetworkConstants
import org.plasmalabs.sdk.models.LockAddress
import org.plasmalabs.sdk.models.LockId
import com.google.protobuf.ByteString

import scala.util.Random

class AddressCodecsSpec extends munit.FunSuite with AddressCodecTestCases {

  def checkEquality(
    address:     String,
    lockAddress: LockAddress
  ): Boolean =
    AddressCodecs
      .decodeAddress(address)
      .toOption
      .get
      .id
      .value
      .toByteArray()
      .zip(lockAddress.id.value.toByteArray())
      .map(x => x._1 == x._2)
      .fold(true)(_ && _) &&
    AddressCodecs
      .decodeAddress(address)
      .toOption
      .get
      .ledger == lockAddress.ledger &&
    AddressCodecs
      .decodeAddress(address)
      .toOption
      .get
      .network == lockAddress.network

  test("Main Network Main Ledger Zero Test") {
    assertEquals(
      AddressCodecs.encodeAddress(
        testMainLockZeroLockAddress
      ),
      testMainLockZeroLockAddressEncoded
    )
  }

  test("Main Network Main Ledger Zero Test Decode") {
    assert(
      checkEquality(
        testMainLockZeroLockAddressEncoded,
        testMainLockZeroLockAddress
      )
    )
  }

  test("Valhalla Network Main Ledger Zero Test") {
    assertEquals(
      AddressCodecs.encodeAddress(
        testTestLockZeroLockAddress
      ),
      testTestLockZeroLockAddressEncoded
    )
  }

  test("Valhalla Network Main Ledger Zero Test Decode") {
    assert(
      checkEquality(
        testTestLockZeroLockAddressEncoded,
        testTestLockZeroLockAddress
      )
    )
  }

  test("Private Network Main Ledger Zero Test") {
    assertEquals(
      AddressCodecs.encodeAddress(
        testPrivateLockZeroLockAddress
      ),
      testPrivateLockZeroLockAddressEncoded
    )
  }

  test("Private Network Main Ledger Zero Test Decode") {
    assert(
      checkEquality(
        testPrivateLockZeroLockAddressEncoded,
        testPrivateLockZeroLockAddress
      )
    )
  }

  test("Main Network Main Ledger All One Test") {
    assertEquals(
      AddressCodecs.encodeAddress(
        testMainLockAllOneLockAddress
      ),
      testMainLockAllOneLockAddressEncoded
    )
  }

  test("Main Network Main Ledger All One Test Decode") {
    assert(
      checkEquality(
        testMainLockAllOneLockAddressEncoded,
        testMainLockAllOneLockAddress
      )
    )
  }

  test("Valhalla Network Main Ledger All One Test") {
    assertEquals(
      AddressCodecs.encodeAddress(
        testTestLockAllOneLockAddress
      ),
      testTestLockAllOneLockAddressEncoded
    )
  }

  test("Valhalla Network Main Ledger All One Test Decode") {
    assert(
      checkEquality(
        testTestLockAllOneLockAddressEncoded,
        testTestLockAllOneLockAddress
      )
    )
  }

  test("Private Network Main Ledger All One Test") {
    assertEquals(
      AddressCodecs.encodeAddress(
        testPrivateLockAllOneLockAddress
      ),
      testPrivateLockAllOneLockAddressEncoded
    )
  }

  test("Private Network Main Ledger All One Test Decode") {
    assert(
      checkEquality(
        testPrivateLockAllOneLockAddressEncoded,
        testPrivateLockAllOneLockAddress
      )
    )
  }

  test("Test random encode and decode") {
    val randomLockAddress = LockAddress(
      NetworkConstants.MAIN_NETWORK_ID,
      NetworkConstants.MAIN_LEDGER_ID,
      LockId(
        ByteString.copyFrom(Random.nextBytes(32))
      )
    )
    val encoded = AddressCodecs.encodeAddress(
      randomLockAddress
    )
    assert(
      checkEquality(
        encoded,
        randomLockAddress
      )
    )
  }

}
