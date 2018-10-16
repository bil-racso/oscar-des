/**
  * *****************************************************************************
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
  * ****************************************************************************
  */

package oscar.cbls.business.routing.neighborhood.vlsn

import oscar.cbls.Objective
import oscar.cbls.core.search.{Neighborhood, _}

abstract sealed class CachedExploration
case class CachedAtomicMove(move:Move,delta:Int) extends CachedExploration
case object CachedAtomicNoMove extends CachedExploration
case object CacheDirty extends CachedExploration

object CachedAtomicMove{
  def apply(edge:Edge) = new CachedAtomicMove(edge.move,edge.deltaObj)
}

import oscar.cbls.business.routing.neighborhood.vlsn.VLSNMoveType._

import scala.collection.immutable.{SortedMap, SortedSet}


object CachedExplorations{
  def apply(oldGraph:VLSNGraph,
            performedMoves:List[Edge],
            v:Int):Option[CachedExplorations] = {

    var dirtyNodes: SortedSet[Int] = SortedSet.empty
    val isDirtyVehicle = Array.fill[Boolean](v)(false)

    for (edge: Edge <- performedMoves) {
      val fromNode = edge.from
      val toNode = edge.to

      edge.moveType match {
        case InsertNoEject =>
          dirtyNodes += fromNode.representedNode
          isDirtyVehicle(toNode.vehicle) = true
        case InsertWithEject =>
          dirtyNodes += fromNode.representedNode
          isDirtyVehicle(toNode.vehicle) = true
        case MoveNoEject =>
          isDirtyVehicle(fromNode.vehicle) = true
          isDirtyVehicle(toNode.vehicle) = true
        case MoveWithEject =>
          isDirtyVehicle(fromNode.vehicle) = true
          isDirtyVehicle(toNode.vehicle) = true
        case Remove =>
          isDirtyVehicle(fromNode.vehicle) = true
          dirtyNodes += fromNode.representedNode
        case _ => ;
      }
    }

    //println("isDirtyVehicle:" + isDirtyVehicle.indices.map(vehicle => "v_"+vehicle+":"+isDirtyVehicle(vehicle)).mkString(","))
    //println("dirtyNodes:" + dirtyNodes.mkString(","))
    //println(oldGraph.statistics)

    if(isDirtyVehicle.forall(p => p)) None
    else Some(new CachedExplorations(oldGraph: VLSNGraph,
      dirtyNodes:SortedSet[Int],
      isDirtyVehicle: Array[Boolean],
      v: Int))
  }
}

class CachedExplorations(oldGraph:VLSNGraph,
                         dirtyNodes:SortedSet[Int], //only for unrouted nodes that were inserted of newly removed
                         isDirtyVehicle:Array[Boolean],
                         v:Int) {

  def isDirtyNode(node: Int): Boolean = dirtyNodes.contains(node)

  //TODO: use arrays for O(1) access?
  var cachedInsertNoEject: SortedMap[(Int, Int), CachedAtomicMove] = SortedMap.empty //unroute,targetVehicle
  var cachedInsertWithEject: SortedMap[(Int, Int), CachedAtomicMove] = SortedMap.empty //
  var cachedMoveNoEject: SortedMap[(Int, Int), CachedAtomicMove] = SortedMap.empty //node,vehicle
  var cachedMoveWithEject: SortedMap[(Int, Int), CachedAtomicMove] = SortedMap.empty
  var cachedRemove: SortedMap[(Int), CachedAtomicMove] = SortedMap.empty

  var size = 0
  var dirtyEdge = 0

  //TODO: on peut en fait sauver beaucoup plus
  //pour les moves avec eject: on peut sauver les moves si le target vehicle est clean et que le source node est clean (on doit pas regarder le vehicle source)
  //idem pour les move no eject

  for (edge <- oldGraph.edges){

    val fromNode = edge.from
    val toNode = edge.to

    edge.moveType match {
      case InsertNoEject =>
        if (!isDirtyNode(fromNode.representedNode) && !isDirtyVehicle(toNode.vehicle)) {
          cachedInsertNoEject += (fromNode.representedNode, toNode.vehicle) -> CachedAtomicMove(edge)
          size += 1
        }else{
          dirtyEdge += 1
        }
      case InsertWithEject =>
        if (!isDirtyNode(fromNode.representedNode) && !isDirtyVehicle(toNode.vehicle)) {
          cachedInsertWithEject += (fromNode.representedNode, toNode.representedNode) -> CachedAtomicMove(edge)
          size += 1
        }else{
          dirtyEdge += 1
        }
      case MoveNoEject =>
        if(!isDirtyNode(fromNode.representedNode) && !isDirtyVehicle(toNode.vehicle)) {
          cachedMoveNoEject += (fromNode.representedNode, toNode.vehicle) -> CachedAtomicMove(edge)
          size += 1
        }else{
          dirtyEdge += 1
        }
      case MoveWithEject =>
        if (!isDirtyNode(fromNode.representedNode) && !isDirtyVehicle(toNode.vehicle)) {
          cachedMoveWithEject += (fromNode.representedNode, toNode.representedNode) -> CachedAtomicMove(edge)
          size += 1
        }else{
          dirtyEdge += 1
        }
      case Remove =>
        if(!isDirtyVehicle(fromNode.vehicle)) {
          cachedRemove += fromNode.representedNode -> CachedAtomicMove(edge)
          size += 1
        }else{
          dirtyEdge += 1
        }
      case _ => ; // non cachable
    }
  }

  //println("cacheSize:" + size)
  //println("dirtyEdges:" + dirtyEdge)

  def getInsertOnVehicleNoRemove(unroutedNodeToInsert: Int,
                                 targetVehicleForInsertion: Int): CachedExploration = {
    if (!isDirtyNode(unroutedNodeToInsert) && !isDirtyVehicle(targetVehicleForInsertion)) {
      cachedInsertNoEject.getOrElse((unroutedNodeToInsert, targetVehicleForInsertion), CachedAtomicNoMove)
    } else {
      CacheDirty
    }
  }

  def getInsertOnVehicleWithRemove(unroutedNodeToInsert: Int,
                                   targetVehicleForInsertion: Int,
                                   removedNode: Int): CachedExploration = {
    if (!isDirtyNode(unroutedNodeToInsert) && !isDirtyVehicle(targetVehicleForInsertion)) {
      cachedInsertWithEject.getOrElse((unroutedNodeToInsert, removedNode), CachedAtomicNoMove)
    } else {
      CacheDirty
    }
  }

  def getMoveToVehicleNoRemove(routingNodeToMove: Int, fromVehicle: Int, targetVehicle: Int): CachedExploration = {
    if (!isDirtyNode(routingNodeToMove) && !isDirtyVehicle(targetVehicle)) {
      cachedMoveNoEject.getOrElse((routingNodeToMove, targetVehicle), CachedAtomicNoMove)
    } else {
      CacheDirty
    }
  }

  def getMoveToVehicleWithRemove(routingNodeToMove: Int, fromVehicle: Int, targetVehicle: Int, removedNode: Int): CachedExploration = {
    if (!isDirtyNode(routingNodeToMove) && !isDirtyVehicle(targetVehicle)) {
      cachedMoveWithEject.getOrElse((routingNodeToMove, removedNode), CachedAtomicNoMove)
    } else {
      CacheDirty
    }
  }

  def getRemoveNode(removedNode: Int, fromVehicle: Int): CachedExploration = {
    if (!isDirtyVehicle(fromVehicle)) {
      cachedRemove.getOrElse(removedNode, CachedAtomicNoMove)
    } else {
      CacheDirty
    }
  }
}

class IncrementalMoveExplorerAlgo(v:Int,
                                  vehicleToRoutedNodes:SortedMap[Int,Iterable[Int]],
                                  unroutedNodesToInsert:Iterable[Int],
                                  nodeToRelevantVehicles:Map[Int,Iterable[Int]],

                                  targetVehicleNodeToInsertNeighborhood:Int => Int => Neighborhood,
                                  targetVehicleNodeToMoveNeighborhood:Int => Int => Neighborhood,
                                  nodeToRemoveNeighborhood:Int => Neighborhood,

                                  removeAndReInsert:Int => () => Unit,
                                  useDirectInsert:Boolean,

                                  vehicleToObjectives:Array[Objective],
                                  unroutedNodesPenalty:Objective,
                                  globalObjective:Objective,

                                  cached:CachedExplorations
                                 )
  extends MoveExplorerAlgo(v:Int,
    vehicleToRoutedNodes:SortedMap[Int,Iterable[Int]],
    unroutedNodesToInsert:Iterable[Int],
    nodeToRelevantVehicles:Map[Int,Iterable[Int]],

    targetVehicleNodeToInsertNeighborhood:Int => Int => Neighborhood,
    targetVehicleNodeToMoveNeighborhood:Int => Int => Neighborhood,
    nodeToRemoveNeighborhood:Int => Neighborhood,

    removeAndReInsert:Int => () => Unit,
    useDirectInsert,

    vehicleToObjectives:Array[Objective],
    unroutedNodesPenalty:Objective,
    globalObjective:Objective){


  override def evaluateInsertOnVehicleNoRemove(unroutedNodeToInsert: Int, targetVehicleForInsertion: Int, nCached:Boolean): (Move, Int) = {
    cached.getInsertOnVehicleNoRemove(unroutedNodeToInsert,targetVehicleForInsertion) match{
      case CachedAtomicMove(move:Move,delta:Int) =>
        assert(super.evaluateInsertOnVehicleNoRemove(unroutedNodeToInsert, targetVehicleForInsertion,false)._2 == delta)
        (move,delta)
      case CachedAtomicNoMove =>
        assert(super.evaluateInsertOnVehicleNoRemove(unroutedNodeToInsert, targetVehicleForInsertion,false) == null)
        null
      case CacheDirty =>
        super.evaluateInsertOnVehicleNoRemove(unroutedNodeToInsert, targetVehicleForInsertion,true)
    }
  }

  override def evaluateInsertOnVehicleWithRemove(unroutedNodeToInsert: Int, targetVehicleForInsertion: Int, removedNode: Int, correctedGlobalInit: Int,nCached:Boolean): (Move, Int) = {
    cached.getInsertOnVehicleWithRemove(unroutedNodeToInsert,targetVehicleForInsertion,removedNode) match {
      case CachedAtomicMove(move: Move, delta: Int) =>
        assert(super.evaluateInsertOnVehicleWithRemove(unroutedNodeToInsert, targetVehicleForInsertion, removedNode, correctedGlobalInit,false)._2 == delta)
        (move, delta)
      case CachedAtomicNoMove =>
        assert(super.evaluateInsertOnVehicleWithRemove(unroutedNodeToInsert, targetVehicleForInsertion, removedNode, correctedGlobalInit,false) == null)
        null
      case CacheDirty =>
        super.evaluateInsertOnVehicleWithRemove(unroutedNodeToInsert, targetVehicleForInsertion, removedNode, correctedGlobalInit,true)
    }
  }

  override def evaluateMoveToVehicleNoRemove(routingNodeToMove: Int, fromVehicle:Int, targetVehicle: Int,nCached:Boolean): (Move, Int) = {
    cached.getMoveToVehicleNoRemove(routingNodeToMove: Int, fromVehicle: Int, targetVehicle: Int) match{
      case CachedAtomicMove(move: Move, delta: Int) =>
        assert(super.evaluateMoveToVehicleNoRemove(routingNodeToMove: Int, fromVehicle:Int, targetVehicle: Int,false)._2 == delta)
        (move, delta)
      case CachedAtomicNoMove =>
        assert(super.evaluateMoveToVehicleNoRemove(routingNodeToMove: Int, fromVehicle:Int, targetVehicle: Int,false) == null,
          s"evaluateMoveToVehicleNoRemove(routingNodeToMove:$routingNodeToMove, fromVehicle:$fromVehicle, targetVehicle:$targetVehicle) super:" +
            super.evaluateMoveToVehicleNoRemove(routingNodeToMove: Int, fromVehicle:Int, targetVehicle: Int,false))
        null
      case CacheDirty =>
        super.evaluateMoveToVehicleNoRemove(routingNodeToMove: Int, fromVehicle:Int, targetVehicle: Int,true)
    }
  }

  override def evaluateMoveToVehicleWithRemove(routingNodeToMove: Int, fromVehicle: Int, targetVehicleID: Int, removedNode: Int,nCached:Boolean): (Move, Int) = {

    cached.getMoveToVehicleWithRemove(routingNodeToMove, fromVehicle, targetVehicleID, removedNode) match{
      case CachedAtomicMove(move: Move, delta: Int) =>
        assert(super.evaluateMoveToVehicleWithRemove(routingNodeToMove, fromVehicle, targetVehicleID, removedNode,false)._2 == delta)
        (move, delta)
      case CachedAtomicNoMove =>
        assert(super.evaluateMoveToVehicleWithRemove(routingNodeToMove, fromVehicle, targetVehicleID, removedNode,false) == null,
          s"evaluateMoveToVehicleWithRemove(routingNodeToMove:$routingNodeToMove, fromVehicle:$fromVehicle, targetVehicleID:$targetVehicleID, removedNode:$removedNode)")
        null
      case CacheDirty =>
        super.evaluateMoveToVehicleWithRemove(routingNodeToMove, fromVehicle, targetVehicleID, removedNode,true)
    }
  }

  override def evaluateRemove(routingNodeToRemove: Int, fromVehicle: Int): (Move, Int) = {
    cached.getRemoveNode(routingNodeToRemove,fromVehicle) match{
      case CachedAtomicMove(move: Move, delta: Int) =>
        assert(super.evaluateRemove(routingNodeToRemove, fromVehicle)._2 == delta)
        (move, delta)
      case CachedAtomicNoMove =>
        assert(super.evaluateRemove(routingNodeToRemove, fromVehicle) == null)
        null
      case CacheDirty =>
        super.evaluateRemove(routingNodeToRemove, fromVehicle)
    }
  }
}