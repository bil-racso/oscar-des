package oscar.cbls.business.routing.model.helpers

import oscar.cbls.algo.search.Pairs
import oscar.cbls.business.routing._
import oscar.cbls.business.routing.model.TTFMatrix
import oscar.cbls.business.routing.model.extensions.TimeWindows

import scala.collection.immutable.HashSet

/**
  * Created by fg on 12/09/17.
  */
object TimeWindowHelper{

  /**
    * This method is used to precompute the relevant predecessors of all the nodes of the problem.
    * Using this you can filter a lot of useless predecessors.
    *
    * (call it only once ;) )
    *
    * A node x is a relevant predecessor of another node y if
    *   earliestArrivalTimes(x) +
    *   taskDurations(x) +
    *   timeMatrix.getTravelDuration(x, earliestArrivalTimes(x) + taskDurations(x), y) +
    *   taskDurations(y) <= latestLeavingTimes(y)
    * Meaning if we can start the task at node x, finish it, travel to y,
    * and finish the task at y before the latestLeavingTime of y, then x is a relevant neighbor of y.
    *
    * All these informations are used to define the problem, therefore they are static.
    * So we can use them to precompute the relevant predecessors.
    *
    * @param vrp The vehicle routing problem
    * @param timeExtension The timeExtension model
    * @param timeMatrix The time matrix
    * @param parallelizeNodes whether or not the nodes are allowed to be made in parallel.
    *                      If it's the case and the nodes are parallelisable (same place and with overlapping time windows),
    *
    * @return true if the node is relevant
    */
  def relevantPredecessorsOfNodes(vrp: VRP,
                                  timeExtension: TimeWindows,
                                  timeMatrix: TTFMatrix,
                                  parallelizeNodes: Boolean = true): Map[Int,HashSet[Int]] = {
    val earliestArrivalTimes = timeExtension.earliestArrivalTimes
    val latestLeavingTimes = timeExtension.latestLeavingTimes
    val taskDurations = timeExtension.taskDurations

    def areNodesParallelisable(predecessor: Int, node: Int): Boolean = {
      parallelizeNodes &&
      latestLeavingTimes(predecessor) > earliestArrivalTimes(node) &&
      timeMatrix.getTravelDuration(predecessor, earliestArrivalTimes(predecessor) + taskDurations(predecessor), node) == 0
    }

    Array.tabulate(vrp.n)(node => node -> HashSet(vrp.nodes.collect {
      case predecessor if areNodesParallelisable(predecessor,node) &&
          (Math.max(earliestArrivalTimes(predecessor) + taskDurations(predecessor),
            earliestArrivalTimes(node) + taskDurations(node)) <= latestLeavingTimes(node)) &&
          predecessor != node => predecessor
      case predecessor if !areNodesParallelisable(predecessor,node) &&
        (earliestArrivalTimes(predecessor) +
          taskDurations(predecessor) +
          timeMatrix.getTravelDuration(predecessor, earliestArrivalTimes(predecessor) + taskDurations(predecessor), node) +
          taskDurations(node)) <= latestLeavingTimes(node) &&
        predecessor != node => predecessor
    }: _*)).toMap
  }


  /**
    * This method is meant to precompute the relevant successors of all node.
    *
    * A node z is an relevant successor of another node y if
    *   earliestArrivalTimes(y) +
    *   taskDurations(y) +
    *   timeMatrix.getTravelDuration(y, earliestArrivalTimes(y) + taskDurations(y), z) +
    *   taskDurations(z) <= latestLeavingTimes(z)
    *
    * e.g : You have a list of relevant predecessor for a node y but inserting this node y
    * could delayed the arrival time to the current next node of the relevant predecessor.
    *
    *
    * Meaning if we can start the task at node y, finish it, travel to z,
    * and finish the task at z before the latestLeavingTimes of z, then x is an open relevant neighbor of y.
    *
    * All these informations are used to define the problem, therefore they are static.
    * The only information that's not static is the current next of the relevant predecessor we want to insert after.
    * But we can precompute all the relevant successor of the node we want to insert
    *
    * @param vrp The vehicle routing problem
    * @param timeExtension The timeExtension model
    * @return An array of precomputed
    */
  def relevantSuccessorsOfNodes(vrp: VRP,
                                timeExtension: TimeWindows,
                                timeMatrix: TTFMatrix): Map[Int,HashSet[Int]] = {
    val earliestArrivalTimes = timeExtension.earliestArrivalTimes
    val latestLeavingTimes = timeExtension.latestLeavingTimes
    val taskDurations = timeExtension.taskDurations

    Array.tabulate(vrp.n)(node => node -> HashSet(vrp.nodes.collect {
      case successor if
        latestLeavingTimes(successor)
        >= (earliestArrivalTimes(node) +
          taskDurations(node) +
          timeMatrix.getTravelDuration(node, earliestArrivalTimes(node) + taskDurations(node), successor) +
          taskDurations(successor)) &&
        successor != node
        => successor
    }: _*)).toMap
  }


  /**
    * This method is used to restraint the time window of the nodes (earliestArrivalTimes and latestLeavingTimes)
    * This restriction is based on the maxTravelDurations values.
    *
    * NOTE : Use it before the instantiation of the time constraint
    *
    * @param vrp The vehicle routing problem
    * @param maxTravelDurations A map (from,to) -> value representing the max travel duration from from to to
    * @param earliestArrivalTimes The earliestArrivalTime of all nodes (meaning, we can't start the task at node x before (earliestArrivalTimes(x))
    * @param latestLeavingTimes The latestLeavingTime of all nodes (meaning, the task at node x must be finished before (earliestArrivalTimes(x))
    * @param taskDurations The task duration of all nodes
    */
  def reduceTimeWindows(vrp: VRP,
                        travelTimeFunction: TTFMatrix,
                        maxTravelDurations: Map[List[Int],Int],
                        earliestArrivalTimes: Array[Int],
                        latestLeavingTimes: Array[Int],
                        taskDurations: Array[Int]): Unit ={
    if(maxTravelDurations.nonEmpty){
      val keys = maxTravelDurations.keys.toList
      val maxTravelDurationStartingAt = maxTravelDurations.map(md => md._1.head -> (md._1, md._2))

      /**
        * We compute the starting node of each sequence of maxTravelDurations.
        *
        * e.g.: Map((0,1) -> 20, (1,2) -> 40, (1,3) -> 30)     => the starting node is 0
        * @return A list of starting nodes
        */
      def startingNodes(): List[Int] = {
        val origins = keys.map(_.head).distinct
        val destinations = keys.map(_.last).distinct

        val startingNodes = vrp.nodes.collect {
          case node if origins.contains(node) && !destinations.contains(node) => node
        }
        require(startingNodes.nonEmpty, "No starting nodes in your maxDetours couples. You may have introduce some cycle.")
        startingNodes.toList
      }

      for (startNode <- startingNodes()) {
        var from = startNode
        while (maxTravelDurationStartingAt.get(from).isDefined) {
          val to = maxTravelDurationStartingAt(from)._1.last
          val value = maxTravelDurationStartingAt(from)._2
          val impactedNodes = maxTravelDurationStartingAt(from)._1.toArray
          val minTravelTimeFromFromToTo: Int =
            (for(i <- 1 until impactedNodes.length)
              yield taskDurations(impactedNodes(i-1)) +
                travelTimeFunction.getTravelDuration(impactedNodes(i-1),earliestArrivalTimes(impactedNodes(i-1)),impactedNodes(i))
              ).sum - taskDurations(impactedNodes(0))

          latestLeavingTimes(to) = Math.min(latestLeavingTimes(to), latestLeavingTimes(from) + value + taskDurations(to))
          earliestArrivalTimes(to) = Math.max(earliestArrivalTimes(to), earliestArrivalTimes(from) + taskDurations(from) + minTravelTimeFromFromToTo)

          val chainForward = maxTravelDurationStartingAt(from)._1.toArray
          val chainBackward = maxTravelDurationStartingAt(from)._1.toArray.reverse
          for(i <- 1 until chainForward.length) {
            val fromNode = chainForward(i-1)
            val toNode = chainForward(i)
            earliestArrivalTimes(toNode) =
              Math.max(earliestArrivalTimes(toNode), earliestArrivalTimes(fromNode) +
                taskDurations(fromNode) +
                travelTimeFunction.getTravelDuration(fromNode, earliestArrivalTimes(fromNode), toNode))
          }
          for(i <- 1 until chainBackward.length-1) {
            val toNode = chainBackward(i-1)
            val fromNode = chainBackward(i)
            latestLeavingTimes(fromNode) =
              Math.min(latestLeavingTimes(fromNode), latestLeavingTimes(toNode) -
                taskDurations(toNode) -
                travelTimeFunction.getTravelDuration(fromNode, earliestArrivalTimes(fromNode), toNode))
          }

          //TODO find a better way to compute travel time backward
          from = to
        }
      }
    }
  }
}
