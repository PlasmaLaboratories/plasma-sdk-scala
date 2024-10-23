package org.plasmalabs.sdk.dataApi

import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.effect.kernel.{Resource, Sync}
import org.plasmalabs.sdk.syntax.ioTransactionAsTransactionSyntaxOps
import org.plasmalabs.node.services.{NodeRpcGrpc, _}
import io.grpc.ManagedChannel

/**
 * Defines an interpreter for Node Query API.
 */
trait NodeQueryInterpreter {

  def interpretADT[A, F[_]: Sync](
    channelResource: Resource[F, ManagedChannel],
    computation:     NodeQueryAlgebra.NodeQueryADTMonad[A]
  ): F[A] = {
    type ChannelContextKlesli[A] =
      Kleisli[F, (NodeRpcGrpc.NodeRpcBlockingStub, RegtestRpcGrpc.RegtestRpcBlockingStub), A]
    val kleisliComputation = computation.foldMap[ChannelContextKlesli](
      new FunctionK[NodeQueryAlgebra.NodeQueryADT, ChannelContextKlesli] {

        override def apply[A](
          fa: NodeQueryAlgebra.NodeQueryADT[A]
        ): ChannelContextKlesli[A] = {
          import cats.implicits._
          fa match {
            case NodeQueryAlgebra.MakeBlocks(nbOfBlocks) =>
              Kleisli(blockingStubAndRegTestStub =>
                Sync[F]
                  .blocking(
                    blockingStubAndRegTestStub._2
                      .makeBlocks(
                        MakeBlocksReq(nbOfBlocks)
                      )
                  )
                  .map(_ => ())
                  .map(_.asInstanceOf[A])
              )
            case NodeQueryAlgebra.BlockByDepth(depth) =>
              Kleisli(blockingStubAndRegTestStub =>
                Sync[F]
                  .blocking(
                    blockingStubAndRegTestStub._1
                      .fetchBlockIdAtDepth(
                        FetchBlockIdAtDepthReq(depth)
                      )
                  )
                  .map(_.blockId.asInstanceOf[A])
              )
            case NodeQueryAlgebra.FetchBlockHeader(blockId) =>
              Kleisli(blockingStubAndRegTestStub =>
                Sync[F]
                  .blocking(
                    blockingStubAndRegTestStub._1
                      .fetchBlockHeader(
                        FetchBlockHeaderReq(blockId)
                      )
                  )
                  .map(_.header.asInstanceOf[A])
              )
            case NodeQueryAlgebra.FetchBlockBody(blockId) =>
              Kleisli(blockingStubAndRegTestStub =>
                Sync[F]
                  .blocking(
                    blockingStubAndRegTestStub._1
                      .fetchBlockBody(
                        FetchBlockBodyReq(blockId)
                      )
                  )
                  .map(_.body.asInstanceOf[A])
              )
            case NodeQueryAlgebra.FetchTransaction(txId) =>
              Kleisli(blockingStubAndRegTestStub =>
                Sync[F]
                  .blocking(
                    blockingStubAndRegTestStub._1
                      .fetchTransaction(
                        FetchTransactionReq(txId)
                      )
                  )
                  .map(_.transaction.asInstanceOf[A])
              )
            case NodeQueryAlgebra.BlockByHeight(height) =>
              Kleisli(blockingStubAndRegTestStub =>
                Sync[F]
                  .blocking(
                    blockingStubAndRegTestStub._1
                      .fetchBlockIdAtHeight(
                        FetchBlockIdAtHeightReq(height)
                      )
                  )
                  .map(_.blockId.asInstanceOf[A])
              )

            case NodeQueryAlgebra.SynchronizationTraversal() =>
              Kleisli(blockingStubAndRegTestStub =>
                Sync[F]
                  .blocking(
                    blockingStubAndRegTestStub._1
                      .synchronizationTraversal(SynchronizationTraversalReq())
                  )
                  .map(_.asInstanceOf[A])
              )
            case NodeQueryAlgebra.BroadcastTransaction(tx) =>
              Kleisli(blockingStubAndRegTestStub =>
                Sync[F]
                  .blocking(
                    blockingStubAndRegTestStub._1
                      .broadcastTransaction(
                        BroadcastTransactionReq(tx)
                      )
                  )
                  .map(_ => (tx.computeId).asInstanceOf[A])
              )
          }
        }
      }
    )
    (for {
      channel <- channelResource
    } yield channel).use { channel =>
      kleisliComputation.run(
        (NodeRpcGrpc.blockingStub(channel), RegtestRpcGrpc.blockingStub(channel))
      )
    }
  }

}
