package oscar.cbls.business.routing.model.helpers

import oscar.cbls._
import oscar.cbls.algo.search.KSmallest
import oscar.cbls.lib.invariant.numeric.Sum

import scala.collection.immutable.HashSet

/**
  * Created by fg on 14/09/17.
  */
object DistanceHelper{

  def totalDistance(constantRoutingDistance: Array[CBLSIntVar])= Sum(constantRoutingDistance)
  def distancePerVehicle(constantRoutingDistance: Array[CBLSIntVar]): Array[Int] = constantRoutingDistance.map(_.value)

  /**
    * FROM NODE TO NEIGHBORS
    *
    * This method returns a lazy sorted sequence of neighbors.
    * The neighbors are sorted considering the distance between a specified node and his neighbors.
    * We consider all neighbors in the array so you should filter them before calling this method.
    * @param distanceMatrix the distance matrix
    * @param node The node
    * @param neighbors An array of filtered neighbors
    */
  def computeClosestPathToNeighbor(distanceMatrix: Array[Array[Int]], neighbors: (Int) => Iterable[Int])(node:Int): Iterable[Int] ={
    KSmallest.lazySort(neighbors(node).toArray,
      neighbor => distanceMatrix(node)(neighbor)
    )
  }

  /**
    * FROM NEIGHBORS TO NODE
    *
    * This method returns a lazy sorted sequence of neighbors.
    * The neighbors are sorted considering the distance between a specified node and his neighbors
    * We consider all neighbors in the array so you should filter them before calling this method..
    * @param distanceMatrix the distance matrix
    * @param node The node
    * @param neighbors An array of filtered neighbors
    */
  def computeClosestPathFromNeighbor(distanceMatrix: Array[Array[Int]], neighbors: (Int) => Iterable[Int])(node: Int): Iterable[Int] ={
    KSmallest.lazySort(neighbors(node).toArray,
      neighbor => distanceMatrix(neighbor)(node)
    )
  }
}