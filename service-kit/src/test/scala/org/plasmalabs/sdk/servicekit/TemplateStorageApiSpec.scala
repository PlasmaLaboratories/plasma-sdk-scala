package org.plasmalabs.sdk.servicekit

import cats.effect.IO
import org.plasmalabs.sdk.builders.locks.{LockTemplate, PropositionTemplate}
import org.plasmalabs.sdk.codecs.LockTemplateCodecs.encodeLockTemplate
import org.plasmalabs.sdk.dataApi.{TemplateStorageAlgebra, WalletTemplate}
import munit.CatsEffectSuite
import org.plasmalabs.sdk.constants.NetworkConstants
import org.plasmalabs.sdk.servicekit.TemplateStorageApi

class TemplateStorageApiSpec extends CatsEffectSuite with BaseSpec {

  val contractApi: TemplateStorageAlgebra[IO] = TemplateStorageApi.make[IO](dbConnection)

  testDirectory.test("addTemplate then findTemplates") { _ =>
    val lockTemplate: LockTemplate[IO] =
      LockTemplate.PredicateTemplate[IO](List(PropositionTemplate.HeightTemplate[IO]("chain", 0, 100)), 1)
    val lockTemplateStr = encodeLockTemplate(lockTemplate).noSpaces
    val contract = WalletTemplate(3, "testTemplate", lockTemplateStr)
    assertIO(
      for {
        init <- walletStateApi
          .initWalletState(NetworkConstants.PRIVATE_NETWORK_ID, NetworkConstants.MAIN_NETWORK_ID, mockMainKeyPair)
        _         <- contractApi.addTemplate(contract)
        templates <- contractApi.findTemplates()
      } yield templates.length == 3 && templates.last == contract,
      true
    )
  }
}
