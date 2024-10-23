package org.plasmalabs.sdk.display

import org.plasmalabs.sdk.utils.Encoding
import org.plasmalabs.consensus.models.BlockId

trait BlockDisplayOps {

  implicit val blockIdDisplay: DisplayOps[BlockId] = (blockId: BlockId) =>
    Encoding.encodeToBase58(blockId.value.toByteArray())
}
