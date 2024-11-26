package org.plasmalabs.sdk.display

import cats.effect.unsafe.implicits.global
import org.plasmalabs.sdk.Context
import org.plasmalabs.sdk.MockHelpers
import org.plasmalabs.sdk.MockWalletKeyApi
import org.plasmalabs.sdk.MockWalletStateApi
import org.plasmalabs.sdk.display.DisplayOps.DisplayTOps
import org.plasmalabs.sdk.models.{AssetMintingStatement, Datum, Event}
import org.plasmalabs.sdk.models.box._
import org.plasmalabs.sdk.models.transaction.{IoTransaction, Schedule}
import org.plasmalabs.sdk.syntax.bigIntAsInt128
import org.plasmalabs.sdk.syntax.longAsInt128
import org.plasmalabs.sdk.wallet.CredentiallerInterpreter
import org.plasmalabs.sdk.wallet.WalletApi
import com.google.protobuf.ByteString
import org.plasmalabs.quivr.models.Proof
import org.plasmalabs.quivr.models.SmallData
import org.plasmalabs.quivr.api.Proposer

class DisplaySpec extends munit.FunSuite with MockHelpers {

  test("Display Complex Transaction") {
    val testTx =
      IoTransaction.defaultInstance
        .withDatum(
          Datum.IoTransaction(
            Event
              .IoTransaction(
                Schedule(0, Long.MaxValue, System.currentTimeMillis),
                SmallData(ByteString.copyFrom("metadata".getBytes))
              )
              .withGroupPolicies(Seq(mockGroupPolicy))
              .withSeriesPolicies(Seq(mockSeriesPolicy))
              .withMintingStatements(
                Seq(AssetMintingStatement(mockGroupPolicy.registrationUtxo, mockSeriesPolicy.registrationUtxo, 1))
              )
          )
        )
        .withInputs(
          Seq(
            lvlValue,
            groupValue,
            seriesValue,
            assetGroupSeries
          ).map(value => inputFull.withValue(value))
        )
        .withOutputs(
          Seq(
            lvlValue,
            groupValue,
            seriesValue,
            assetGroupSeries
          ).map(value => output.withValue(value))
        )
    assertNoDiff(
      testTx.display.trim(),
      s"""
TransactionId              : 5TFBe6eADcWbG8xxkr8boGwtkv1jsQuPEqDTWALh1g1o

Group Policies
==============
Label                      : Mock Group Policy
Registration-Utxo          : 4pX2G4weCKBHDT9axEm3HChq6jURV7ZYRPgeb7KWkEzm#0
Fixed-Series               : NO FIXED SERIES

Series Policies
===============
Label                      : Mock Series Policy
Registration-Utxo          : 4pX2G4weCKBHDT9axEm3HChq6jURV7ZYRPgeb7KWkEzm#0
Fungibility                : group-and-series
Quantity-Descriptor        : liquid
Token-Supply               : UNLIMITED
Permanent-Metadata-Scheme  : \nNo permanent metadata
Ephemeral-Metadata-Scheme  : \nNo ephemeral metadata

Asset Minting Statements
========================
Group-Token-Utxo           : 4pX2G4weCKBHDT9axEm3HChq6jURV7ZYRPgeb7KWkEzm#0
Series-Token-Utxo          : 4pX2G4weCKBHDT9axEm3HChq6jURV7ZYRPgeb7KWkEzm#0
Quantity                   : 1
Permanent-Metadata         : \nNo permanent metadata

Asset Merging Statements
========================


Inputs
======
TxoAddress                 : 4pX2G4weCKBHDT9axEm3HChq6jURV7ZYRPgeb7KWkEzm#0
Attestation                : Not implemented
Type                       : LVL
Value                      : 1
-----------
TxoAddress                 : 4pX2G4weCKBHDT9axEm3HChq6jURV7ZYRPgeb7KWkEzm#0
Attestation                : Not implemented
Type                       : Group Constructor
Id                         : d69cbea20a2e8fe2001a5f7450a87503911598de76081b6e1108ce9cffba157c
Fixed-Series               : NO FIXED SERIES
Value                      : 1
-----------
TxoAddress                 : 4pX2G4weCKBHDT9axEm3HChq6jURV7ZYRPgeb7KWkEzm#0
Attestation                : Not implemented
Type                       : Series Constructor
Id                         : c5ae5cf5e687c46d19adc538941627fdb4f18c7e21b42bcb94ff5f6e5c84334f
Fungibility                : group-and-series
Token-Supply               : UNLIMITED
Quant-Descr.               : liquid
Value                      : 1
-----------
TxoAddress                 : 4pX2G4weCKBHDT9axEm3HChq6jURV7ZYRPgeb7KWkEzm#0
Attestation                : Not implemented
Type                       : Asset
GroupId                    : d69cbea20a2e8fe2001a5f7450a87503911598de76081b6e1108ce9cffba157c
SeriesId                   : c5ae5cf5e687c46d19adc538941627fdb4f18c7e21b42bcb94ff5f6e5c84334f
GroupAlloy                 : N/A
SeriesAlloy                : N/A
Commitment                 : No commitment
Ephemeral-Metadata         : \nNo ephemeral metadata
Value                      : 1

Outputs
=======
LockAddress                : ptetP7jshHTxZJvycCHWafhM42fdjBL3TtVZ4C9ngp5CieDUbbeNiR48Ffqq
Type                       : LVL
Value                      : 1
-----------
LockAddress                : ptetP7jshHTxZJvycCHWafhM42fdjBL3TtVZ4C9ngp5CieDUbbeNiR48Ffqq
Type                       : Group Constructor
Id                         : d69cbea20a2e8fe2001a5f7450a87503911598de76081b6e1108ce9cffba157c
Fixed-Series               : NO FIXED SERIES
Value                      : 1
-----------
LockAddress                : ptetP7jshHTxZJvycCHWafhM42fdjBL3TtVZ4C9ngp5CieDUbbeNiR48Ffqq
Type                       : Series Constructor
Id                         : c5ae5cf5e687c46d19adc538941627fdb4f18c7e21b42bcb94ff5f6e5c84334f
Fungibility                : group-and-series
Token-Supply               : UNLIMITED
Quant-Descr.               : liquid
Value                      : 1
-----------
LockAddress                : ptetP7jshHTxZJvycCHWafhM42fdjBL3TtVZ4C9ngp5CieDUbbeNiR48Ffqq
Type                       : Asset
GroupId                    : d69cbea20a2e8fe2001a5f7450a87503911598de76081b6e1108ce9cffba157c
SeriesId                   : c5ae5cf5e687c46d19adc538941627fdb4f18c7e21b42bcb94ff5f6e5c84334f
GroupAlloy                 : N/A
SeriesAlloy                : N/A
Commitment                 : No commitment
Ephemeral-Metadata         : \nNo ephemeral metadata
Value                      : 1

Datum
=====
Value                      : KJHK1EAZuVA
""".trim()
    )
  }

  test("Display Validation Errors") {
    val walletApi: WalletApi[F] = WalletApi.make[F](MockWalletKeyApi)
    val vErrs = for {
      andProp <- Proposer.andProposer[F].propose((MockHeightProposition, MockSignatureProposition))
      orProp  <- Proposer.orProposer[F].propose((MockDigestProposition, MockLockedProposition))
      notProp <- Proposer.notProposer[F].propose(MockTickProposition)
      innerPropositions = List(andProp, notProp, orProp)
      thresh <- Proposer.thresholdProposer[F].propose((innerPropositions.toSet, innerPropositions.length))
      testTx = txFull
        .withInputs(
          List(
            inputFull.copy(attestation =
              Attestation().withPredicate(
                Attestation.Predicate(Lock.Predicate(List(Challenge().withRevealed(thresh)), 1), List.fill(3)(Proof()))
              )
            )
          )
        )
        .withOutputs(
          Seq(
            output.withValue(output.value.withLvl(Value.LVL(BigInt(-1)))),
            output.withValue(output.value.withLvl(Value.LVL(BigInt(100))))
          )
        )
      ctx = Context[F](testTx, 50, _ => None) // Tick should pass, height should fail
      res <- CredentiallerInterpreter
        .make[F](walletApi, MockWalletStateApi, MockMainKeyPair)
        .proveAndValidate(testTx, ctx)
    } yield res.swap

    val errsDisplay = vErrs.unsafeRunSync().toOption.get.map("> " + _.display).mkString("\n").trim
    assertNoDiff(
      errsDisplay,
      s"""
> Transaction has an output with a non-positive quantity value
> Transaction inputs cannot satisfy outputs
> Authorization failed. Causes:
- Proof does not satisfy proposition.
  Proposition: Threshold
    threshold: 3
    challenges:
    - And
      left: HeightRange
      right: Signature
        routine: ExtendedEd25519
        vk: GeMD3jTehpe52JDKVn8iiGYfjv7kJpaKKeu7ub1ayoB7W1eUwSVCWHkPdT9px9ne1oTesjkTVTQ9ZA5ub869wFwm9zhJPs1Z97
    - Not
        TickRange
    - Or
      left: Digest
        routine: Blake2b256
        8YmGaFtQ5WmMQ7i6uStCZF7T6N6B6w5CT8nChEsr5ZrA
      right: Locked
  Proof: Threshold
    responses:
    - And
      left: HeightRange
      right: Signature
        4NTqoMeSwnYrcukLjwQwo2qUpXSHzxzDMMBWRCZn8kTCRGUwHw8SEsYDBJMLBWjiZb7cxeTyff6eKfuzNmbgFPeq
    - Not
        TickRange
    - Or
      left: Digest
        input: zTuS2beK
        salt: 3x4JNf
      right: Locked""".stripMargin
    )
  }

}
