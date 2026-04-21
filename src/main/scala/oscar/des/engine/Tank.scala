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
package oscar.des.engine

import scala.collection.mutable

/**
 * A bounded pool of fungible units. Unlike `Resource`, operations take effect
 * immediately when their precondition holds — there is no explicit release.
 *
 *   - `get(qty)(block)` consumes `qty` units as soon as `load >= qty`.
 *   - `put(qty)(block)` deposits `qty` units as soon as `load + qty <= capacity`.
 *
 * Waiters are served in FIFO order within each side, head-only: a large request
 * at the head will block smaller followers even if they would fit.
 */
class Tank(m: Model, val capacity: Double) {
  require(capacity > 0, s"Tank capacity must be positive (got $capacity)")

  private var load = 0.0
  private val getPending = mutable.Queue[(Double, () => Unit)]()
  private val putPending = mutable.Queue[(Double, () => Unit)]()

  def level: Double = load

  def get(qty: Double)(block: => Unit): Unit = {
    checkQty("get", qty)
    if (load >= qty) {
      load -= qty
      block
    } else {
      getPending += ((qty, () => block))
    }
    drain()
  }

  def put(qty: Double)(block: => Unit): Unit = {
    checkQty("put", qty)
    if (load + qty <= capacity) {
      load += qty
      block
    } else {
      putPending += ((qty, () => block))
    }
    drain()
  }

  private def checkQty(op: String, qty: Double): Unit = {
    require(qty > 0, s"$op qty must be positive (got $qty)")
    require(qty <= capacity, s"$op qty ($qty) exceeds tank capacity ($capacity)")
  }

  private def drain(): Unit = {
    var progress = true
    while (progress) {
      progress = false
      if (getPending.nonEmpty && load >= getPending.head._1) {
        val (qty, block) = getPending.dequeue()
        load -= qty
        block()
        progress = true
      }
      if (putPending.nonEmpty && load + putPending.head._1 <= capacity) {
        val (qty, block) = putPending.dequeue()
        load += qty
        block()
        progress = true
      }
    }
  }
}
