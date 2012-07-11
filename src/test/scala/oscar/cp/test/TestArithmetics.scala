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

package oscar.cp.test

import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

import oscar.cp.constraints._
import oscar.cp.core._
import oscar.cp.search._
import oscar.cp.modeling._

import org.scalacheck._

class TestArithmetics extends FunSuite with ShouldMatchers with CPModel {


  test("Arithmetics") {
    val cp = CPSolver()
    val i = CPVarInt(cp,1)
    val j = CPVarInt(cp,0)
    val a = CPVarInt(cp,-1 to 1)
    val b = CPVarInt(cp,0 to 1)
    val n = 8
    
    val ia = i+a
    val jb = j+b
    
        
    cp.add(ia >= 0)
    cp.add(ia <  n)
    cp.add(jb >= 0)
    cp.add(jb <  n)

    println(ia)
    println(jb)
    val ix2 = (ia)*n + (jb) // what is the index of k+1
    
    println(ix2)

    ix2.getSize() should be(18) // should contain: 0,1,8,16,17


  } 
  
  test("cripta") {
    val cp = CPSolver()
    
    val x = Array.fill(10)(CPVarInt(cp, 0 to 9))
    val Array(a,b,c,d,e,f,g,h,i,j) = x
    
    cp.add(a + j*10 + j*100 + i*1000 + a*10000 + b*100000 + 
           b + a*10 + g*100 + f*1000 + h*10000 + d*100000 == 705713)
    
    val sol = Array(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
    for (i <- 0 until sol.size) {
        cp.add(x(i) == sol(i))        	
    }
    cp.getStatus() should be(CPOutcome.Suspend)

  }   

  
  

}
