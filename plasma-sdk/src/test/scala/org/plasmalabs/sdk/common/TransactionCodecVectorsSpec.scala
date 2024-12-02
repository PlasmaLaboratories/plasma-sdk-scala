package org.plasmalabs.sdk.common

import com.google.protobuf.ByteString
import org.plasmalabs.quivr.models.{Int128, Proposition, SmallData}
import org.plasmalabs.sdk.common.ContainsSignable.ContainsSignableTOps
import org.plasmalabs.sdk.common.ContainsSignable.instances.ioTransactionSignable
import org.plasmalabs.sdk.models.box.{Attestation, Challenge, Lock, Value}
import org.plasmalabs.sdk.models.{Datum, Event, LockAddress, LockId, TransactionId, TransactionOutputAddress}
import org.plasmalabs.sdk.models.transaction.{IoTransaction, Schedule, SpentTransactionOutput, UnspentTransactionOutput}
import org.plasmalabs.sdk.syntax.ioTransactionAsTransactionSyntaxOps
import org.plasmalabs.sdk.utils.Encoding

class TransactionCodecVectorsSpec extends munit.FunSuite {

  import TransactionCodecVectorsSpec._

  vectors.zipWithIndex.foreach { case (vector, index) =>
    test(s"Vector $index should result in correct signable+id") {
      val txBytes = Encoding.decodeFromHex(vector.txHex).toOption.get
      val tx = IoTransaction.parseFrom(txBytes)
      val signable = tx.signable.value
      assertEquals(Encoding.encodeToHex(signable.toByteArray), vector.txSignableHex)
      val id = tx.computeId
      assertEquals(Encoding.encodeToBase58(id.value.toByteArray), vector.txId)
    }

  }
}

object TransactionCodecVectorsSpec {

  /**
   * NOTE: These test vectors are intended to be consistent and implemented across all SDK target languages.
   * Any updates to these vectors should also be included in the other SDK repositories.
   */
  val vectors: List[TestVector] = List(
    TestVector(
      "1a060a040a001200",
      "0000",
      "BhHbw2zXrJGgRW9YpKQV4c6sXfSwChXeYrRjW1aCQqRF"
    ),
    TestVector(
      "0a4f0a2b08d1041016180622220a202af1498060d30c7fa337ea54bd03905ff871c51bb658e14213992cab07825bd812150a130a110a0f0a0d220b0a03666f6f10b20318ff041a090a070a050a03325b7512360a2908d10410051a220a20ef52a274cb19813f68b826c34fe60ba7348f61d40fb279fb56df459b1ebd5ded12090a070a050a03214d7a1a170a150a0c08882710a8461885ccfba40112050a0336f42c",
      "0002511606696f5f7472616e73616374696f6e5f33322af1498060d30c7fa337ea54bd03905ff871c51bb658e14213992cab07825bd800006865696768745f72616e6765666f6f01b2027f325b7500025105626f785f6c6f636b5f3332ef52a274cb19813f68b826c34fe60ba7348f61d40fb279fb56df459b1ebd5ded214d7a1388232836f42c",
      "HroUqAw2X9eJPwgzsKJxMLAoJbCx27aVGsdVJFZNFMJH"
    )
  )

  case class TestVector(txHex: String, txSignableHex: String, txId: String)
}

/**
 * This is the utility which was used to generate the test vectors defined above. Any changes made should be reflected
 * across all target languages.
 */
private object TransactionCodecVectorsReference extends scala.App {
  val vector0 = IoTransaction(inputs = Nil, outputs = Nil, datum = Datum.IoTransaction.defaultInstance)
  println("vector0")
  println(Encoding.encodeToHex(vector0.toByteArray))
  println(Encoding.encodeToHex(ioTransactionSignable.signableBytes(vector0).value.toByteArray))
  println(Encoding.encodeToBase58(vector0.computeId.value.toByteArray))

  val bs32_0 = ByteString.copyFrom(
    Array[Byte](
      42, -15, 73, -128, 96, -45, 12, 127, -93, 55, -22, 84, -67, 3, -112, 95, -8, 113, -59, 27, -74, 88, -31, 66, 19,
      -103, 44, -85, 7, -126, 91, -40
    )
  )

  val bs32_1 = ByteString.copyFrom(
    Array[Byte](
      -17, 82, -94, 116, -53, 25, -127, 63, 104, -72, 38, -61, 79, -26, 11, -89, 52, -113, 97, -44, 15, -78, 121, -5,
      86, -33, 69, -101, 30, -67, 93, -19
    )
  )

  val vector1 = IoTransaction(
    inputs = List(
      SpentTransactionOutput(
        TransactionOutputAddress(network = 593, ledger = 22, index = 6, id = TransactionId(bs32_0)),
        attestation = Attestation(
          Attestation.Value.Predicate(
            Attestation.Predicate(
              Lock.Predicate(
                List(
                  Challenge(
                    Challenge.Proposition.Revealed(
                      Proposition(Proposition.Value.HeightRange(Proposition.HeightRange("foo", 434, 639)))
                    )
                  )
                )
              )
            )
          )
        ),
        value = Value(Value.Value.Lvl(Value.LVL(Int128(ByteString.copyFrom(Array[Byte](50, 91, 117))))))
      )
    ),
    outputs = List(
      UnspentTransactionOutput(
        address = LockAddress(network = 593, ledger = 5, id = LockId(bs32_1)),
        value = Value(Value.Value.Lvl(Value.LVL(Int128(ByteString.copyFrom(Array[Byte](33, 77, 122))))))
      )
    ),
    datum = Datum.IoTransaction(
      Event.IoTransaction(
        Schedule(5000, 9000, 345957893L),
        metadata = SmallData(ByteString.copyFrom(Array[Byte](54, -12, 44)))
      )
    )
  )
  println("vector1")
  println(Encoding.encodeToHex(vector1.toByteArray))
  println(Encoding.encodeToHex(ioTransactionSignable.signableBytes(vector1).value.toByteArray))
  println(Encoding.encodeToBase58(vector1.computeId.value.toByteArray))

}
