package xyz.stratalab.sdk.display

import xyz.stratalab.sdk.display.DisplayOps.DisplayTOps
import xyz.stratalab.sdk.models.box.Value
import xyz.stratalab.sdk.models.box.Value.Value._
import xyz.stratalab.sdk.syntax.{int128AsBigInt, valueToQuantitySyntaxOps}

import scala.util.{Failure, Success, Try}

trait ValueDisplayOps {

  implicit val valueDisplay: DisplayOps[Value.Value] = (value: Value.Value) =>
    Seq(typeDisplay(value), quantityDisplay(value)).mkString("\n")

  def typeDisplay(value: Value.Value): String = {
    val vType = value match {
      case Lvl(_)    => "LVL"
      case Group(g)  => g.display
      case Series(s) => s.display
      case Asset(a)  => a.display
      case Topl(_)   => "TOPL"
      case _         => "Unknown txo type"
    }
    padLabel("Type") + vType
  }

  def quantityDisplay(value: Value.Value): String = {
    val quantity = Try {
      value.quantity
    } match {
      case Success(asInt128) => (asInt128: BigInt).toString()
      case Failure(_)        => "Undefine type"
    }
    padLabel("Value") + quantity
  }
}
