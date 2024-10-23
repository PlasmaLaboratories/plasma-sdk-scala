package org.plasmalabs.sdk.display

import org.plasmalabs.sdk.display.DisplayOps.DisplayTOps
import org.plasmalabs.sdk.models.{Datum, SeriesId}
import org.plasmalabs.sdk.models.box.{FungibilityType, QuantityDescriptorType}
import org.plasmalabs.sdk.utils.Encoding
import org.plasmalabs.sdk.models.box.Value

trait SeriesDisplayOps {

  implicit val seriesIdDisplay: DisplayOps[SeriesId] = (id: SeriesId) => Encoding.encodeToHex(id.value.toByteArray())

  implicit val fungibilityDisplay: DisplayOps[FungibilityType] = {
    case FungibilityType.GROUP_AND_SERIES => "group-and-series"
    case FungibilityType.GROUP            => "group"
    case FungibilityType.SERIES           => "series"
    case _                                => throw new Exception("Unknown fungibility type") // this should not happen
  }

  implicit val quantityDescriptorDisplay: DisplayOps[QuantityDescriptorType] = {
    case QuantityDescriptorType.LIQUID       => "liquid"
    case QuantityDescriptorType.ACCUMULATOR  => "accumulator"
    case QuantityDescriptorType.FRACTIONABLE => "fractionable"
    case QuantityDescriptorType.IMMUTABLE    => "immutable"
    case _ => throw new Exception("Unknown quantity descriptor type") // should not happen
  }

  implicit val seriesPolicyDisplay: DisplayOps[Datum.SeriesPolicy] = (sp: Datum.SeriesPolicy) =>
    Seq(
      padLabel("Label") + sp.event.label,
      padLabel("Registration-Utxo") + sp.event.registrationUtxo.display,
      padLabel("Fungibility") + sp.event.fungibility.display,
      padLabel("Quantity-Descriptor") + sp.event.quantityDescriptor.display,
      padLabel("Token-Supply") + displayTokenSupply(sp.event.tokenSupply),
      padLabel("Permanent-Metadata-Scheme"),
      sp.event.permanentMetadataScheme.map(meta => meta.display).getOrElse("No permanent metadata"),
      padLabel("Ephemeral-Metadata-Scheme"),
      sp.event.ephemeralMetadataScheme.map(meta => meta.display).getOrElse("No ephemeral metadata")
    ).mkString("\n")

  implicit val seriesDisplay: DisplayOps[Value.Series] = (series: Value.Series) =>
    Seq(
      "Series Constructor",
      padLabel("Id") + series.seriesId.display,
      padLabel("Fungibility") + series.fungibility.display,
      padLabel("Token-Supply") + displayTokenSupply(series.tokenSupply),
      padLabel("Quant-Descr.") + series.quantityDescriptor.display
    ).mkString("\n")

  private def displayTokenSupply(tokenSupply: Option[Int]): String =
    tokenSupply.map(_.toString).getOrElse("UNLIMITED")
}
