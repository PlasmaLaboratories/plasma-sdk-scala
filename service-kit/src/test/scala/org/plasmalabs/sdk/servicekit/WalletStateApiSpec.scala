package org.plasmalabs.sdk.servicekit

import cats.effect.IO
import org.plasmalabs.sdk.builders.TransactionBuilderApi.implicits.lockAddressOps
import org.plasmalabs.sdk.builders.locks.{LockTemplate, PropositionTemplate}
import org.plasmalabs.sdk.common.ContainsEvidence.Ops
import org.plasmalabs.sdk.common.ContainsImmutable.instances._
import org.plasmalabs.sdk.constants.NetworkConstants
import org.plasmalabs.sdk.models.{Indices, LockAddress, LockId}
import org.plasmalabs.sdk.models.box.Lock
import org.plasmalabs.sdk.utils.Encoding
import com.google.protobuf.ByteString
import munit.CatsEffectSuite
import org.plasmalabs.quivr.models.{Digest, Preimage, Proposition}

class WalletStateApiSpec extends CatsEffectSuite with BaseSpec {

  testDirectory.test("initWalletState") { _ =>
    assertIO(
      for {
        _ <- walletStateApi.initWalletState(
          NetworkConstants.PRIVATE_NETWORK_ID,
          NetworkConstants.MAIN_NETWORK_ID,
          mockMainKeyPair
        )
        fellowshipCount <- dbConnection.use { conn =>
          for {
            stmt <- IO.delay(conn.createStatement())
            rs <- IO.blocking(
              stmt.executeQuery("SELECT COUNT(*) as res FROM fellowships WHERE fellowship IN ('nofellowship', 'self')")
            )
            count <- IO.delay(rs.getInt("res"))
          } yield count
        }
        contractCount <- dbConnection.use { conn =>
          for {
            stmt <- IO.delay(conn.createStatement())
            rs <- IO.blocking(
              stmt.executeQuery("SELECT COUNT(*) as res FROM templates WHERE template IN ('default', 'genesis')")
            )
            count <- IO.delay(rs.getInt("res"))
          } yield count
        }
        vkCount <- dbConnection.use { conn =>
          for {
            stmt  <- IO.delay(conn.createStatement())
            rs    <- IO.blocking(stmt.executeQuery("SELECT COUNT(*) as res FROM verification_keys"))
            count <- IO.delay(rs.getInt("res"))
          } yield count
        }
        cartesianCount <- dbConnection.use { conn =>
          for {
            stmt  <- IO.delay(conn.createStatement())
            rs    <- IO.blocking(stmt.executeQuery("SELECT COUNT(*) as res FROM cartesian"))
            count <- IO.delay(rs.getInt("res"))
          } yield count
        }
      } yield fellowshipCount == 2 && contractCount == 2 && vkCount == 2 && cartesianCount == 2,
      true
    )
  }

  testDirectory.test("updateWalletState") { _ =>
    val testValue = "testValue"
    assertIO(
      for {
        _ <- walletStateApi.initWalletState(
          NetworkConstants.PRIVATE_NETWORK_ID,
          NetworkConstants.MAIN_NETWORK_ID,
          mockMainKeyPair
        )
        _ <- walletStateApi.updateWalletState(
          testValue,
          testValue,
          Some(testValue),
          Some(testValue),
          Indices(9, 9, 9)
        )
        count <- dbConnection.use { conn =>
          for {
            stmt <- IO.delay(conn.createStatement())
            rs <- IO.blocking(
              stmt.executeQuery("SELECT COUNT(*) as res FROM cartesian")
            )
            count <- IO.delay(rs.getInt("res"))
          } yield count
        }
        rowValid <- dbConnection.use { conn =>
          for {
            stmt <- IO.delay(conn.createStatement())
            rs <- IO.blocking(
              stmt.executeQuery(
                "SELECT * FROM cartesian WHERE x_fellowship = 9 AND y_template = 9 AND z_interaction = 9"
              )
            )
            predicate <- IO.delay(rs.getString("lock_predicate"))
            address   <- IO.delay(rs.getString("address"))
            routine   <- IO.delay(rs.getString("routine"))
            vk        <- IO.delay(rs.getString("vk"))
          } yield predicate.equals(testValue) && address.equals(testValue) && routine.equals(testValue) && vk.equals(
            testValue
          )
        }
      } yield count == 3 && rowValid,
      true
    )
  }

  testDirectory.test("getIndicesBySignature") { _ =>
    val testValue = "testValue"
    val idx = Indices(9, 9, 9)
    val proposition = Proposition.DigitalSignature(testValue, mockMainKeyPair.vk)
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        _ <- walletStateApi.updateWalletState(
          testValue,
          testValue,
          Some(testValue),
          Some(Encoding.encodeToBase58(proposition.verificationKey.toByteArray)),
          idx
        )
        indices <- walletStateApi.getIndicesBySignature(proposition)
      } yield indices.isDefined && indices.get == idx,
      true
    )
  }

  testDirectory.test("getLockByIndex") { _ =>
    val testValue = "testValue"
    val idx = Indices(9, 9, 9)
    val predicate = Lock.Predicate(Seq(), 1)
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        _ <- walletStateApi.updateWalletState(
          Encoding.encodeToBase58Check(predicate.toByteArray),
          testValue,
          None,
          None,
          idx
        )
        lock <- walletStateApi.getLockByIndex(idx)
      } yield lock.isDefined && lock.get == predicate,
      true
    )
  }

  testDirectory.test("getLockByAddress") { _ =>
    val predicate = Lock.Predicate(Seq(), 1)
    val lockAddress = LockAddress(
      NetworkConstants.PRIVATE_NETWORK_ID,
      NetworkConstants.MAIN_LEDGER_ID,
      LockId(Lock().withPredicate(predicate).sizedEvidence.digest.value)
    )
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        _ <- walletStateApi.updateWalletState(
          Encoding.encodeToBase58Check(predicate.toByteArray),
          lockAddress.toBase58(),
          None,
          None,
          Indices(9, 9, 9)
        )
        lock <- walletStateApi.getLockByAddress(lockAddress.toBase58())
      } yield lock.isDefined && lock.get == predicate,
      true
    )
  }

  testDirectory.test("getLockByAddress > LockAddress not known in Wallet State") { _ =>
    val predicate = Lock.Predicate(Seq(), 1)
    val lockAddress = LockAddress(
      NetworkConstants.PRIVATE_NETWORK_ID,
      NetworkConstants.MAIN_LEDGER_ID,
      LockId(Lock().withPredicate(predicate).sizedEvidence.digest.value)
    )
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        lock <- walletStateApi.getLockByAddress(lockAddress.toBase58())
      } yield lock.isEmpty,
      true
    )
  }

  testDirectory.test("getInteractionList successful simple") { _ =>
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        someList <- walletStateApi.getInteractionList("self", "default")
      } yield someList.get.size,
      1
    )
  }

  testDirectory.test("getInteractionList successful more than one result") { _ =>
    val predicate = Lock.Predicate(Seq(), 1)
    val lockAddress = LockAddress(
      NetworkConstants.PRIVATE_NETWORK_ID,
      NetworkConstants.MAIN_LEDGER_ID,
      LockId(Lock().withPredicate(predicate).sizedEvidence.digest.value)
    )

    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        _ <- walletStateApi.updateWalletState(
          Encoding.encodeToBase58Check(predicate.toByteArray),
          lockAddress.toBase58(),
          None,
          None,
          Indices(1, 1, 2)
        )
        _ <- walletStateApi.updateWalletState(
          Encoding.encodeToBase58Check(predicate.toByteArray),
          lockAddress.toBase58(),
          None,
          None,
          Indices(1, 1, 3)
        )
        someList <- walletStateApi.getInteractionList("self", "default")
      } yield someList.get.size,
      3
    )
  }

  testDirectory.test("getInteractionList failure") { _ =>
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        someList <- walletStateApi.getInteractionList("self", "default1")
      } yield someList.isEmpty,
      true
    )
  }

  testDirectory.test("getNextIndicesForFunds") { _ =>
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        idx <- walletStateApi.getNextIndicesForFunds("self", "default")
      } yield idx.isDefined && idx.get == Indices(1, 1, 2),
      true
    )
  }

  testDirectory.test("validateCurrentIndicesForFunds") { _ =>
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        idx <- walletStateApi.validateCurrentIndicesForFunds("self", "default", None)
      } yield idx.isValid && idx.toOption.get == Indices(1, 1, 1),
      true
    )
  }

  testDirectory.test("getAddress") { _ =>
    val testValue = "testValue"
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        _ <- walletStateApi.updateWalletState(
          testValue,
          testValue,
          Some(testValue),
          Some(testValue),
          Indices(1, 1, 2)
        )
        addr <- walletStateApi.getAddress("self", "default", None)
      } yield addr.isDefined && addr.get.equals(testValue),
      true
    )
  }

  testDirectory.test("getCurrentIndicesForFunds") { _ =>
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        idx <- walletStateApi.getCurrentIndicesForFunds("self", "default", None)
      } yield idx.isDefined && idx.get == Indices(1, 1, 1),
      true
    )
  }

  testDirectory.test("getCurrentAddress") { _ =>
    val testValue = "testValue"
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        _ <- walletStateApi.updateWalletState(
          testValue,
          testValue,
          Some(testValue),
          Some(testValue),
          Indices(1, 1, 2)
        )
        addr <- walletStateApi.getCurrentAddress
      } yield addr.equals(testValue),
      true
    )
  }

  testDirectory.test("getPreimage") { _ =>
    val proposition = Proposition.Digest("testValue", Digest(ByteString.copyFrom(Array.fill(32)(0: Byte))))
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        preimage <- walletStateApi.getPreimage(proposition)
      } yield preimage.isEmpty,
      true
    )
  }

  testDirectory.test("addPreimage") { _ =>
    val proposition = Proposition.Digest("testValue", Digest(ByteString.copyFrom(Array.fill(32)(0: Byte))))
    val secret = Preimage(ByteString.copyFrom("input".getBytes), ByteString.copyFrom("salt".getBytes))
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        preimageBefore <- walletStateApi.getPreimage(proposition)
        _              <- walletStateApi.addPreimage(secret, proposition)
        preimageAfter  <- walletStateApi.getPreimage(proposition)
      } yield preimageBefore.isEmpty && preimageAfter.isDefined && preimageAfter.get == secret,
      true
    )
  }

  testDirectory.test("addEntityVks then getEntityVks") { _ =>
    val testValues = List("testValue1", "testValue2")
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        _   <- walletStateApi.addEntityVks("test", "default", testValues)
        vks <- walletStateApi.getEntityVks("test", "default")
      } yield vks.isDefined && vks.get == testValues,
      true
    )
  }

  testDirectory.test("addNewLockTemplate then getLockTemplate") { _ =>
    val lockTemplate: LockTemplate[IO] =
      LockTemplate.PredicateTemplate[IO](List(PropositionTemplate.HeightTemplate[IO]("chain", 0, 100)), 1)
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        _        <- walletStateApi.addNewLockTemplate("height", lockTemplate)
        template <- walletStateApi.getLockTemplate("height")
      } yield template.isDefined && template.get == lockTemplate,
      true
    )
  }

  testDirectory.test("getLock") { _ =>
    val lockTemplate: LockTemplate[IO] =
      LockTemplate.PredicateTemplate[IO](List(PropositionTemplate.SignatureTemplate[IO]("routine", 0)), 1)
    val entityVks = List(mockMainKeyPair.vk)
    assertIO(
      for {
        _ <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        _ <- walletStateApi.addNewLockTemplate("test", lockTemplate)
        _ <- walletStateApi.addEntityVks("self", "test", entityVks.map(vk => Encoding.encodeToBase58(vk.toByteArray)))
        lock <- walletStateApi.getLock("self", "test", 2)
      } yield lock.isDefined && lock.get.getPredicate.challenges.head.getRevealed.value
        .isInstanceOf[Proposition.Value.DigitalSignature],
      true
    )
  }

}
