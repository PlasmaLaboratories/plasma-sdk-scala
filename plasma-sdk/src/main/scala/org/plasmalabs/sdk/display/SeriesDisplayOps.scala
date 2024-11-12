package org.plasmalabs.sdk.display

import org.plasmalabs.sdk.display.DisplayOps.DisplayTOps
import org.plasmalabs.sdk.models.{SeriesId, SeriesPolicy}
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

  implicit val seriesPolicyDisplay: DisplayOps[SeriesPolicy] = (sp: SeriesPolicy) =>
    Seq(
      padLabel("Label") + sp.label,
      padLabel("Registration-Utxo") + sp.registrationUtxo.display,
      padLabel("Fungibility") + sp.fungibility.display,
      padLabel("Quantity-Descriptor") + sp.quantityDescriptor.display,
      padLabel("Token-Supply") + displayTokenSupply(sp.tokenSupply),
      padLabel("Permanent-Metadata-Scheme"),
      sp.permanentMetadataScheme.map(meta => meta.display).getOrElse("No permanent metadata"),
      padLabel("Ephemeral-Metadata-Scheme"),
      sp.ephemeralMetadataScheme.map(meta => meta.display).getOrElse("No ephemeral metadata")
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
