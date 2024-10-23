---
sidebar_position: 2
title: Easy API
---

For the most common use cases, we provide an easy-to-use API that allows you to interact with the platform without having 
to worry about the underlying details. This API is available in the Service Kit.

## Initialization

To initialize the Easy API, you need to create an instance of the `EasyApi` class using the provided function. 
This class provides a set of methods that allow you to interact with the platform.

```scala
case class InitArgs(
    networkId:    Int = MAIN_NETWORK_ID,
    ledgerId:     Int = MAIN_LEDGER_ID,
    host:         String = "localhost",
    port:         Int = 9084,
    secure:       Boolean = false,
    dbFile:       String = "wallet.db",
    keyFile:      String = "keyFile.json",
    mnemonicFile: String = "mnemonic.txt",
    passphrase:   Option[String] = None
)

def initialize[F[_]: Async](
    password: String,
    args:     InitArgs = InitArgs()
): F[EasyApi[F]]
```

Optionally, you can provide a custom configuration to the `initialize` method. The configuration allows you to specify the
network to use, the node to connect to, and wallet storage files. If you don't provide a configuration, the default one will be used.

## Usage

The Easy API makes it easy to transfer funds from one wallet account to another. Certain methods are provided to help you do so:

- `transferFunds`: Transfer funds from one account to another.
- `getBalance`: Get the balance of an account.
- `getAddressToReceiveFunds`: Generate an address to receive funds into an account.

By default, you have two accounts loaded into your wallet during initialization: the default account (`DefaultAccount`) and the faucet account (`GenesisAccount`). 

### Transfer Funds

To transfer funds from one account to another, you need to provide the sender account, the recipient address, the amount to transfer, the value type, and the fee. 
The sender account must have enough funds to cover the amount to transfer and the fee and must exist in your local wallet.
Any excess funds (i.e. change) will be transferred back into the sender account.

```scala
def transferFunds(
    from:      WalletAccount,
    recipient: LockAddress,
    amount:    Long,
    valueType: ValueTypeIdentifier,
    fee:       Long
): F[TransactionId]
```

### Get Balance

You can get the balance of an account using the following method. The resulting balance is grouped by value type.

```scala
def getBalance(account: WalletAccount): F[Map[ValueTypeIdentifier, Long]]
```

### Get Address to Receive Funds

To receive funds into an account, you need to first generate an address. This address can be used to receive funds from other accounts or other users.

```scala
def getAddressToReceiveFunds(account: WalletAccount): F[LockAddress]
```

## Example

The following example demonstrates how to initialize the Easy API and load your default wallet account (`DefaultAccount`) 
with 100Lvls from the faucet account (`GenesisAccount`). Finally, it prints the new balance of the default account.

For the code to work, you need to have a local instance of the node running.

```scala
package org.plasmalabs.sdk.servicekit

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.plasmalabs.sdk.constants.NetworkConstants.PRIVATE_NETWORK_ID
import org.plasmalabs.sdk.syntax.LvlType
import org.plasmalabs.sdk.servicekit.EasyApi.{DefaultAccount, GenesisAccount, InitArgs}

import scala.concurrent.duration.DurationInt

object Demo extends App {
  val Api = for {
    api <- EasyApi.initialize[IO]("your-wallet-password", InitArgs(networkId = PRIVATE_NETWORK_ID, keyFile = "default.db"))
    receivingAddress <- api.getAddressToReceiveFunds(DefaultAccount)
    _ <- api.transferFunds(GenesisAccount, receivingAddress, 100L, LvlType, 1L).andWait(15.seconds)
    walletBalance <- api.getBalance(DefaultAccount)
    _ <- IO.println(s"Wallet balance: \n$walletBalance")
  } yield ()

  Api.unsafeRunSync()
}
```

After running, you should be able to see that your default account has been loaded with 100Lvls.

```bash
Wallet balance: 
Map(LvlType -> 100)
```