package org.plasmalabs.sdk.syntax

import org.plasmalabs.sdk.models.box.{FungibilityType, QuantityDescriptorType}
import org.plasmalabs.sdk.models.box.Value._
import org.plasmalabs.quivr.models.Int128

import scala.language.implicitConversions

trait BoxValueSyntax {
  implicit def lvlAsBoxVal(lvl:  LVL): Value = Value.Lvl(lvl)
  implicit def toplAsBoxVal(tpl: TOPL): Value = Value.Topl(tpl)
  implicit def groupAsBoxVal(g:  Group): Value = Value.Group(g)
  implicit def seriesAsBoxVal(s: Series): Value = Value.Series(s)
  implicit def assetAsBoxVal(a:  Asset): Value = Value.Asset(a)

  implicit def valueToQuantitySyntaxOps(v: Value): ValueToQuantitySyntaxOps = new ValueToQuantitySyntaxOps(v)

  implicit def valueToQuantityDescriptorSyntaxOps(v: Value): ValueToQuantityDescriptorSyntaxOps =
    new ValueToQuantityDescriptorSyntaxOps(v)
  implicit def valueToFungibilitySyntaxOps(v: Value): ValueToFungibilitySyntaxOps = new ValueToFungibilitySyntaxOps(v)
}

class ValueToQuantitySyntaxOps(val value: Value) extends AnyVal {

  def quantity: Int128 = value match {
    case Value.Lvl(l)    => l.quantity
    case Value.Topl(t)   => t.quantity
    case Value.Group(g)  => g.quantity
    case Value.Series(s) => s.quantity
    case Value.Asset(a)  => a.quantity
    case _               => throw new Exception("Invalid value type")
  }

  def setQuantity(quantity: Int128): Value = value match {
    case Value.Lvl(l)    => l.withQuantity(quantity)
    case Value.Topl(t)   => t.withQuantity(quantity)
    case Value.Group(g)  => g.withQuantity(quantity)
    case Value.Series(s) => s.withQuantity(quantity)
    case Value.Asset(a)  => a.withQuantity(quantity)
    case _               => throw new Exception("Invalid value type")
  }
}

class ValueToQuantityDescriptorSyntaxOps(val value: Value) extends AnyVal {

  def getQuantityDescriptor: Option[QuantityDescriptorType] = value match {
    case Value.Asset(a) => Some(a.quantityDescriptor)
    case _              => None
  }
}

class ValueToFungibilitySyntaxOps(val value: Value) extends AnyVal {

  def getFungibility: Option[FungibilityType] = value match {
    case Value.Asset(a) => Some(a.fungibility)
    case _              => None
  }
}
