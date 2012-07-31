/*******************************************************************************
 * This file is part of OscaR (Scala in OR).
 *  
 * OscaR is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 * 
 * OscaR is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OscaR.
 * If not, see http://www.gnu.org/licenses/gpl-3.0.html
 ******************************************************************************/

package oscar.examples.cp.scheduling

import oscar.cp.constraints._
import oscar.cp.modeling._
import oscar.cp.core._
import oscar.cp.scheduling._
import oscar.reversible.ReversibleSetIndexedArray
import oscar.reversible.ReversibleInt
import oscar.search._
import oscar.visual._

import scala.io.Source

object CumulativeJobShop {
  
	def main(args: Array[String]) {
	  
		// Parsing		
		// -----------------------------------------------------------------------
		
		var lines = Source.fromFile("data/cJobShop.txt").getLines.toList
		
		val nJobs        = lines.head.trim().split(" ")(0).toInt 
		val nTasksPerJob = lines.head.trim().split(" ")(1).toInt
		val nResources   = lines.head.trim().split(" ")(2).toInt
		val capacity     = lines.head.trim().split(" ")(3).toInt
		
		val nActivities  = nJobs*nTasksPerJob
		
		val Activities = 0 until nActivities
		val Jobs       = 0 until nJobs
		val Resources  = 0 until nResources
		
		println("#Jobs       : " + nJobs)
		println("#Activities : " + nActivities)
		println("#Resources  : " + nResources)
		println("Capacity    : " + capacity)
		
		lines = lines.drop(1)
		
		val jobs      = new Array[Int](nActivities)
		val machines  = new Array[Int](nActivities)
		val durations = new Array[Int](nActivities)
		
		for (i <- Activities) {
			
			val l = lines.head.trim().split("[ ,\t]+").map(_.toInt).toArray
			
			jobs(i)      = l(0)
			machines(i)  = l(1)
			durations(i) = l(2)
			
	  	    lines = lines.drop(1)
		}
		
		
		// Modeling	
		// -----------------------------------------------------------------------
  	   	
		val horizon = durations.sum
  	   	val cp = new CPScheduler
  	   	
  	   	// Activities
  	    val activities = Array.tabulate(nActivities)(i => new Activity(CPVarInt(cp, 0 to horizon - durations(i)), durations(i)))
  	   	 	
  	   	// Resources
  	   	val resources : Array[CumulativeResource] = Array.tabulate(nResources)(m => CumulativeResource(cp, 2))
	   	
  	   	// Resource allocation
  	   	for (i <- 0 until activities.size) 
  	   		activities(i).needs(resources(machines(i)), 1)
  	   		
  	   	// The make span to minimize
  	   	val makespan = maximum(0 until nActivities)(i => activities(i).end)
  	   	
		
  	   	cp.minimize(makespan) subjectTo {
			
			// Precedence constraints
			for (i <- 0 until nActivities-1; if (jobs(i) == jobs(i+1)))
				cp.add(activities(i) << activities(i+1))
				
			// Posting the resources
			for (i <- Resources) 
				resources(i).setup()
				
		} exploration {
			
			// Test heuristic
			cp.binaryFirstFail(activities.map(_.start))
			
			// Efficient but not complete search strategy
			//cp.setTimesSearch(activities)
		}    
		
		cp.printStats() 
		for(i <- 0 until activities.size)
			println(activities(i))
	}
}
