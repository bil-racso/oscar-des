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
package oscar.des.examples

import oscar.des.engine._
import JSci.maths.statistics.ExponentialDistribution

/**
 * A garage owner has installed an automatic car wash that services cars one at a time.
 * When a car arrives, it goes straight into the car wash if a bay is idle; otherwise it
 * waits FIFO. Service takes exactly 10 minutes; arrivals are Poisson with mean
 * inter-arrival time of 11 minutes. The garage is open for 8 hours, but any car that
 * arrived before closing is washed.
 *
 * The original Sebastien Mouthuy version relied on a richer reactive layer
 * (Var/Event/waitFor/whenever, SimQueue, ProcessWithStates, a Joda-based time DSL).
 * Those primitives are not in the current engine, so this rewrite uses only
 * Model + Resource + NumberGenerator and tracks the queue/wait stats explicitly.
 *
 * Clock unit: minutes.
 */
class CarWash(m: Model, name: String, bays: Int) {
  private val bay     = new Resource(m, bays)
  private var seen    = 0      // cars arrived
  private var done    = 0      // cars washed
  private var queued  = 0      // currently waiting (not yet in a bay)
  private var maxQ    = 0
  private var sumWait = 0.0

  /** A car arrives at time `arrival`; `onServed` fires when its wash finishes. */
  def request(arrival: Double)(onServed: => Unit): Unit = {
    seen += 1
    queued += 1
    if (queued > maxQ) maxQ = queued
    m.request(bay) {
      queued -= 1
      sumWait += (m.clock() - arrival)
      m.wait(10.0) {
        done += 1
        m.release(bay)
        onServed
      }
    }
  }

  def report(): Unit = {
    val avg = if (done == 0) 0.0 else sumWait / done
    println(f"$name%-12s bays=$bays  arrived=$seen  washed=$done  " +
            f"maxQueue=$maxQ  avgWait=$avg%.2f min")
  }
}

class WashCar(m: Model, id: Int, wash: CarWash) {
  def arrive(): Unit = {
    val t0 = m.clock()
    wash.request(t0) {
      // car leaves; nothing further to do
    }
  }
}

object CarWash {

  /** Pre-generate the arrival schedule for an 8-hour day, so both bay scenarios
   *  see identical arrivals and the comparison is fair. */
  private def arrivalTimes(meanMinutes: Double, dayMinutes: Double): Vector[Double] = {
    val rng  = new NumberGenerator(new ExponentialDistribution(1.0 / meanMinutes), seed = 42L)
    val out  = Vector.newBuilder[Double]
    var t    = rng()
    while (t <= dayMinutes) {
      out += t
      t   += rng()
    }
    out.result()
  }

  private def runScenario(label: String, bays: Int, arrivals: Vector[Double]): Unit = {
    val mod  = new Model()
    val wash = new CarWash(mod, label, bays)
    arrivals.zipWithIndex.foreach { case (t, idx) =>
      mod.wait(t) { new WashCar(mod, idx, wash).arrive() }
    }
    // Horizon well past 8 h so any in-progress wash and any queued car finishes.
    mod.simulate(horizon = 24 * 60, verbose = false)
    wash.report()
  }

  def main(args: Array[String]): Unit = {
    val dayMinutes  = 8 * 60.0
    val arrivals    = arrivalTimes(meanMinutes = 11.0, dayMinutes = dayMinutes)
    println(s"Generated ${arrivals.size} arrivals over an ${dayMinutes.toInt}-min day.")
    runScenario("1-bay",  bays = 1, arrivals)
    runScenario("2-bays", bays = 2, arrivals)
  }
}
