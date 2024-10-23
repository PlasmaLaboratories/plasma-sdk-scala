package org.plasmalabs.sdk.display

import org.plasmalabs.sdk.display.DisplayOps.DisplayTOps
import org.plasmalabs.sdk.models.{Datum, GroupId, SeriesId}
import org.plasmalabs.sdk.utils.Encoding
import org.plasmalabs.sdk.models.box.Value

trait GroupDisplayOps {
  implicit val groupIdDisplay: DisplayOps[GroupId] = (id: GroupId) => Encoding.encodeToHex(id.value.toByteArray())

  implicit val groupPolicyDisplay: DisplayOps[Datum.GroupPolicy] = (gp: Datum.GroupPolicy) =>
    Seq(
      padLabel("Label") + gp.event.label,
      padLabel("Registration-Utxo") + gp.event.registrationUtxo.display,
      padLabel("Fixed-Series") + displayFixedSeries(gp.event.fixedSeries)
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
