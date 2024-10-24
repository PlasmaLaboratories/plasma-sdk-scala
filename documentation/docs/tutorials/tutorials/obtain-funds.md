---
sidebar_position: 1
title: Load Wallet with Funds
description: Populate a wallet with LVL tokens from your Local Node
---

## Use Case

To load a new wallet with 100 LVL tokens from your Local Node.

**Objectives:**

- Create and initialize a wallet
- Create an unproven transaction to transfer funds from the Genesis block to the wallet
- Prove the transaction
- Broascast the transaction to the local node

## Set-Up

Get started with launching a Local Node:

1. [Get Docker](https://docs.docker.com/get-docker/)
2. Pull latest node image
   ```bash
    docker pull docker.io/toplprotocol/bifrost-node:2.0.0-alpha10
   ``` 
3. Run a node
   ```bash
    docker run --rm -p 9085:9085 -p 9084:9084 -p 9091:9091 docker.io/toplprotocol/bifrost-node:2.0.0-alpha10
   ```

## Step 1: Create and Initialize a Wallet

Before we can load a wallet with funds, we need to create the wallet. There are 2 steps to this process: creating the wallet's 
 main key and initializing the wallet state.

### Create the Wallet's Main Key

We will generate a new Main Key for the wallet using `createAndSaveNewWallet`. This will also save the keyfile and 
mnemonic to the local file system. See [Create a Wallet](../../reference/wallets/create).

1. Initialize a Wallet Key API to persist the keyfile and mnemonic. Here we are using the provided default implementation
provided by the Service Kit to persist to the local file system.
   ```scala
   val walletKeyApi = WalletKeyApi.make[IO]()
   ```
2. Using the `walletKeyApi` created above, initialize a Wallet Api
   ```scala
   val walletApi = WalletApi.make(walletKeyApi)
   ```
3. Using the `walletApi` created above, create and persist a new wallet using `createAndSaveNewWallet`
   ```scala
   walletResult <- walletApi.createAndSaveNewWallet[IO]("password".getBytes, name = "keyfile.json", mnemonicName = "mnemonic.txt")
   ```

### Initialize the Wallet State

We will initialize the wallet state using `initWalletState`. Here we are using the provided default implementation 
provided by the Service Kit to persist to a SQLite database file. See [Initialize Wallet State](../../reference/wallet-state#initialize-wallet-state)

1. With the `walletApi` created in the previous section, initialize a Wallet State API
   ```scala
   val walletStateApi = WalletStateApi.make[IO](WalletStateResource.walletResource("wallet.db"), walletApi)
   ```
2. Using the `walletResult` created above, extract the main key.
   ```scala
   mainKeyPair <- walletApi.extractMainKey(walletResult.mainKeyVaultStore, "password".getBytes())
   ```
3. Using the main key and `walletStateApi` created above, initialize the wallet state using `initWalletState`
   ```scala
   walletStateApi.initWalletState(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, mainKeyPair)
   ```

### Breakpoint Check

At this point, your code should look something like this:

```scala
import cats.arrow.FunctionK
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import co.topl.brambl.constants.NetworkConstants.{PRIVATE_NETWORK_ID, MAIN_LEDGER_ID}
import org.plasmalabs.sdk.servicekit.{WalletKeyApi, WalletStateApi, WalletStateResource}
import org.plasmalabs.sdk.wallet.WalletApi

import java.io.File
import java.nio.file.Paths

implicit val transformType: FunctionK[IO, IO] = FunctionK.id[IO]

// Replace with the desired location for your tutorial directory
val tutorialDir = Paths.get(System.getProperty("user.home"), "tutorial").toString
new File(tutorialDir).mkdirs() // Create the directory if it doesn't exist

def initFilePath(fileName: String): String = {
   val filePath = Paths.get(tutorialDir, fileName).toString
   new File(filePath).delete() // Clear the file if it already exists
   filePath
}

// Replace with the desired location for your key file
val keyFile = initFilePath("keyfile.json")
// Replace with the desired location of for your mnemonic file
val mnemonicFile = initFilePath("mnemonic.txt")
// Replace with the desired location of for your wallet state DB file
val walletDb = initFilePath("wallet.db")

val walletKeyApi = WalletKeyApi.make[IO]()
val walletApi = WalletApi.make(walletKeyApi)
val conn = WalletStateResource.walletResource(walletDb)
val walletStateApi = WalletStateApi.make[IO](conn, walletApi)

val initializeWallet = for {
   walletResult <- walletApi.createAndSaveNewWallet[IO]("password".getBytes, name = keyFile, mnemonicName = mnemonicFile)
   mainKeyPair <- walletApi.extractMainKey(walletResult.toOption.get.mainKeyVaultStore, "password".getBytes())
   _ <- walletStateApi.initWalletState(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, mainKeyPair.toOption.get)
} yield mainKeyPair

initializeWallet.unsafeRunSync()
```

Running this code would create 3 files in your home directory: `keyfile.json`, `mnemonic.txt`, and `wallet.db`.

## Step 2: Create Transaction

After you have a wallet initialized, the next step is to build a transaction that will populate your wallet with some funds. 
This transaction should transfer funds from the Genesis block to your wallet.

### Query Genesis Funds
The Genesis block encumbers tokens with a height lock. To retrieve these tokens, we need to create the LockAddress for this Height Lock.
This LockAddress will also be used as the change address for the transaction (any excess funds will go back to this Address).

1. Retrieve HeightLock from the Wallet State API. Since we are using the provided default implementation of the Wallet State API,
we can retrieve the HeightLock using the following code:
   ```scala
   heightLock <- walletStateApi.getLock("nofellowship", "genesis", 0)
   ```
2. Create a LockAddress from the HeightLock retrieved in the previous step. See [Initialize Wallet State](../../reference/locks/create-lock-addr)
   ```scala
   heightAddress <- TransactionBuilderApi.make[IO](PRIVATE_NETWORK_ID, MAIN_LEDGER_ID).lockAddress(heightLock)
   ```
3. With the LockAddress created in the previous step, we can query the Genesis block for funds. This will return a list of UTXOs
which will be the inputs for the transaction. See [Querying UTXOs](../../reference/rpc#querying-utxos)
   ```scala
   genusQueryApi = GenusQueryAlgebra.make[IO](RpcChannelResource.channelResource[IO]("localhost", 9084, false))
   txos <- genusQueryApi.queryUtxo(heightAddress)
   ```

### Generate a New Lock Address to Receive Tokens

To receive the funds, we need to generate a LockAddress for which the funds will be transferred to (i.e, an output in 
the transaction). This LockAddress that will encumber our new funds will be generated from our wallet. This will allow us
to prove ownership once we want to spend the funds. For this tutorial, we will generate a LockAddress for a 1-of-1 
Digital Signature Lock.

1. Retrieve a 1-of-1 Signature Lock from the Wallet State API. Since we are using the provided default implementation of the Wallet State API,
we can retrieve the Signature Lock using the following code:
   ```scala
   sigLock <- walletStateApi.getLock("self", "default", 1)
   ```
2. Create a LockAddress from the Signature Lock retrieved in the previous step.
   ```scala
   sigAddress <- TransactionBuilderApi.make[IO](PRIVATE_NETWORK_ID, MAIN_LEDGER_ID).lockAddress(sigLock)
   ```

### Build the Transaction

Using everything we generated in this section, we can finally build the transaction using the Transaction Builder API.
See [Build Transfer Transaction](../../reference/transactions/transfer). For this tutorial, we will transfer 100 LVLs 
to our wallet. The excess funds will be sent back to the Genesis LockAddress.

```scala
TransactionBuilderApi.make[IO](PRIVATE_NETWORK_ID, MAIN_LEDGER_ID).buildTransferAmountTransaction(
   LvlType, // We are transferring LVLs
   txos,  // The UTXOs we queried from the Genesis block in "Query Genus Funds"
   heightLock.getPredicate, // The HeightLock we retrieved in "Query Genus Funds" 
   100L, // The amount of LVLs we want to transfer
   sigAddress, // Our LockAddress that we generated in "Generate a New Lock Address to Receive Tokens"
   heightAddress, // The LockAddress to send back change that we generated in "Query Genus Funds"
   1L // An arbitrary fee amount
)
```

### Breakpoint Check

At this point, your code should look something like this:

```scala
import cats.arrow.FunctionK
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.plasmalabs.sdk.builders.TransactionBuilderApi
import co.topl.brambl.constants.NetworkConstants.{MAIN_LEDGER_ID, PRIVATE_NETWORK_ID}
import org.plasmalabs.sdk.dataApi.{GenusQueryAlgebra, RpcChannelResource}
import org.plasmalabs.sdk.servicekit.{WalletKeyApi, WalletStateApi, WalletStateResource}
import org.plasmalabs.sdk.syntax.LvlType
import org.plasmalabs.sdk.wallet.WalletApi

import java.io.File
import java.nio.file.Paths

implicit val transformType: FunctionK[IO, IO] = FunctionK.id[IO]

// Replace with the desired location for your tutorial directory
val tutorialDir = Paths.get(System.getProperty("user.home"), "tutorial").toString
new File(tutorialDir).mkdirs() // Create the directory if it doesn't exist

def initFilePath(fileName: String): String = {
   val filePath = Paths.get(tutorialDir, fileName).toString
   new File(filePath).delete() // Clear the file if it already exists
   filePath
}

// Replace with the desired location for your key file
val keyFile = initFilePath("keyfile.json")
// Replace with the desired location of for your mnemonic file
val mnemonicFile = initFilePath("mnemonic.txt")
// Replace with the desired location of for your wallet state DB file
val walletDb = initFilePath("wallet.db")

val walletKeyApi = WalletKeyApi.make[IO]()
val walletApi = WalletApi.make(walletKeyApi)
val conn = WalletStateResource.walletResource(walletDb)
val walletStateApi = WalletStateApi.make[IO](conn, walletApi)

val initializeWallet = for {
  walletResult <- walletApi.createAndSaveNewWallet[IO]("password".getBytes, name = keyFile, mnemonicName = mnemonicFile)
  mainKeyPair <- walletApi.extractMainKey(walletResult.toOption.get.mainKeyVaultStore, "password".getBytes())
  _ <- walletStateApi.initWalletState(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, mainKeyPair.toOption.get)
} yield mainKeyPair

// Replace with the address and port of your node's gRPC endpoint
val channelResource = RpcChannelResource.channelResource[IO]("localhost", 9084, secureConnection = false)
val genusQueryApi = GenusQueryAlgebra.make[IO](channelResource)
val txBuilder = TransactionBuilderApi.make[IO](PRIVATE_NETWORK_ID, MAIN_LEDGER_ID)

val unprovenTransaction = for {
    _ <- initializeWallet
    heightLock <- walletStateApi.getLock("nofellowship", "genesis", 1)
    heightAddress <- txBuilder.lockAddress(heightLock.get)
    txos <- genusQueryApi.queryUtxo(heightAddress)
    sigLock <- walletStateApi.getLock("self", "default", 1)
    sigAddress <- txBuilder.lockAddress(sigLock.get)
    tx <- txBuilder.buildTransferAmountTransaction(
      LvlType,
      txos,
      heightLock.get.getPredicate,
      100L,
      sigAddress,
      heightAddress,
      1L
    )
} yield tx.toOption.get

unprovenTransaction.unsafeRunSync()
```

## Step 3: Prove Transaction

In order for the funds to be transferred, we need to prove the Lock encumbering the funds in the Genesis block. You can think of this as
"unlocking" the funds. See [Prove Transaction](../../reference/prove). It is best practice to validate the transaction as well.

1. Create a Credentialler using your main key.
    ```scala
    val credentialler = CredentiallerInterpreter.make[IO](walletApi, walletStateApi, mainKey)
    ```
2. Create a context in which we will validate the transaction against. Ideally, this context should represent the current
context of the node as best as possible. This is more important for validating Locks that contain Height or Tick range 
propositions. In our case, the Genesis Height Lock will be satisfied by a header height in between `1` and `Long.MaxValue`
so for our tutorial, we can choose any height and tick.
   ```scala
   ctx = Context[IO](unprovenTransaction, 50, Map("header" -> Datum().withHeader(Datum.Header(Event.Header(50)))).lift)
   ```
2. Prove and validate the unproven transaction from the previous section. If the transaction is valid, the proven transaction
will be returned. If the transaction is invalid, the validation errors will be returned.
   ```scala
   credentialler.proveAndValidate(tx, validCtx)
   ```

### Breakpoint Check

At this point, your code should look something like this:

```scala
import cats.arrow.FunctionK
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.plasmalabs.sdk.Context
import org.plasmalabs.sdk.builders.TransactionBuilderApi
import co.topl.brambl.constants.NetworkConstants.{MAIN_LEDGER_ID, PRIVATE_NETWORK_ID}
import org.plasmalabs.sdk.dataApi.{GenusQueryAlgebra, RpcChannelResource}
import org.plasmalabs.sdk.models.{Datum, Event}
import org.plasmalabs.sdk.servicekit.{WalletKeyApi, WalletStateApi, WalletStateResource}
import org.plasmalabs.sdk.syntax.LvlType
import org.plasmalabs.sdk.wallet.{CredentiallerInterpreter, WalletApi}

import java.io.File
import java.nio.file.Paths

implicit val transformType: FunctionK[IO, IO] = FunctionK.id[IO]

// Replace with the desired location for your tutorial directory
val tutorialDir = Paths.get(System.getProperty("user.home"), "tutorial").toString
new File(tutorialDir).mkdirs() // Create the directory if it doesn't exist

def initFilePath(fileName: String): String = {
   val filePath = Paths.get(tutorialDir, fileName).toString
   new File(filePath).delete() // Clear the file if it already exists
   filePath
}

// Replace with the desired location for your key file
val keyFile = initFilePath("keyfile.json")
// Replace with the desired location of for your mnemonic file
val mnemonicFile = initFilePath("mnemonic.txt")
// Replace with the desired location of for your wallet state DB file
val walletDb = initFilePath("wallet.db")

val walletKeyApi = WalletKeyApi.make[IO]()
val walletApi = WalletApi.make(walletKeyApi)
val conn = WalletStateResource.walletResource(walletDb)
val walletStateApi = WalletStateApi.make[IO](conn, walletApi)

val initializeWallet = for {
  walletResult <- walletApi.createAndSaveNewWallet[IO]("password".getBytes, name = keyFile, mnemonicName = mnemonicFile)
  mainKeyPair <- walletApi.extractMainKey(walletResult.toOption.get.mainKeyVaultStore, "password".getBytes())
  _ <- walletStateApi.initWalletState(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, mainKeyPair.toOption.get)
} yield mainKeyPair

// Replace with the address and port of your node's gRPC endpoint
val channelResource = RpcChannelResource.channelResource[IO]("localhost", 9084, secureConnection = false)
val genusQueryApi = GenusQueryAlgebra.make[IO](channelResource)
val txBuilder = TransactionBuilderApi.make[IO](PRIVATE_NETWORK_ID, MAIN_LEDGER_ID)

val unprovenTransaction = for {
    _ <- initializeWallet
    heightLock <- walletStateApi.getLock("nofellowship", "genesis", 1)
    heightAddress <- txBuilder.lockAddress(heightLock.get)
    txos <- genusQueryApi.queryUtxo(heightAddress)
    sigLock <- walletStateApi.getLock("self", "default", 1)
    sigAddress <- txBuilder.lockAddress(sigLock.get)
    tx <- txBuilder.buildTransferAmountTransaction(
      LvlType,
      txos,
      heightLock.get.getPredicate,
      100L,
      sigAddress,
      heightAddress,
      1L
    )
} yield tx.toOption.get

val proveAndValidateResult = for {
  tx <- unprovenTransaction
  mainKey <- walletApi.loadAndExtractMainKey[IO]("password".getBytes, keyFile)
  credentialler = CredentiallerInterpreter.make[IO](walletApi, walletStateApi, mainKey.toOption.get)
  ctx = Context[IO](tx, 50, Map("header" -> Datum().withHeader(Datum.Header(Event.Header(50)))).lift)
  res <- credentialler.proveAndValidate(tx, ctx)
} yield res

proveAndValidateResult.unsafeRunSync()
```

## Step 4: Broadcast Transaction

The last step is to broadcast the transaction to the network. See [Broadcast a Transaction](../../reference/rpc#broadcast-a-transaction).
The `channelResource` can be the same as the one used for `genusQueryApi` in a previous section.

```scala
val bifrostQuery = BifrostQueryAlgebra.make[IO](channelResource)

val broadcastTransaction = for {
   provenTx <- proveAndValidateResult
   txId <- bifrostQuery.broadcastTransaction(provenTx.toOption.get)
} yield txId
```

## Optional Step: Check Balance

If everything went well, you will soon have the funds available in your wallet. You can check your balance by querying the
Genus node for funds encumbered by your `sigAddress`. See [Querying UTXOs](../../reference/rpc#querying-utxos).

You should allow some time for the transaction to be processed by the network.

## Putting It All Together

At this point, your code should look something like this:

```scala
import cats.arrow.FunctionK
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.plasmalabs.sdk.Context
import org.plasmalabs.sdk.builders.TransactionBuilderApi
import co.topl.brambl.constants.NetworkConstants.{MAIN_LEDGER_ID, PRIVATE_NETWORK_ID}
import org.plasmalabs.sdk.dataApi.{BifrostQueryAlgebra, GenusQueryAlgebra, RpcChannelResource}
import org.plasmalabs.sdk.models.{Datum, Event}
import org.plasmalabs.sdk.servicekit.{WalletKeyApi, WalletStateApi, WalletStateResource}
import org.plasmalabs.sdk.syntax.{LvlType, valueToQuantitySyntaxOps, valueToTypeIdentifierSyntaxOps, int128AsBigInt}
import org.plasmalabs.sdk.wallet.{CredentiallerInterpreter, WalletApi}

import java.io.File
import java.nio.file.Paths

implicit val transformType: FunctionK[IO, IO] = FunctionK.id[IO]

// Replace with the desired location for your tutorial directory
val tutorialDir = Paths.get(System.getProperty("user.home"), "tutorial").toString
new File(tutorialDir).mkdirs() // Create the directory if it doesn't exist

def initFilePath(fileName: String): String = {
   val filePath = Paths.get(tutorialDir, fileName).toString
   new File(filePath).delete() // Clear the file if it already exists
   filePath
}

// Replace with the desired location for your key file
val keyFile = initFilePath("keyfile.json")
// Replace with the desired location of for your mnemonic file
val mnemonicFile = initFilePath("mnemonic.txt")
// Replace with the desired location of for your wallet state DB file
val walletDb = initFilePath("wallet.db")

val walletKeyApi = WalletKeyApi.make[IO]()
val walletApi = WalletApi.make(walletKeyApi)
val conn = WalletStateResource.walletResource(walletDb)
val walletStateApi = WalletStateApi.make[IO](conn, walletApi)

val initializeWallet = for {
  walletResult <- walletApi.createAndSaveNewWallet[IO]("password".getBytes, name = keyFile, mnemonicName = mnemonicFile)
  mainKeyPair <- walletApi.extractMainKey(walletResult.toOption.get.mainKeyVaultStore, "password".getBytes())
  _ <- walletStateApi.initWalletState(PRIVATE_NETWORK_ID, MAIN_LEDGER_ID, mainKeyPair.toOption.get)
} yield mainKeyPair

// Replace with the address and port of your node's gRPC endpoint
val channelResource = RpcChannelResource.channelResource[IO]("localhost", 9084, secureConnection = false)
val genusQueryApi = GenusQueryAlgebra.make[IO](channelResource)
val txBuilder = TransactionBuilderApi.make[IO](PRIVATE_NETWORK_ID, MAIN_LEDGER_ID)

val unprovenTransaction = for {
    _ <- initializeWallet
    heightLock <- walletStateApi.getLock("nofellowship", "genesis", 1)
    heightAddress <- txBuilder.lockAddress(heightLock.get)
    txos <- genusQueryApi.queryUtxo(heightAddress)
    sigLock <- walletStateApi.getLock("self", "default", 1)
    sigAddress <- txBuilder.lockAddress(sigLock.get)
    tx <- txBuilder.buildTransferAmountTransaction(
      LvlType,
      txos,
      heightLock.get.getPredicate,
      100L,
      sigAddress,
      heightAddress,
      1L
    )
} yield tx.toOption.get

val proveAndValidateResult = for {
  tx <- unprovenTransaction
  mainKey <- walletApi.loadAndExtractMainKey[IO]("password".getBytes, keyFile)
  credentialler = CredentiallerInterpreter.make[IO](walletApi, walletStateApi, mainKey.toOption.get)
  ctx = Context[IO](tx, 50, Map("header" -> Datum().withHeader(Datum.Header(Event.Header(50)))).lift)
  res <- credentialler.proveAndValidate(tx, ctx)
} yield res

val bifrostQuery = BifrostQueryAlgebra.make[IO](channelResource)

val broadcastTransaction = for {
  provenTx <- proveAndValidateResult
  txId <- bifrostQuery.broadcastTransaction(provenTx.toOption.get)
} yield txId

broadcastTransaction.unsafeRunSync()

// Allow some time to pass before querying the transaction
Thread.sleep(15000)

// optionally view your funds
val queryFunds = for {
  sigLock <- walletStateApi.getLock("self", "default", 1)
  sigAddress <- txBuilder.lockAddress(sigLock.get)
  txos <- genusQueryApi.queryUtxo(sigAddress)
} yield txos.map(_.transactionOutput.value.value).map(value => s"${value.typeIdentifier}: ${value.quantity.intValue}")

queryFunds.unsafeRunSync().foreach(println)
```

If all went well, you should see that you have 100 LVLs in encumbered by your `sigAddress` ready to be spent by you.

```bash
LvlType: 100
```

## Next Steps

Read our other tutorials to learn how to spend the funds you just transferred to your wallet.