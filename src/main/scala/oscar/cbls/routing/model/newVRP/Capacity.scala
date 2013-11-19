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
/*******************************************************************************
  * Contributors:
  *     This code has been initially developed by De Landtsheer Renaud
  ******************************************************************************/

package oscar.cbls.routing.model.newVRP

import oscar.cbls.invariants.core.computation.IntVar
import oscar.cbls.invariants.lib.numeric.SumElements
import oscar.cbls.modeling.Algebra._
import oscar.cbls.invariants.lib.minmax.{Max2, Max}
import oscar.cbls.routing.model.newVRP.{NodesOfVehicle, VRP}

/** maintains a cost associated to each vehicle
  *the cost is the sum of the cost associated to each node
  * @param vrp
  * @param nodeCost
  */
class ComutativeCapacity(vrp:VRP with NodesOfVehicle, nodeCost:Array[IntVar]){
  val CostOfVehicle = Array.tabulate(vrp.V)(v => SumElements(nodeCost,vrp.NodesOfVehicle(v)).toIntVar)
}

abstract class StatefulCapacity(val vrp:VRP with Predecessors,
                                val CapacityOut:Array[IntVar] = Array.tabulate(vrp.N)(n => IntVar(vrp.m,name = "CapacityOutOf_" + n))){
  //the capacity when leaving the predecessor of the node
//  val CapacityOutPred:Array[IntVar] = Array.tabulate(vrp.N)(n => if (n < vrp.V) 0 else CapacityOut.element(vrp.preds(n)))
}

class TimeWindow(vrp:VRP with Predecessors with StrongConstraints,
                 earliestTime:Array[Int],
                 latestTime:Array[Int],
                 durationIn:Array[IntVar],
                 durationAfter:Array[IntVar]
                  )
  extends StatefulCapacity(vrp,Array.tabulate(vrp.N)(n => IntVar(vrp.m,name = "LeaveTimeAt_" + n))){

/*  for(n <- vrp.N){
    //earliestTime
    val ArrivalTime:IntVar = CapacityOutPred(n) + durationAfter(n)
    val NodeStart:IntVar =  if(earliestTime(n) != Int.MaxValue)
        Max2(ArrivalTime,earliestTime(n))
    else ArrivalTime
//    val NodeEnd = NodeStart +


    }

    //leaveTime = arrivalTime + duration

  //}
*/

}


/**
 * Maintains a integer weight on each node to help to form constraints (adding information).
 */
trait WeightedNode extends VRP {
  /**
   * the data structure array which maintains weights.
   */
  val weightNode : Array[IntVar] = Array.tabulate(N)(i => IntVar(m, Int.MinValue, Int.MaxValue, 0,
    "weight of node " + i))

  /**
   * It allows you to set the weight of a given point.
   * @param n the point.
   * @param w the weight.
   */
  def fixWeightNode(n:Int,w:Int) { weightNode(n) := w}

  /**
   * It allows you to set a specific weight for all points of the VRP.
   * @param w the weight.
   */
  def fixWeightNode(w:Int) {weightNode.foreach(p => p := w)}
}