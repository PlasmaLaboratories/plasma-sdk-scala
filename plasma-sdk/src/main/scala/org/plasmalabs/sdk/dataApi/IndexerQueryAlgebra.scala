package org.plasmalabs.sdk.dataApi

import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import org.plasmalabs.sdk.models.LockAddress
import org.plasmalabs.indexer.services.QueryByLockAddressRequest
import org.plasmalabs.indexer.services.TransactionServiceGrpc
import org.plasmalabs.indexer.services.Txo
import org.plasmalabs.indexer.services.TxoState
import io.grpc.ManagedChannel

/**
 * Defines a Indexer Query API for interacting with a Indexer node.
 */
trait IndexerQueryAlgebra[F[_]] {

  /**
   * Query and retrieve a set of UTXOs encumbered by the given LockAddress.
   * @param fromAddress The lock address to query the unspent UTXOs by.
   * @param txoState The state of the UTXOs to query. By default, only unspent UTXOs are returned.
   * @return A sequence of UTXOs.
   */
  def queryUtxo(fromAddress: LockAddress, txoState: TxoState = TxoState.UNSPENT): F[Seq[Txo]]
}

object IndexerQueryAlgebra {

  def make[F[_]: Sync](channelResource: Resource[F, ManagedChannel]): IndexerQueryAlgebra[F] =
    new IndexerQueryAlgebra[F] {

      def queryUtxo(fromAddress: LockAddress, txoState: TxoState = TxoState.UNSPENT): F[Seq[Txo]] = {
        import cats.implicits._
        (for {
          channel <- channelResource
        } yield channel).use { channel =>
          for {
            blockingStub <- Sync[F].point(
              TransactionServiceGrpc.blockingStub(channel)
            )
            response <- Sync[F].blocking(
              blockingStub
                .getTxosByLockAddress(
                  QueryByLockAddressRequest(fromAddress, None, txoState)
                )
            )
          } yield response.txos
        }
      }
    }
}
