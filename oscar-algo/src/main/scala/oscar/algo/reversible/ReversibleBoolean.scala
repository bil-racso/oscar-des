/*******************************************************************************
 * OscaR is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *   
 * OscaR is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License  for more details.
 *   
 * You should have received a copy of the GNU Lesser General Public License along with OscaR.
 * If not, see http://www.gnu.org/licenses/lgpl-3.0.en.html
 ******************************************************************************/
package oscar.algo.reversible;

/**
 * Reversible Boolean
 * @author Pierre Schaus pschaus@gmail.com
 * @author Renaud Hartert ren.hartert@gmail.com
 */

class TrueTrailEntry(reversible: ReversibleBoolean) extends TrailEntry {
  @inline final override def restore(): Unit = reversible.restoreTrue()
}

class FalseTrailEntry(reversible: ReversibleBoolean) extends TrailEntry {
  @inline final override def restore(): Unit = reversible.restoreFalse()
}

class ReversibleBoolean(node: ReversibleContext, initialValue: Boolean) {

  def this(node: ReversibleContext) = this(node, true)
  
  private[this] var lastMagic: Long = -1L
  private[this] var pointer: Boolean = initialValue
  private[this] val trueTrailEntry = new TrueTrailEntry(this)
  private[this] val falseTrailEntry = new FalseTrailEntry(this)
  
  @inline private def trail(): Unit = {
    val contextMagic = node.magic
    if (lastMagic != contextMagic) {
      lastMagic = contextMagic
      if (pointer) node.trail(trueTrailEntry)
      else node.trail(falseTrailEntry)
    }
  }
  
  @inline final def setValue(value: Boolean): Unit = {
    if (value != pointer) {
      trail()
      this.pointer = value
    }
  }
  
  /** @param value to assign */
  @inline final def value_= (value: Boolean): Unit = {
    if (value != pointer) {
      trail()
      this.pointer = value
    }
  }
  
  @inline final def restoreTrue(): Unit = pointer = true
  
  @inline final def restoreFalse(): Unit = pointer = false
  
  @inline final def setTrue(): Unit = {
    if (!pointer) {
      trail()
      this.pointer = true
    }
  }
  
  @inline final def setFalse(): Unit = {
    if (pointer) {
      trail()
      this.pointer = false
    }
  }
  
  /** @return current value */
  @inline final def value: Boolean = pointer

  /** @return the current pointer */
  @inline final def getValue(): Boolean = pointer

  override def toString(): String = pointer.toString
}