package org.plasmalabs.sdk.validation

import cats.Applicative
import cats.data.{Chain, NonEmptyChain, Validated, ValidatedNec}
import cats.implicits._
import org.plasmalabs.sdk.builders.MergingOps
import org.plasmalabs.sdk.common.ContainsImmutable.ContainsImmutableTOps
import org.plasmalabs.sdk.common.ContainsImmutable.instances._
import org.plasmalabs.sdk.models.{AssetMergingStatement, AssetMintingStatement, TransactionOutputAddress}
import org.plasmalabs.sdk.models.box._
import org.plasmalabs.sdk.models.transaction.{IoTransaction, SpentTransactionOutput, UnspentTransactionOutput}
import org.plasmalabs.sdk.syntax._
import org.plasmalabs.sdk.validation.algebras.TransactionSyntaxVerifier
import org.plasmalabs.quivr.models.{Int128, Proof, Proposition}
import scala.util.Try

object TransactionSyntaxInterpreter {

  final val MaxDataLength = 15360

  def make[F[_]: Applicative](): TransactionSyntaxVerifier[F] = new TransactionSyntaxVerifier[F] {

    override def validate(t: IoTransaction): F[Either[NonEmptyChain[TransactionSyntaxError], IoTransaction]] =
      validators
        .foldMap(_ apply t)
        .toEither
        .as(t)
        .pure[F]
  }

  private val validators: Chain[IoTransaction => ValidatedNec[TransactionSyntaxError, Unit]] =
    Chain(
      nonEmptyInputsValidation,
      distinctInputsValidation,
      maximumOutputsCountValidation,
      nonNegativeTimestampValidation,
      scheduleValidation,
      positiveOutputValuesValidation,
      sufficientFundsValidation,
      attestationValidation,
      dataLengthValidation,
      assetEqualFundsValidation,
      groupEqualFundsValidation,
      seriesEqualFundsValidation,
      assetNoRepeatedUtxosValidation,
      mintingValidation,
      updateProposalValidation,
      mergingDistinctValidation,
      mergingValidation,
      lockAddressesNetworkIdValidation
    )

  /**
   * Verify that this transaction contains at least one input
   */
  private def nonEmptyInputsValidation(
    transaction: IoTransaction
  ): ValidatedNec[TransactionSyntaxError, Unit] =
    Validated.condNec(transaction.inputs.nonEmpty, (), TransactionSyntaxError.EmptyInputs)

  /**
   * Verify that this transaction does not spend the same box more than once
   */
  private def distinctInputsValidation(
    transaction: IoTransaction
  ): ValidatedNec[TransactionSyntaxError, Unit] =
    NonEmptyChain
      .fromSeq(
        transaction.inputs
          .groupBy(_.address)
          .collect {
            case (knownIdentifier, inputs) if inputs.size > 1 =>
              TransactionSyntaxError.DuplicateInput(knownIdentifier)
          }
          .toSeq
      )
      .fold(().validNec[TransactionSyntaxError])(_.invalid[Unit])

  /**
   * Verify that this transaction does not contain too many outputs.  A transaction's outputs are referenced by index,
   * but that index must be a Short value.
   */
  private def maximumOutputsCountValidation(
    transaction: IoTransaction
  ): ValidatedNec[TransactionSyntaxError, Unit] =
    Validated.condNec(transaction.outputs.size < Short.MaxValue, (), TransactionSyntaxError.ExcessiveOutputsCount)

  /**
   * Verify that the timestamp of the transaction is positive (greater than or equal to 0).  Transactions _can_ be created
   * in the past.
   */
  private def nonNegativeTimestampValidation(
    transaction: IoTransaction
  ): ValidatedNec[TransactionSyntaxError, Unit] =
    Validated.condNec(
      transaction.datum.event.schedule.timestamp >= 0,
      (),
      TransactionSyntaxError.InvalidTimestamp(transaction.datum.event.schedule.timestamp)
    )

  /**
   * Verify that the schedule of the timestamp contains valid minimum and maximum slot values
   */
  private def scheduleValidation(
    transaction: IoTransaction
  ): ValidatedNec[TransactionSyntaxError, Unit] =
    Validated.condNec(
      transaction.datum.event.schedule.max >= transaction.datum.event.schedule.min &&
      transaction.datum.event.schedule.min >= 0,
      (),
      TransactionSyntaxError.InvalidSchedule(transaction.datum.event.schedule)
    )

  /**
   * Verify that each transaction output contains a positive quantity (where applicable)
   */
  private def positiveOutputValuesValidation(
    transaction: IoTransaction
  ): ValidatedNec[TransactionSyntaxError, Unit] =
    transaction.outputs
      .foldMap[ValidatedNec[TransactionSyntaxError, Unit]](output =>
        (output.value.value match {
          case Value.Value.Lvl(v)  => BigInt(v.quantity.value.toByteArray).some
          case Value.Value.Topl(v) => BigInt(v.quantity.value.toByteArray).some
          case Value.Value.Asset(Value.Asset(_, _, Int128(q, _), _, _, _, _, _, _, _)) => BigInt(q.toByteArray).some
          case _                                                                       => none
        }).foldMap((quantity: BigInt) =>
          Validated
            .condNec(
              quantity > BigInt(0),
              (),
              TransactionSyntaxError.NonPositiveOutputValue(output.value): TransactionSyntaxError
            )
        )
      )

  /**
   * Ensure the input value quantities exceed or equal the (non-minting) output value quantities
   */
  private def sufficientFundsValidation(
    transaction: IoTransaction
  ): ValidatedNec[TransactionSyntaxError, Unit] =
    quantityBasedValidation(transaction) { f =>
      val filteredInputs = transaction.inputs.map(_.value.value).filter(f.isDefinedAt)
      val filteredOutputs = transaction.outputs.map(_.value.value).filter(f.isDefinedAt)
      val inputsSum = filteredInputs.map(f).sumAll
      val outputsSum = filteredOutputs.map(f).sumAll
      Validated.condNec(
        inputsSum >= outputsSum,
        (),
        TransactionSyntaxError.InsufficientInputFunds(
          filteredInputs.toList,
          filteredOutputs.toList
        ): TransactionSyntaxError
      )
    }

  /**
   * Perform validation based on the quantities of boxes grouped by type
   *
   * @param f an extractor function which retrieves a BigInt from a Box.Value
   */
  private def quantityBasedValidation(transaction: IoTransaction)(
    f: PartialFunction[Value.Value, BigInt] => ValidatedNec[TransactionSyntaxError, Unit]
  ): ValidatedNec[TransactionSyntaxError, Unit] =
    NonEmptyChain(
      // Extract all Token values and their quantities
      f { case Value.Value.Lvl(v) => BigInt(v.quantity.value.toByteArray) },
      f { case Value.Value.Topl(v) => BigInt(v.quantity.value.toByteArray) }
      // Extract all Token Asset values and their quantities. TODO
    ).combineAll

  /**
   * Validates that the attestations for each of the transaction's inputs are valid
   */
  private def attestationValidation(
    transaction: IoTransaction
  ): ValidatedNec[TransactionSyntaxError, Unit] =
    transaction.inputs
      .map(input =>
        input.attestation.value match {
          case Attestation.Value.Predicate(Attestation.Predicate(lock, responses, _)) =>
            predicateLockProofTypeValidation(lock, responses)
          // TODO: There is no validation for Attestation types other than Predicate for now
          case _ => ().validNec[TransactionSyntaxError]
        }
      )
      .combineAll

  /**
   * Validates that the proofs associated with each proposition matches the expected _type_ for a Predicate Attestation
   *
   * (i.e. a DigitalSignature Proof that is associated with a HeightRange Proposition, this validation will fail)
   *
   * Preconditions: lock.challenges.length <= responses.length
   */
  private def predicateLockProofTypeValidation(
    lock:      Lock.Predicate,
    responses: Seq[Proof]
  ): ValidatedNec[TransactionSyntaxError, Unit] =
    (lock.challenges zip responses)
      // TODO: Fix `.getRevealed`
      .map(challenge => proofTypeMatch(challenge._1.getRevealed, challenge._2))
      .combineAll

  /**
   * Validate that the type of Proof matches the type of the given Proposition
   * A Proof.Value.Empty type is considered valid for all Proposition types
   */
  private def proofTypeMatch(proposition: Proposition, proof: Proof): ValidatedNec[TransactionSyntaxError, Unit] =
    (proposition.value, proof.value) match {
      case (_, Proof.Value.Empty) =>
        ().validNec[TransactionSyntaxError] // Empty proofs are valid for all Proposition types
      case (Proposition.Value.Locked(_), Proof.Value.Locked(_)) => ().validNec[TransactionSyntaxError]
      case (Proposition.Value.Digest(_), Proof.Value.Digest(_)) => ().validNec[TransactionSyntaxError]
      case (Proposition.Value.DigitalSignature(_), Proof.Value.DigitalSignature(_)) =>
        ().validNec[TransactionSyntaxError]
      case (Proposition.Value.HeightRange(_), Proof.Value.HeightRange(_)) => ().validNec[TransactionSyntaxError]
      case (Proposition.Value.TickRange(_), Proof.Value.TickRange(_))     => ().validNec[TransactionSyntaxError]
      case (Proposition.Value.ExactMatch(_), Proof.Value.ExactMatch(_))   => ().validNec[TransactionSyntaxError]
      case (Proposition.Value.LessThan(_), Proof.Value.LessThan(_))       => ().validNec[TransactionSyntaxError]
      case (Proposition.Value.GreaterThan(_), Proof.Value.GreaterThan(_)) => ().validNec[TransactionSyntaxError]
      case (Proposition.Value.EqualTo(_), Proof.Value.EqualTo(_))         => ().validNec[TransactionSyntaxError]
      case (Proposition.Value.Threshold(_), Proof.Value.Threshold(_))     => ().validNec[TransactionSyntaxError]
      case (Proposition.Value.Not(_), Proof.Value.Not(_))                 => ().validNec[TransactionSyntaxError]
      case (Proposition.Value.And(_), Proof.Value.And(_))                 => ().validNec[TransactionSyntaxError]
      case (Proposition.Value.Or(_), Proof.Value.Or(_))                   => ().validNec[TransactionSyntaxError]
      case _ => TransactionSyntaxError.InvalidProofType(proposition, proof).invalidNec[Unit]
    }

  /**
   * DataLengthValidation validates approved transaction data length, includes proofs
   * @see [[https://topl.atlassian.net/browse/BN-708]]
   * @param transaction transaction
   * @return
   */
  private def dataLengthValidation(
    transaction: IoTransaction
  ): ValidatedNec[TransactionSyntaxError, Unit] =
    Validated.condNec(
      transaction.immutable.value.size <= MaxDataLength,
      (),
      TransactionSyntaxError.InvalidDataLength
    )

  /**
   * AssetEqualFundsValidation For each asset: input assets + minted assets - merging inputs == output asset - merged outputs
   * @param transaction - transaction
   * @return
   */
  private def assetEqualFundsValidation(transaction: IoTransaction): ValidatedNec[TransactionSyntaxError, Unit] = {
    val inputAssets = transaction.inputs
      .filterNot(in =>
        transaction.datum.event.mergingStatements.flatMap(_.inputUtxos).contains(in.address)
      ) // ignoring merging inputs
      .filter(_.value.value.isAsset)
      .map(_.value.value)
    val outputAssets = transaction.outputs.zipWithIndex
      .filterNot(out =>
        transaction.datum.event.mergingStatements.map(_.outputIdx).contains(out._2)
      ) // ignoring merged outputs
      .map(_._1)
      .filter(_.value.value.isAsset)
      .map(_.value.value)

    def groupGivenMintedStatements(stm: AssetMintingStatement) =
      transaction.inputs
        .filter(_.address == stm.groupTokenUtxo)
        .filter(_.value.value.isGroup)
        .map(_.value.getGroup)
        .headOption

    def seriesGivenMintedStatements(mintedAsset: AssetMintingStatement) =
      transaction.inputs
        .filter(_.address == mintedAsset.seriesTokenUtxo)
        .filter(_.value.value.isSeries)
        .map(_.value.getSeries)
        .headOption

    val mintedAsset = transaction.datum.event.mintingStatements.map { stm =>
      val series = seriesGivenMintedStatements(stm)
      Value.defaultInstance
        .withAsset(
          Value.Asset(
            groupId = groupGivenMintedStatements(stm).map(_.groupId),
            seriesId = series.map(_.seriesId),
            quantity = stm.quantity,
            fungibility = series.map(_.fungibility).getOrElse(FungibilityType.GROUP_AND_SERIES)
          )
        )
        .value
    }

    def tupleAndGroup(s: Seq[Value.Value]) =
      Try {
        s.map(v => ((v.typeIdentifier, v.getFungibility, v.getQuantityDescriptor), v.quantity: BigInt))
          // Grouping includes fungibility and quantity descriptor to account for invalid asset configurations
          // I.e, we validate that the fungibility and quantity descriptor does not differ from their
          // corresponding asset input (transfer) or series constructor (minting)
          .groupBy(_._1)
          .view
          .mapValues(_.map(_._2).sum)
          .toMap
      }

    val res = for {
      input  <- tupleAndGroup(inputAssets).toEither
      minted <- tupleAndGroup(mintedAsset).toEither
      output <- tupleAndGroup(outputAssets).toEither
      keySetResult = input.keySet ++ minted.keySet == output.keySet
      compareResult = output.keySet.forall(k =>
        input.getOrElse(k, 0: BigInt) + minted.getOrElse(k, 0) == output.getOrElse(k, 0)
      )
    } yield (keySetResult && compareResult)

    Validated.condNec(
      res.map(_ == true).getOrElse(false),
      (),
      TransactionSyntaxError.InsufficientInputFunds(
        transaction.inputs.map(_.value.value).toList,
        transaction.outputs.map(_.value.value).toList
      )
    )

  }

  /**
   * GroupEqualFundsValidation
   *
   *  - Check Moving Constructor Tokens: Let 'g' be a group identifier, then the number of Group Constructor Tokens with group identifier 'g'
   *    in the input is equal to the quantity of Group Constructor Tokens with identifier 'g' in the output.
   *  - Check Minting Constructor Tokens: Let 'g' be a group identifier and 'p' the group policy whose digest is equal to 'g', a transaction is valid only if the all of the following statements are true:
   *   - The policy 'p' is attached to the transaction.
   *   - The number of group constructor tokens with identifier 'g' in the output of the transaction is strictly bigger than 0.
   *   - The registration UTXO referenced in 'p' is present in the inputs and contains LVLs.
   *
   * @param transaction - transaction
   * @return
   */
  private def groupEqualFundsValidation(transaction: IoTransaction): ValidatedNec[TransactionSyntaxError, Unit] = {

    val groupsIn = transaction.inputs.flatMap(_.value.value.group)
    val groupsOut = transaction.outputs.flatMap(_.value.value.group)

    val gIds =
      groupsIn.groupBy(_.groupId).keySet ++
      groupsOut.groupBy(_.groupId).keySet ++
      transaction.datum.event.groupPolicies.map(_.computeId).toSet

    val res = gIds.forall { gId =>
      if (!transaction.datum.event.groupPolicies.map(_.computeId).contains(gId)) {
        groupsIn.filter(_.groupId == gId).map(_.quantity: BigInt).sum ==
          groupsOut.filter(_.groupId == gId).map(_.quantity: BigInt).sum
      } else {
        groupsOut.filter(_.groupId == gId).map(_.quantity: BigInt).sum > 0
      }
    }

    Validated.condNec(
      res,
      (),
      TransactionSyntaxError.InsufficientInputFunds(
        transaction.inputs.map(_.value.value).toList,
        transaction.outputs.map(_.value.value).toList
      )
    )

  }

  /**
   * SeriesEqualFundsValidation
   *  - Check Moving Series Tokens: Let s be a series identifier, then the number of Series Constructor Tokens with group identifier s
   * in the input is equal to the number of the number of Series Constructor Tokens with identifier s in the output.
   *  - Check Minting Constructor Tokens: Let s be a series identifier and p the series policy whose digest is equal to s, all of the following statements are true:
   *    The policy p is attached to the transaction.
   *    The number of series constructor tokens with identifiers in the output of the transaction is strictly bigger than 0.
   *    The registration UTXO referenced in p is present in the inputs and contains LVLs.
   *
   * @param transaction
   * @return
   */
  private def seriesEqualFundsValidation(transaction: IoTransaction): ValidatedNec[TransactionSyntaxError, Unit] = {

    val seriesIn = transaction.inputs.flatMap(_.value.value.series)
    val seriesOut = transaction.outputs.flatMap(_.value.value.series)

    val sIds =
      seriesIn.groupBy(_.seriesId).keySet ++
      seriesOut.groupBy(_.seriesId).keySet ++
      transaction.datum.event.seriesPolicies.map(_.computeId).toSet

    val sIdsOnMintingStatements = {
      val sIdsAddress = transaction.datum.event.mintingStatements.map(_.seriesTokenUtxo)
      transaction.inputs
        .filter(sto => sIdsAddress.contains(sto.address))
        .filter(_.value.value.isSeries)
        .map(_.value.getSeries.seriesId)
    }

    val res = sIds.forall { sId =>
      if (sIdsOnMintingStatements.contains(sId)) {
        seriesOut.filter(_.seriesId == sId).map(_.quantity: BigInt).sum >= 0
      } else if (!transaction.datum.event.seriesPolicies.map(_.computeId).contains(sId)) {
        seriesIn.filter(_.seriesId == sId).map(_.quantity: BigInt).sum ==
          seriesOut.filter(_.seriesId == sId).map(_.quantity: BigInt).sum
      } else {
        seriesOut.filter(_.seriesId == sId).map(_.quantity: BigInt).sum > 0
      }
    }

    Validated.condNec(
      res,
      (),
      TransactionSyntaxError.InsufficientInputFunds(
        transaction.inputs.map(_.value.value).toList,
        transaction.outputs.map(_.value.value).toList
      )
    )

  }

  /**
   * Asset, Group and Series,  No Repeated Utxos Validation
   * - For all assets minting statement ams1, ams2, ...,  Should not contain repeated UTXOs
   * - For all group/series policies gp1, gp2, ..., ++ sp1, sp2, ..., Should not contain repeated UTXOs
   *
   * @param transaction - transaction
   * @return
   */
  private def assetNoRepeatedUtxosValidation(transaction: IoTransaction): ValidatedNec[TransactionSyntaxError, Unit] =
    NonEmptyChain
      .fromSeq {
        val mintingStatementsValidation = transaction.datum.event.mintingStatements
          .flatMap(stm => Seq(stm.groupTokenUtxo, stm.seriesTokenUtxo))
          .groupBy(identity)
          .collect {
            case (address, seqAddresses) if seqAddresses.size > 1 =>
              TransactionSyntaxError.DuplicateInput(address)
          }
          .toSeq

        val policiesValidation =
          (transaction.datum.event.groupPolicies.map(
            _.registrationUtxo
          ) ++ transaction.datum.event.seriesPolicies
            .map(_.registrationUtxo))
            .groupBy(identity)
            .collect {
              case (address, seqAddresses) if seqAddresses.size > 1 =>
                TransactionSyntaxError.DuplicateInput(address)
            }
            .toSeq

        policiesValidation ++ mintingStatementsValidation
      }
      .fold(().validNec[TransactionSyntaxError])(_.invalid[Unit])

  private def mintingInputsProjection(transaction: IoTransaction): Seq[SpentTransactionOutput] =
    transaction.inputs.filter { stxo =>
      !stxo.value.value.isTopl &&
      !stxo.value.value.isAsset &&
      (!stxo.value.value.isLvl || (transaction.datum.event.groupPolicies.exists(
        _.registrationUtxo == stxo.address
      ) || transaction.datum.event.seriesPolicies.exists(_.registrationUtxo == stxo.address)))
    }

  private def mintingOutputsProjection(transaction: IoTransaction): Seq[UnspentTransactionOutput] = {
    val groupIdsOnMintedStatements =
      transaction.inputs
        .filter(_.value.value.isGroup)
        .filter(sto => transaction.datum.event.mintingStatements.map(_.groupTokenUtxo).contains(sto.address))
        .map(_.value.getGroup.groupId)

    val seriesIdsOnMintedStatements =
      transaction.inputs
        .filter(_.value.value.isSeries)
        .filter(sto => transaction.datum.event.mintingStatements.map(_.seriesTokenUtxo).contains(sto.address))
        .map(_.value.getSeries.seriesId)

    transaction.outputs.filter { utxo =>
      !utxo.value.value.isLvl &&
      !utxo.value.value.isTopl &&
      (!utxo.value.value.isGroup || transaction.datum.event.groupPolicies
        .map(_.computeId)
        .contains(utxo.value.getGroup.groupId)) &&
      (!utxo.value.value.isSeries || transaction.datum.event.seriesPolicies
        .map(_.computeId)
        .contains(utxo.value.getSeries.seriesId)) &&
      (!utxo.value.value.isAsset || (utxo.value.getAsset.groupId.exists(
        groupIdsOnMintedStatements.contains
      ) && utxo.value.getAsset.seriesId.exists(seriesIdsOnMintedStatements.contains)))
    }
  }

  private def mintingValidation(transaction: IoTransaction) = {
    val projectedTransaction = transaction
      .withInputs(mintingInputsProjection(transaction))
      .withOutputs(mintingOutputsProjection(transaction))

    val groups = projectedTransaction.outputs.flatMap(_.value.value.group)
    val series = projectedTransaction.outputs.flatMap(_.value.value.series)

    def registrationInPolicyContainsLvls(registrationUtxo: TransactionOutputAddress): Boolean =
      projectedTransaction.inputs.exists { stxo =>
        stxo.value.value.isLvl &&
        stxo.value.getLvl.quantity > 0 &&
        stxo.address == registrationUtxo
      }

    val validGroups = groups.forall { group =>
      transaction.datum.event.groupPolicies.exists { policy =>
        policy.computeId == group.groupId &&
        registrationInPolicyContainsLvls(policy.registrationUtxo)
      } &&
      group.quantity > 0
    }

    val validSeries = series.forall { series =>
      transaction.datum.event.seriesPolicies.exists { policy =>
        policy.computeId == series.seriesId &&
        registrationInPolicyContainsLvls(policy.registrationUtxo) &&
        series.quantity > 0
      }
    }

    /**
     * Let sIn be the total number of series constructor tokens with identifier in the input,
     * sOut the total number of series constructor tokens with identifier in the output,
     * and burned the number of where the referenced series specifies a token supply, then we have:
     * sIn - burned = sOut
     */
    val validAssets = transaction.datum.event.mintingStatements.forall { ams =>
      val maybeSeries: Option[Value.Series] =
        transaction.inputs
          .filter(_.value.value.isSeries)
          .filter(_.address == ams.seriesTokenUtxo)
          .map(_.value.getSeries)
          .headOption

      maybeSeries match {
        case Some(s) =>
          s.tokenSupply match {
            case Some(tokenSupplied) =>
              val sIn = transaction.inputs
                .filter(_.value.value.isSeries)
                .filter(_.value.getSeries.seriesId == s.seriesId)
                .map(_.value.getSeries.quantity: BigInt)
                .sum

              val sOut =
                transaction.outputs
                  .filter(_.value.value.isSeries)
                  .filter(_.value.getSeries.seriesId == s.seriesId)
                  .map(_.value.getSeries.quantity: BigInt)
                  .sum

              val burned = sIn - sOut

              // all asset minting statements quantity with the same series id
              def quantity(s: Value.Series) = transaction.datum.event.mintingStatements.map { ams =>
                val filterSeries = transaction.inputs
                  .filter(_.address == ams.seriesTokenUtxo)
                  .filter(_.value.value.isSeries)
                  .map(_.value.getSeries)
                  .filter(_.seriesId == s.seriesId)
                if (filterSeries.isEmpty) BigInt(0) else ams.quantity: BigInt
              }

              (ams.quantity: BigInt) <= s.quantity * tokenSupplied &&
              (ams.quantity: BigInt) % tokenSupplied == 0 &&
              burned * tokenSupplied == quantity(s).sum

            case None => true
          }
        case None => false
      }
    }

    Validated.condNec(
      validGroups && validSeries && validAssets,
      (),
      TransactionSyntaxError.InsufficientInputFunds(
        transaction.inputs.map(_.value.value).toList,
        transaction.outputs.map(_.value.value).toList
      )
    )

  }

  private def updateProposalValidation(transaction: IoTransaction) = {
    val upsOut = transaction.outputs.flatMap(_.value.value.updateProposal)
    val isValid = upsOut.forall { up =>
      up.label.nonEmpty &&
      up.fEffective.forall(r => (r.denominator: BigInt) > 0 && (r.numerator: BigInt) > 0) &&
      up.vrfLddCutoff.forall(_ > 0) &&
      up.vrfPrecision.forall(_ > 0) &&
      up.vrfBaselineDifficulty.forall(r => (r.denominator: BigInt) > 0 && (r.numerator: BigInt) > 0) &&
      up.vrfAmplitude.forall(r => (r.denominator: BigInt) > 0 && (r.numerator: BigInt) > 0) &&
      up.chainSelectionKLookback.forall(_ > 0) &&
      up.slotDuration.forall(d => d.seconds > 0) &&
      up.forwardBiasedSlotWindow.forall(_ > 0) &&
      up.operationalPeriodsPerEpoch.forall(_ > 0) &&
      up.kesKeyHours.forall(_ > 0) &&
      up.kesKeyMinutes.forall(_ > 0)
    }

    Validated.condNec(isValid, (), TransactionSyntaxError.InvalidUpdateProposal(upsOut))
  }

  /**
   * Validate that the merging inputs are distinct (not re-used in other merging statements)
   */
  private def mergingDistinctValidation(transaction: IoTransaction): ValidatedNec[TransactionSyntaxError, Unit] = {
    val mergingInputs = transaction.datum.event.mergingStatements.flatMap(
      _.inputUtxos.distinct
    ) // distinct within each merging statement since those duplicates are handled via InvalidMergingStatement
    val repeatedInputs: Seq[TransactionSyntaxError] = mergingInputs
      .diff(mergingInputs.distinct)
      .map(
        TransactionSyntaxError.NonDistinctMergingInput.apply
      ) // results with a list of inputs that are present in multiple merging statements
    NonEmptyChain.fromSeq(repeatedInputs) match {
      case Some(repeated) => Validated.Invalid(repeated)
      case None           => ().validNec[TransactionSyntaxError]
    }
  }

  /**
   * Validate that the transaction w.r.t the merging statements is valid AND that the merge operations are syntactically valid
   */
  private def mergingValidation(transaction: IoTransaction): ValidatedNec[TransactionSyntaxError, Unit] =
    mergingStatementsValidation(transaction).andThen(_ => mergingCompatibilityValidation(transaction))

  /**
   * Validate that the transaction w.r.t the merging statements is valid
   */
  private def mergingStatementsValidation(transaction: IoTransaction): ValidatedNec[TransactionSyntaxError, Unit] = {
    type AsmCheck = AssetMergingStatement => Boolean
    val outputIdxInBounds: AsmCheck = _.outputIdx < transaction.outputs.size
    val multipleInputs: AsmCheck = _.inputUtxos.length >= 2
    val allInputsPresent: AsmCheck = _.inputUtxos.forall(input => transaction.inputs.exists(_.address == input))
    val distinctInputs: AsmCheck = s => s.inputUtxos.distinct.length == s.inputUtxos.length

    def isValidStatement: AsmCheck = s =>
      Seq(outputIdxInBounds, multipleInputs, allInputsPresent, distinctInputs).forall(_(s))

    val invalidMergingStatements = transaction.datum.event.mergingStatements.filterNot(isValidStatement)
    NonEmptyChain.fromSeq(invalidMergingStatements.map(TransactionSyntaxError.InvalidMergingStatement.apply)) match {
      case Some(repeated) => Validated.Invalid(repeated)
      case None           => ().validNec[TransactionSyntaxError]
    }
  }

  /**
   * Validate that the merge operations are syntactically valid
   */
  private def mergingCompatibilityValidation(transaction: IoTransaction): ValidatedNec[TransactionSyntaxError, Unit] = {
    case class MergingValues(inputs: Seq[Value], output: Value)
    type MergeCheck = MergingValues => Boolean
    val allAssetInputs: MergeCheck = _.inputs.forall(_.value.isAsset)
    val assetOutput: MergeCheck = _.output.value.isAsset
    val sumInputsEqualsOutput: MergeCheck = v =>
      v.inputs.map(_.getAsset.quantity: BigInt).sum == (v.output.getAsset.quantity: BigInt)
    val sameQuantityDescriptors: MergeCheck = v =>
      v.inputs.forall(_.getAsset.quantityDescriptor == v.output.getAsset.quantityDescriptor)
    val sameFungibility: MergeCheck = v => v.inputs.forall(_.getAsset.fungibility == v.output.getAsset.fungibility)

    val sameSeriesId: MergeCheck = v => v.inputs.forall(_.getAsset.seriesId == v.output.getAsset.seriesId)
    val noGroupId: MergeCheck = _.output.getAsset.groupId.isEmpty
    val groupAlloy: MergeCheck = v =>
      v.output.getAsset.groupAlloy.contains(MergingOps.getAlloy(v.inputs.map(_.getAsset)))

    val sameGroupId: MergeCheck = v => v.inputs.forall(in => in.getAsset.groupId == v.output.getAsset.groupId)
    val noSeriesId: MergeCheck = _.output.getAsset.seriesId.isEmpty
    val seriesAlloy: MergeCheck = v =>
      v.output.getAsset.seriesAlloy.contains(MergingOps.getAlloy(v.inputs.map(_.getAsset)))

    val validFungibility: MergeCheck = {
      case v if v.output.getAsset.fungibility == FungibilityType.SERIES =>
        Seq(sameSeriesId, noGroupId, groupAlloy).forall(_(v))
      case v if v.output.getAsset.fungibility == FungibilityType.GROUP =>
        Seq(sameGroupId, noSeriesId, seriesAlloy).forall(_(v))
      case _ => false // GROUP_AND_SERIES is not allowed to merge
    }

    def isValidMerge: MergeCheck = v =>
      Seq(
        allAssetInputs,
        assetOutput,
        sumInputsEqualsOutput,
        sameQuantityDescriptors,
        sameFungibility,
        validFungibility
      ).forall(_(v))

    val toMerge = transaction.datum.event.mergingStatements.map(asm =>
      MergingValues(
        asm.inputUtxos.map(input => transaction.inputs.find(_.address == input).get.value),
        transaction.outputs(asm.outputIdx).value
      )
    )
    NonEmptyChain.fromSeq(
      toMerge.filterNot(isValidMerge).map(vals => TransactionSyntaxError.IncompatibleMerge(vals.inputs, vals.output))
    ) match {
      case Some(repeated) => Validated.Invalid(repeated)
      case None           => ().validNec[TransactionSyntaxError]
    }
  }

  /**
   * Validate that all the lock addresses in the transaction share the same network ID
   */
  private def lockAddressesNetworkIdValidation(
    transaction: IoTransaction
  ): ValidatedNec[TransactionSyntaxError, Unit] = {
    val networkIds = transaction.inputs.map(_.address.network) ++ transaction.outputs.map(_.address.network)
    val distinctNetworkIds = networkIds.distinct
    Validated.condNec(
      distinctNetworkIds.length == 1,
      (),
      TransactionSyntaxError.InconsistentNetworkIDs(distinctNetworkIds.toSet)
    )
  }
}
