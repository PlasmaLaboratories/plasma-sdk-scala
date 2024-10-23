package org.plasmalabs.sdk.servicekit

import cats.arrow.FunctionK
import cats.data.EitherT
import cats.effect.Async
import cats.implicits.{catsSyntaxApplicativeError, catsSyntaxEitherId, toBifunctorOps, toFlatMapOps, toFunctorOps}
import org.plasmalabs.sdk.models.transaction.IoTransaction
import org.plasmalabs.sdk.models.{Datum, Event, LockAddress, TransactionId}
import quivr.models.VerificationKey
import org.plasmalabs.sdk.Context
import org.plasmalabs.sdk.builders.TransactionBuilderApi
import org.plasmalabs.sdk.builders.TransactionBuilderApi.implicits.lockAddressOps
import org.plasmalabs.sdk.constants.NetworkConstants.{MAIN_LEDGER_ID, MAIN_NETWORK_ID}
import org.plasmalabs.sdk.dataApi._
import org.plasmalabs.sdk.servicekit._
import org.plasmalabs.sdk.syntax.{
  int128AsBigInt,
  valueToQuantitySyntaxOps,
  valueToTypeIdentifierSyntaxOps,
  ValueTypeIdentifier
}
import org.plasmalabs.sdk.utils.Encoding
import org.plasmalabs.sdk.wallet.CredentiallerInterpreter.InvalidTransaction
import org.plasmalabs.sdk.wallet.{Credentialler, CredentiallerInterpreter, WalletApi}
import org.plasmalabs.sdk.servicekit.EasyApi._

import java.nio.charset.StandardCharsets

class EasyApi[F[
  _
]: Async: WalletKeyApiAlgebra: WalletStateAlgebra: TemplateStorageAlgebra: FellowshipStorageAlgebra: TransactionBuilderApi: WalletApi: NodeQueryAlgebra: IndexerQueryAlgebra: Credentialler] {
  val walletKeyApiAlgebra: WalletKeyApiAlgebra[F] = implicitly[WalletKeyApiAlgebra[F]]
  val walletStateAlgebra: WalletStateAlgebra[F] = implicitly[WalletStateAlgebra[F]]
  val templateStorageAlgebra: TemplateStorageAlgebra[F] = implicitly[TemplateStorageAlgebra[F]]
  val fellowshipStorageAlgebra: FellowshipStorageAlgebra[F] = implicitly[FellowshipStorageAlgebra[F]]
  val transactionBuilderApi: TransactionBuilderApi[F] = implicitly[TransactionBuilderApi[F]]
  val walletApi: WalletApi[F] = implicitly[WalletApi[F]]
  val nodeQueryAlgebra: NodeQueryAlgebra[F] = implicitly[NodeQueryAlgebra[F]]
  val genusQueryAlgebra: IndexerQueryAlgebra[F] = implicitly[IndexerQueryAlgebra[F]]
  val credentialler: Credentialler[F] = implicitly[Credentialler[F]]

  def transferFunds(
    from:      WalletAccount,
    recipient: LockAddress,
    amount:    Long,
    valueType: ValueTypeIdentifier,
    fee:       Long
  ): F[TransactionId] =
    (for {
      curIdx <- EitherT(
        walletStateAlgebra
          .getCurrentIndicesForFunds(from.fellowship, from.template, None)
          .map(
            _.toRight(new RuntimeException(s"Unable to obtain Idx for (${from.fellowship}, ${from.template}) account"))
          )
      )
      inputLock <- EitherT(
        walletStateAlgebra
          .getLock(from.fellowship, from.template, curIdx.z)
          .map(
            _.toRight(new RuntimeException(s"Unable to get lock for (${from.fellowship}, ${from.template}) account"))
          )
      )
      inputAddr <- EitherT(transactionBuilderApi.lockAddress(inputLock).map(_.asRight[RuntimeException]))
      txos <- EitherT(
        genusQueryAlgebra
          .queryUtxo(inputAddr)
          .map(_.asRight[RuntimeException])
          .handleError(e => new RuntimeException("Unable to query UTXO", e).asLeft)
      )
      changeLock <- EitherT(
        walletStateAlgebra
          .getLock(from.fellowship, from.template, curIdx.z + 1)
          .map(
            _.toRight(
              new RuntimeException(s"Unable to get change lock for next (${from.fellowship}, ${from.template}) account")
            )
          )
      )
      changeAddr <- EitherT(transactionBuilderApi.lockAddress(changeLock).map(_.asRight[RuntimeException]))
      unproven <- EitherT(
        transactionBuilderApi
          .buildTransferAmountTransaction(valueType, txos, inputLock.getPredicate, amount, recipient, changeAddr, fee)
          .map(_.leftMap(err => new RuntimeException("Unable to build transaction", err)))
      )
      context <- EitherT(
        buildContext(unproven)
          .map(_.asRight[RuntimeException])
          .handleError(e => new RuntimeException("Unable to build context", e).asLeft)
      )
      proven <- EitherT(
        credentialler
          .proveAndValidate(unproven, context)
          .map(_.leftMap(errs => new RuntimeException("Transaction failed validation", InvalidTransaction(errs))))
      )
      txId <- EitherT(
        nodeQueryAlgebra
          .broadcastTransaction(proven)
          .map(_.asRight[RuntimeException])
          .handleError(e => new RuntimeException("Broadcast transaction failed", e).asLeft)
      )
      hasChange = proven.outputs.map(_.address).contains(changeAddr) // check if change address is in outputs
      res <-
        if (hasChange) for {
          // The vk in the cartesian wallet state will always refer to the derivation of the user's main key (which can partially be obtained via the Default account)
          parentVk <- EitherT(
            walletStateAlgebra
              .getEntityVks(DefaultAccount.fellowship, DefaultAccount.template)
              .map(
                _.flatMap(_.headOption.flatMap(vk => Encoding.decodeFromBase58(vk).toOption))
                  .toRight(new RuntimeException("Unable to get (self,default) VK"))
                  .map(VerificationKey.parseFrom)
              )
          )
          childVk <- EitherT(
            walletApi
              .deriveChildVerificationKey(parentVk, curIdx.z + 1)
              .map(_.asRight[RuntimeException])
              .handleError(e => new RuntimeException("Unable to derive child verification key", e).asLeft)
          )
          res <- EitherT(
            walletStateAlgebra
              .updateWalletState(
                Encoding.encodeToBase58(changeLock.getPredicate.toByteArray),
                changeAddr.toBase58(),
                Some("ExtendedEd25519"),
                Some(Encoding.encodeToBase58(childVk.toByteArray)),
                curIdx.copy(z = curIdx.z + 1)
              )
              .map(_ => txId.asRight[RuntimeException])
              .handleError(e => new RuntimeException("Unable to update wallet state", e).asLeft)
          )
        } yield res
        else EitherT.pure[F, RuntimeException](txId)
    } yield res).value map {
      case Left(err)   => throw UnableToTransferFunds(err)
      case Right(txId) => txId
    }

  def getAddressToReceiveFunds(account: WalletAccount): F[LockAddress] = (for {
    nextIdx <- EitherT(
      walletStateAlgebra
        .getCurrentIndicesForFunds(account.fellowship, account.template, None)
        .map(_.toRight(new RuntimeException(s"Invalid (${account.fellowship}, ${account.template}) account")))
    )
    nextLock <- EitherT(
      walletStateAlgebra
        .getLock(account.fellowship, account.template, nextIdx.z)
        .map(
          _.toRight(
            new RuntimeException(s"Unable to get lock for next (${account.fellowship}, ${account.template}) account")
          )
        )
    )
    nextAddr <- EitherT(transactionBuilderApi.lockAddress(nextLock).map(_.asRight[RuntimeException]))
  } yield nextAddr).value map {
    case Left(err)       => throw UnableToGetAddressForFunds(err)
    case Right(lockAddr) => lockAddr
  }

  def buildContext(tx: IoTransaction): F[Context[F]] = for {
    tipBlockHeader <- nodeQueryAlgebra
      .blockByDepth(1L)
      .map(_.get._2)
  } yield Context[F](
    tx,
    tipBlockHeader.slot,
    Map(
      "header" -> Datum().withHeader(
        Datum.Header(Event.Header(tipBlockHeader.height))
      )
    ).lift
  )

  def getBalance(account: WalletAccount): F[Map[ValueTypeIdentifier, Long]] =
    (for {
      curIdx <- EitherT(
        walletStateAlgebra
          .getCurrentIndicesForFunds(account.fellowship, account.template, None)
          .map(_.toRight(new RuntimeException(s"Invalid (${account.fellowship}, ${account.template}) account")))
      )
      lock <- EitherT(
        walletStateAlgebra
          .getLock(account.fellowship, account.template, curIdx.z)
          .map(
            _.toRight(
              new RuntimeException(s"Unable to get lock for (${account.fellowship}, ${account.template}) account")
            )
          )
      )
      lockAddr <- EitherT(transactionBuilderApi.lockAddress(lock).map(_.asRight[RuntimeException]))
      utxos <- EitherT(
        genusQueryAlgebra
          .queryUtxo(lockAddr)
          .map(_.asRight[RuntimeException])
          .handleError(e => new RuntimeException("Unable to query UTXO", e).asLeft)
      )
    } yield utxos
      .map(_.transactionOutput.value.value)
      .groupBy(_.typeIdentifier)
      .view
      .mapValues(_.map(_.quantity: BigInt).sum.toLong)).value map {
      case Left(err)  => throw UnableToGetBalance(err)
      case Right(res) => res.toMap
    }

}

object EasyApi {

  case class UnableToInitializeSdk(err: Throwable = null)
      extends RuntimeException(s"Unable to initialize SDK: ${err.getMessage}", err)

  case class UnableToTransferFunds(err: Throwable = null)
      extends RuntimeException(s"Issue transferring funds: ${err.getMessage}", err)

  case class UnableToGetBalance(err: Throwable = null)
      extends RuntimeException(s"Unable to get balance: ${err.getMessage}", err)

  case class UnableToGetAddressForFunds(err: Throwable = null)
      extends RuntimeException(s"Unable to get generate address: ${err.getMessage}", err)

  case class InitArgs(
    networkId:    Int = MAIN_NETWORK_ID,
    ledgerId:     Int = MAIN_LEDGER_ID,
    host:         String = "localhost",
    port:         Int = 9084,
    secure:       Boolean = false,
    dbFile:       String = "wallet.db",
    keyFile:      String = "keyFile.json",
    mnemonicFile: String = "mnemonic.txt", // only needed for wallet creation
    passphrase:   Option[String] = None // only needed for wallet creation
  )

  case class WalletAccount(fellowship: String, template: String)

  // Common (fellowship, template) pairs
  val DefaultAccount: WalletAccount = WalletAccount("self", "default")
  val GenesisAccount: WalletAccount = WalletAccount("nofellowship", "genesis")

  def initialize[F[_]: Async](
    password: String,
    args:     InitArgs = InitArgs()
  ): F[EasyApi[F]] = initialize(password.getBytes(StandardCharsets.UTF_8), args)

  def initialize[F[_]: Async](
    password: Array[Byte],
    args:     InitArgs
  ): F[EasyApi[F]] = {
    implicit val wka: WalletKeyApiAlgebra[F] = WalletKeyApi.make[F]()
    implicit val wa: WalletApi[F] = WalletApi.make[F](wka)
    val walletConn = WalletStateResource.walletResource(args.dbFile)
    implicit val tsa: TemplateStorageAlgebra[F] = TemplateStorageApi.make[F](walletConn)
    implicit val fsa: FellowshipStorageAlgebra[F] = FellowshipStorageApi.make[F](walletConn)
    val channelResource = RpcChannelResource.channelResource[F](args.host, args.port, args.secure)
    implicit val gq: IndexerQueryAlgebra[F] = IndexerQueryAlgebra.make[F](channelResource)
    implicit val bq: NodeQueryAlgebra[F] = NodeQueryAlgebra.make[F](channelResource)
    implicit val tba: TransactionBuilderApi[F] =
      TransactionBuilderApi.make[F](args.networkId, args.ledgerId)

    implicit val wsa: WalletStateAlgebra[F] = WalletStateApi.make[F](walletConn, wa)

    implicit val fTof: FunctionK[F, F] = FunctionK.id[F]
    for {
      wallet <- wa.loadWallet(args.keyFile)
      keyPairRes <- wallet match {
        case Left(_) =>
          (for {
            vault <- EitherT(
              wa
                .createAndSaveNewWallet[F](
                  password,
                  args.passphrase,
                  name = args.keyFile,
                  mnemonicName = args.mnemonicFile
                )
                .map(_.leftMap(new RuntimeException("Unable to create wallet", _)).map(_.mainKeyVaultStore))
            )
            mainKey <- EitherT(
              wa
                .extractMainKey(vault, password)
                .map(_.leftMap(new RuntimeException("Unable to extract main key", _)))
            )
            res <- EitherT(
              wsa
                .initWalletState(args.networkId, args.ledgerId, mainKey)
                .map(_ => mainKey.asRight[RuntimeException])
                .handleError(e => (new RuntimeException("Unable to initialize wallet state", e)).asLeft)
            )
          } yield res).value
        case Right(vs) =>
          (for {
            mainKey <- EitherT(
              wa.extractMainKey(vs, password).map(_.leftMap(new RuntimeException("Unable to extract main key", _)))
            )
            _ <- EitherT(
              wsa
                .validateWalletInitialization(args.networkId, args.ledgerId, mainKey)
                .map(_.leftMap(m => new RuntimeException(s"Wallet state invalid: ${m.mkString("[", ",", "]")}")))
            )
          } yield mainKey).value
      }
    } yield keyPairRes match {
      case Left(err) => throw UnableToInitializeSdk(err)
      case Right(mainKey) =>
        implicit val c: Credentialler[F] = CredentiallerInterpreter.make[F](wa, wsa, mainKey)
        new EasyApi[F]
    }
  }
}
