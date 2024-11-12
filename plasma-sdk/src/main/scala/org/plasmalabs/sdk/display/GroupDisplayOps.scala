package org.plasmalabs.sdk.display

import org.plasmalabs.sdk.display.DisplayOps.DisplayTOps
import org.plasmalabs.sdk.models.{Datum, GroupId, GroupPolicy, SeriesId}
import org.plasmalabs.sdk.utils.Encoding
import org.plasmalabs.sdk.models.box.Value

trait GroupDisplayOps {
  implicit val groupIdDisplay: DisplayOps[GroupId] = (id: GroupId) => Encoding.encodeToHex(id.value.toByteArray())

  implicit val groupPolicyDisplay: DisplayOps[GroupPolicy] = (gp: GroupPolicy) =>
    Seq(
      padLabel("Label") + gp.label,
      padLabel("Registration-Utxo") + gp.registrationUtxo.display,
      padLabel("Fixed-Series") + displayFixedSeries(gp.fixedSeries)
    ).mkString("\n")

  implicit val groupDisplay: DisplayOps[Value.Group] = (group: Value.Group) =>
    Seq(
      "Group Constructor",
      padLabel("Id") + group.groupId.display,
      padLabel("Fixed-Series") + displayFixedSeries(group.fixedSeries)
    ).mkString("\n")

  private def displayFixedSeries(fixedSeries: Option[SeriesId]): String =
    fixedSeries.map(sId => sId.display).getOrElse("NO FIXED SERIES")
}
