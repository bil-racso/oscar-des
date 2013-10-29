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
package oscar.cp.core

/**
 * @author Andrew Lambert andrew.lambert@student.uclouvain.be
 */

/**
 * Build a graph CPVar
 * @param 	nNodes : number of nodes of the maximal graph, nodes are indexes as : [0,1,...,nNodes-1]
 * 			inputEdges : list of tuple/pair (source, destination) representing the edges of the maximal graph
 *               if an tuple has values out of range (e.g. node value bigger than nNodes-1), this edge is ignored
 */
class CPVarGraph(val s: CPStore, nNodes: Int, inputEdges: List[(Int,Int)], val name: String = "") extends CPVar {
  
  def store = s 
  
  // check if the edge is in range of nodes, otherwise ignore it
  val r = 0 to nNodes
  val correctInputEdges : List[(Int,Int)] = inputEdges.filter(x => (r.contains(x._1)) && (r.contains(x._2)))
  val nEdges = correctInputEdges.length
  
  // N and E will hold current graph interval
  private val N = new CPVarSet(store,0,nNodes-1)
  private val E = new CPVarSet(store,0,nEdges-1)
  
  // define some useful inner class
  // Edge() take 3 param to have immutable src and destination
  class Edge(idx : Int, source : Int, destination : Int){
    val index = idx
    val src = source
    val dest= destination
  }
  class Node(idx : Int){
    val index = idx
    var inEdges  : List[Int] = Nil
    var outEdges : List[Int] = Nil
  }
  
  // the following structures will be used to access easily to the hidden graph
  val nodes : Array[Node] = Array.tabulate(nNodes)(i => new Node(i))
  val edges : Array[Edge] = Array.tabulate(nEdges)(i => new Edge(i,correctInputEdges(i)._1,correctInputEdges(i)._2 ) )
  // fill array nodes with inEdges and outEdges
  for(i <- 0 to (nEdges-1)){
    val e = edges(i)
    nodes(e.src ).outEdges = e.index :: nodes(e.src ).outEdges
    nodes(e.dest).inEdges  = e.index :: nodes(e.dest).inEdges
  }
  
   /**
   * @param node Id
   * @return Return a list with the index of all required outgoing edges from the node
   */
  def requiredOutEdges(nodeId: Int) : List[Int] = nodes(nodeId).outEdges.filter( E isRequired _ )
  
  /**
   * @param node Id
   * @return Return a list with the index of all required incoming edges from the node
   */
  def requiredInEdges(nodeId: Int) : List[Int] = nodes(nodeId).inEdges.filter( E isRequired _ )

  /**
   * @param node Id
   * @return Return a list with the index of all required edges from the node
   */
  def requiredEdges(nodeId: Int) : List[Int] = requiredInEdges(nodeId) ++ requiredOutEdges(nodeId)
  
  /**
   * @param node Id
   * @return Return a list with the index of all possible outgoing edges from the node
   */
  def possibleOutEdges(nodeId: Int) : List[Int] = nodes(nodeId).outEdges.filter( E isPossible _ )
  
  /**
   * @param node Id
   * @return Return a list with the index of all possible incoming edges from the node
   */
  def possibleInEdges(nodeId: Int) : List[Int] = nodes(nodeId).inEdges.filter( E isPossible _ )

  /**
   * @param node Id
   * @return Return a list with the index of all possible edges from the node
   */
  def possibleEdges(nodeId: Int) : List[Int] = possibleInEdges(nodeId) ++ possibleOutEdges(nodeId)
  
  /**
   * @return Return a list with the index of all required nodes
   */
  def requiredNodes() : List[Int] = N.requiredSet.toList
  
  /**
   * @return Return a list with the index of all possible nodes
   */
  def possibleNodes() : List[Int] = N.possibleSet.toList
  
  /**
   * Add a node into required nodes from graph interval
   * @param node Id
   */
  def addNode(nodeId: Int) : CPOutcome = N.requires(nodeId)
  
  /**
   * Remove a node from possible nodes from graph interval and connected edges to this node
   * @param node Id
   */
  def removeNode(nodeId: Int) : CPOutcome = {
    if (N.isRequired(nodeId)) CPOutcome.Failure
    else {
      if (N.isPossible(nodeId)){
        N.excludes(nodeId)
        for (e <- possibleEdges(nodeId)) E.excludes(e)
      }
      CPOutcome.Suspend
    }
  }
  
  /**
   * Add an edge into required edges from graph interval, also add in required nodes connected nodes to that edge
   * @param source and destination of the edge
   */
  def addEdge(src: Int, dest: Int) : CPOutcome = {
    val index = indexOfEdge(src, dest)
    if (! E.isPossible(index)) CPOutcome.Failure
    else {
      E.requires(index)
      N.requires(edges(index).src)
      N.requires(edges(index).dest)
      CPOutcome.Suspend
    }
  }
  
  /**
   * Remove an edge from possible edges from graph interval
   * @param source and destination of the edge
   */
  def removeEdge(src: Int, dest: Int) : CPOutcome = E.excludes(indexOfEdge(src, dest)) 
  
  /**
   * @return the index of the edge (src,dest)
   * 			-1 if the edge is not in the graph interval
   */
  private def indexOfEdge(src: Int, dest: Int) : Int = {
    val edge = edges.filter(e => (e.src == src && e.dest == dest))
    if (edge.isEmpty) -1
    else edge(0).index
  }
  
}

