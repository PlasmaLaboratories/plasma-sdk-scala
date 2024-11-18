package org.plasmalabs.sdk.servicekit

import munit.CatsEffectSuite
import cats.effect.IO
import cats.effect.kernel.Resource
import org.plasmalabs.sdk.dataApi.WalletKeyApiAlgebra
import org.plasmalabs.sdk.wallet.WalletApi

import scala.concurrent.duration.Duration
import java.io.File
import java.nio.file.{Files, Path, Paths}
import org.plasmalabs.sdk.builders.TransactionBuilderApi
import org.plasmalabs.sdk.constants.NetworkConstants._
import org.plasmalabs.sdk.dataApi.WalletStateAlgebra
import org.plasmalabs.sdk.syntax.cryptoToPbKeyPair
import org.plasmalabs.crypto.generation.KeyInitializer.Instances.extendedEd25519Initializer
import org.plasmalabs.quivr.models

import java.sql.Connection
import org.plasmalabs.crypto.signing.{ExtendedEd25519, KeyPair}

trait BaseSpec extends CatsEffectSuite with WalletStateResource {
  override val munitIOTimeout: Duration = Duration(180, "s")

  val TEST_DIR = "./tmp"
  val testDir: File = Paths.get(TEST_DIR).toFile
  val DB_FILE = s"$TEST_DIR/wallet.db"
  val walletKeyApi: WalletKeyApiAlgebra[IO] = WalletKeyApi.make[IO]()
  val walletApi: WalletApi[IO] = WalletApi.make[IO](walletKeyApi)

  val transactionBuilderApi: TransactionBuilderApi[IO] =
    TransactionBuilderApi.make[IO](PRIVATE_NETWORK_ID, MAIN_LEDGER_ID)
  val dbConnection: Resource[IO, Connection] = walletResource(DB_FILE)
  val walletStateApi: WalletStateAlgebra[IO] = WalletStateApi.make[IO](dbConnection, walletApi)

  def mockMainKeyPair: models.KeyPair = {
    implicit val extendedEd25519Instance: ExtendedEd25519 = new ExtendedEd25519
    val sk = extendedEd25519Initializer.random()
    cryptoToPbKeyPair(KeyPair(sk, extendedEd25519Instance.getVerificationKey(sk)))
  }

  private def removeDir() =
    if (testDir.exists()) {
      Paths.get(TEST_DIR).toFile.listFiles().map(_.delete()).mkString("\n")
      Files.deleteIfExists(Paths.get(TEST_DIR))
    }

  val testDirectory: FunFixture[Path] = FunFixture[Path](
    setup = { _ =>
      removeDir()
      Files.createDirectory(Paths.get(TEST_DIR))
    },
    teardown = { _ => removeDir() }
  )
}
