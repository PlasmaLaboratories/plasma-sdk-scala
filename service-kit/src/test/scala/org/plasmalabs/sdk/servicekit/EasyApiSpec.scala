package org.plasmalabs.sdk.servicekit

import cats.Id
import cats.effect.IO
import org.plasmalabs.sdk.models._
import org.plasmalabs.sdk.models.box.{Challenge, Lock, Value}
import org.plasmalabs.sdk.models.transaction.{IoTransaction, UnspentTransactionOutput}
import org.plasmalabs.consensus.models._
import org.plasmalabs.indexer.services.{Txo, TxoState}
import org.plasmalabs.node.models.BlockBody
import org.plasmalabs.node.services.SynchronizationTraversalRes
import com.google.protobuf.ByteString
import org.plasmalabs.sdk.models.Indices
import munit.CatsEffectSuite
import org.plasmalabs.sdk.builders.TransactionBuilderApi
import org.plasmalabs.sdk.common.ContainsEvidence.Ops
import org.plasmalabs.sdk.common.ContainsImmutable.instances.lockImmutable
import org.plasmalabs.sdk.constants.NetworkConstants.{MAIN_LEDGER_ID, MAIN_NETWORK_ID}
import org.plasmalabs.sdk.dataApi._
import org.plasmalabs.sdk.syntax.{bigIntAsInt128, LvlType}
import org.plasmalabs.sdk.wallet.{Credentialler, WalletApi}
import org.plasmalabs.sdk.servicekit.EasyApi._
import org.plasmalabs.quivr.api.Proposer

class EasyApiSpec extends CatsEffectSuite with BaseSpec {

  private def getFileName(name: String) = s"$TEST_DIR/$name"

  private val testArgs = EasyApi.InitArgs(
    dbFile = getFileName("easyApi.db"),
    keyFile = getFileName("easyApiKey.json"),
    mnemonicFile = getFileName("easyApiMnemonic.txt")
  )

  testDirectory.test("Initialize Easy API > Happy path") { _ =>
    assertIO(
      for {
        apiFirst  <- EasyApi.initialize[IO]("password", testArgs)
        idxFirst  <- apiFirst.walletStateAlgebra.getCurrentIndicesForFunds("self", "default", None)
        _         <- apiFirst.walletStateAlgebra.updateWalletState("test", "test", None, None, Indices(1, 1, 2))
        apiSecond <- EasyApi.initialize[IO]("password", testArgs)
        idxSecond <- apiSecond.walletStateAlgebra.getCurrentIndicesForFunds("self", "default", None)
      } yield idxFirst.contains(Indices(1, 1, 1)) && idxSecond.contains(Indices(1, 1, 2)),
      true
    )
  }

  testDirectory.test("Initialize Easy API > invalid password") { _ =>
    interceptMessageIO[UnableToInitializeSdk]("Unable to initialize SDK: Unable to extract main key")(for {
      createWallet <- EasyApi.initialize[IO]("password", testArgs)
      accessWallet <- EasyApi.initialize[IO]("wrong-password", testArgs)
    } yield true)
  }

  testDirectory.test("Initialize Easy API > keyfile exists but mnemonic file does not") { _ =>
    assertIO(
      for {
        createWallet <- EasyApi.initialize[IO]("password", testArgs)
        accessWallet <- EasyApi.initialize[IO]("password", testArgs.copy(mnemonicFile = getFileName("nonExistent.txt")))
      } yield true,
      true
    ) // no exception since mnemonic file is only used for wallet creation
  }

  testDirectory.test("Initialize Easy API > mnemonic file exists but keyfile does not") { _ =>
    interceptMessageIO[UnableToInitializeSdk]("Unable to initialize SDK: Unable to create wallet")(for {
      createWallet <- EasyApi.initialize[IO]("password", testArgs)
      accessWallet <- EasyApi.initialize[IO]("password", testArgs.copy(keyFile = getFileName("nonExistent.json")))
    } yield true)
  }

  testDirectory.test("Initialize Easy API > keyfile exists but state does not") { _ =>
    interceptMessageIO[UnableToInitializeSdk](
      "Unable to initialize SDK: Wallet state invalid: [State not initialized]"
    )(for {
      createWallet <- EasyApi.initialize[IO]("password", testArgs)
      accessWallet <- EasyApi.initialize[IO]("password", testArgs.copy(dbFile = getFileName("nonExistent.db")))
    } yield true)
  }

  testDirectory.test("Initialize Easy API > state exists but keyfile does not") { _ =>
    interceptMessageIO[UnableToInitializeSdk]("Unable to initialize SDK: Unable to initialize wallet state")(for {
      createWallet <- EasyApi.initialize[IO]("password", testArgs)
      accessWallet <- EasyApi.initialize[IO](
        "password",
        testArgs.copy(keyFile = getFileName("nonExistent.json"), mnemonicFile = getFileName("nonExistent.txt"))
      )
    } yield true)
  }

  testDirectory.test("Initialize Easy API > keyfile and state exists but mismatched") { _ =>
    interceptMessageIO[UnableToInitializeSdk]("Unable to initialize SDK: Wallet state invalid: [mainKey mismatch]")(
      for {
        createWallet1 <- EasyApi.initialize[IO]("password", testArgs)
        createWallet2 <- EasyApi.initialize[IO](
          "password",
          testArgs.copy(
            keyFile = getFileName("second.json"),
            mnemonicFile = getFileName("second.txt"),
            dbFile = getFileName("second.db")
          )
        )
        accessWallet <- EasyApi.initialize[IO]("password", testArgs.copy(dbFile = getFileName("second.db")))
      } yield true
    )
  }

  private val HeightLockAddr = LockAddress(
    network = MAIN_NETWORK_ID,
    ledger = MAIN_LEDGER_ID,
    id = LockId(
      Lock()
        .withPredicate(
          Lock.Predicate(
            List(Challenge().withRevealed(Proposer.heightProposer[Id].propose("header", 1, Long.MaxValue))),
            1
          )
        )
        .sizedEvidence
        .digest
        .value
    )
  )

  private def mockEasyApi(original: EasyApi[IO]): EasyApi[IO] = {
    implicit val walletKeyApiAlgebra: WalletKeyApiAlgebra[IO] = original.walletKeyApiAlgebra
    implicit val walletStateAlgebra: WalletStateAlgebra[IO] = original.walletStateAlgebra
    implicit val templateStorageAlgebra: TemplateStorageAlgebra[IO] = original.templateStorageAlgebra
    implicit val fellowshipStorageAlgebra: FellowshipStorageAlgebra[IO] = original.fellowshipStorageAlgebra
    implicit val walletApi: WalletApi[IO] = original.walletApi
    implicit val transactionBuilderApi: TransactionBuilderApi[IO] = original.transactionBuilderApi
    implicit val credentialler: Credentialler[IO] = original.credentialler
    implicit val genusQueryAlgebra: IndexerQueryAlgebra[IO] = (_: LockAddress, _: TxoState) =>
      IO.pure(
        Seq(
          Txo(
            transactionOutput =
              UnspentTransactionOutput(HeightLockAddr, Value.defaultInstance.withLvl(Value.LVL(BigInt(500)))),
            outputAddress = TransactionOutputAddress(
              network = MAIN_NETWORK_ID,
              ledger = MAIN_LEDGER_ID,
              id = TransactionId(ByteString.copyFrom(Array.fill(32)(0.toByte)))
            )
          )
        )
      )
    implicit val nodeQueryAlgebra: NodeQueryAlgebra[IO] = new NodeQueryAlgebra[IO] {

      override def blockByDepth(depth: Long): IO[Option[(BlockId, BlockHeader, BlockBody, Seq[IoTransaction])]] =
        IO.pure(
          Some(
            (
              BlockId(ByteString.copyFrom(Array.fill(32)(0.toByte))),
              BlockHeader(
                parentHeaderId = BlockId(ByteString.copyFrom(Array.fill(32)(0.toByte))),
                txRoot = ByteString.copyFrom(Array.fill(32)(0.toByte)),
                bloomFilter = ByteString.copyFrom(Array.fill(256)(0.toByte)),
                height = 50,
                slot = 100,
                eligibilityCertificate = EligibilityCertificate(
                  ByteString.copyFrom(Array.fill(80)(0.toByte)),
                  ByteString.copyFrom(Array.fill(32)(0.toByte)),
                  ByteString.copyFrom(Array.fill(32)(0.toByte)),
                  ByteString.copyFrom(Array.fill(32)(0.toByte))
                ),
                operationalCertificate = OperationalCertificate(
                  VerificationKeyKesProduct(ByteString.copyFrom(Array.fill(32)(0.toByte))),
                  SignatureKesProduct(
                    SignatureKesSum(
                      ByteString.copyFrom(Array.fill(32)(0.toByte)),
                      ByteString.copyFrom(Array.fill(64)(0.toByte))
                    ),
                    SignatureKesSum(
                      ByteString.copyFrom(Array.fill(32)(0.toByte)),
                      ByteString.copyFrom(Array.fill(64)(0.toByte))
                    ),
                    ByteString.copyFrom(Array.fill(32)(0.toByte))
                  ),
                  ByteString.copyFrom(Array.fill(32)(0.toByte)),
                  ByteString.copyFrom(Array.fill(64)(0.toByte))
                ),
                address = StakingAddress(ByteString.copyFrom(Array.fill(32)(0.toByte))),
                version = ProtocolVersion.defaultInstance
              ),
              BlockBody(Seq.empty, None),
              Seq.empty
            )
          )
        )

      override def broadcastTransaction(tx: IoTransaction): IO[TransactionId] =
        IO.pure(TransactionId(ByteString.copyFrom(Array.fill(32)(0.toByte))))

      override def blockByHeight(height: Long): IO[Option[(BlockId, BlockHeader, BlockBody, Seq[IoTransaction])]] = ???
      override def blockById(blockId: BlockId): IO[Option[(BlockId, BlockHeader, BlockBody, Seq[IoTransaction])]] = ???
      override def fetchTransaction(txId: TransactionId): IO[Option[IoTransaction]] = ???
      override def synchronizationTraversal(): IO[Iterator[SynchronizationTraversalRes]] = ???
      override def makeBlocks(nbOfBlocks: Int): IO[Unit] = ???
    }
    new EasyApi[IO]
  }

  testDirectory.test("Get Balance > Happy Path") { _ =>
    assertIO(
      obtained = for {
        initialize <- EasyApi.initialize[IO]("password", testArgs)
        mockApi = mockEasyApi(initialize)
        balance <- mockApi.getBalance(GenesisAccount)
      } yield balance == Map(LvlType -> 500),
      true
    )
  }

  testDirectory.test("Get Balance > An exception occurred") { _ =>
    interceptIO[UnableToGetBalance](for {
      initialize <- EasyApi.initialize[IO]("password", testArgs)
      mockApi = mockEasyApi(initialize)
      balance <- mockApi.getBalance(WalletAccount("does-not", "exist"))
    } yield true)
  }

  testDirectory.test("Build Context > Happy Path") { _ =>
    assertIO(
      obtained = for {
        initialize <- EasyApi.initialize[IO]("password", testArgs)
        mockApi = mockEasyApi(initialize)
        ctx <- mockApi.buildContext(IoTransaction.defaultInstance)
      } yield ctx.curTick == 100 && ctx.datums("header").flatMap(_.value.header.map(_.event.height)).contains(50),
      true
    )
  }

  testDirectory.test("Get Address > Happy Path") { _ =>
    assertIO(
      obtained = for {
        initialize <- EasyApi.initialize[IO]("password", testArgs)
        mockApi = mockEasyApi(initialize)
        addr <- mockApi.getAddressToReceiveFunds(GenesisAccount)
      } yield addr == HeightLockAddr,
      true
    )
  }

  testDirectory.test("Get Address > An exception occurred") { _ =>
    interceptIO[UnableToGetAddressForFunds](for {
      initialize <- EasyApi.initialize[IO]("password", testArgs)
      mockApi = mockEasyApi(initialize)
      addr <- mockApi.getAddressToReceiveFunds(WalletAccount("does-not", "exist"))
    } yield true)
  }

  testDirectory.test("Transfer 100Lvls > Happy Path") { _ =>
    assertIO(
      obtained = for {
        initialize <- EasyApi.initialize[IO]("password", testArgs)
        mockApi = mockEasyApi(initialize)
        addr <- mockApi.getAddressToReceiveFunds(DefaultAccount)
        txId <- mockApi.transferFunds(GenesisAccount, addr, 100L, LvlType, 1L)
      } yield txId == TransactionId(ByteString.copyFrom(Array.fill(32)(0.toByte))),
      true
    )
  }

  testDirectory.test("Transfer 100Lvls > Invalid From Account") { _ =>
    interceptMessageIO[UnableToTransferFunds](
      "Issue transferring funds: Unable to get lock for (does-not, exist) account"
    )(for {
      initialize <- EasyApi.initialize[IO]("password", testArgs)
      mockApi = mockEasyApi(initialize)
      addr <- mockApi.getAddressToReceiveFunds(DefaultAccount)
      txId <- mockApi.transferFunds(WalletAccount("does-not", "exist"), addr, 100L, LvlType, 1L)
    } yield true)
  }

  testDirectory.test("Transfer 700Lvls > Not enough funds") { _ =>
    interceptMessageIO[UnableToTransferFunds]("Issue transferring funds: Unable to build transaction")(for {
      initialize <- EasyApi.initialize[IO]("password", testArgs)
      mockApi = mockEasyApi(initialize)
      addr <- mockApi.getAddressToReceiveFunds(DefaultAccount)
      txId <- mockApi.transferFunds(GenesisAccount, addr, 700L, LvlType, 1L)
    } yield true)
  }

  testDirectory.test("Transfer 100Lvls > Invalid Transaction") { _ =>
    interceptMessageIO[UnableToTransferFunds]("Issue transferring funds: Transaction failed validation")(for {
      initialize <- EasyApi.initialize[IO]("password", testArgs)
      mockApi = mockEasyApi(initialize)
      addr <- mockApi.getAddressToReceiveFunds(DefaultAccount)
      txId <- mockApi.transferFunds(GenesisAccount, addr.withNetwork(0), 100L, LvlType, 1L)
    } yield true)
  }
}
