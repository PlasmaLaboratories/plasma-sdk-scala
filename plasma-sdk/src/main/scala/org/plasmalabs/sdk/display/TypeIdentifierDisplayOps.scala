package org.plasmalabs.sdk.display

import org.plasmalabs.sdk.display.DisplayOps.DisplayTOps
import org.plasmalabs.sdk.syntax.{AssetType, GroupType, LvlType, SeriesType, ToplType, ValueTypeIdentifier}
import org.plasmalabs.sdk.utils.Encoding

trait TypeIdentifierDisplayOps {

  implicit val typeIdentifierDisplay: DisplayOps[ValueTypeIdentifier] = {
    case LvlType              => "LVL"
    case ToplType(_)          => "TOPL"
    case GroupType(groupId)   => s"Group(${groupId.display})"
    case SeriesType(seriesId) => s"Series(${seriesId.display})"
    case AssetType(groupIdOrAlloy, seriesIdOrAlloy) =>
      s"Asset(${Encoding.encodeToHex(groupIdOrAlloy.toByteArray)}, ${Encoding.encodeToHex(seriesIdOrAlloy.toByteArray)})"
    case _ => "Unknown txo type"
  }
}
