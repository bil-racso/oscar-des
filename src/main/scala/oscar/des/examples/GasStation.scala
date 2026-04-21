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

/**
 * A gas station with a bulk fuel tank. Tanker trucks periodically refill it
 * (Tank.put), cars arrive and draw fuel from it (Tank.get). When the tank is
 * low, cars queue; when a delivery arrives, queued cars are served FIFO.
 *
 * Demonstrates the multi-unit get/put semantics of Tank.
 */
class Tanker(m: Model, tank: Tank, seed: Long) {
  private val rand = new scala.util.Random(seed)

  def deliver(): Unit = {
    val qty = 100.0 + rand.nextInt(51)  // 100..150
    println(f"t=${m.clock()}%6.1f  tanker requests delivery of $qty%.0f gal  (level=${tank.level}%.0f)")
    tank.put(qty) {
      println(f"t=${m.clock()}%6.1f  tanker delivered         $qty%.0f gal  (level=${tank.level}%.0f)")
      m.wait(30.0 + rand.nextInt(21)) { deliver() }
    }
  }

  def run(): Unit = deliver()
}

class Car(m: Model, name: String, tank: Tank, rand: scala.util.Random) {
  def arrive(): Unit = {
    val qty = 10.0 + rand.nextInt(11)  // 10..20
    println(f"t=${m.clock()}%6.1f  $name%-6s requests  $qty%2.0f gal  (level=${tank.level}%.0f)")
    tank.get(qty) {
      println(f"t=${m.clock()}%6.1f  $name%-6s served    $qty%2.0f gal  (level=${tank.level}%.0f)")
    }
  }
}

object GasStation {
  def main(args: Array[String]): Unit = {
    val mod  = new Model()
    val tank = new Tank(mod, capacity = 200.0)
    val rand = new scala.util.Random(7)

    new Tanker(mod, tank, seed = 42L).run()

    var carId = 0
    def scheduleNextCar(): Unit = {
      mod.wait(3.0 + rand.nextInt(5)) {
        carId += 1
        new Car(mod, s"car$carId", tank, rand).arrive()
        scheduleNextCar()
      }
    }
    scheduleNextCar()

    mod.simulate(200, verbose = false)
  }
}
