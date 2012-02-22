/*******************************************************************************
 * This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v3
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *  
 * Contributors:
 *      www.n-side.com
 ******************************************************************************/

package scampi.des.engine

import scampi.invariants._
import scala.util.continuations._

class SimQueue {

  val isOpen = new Var[Boolean](false)
  val serve = new EventOne[Unit]  
  val isBusy = new Var[Boolean](false)
  val isEmpty = new Var[Boolean](true)
  
  def enter: Boolean@suspendable = {
    if ( ! isOpen() ) false
    else {
      isEmpty := false
      if ( isBusy() )
        waitFor(serve) 
      else
        cpsunit
      isBusy := true
      true
    }
  }
  
  def leave(){
    
    if ( serve.hanging > 0 ){
      serve emit()
    }else{
      isEmpty := true
      isBusy := false
    }
    
  }
  def close(){isOpen := false}
  def open(){isOpen := true}
  
}