package oscar.cbls.algo.search

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

import scala.collection.Iterator
import scala.collection.immutable.SortedSet

/**
 * this proposes a set of methods to enable hot restart on iteration over an iterable.
 * it takes an Iterable[Long] and some pivot, and ensures that the iteration will explore
 * the values above the pivot first, in increasing order,
 * and the values below the pivot later, in increasing order as well.
*/
object HotRestart {

  /** this will return a shiftedIterable
    * the most efficient method will be automatically selected for Range and sorted sets
    * @param it
    * @param pivot
    * @return
    */
  def apply(it:Iterable[Long], pivot:Long):Iterable[Long] = {
    it match{
      case r:Range => if (r contains pivot) new InstrumentedRange(r) startBy pivot else r
      case s:SortedSet[Long] => new ShiftedSet(s,pivot)
      case _ => new ShiftedIterable(it, pivot)
    }
  }

  def hotRestartPreserveSequence(it:Iterable[Long],pivot:Long) = new ShiftedIterable(it,pivot,true)

  def apply(r:Range, pivot:Long):Iterable[Long] =  if (r contains pivot) new InstrumentedRange(r) startBy pivot else r

  def apply(s:SortedSet[Long], pivot:Long):Iterable[Long] =  new ShiftedSet(s,pivot)
}

class ShiftedIterable(it:Iterable[Long], pivot:Long, sequence:Boolean = false) extends Iterable[Long] {
  override def iterator: Iterator[Long] = {
    if(sequence) {
      //splitting into two parts:
      val aboveIterator = it.iterator
      def fetchHead: List[Long] = {
        if (aboveIterator.hasNext) {
          val nextValue = aboveIterator.next()
          if (nextValue == pivot) List(nextValue)
           else nextValue :: fetchHead
        }else Nil
      }
      val below = fetchHead
      new ShiftedIterator(aboveIterator.toList, below)
    }else {
      val (above, below) = it.partition(i => i > pivot)
      new ShiftedIterator(above, below)
    }
  }

  class ShiftedIterator(first:Iterable[Long], var second:Iterable[Long]) extends Iterator[Long]{
    //TODO: this is awful: maybe the stuff is already sorted
    //TODO: we should perform a lazy sort since all the first might not be covered anyway
    var it:Iterator[Long] = first.toList.toIterator
    override def hasNext: Boolean = {
      if(it.hasNext) true
      else if (second == null) false
      else{
        it = second.toList.toIterator
        second = null
        it.hasNext
      }
    }

    override def next(): Long = it.next()
  }
}

class InstrumentedRange(r:Range){
  def startBy (pivot:Long) = if (r contains pivot) new ShiftedRange(r.head, r.last,pivot, r.step) else r
}


/**
 * this is an inclusive range.
 * @param start
 * @param end
 * @param startBy
 * @param step
 */
class ShiftedRange(val start:Long, val end:Long, val startBy:Long, val step:Long = 1L) extends Iterable[Long]{
  if((start > startBy) || (startBy > end)) throw new Exception("ShiftedRange must contain startBy value ")
  if(step != 1L) throw new Exception("only step of 1L is supported in ShiftedRange")

  def getNextValue(a:Long) = {
    if(a == end) start
    else a+1L
  }

  override def iterator: Iterator[Long] = new ShiftedRangeIterator(this)

  override def toArray[B >: Long](implicit evidence$1L: scala.reflect.ClassTag[B]): Array[B] = toList.toArray

  override def toString(): String = "ShiftedRange(" + toList + ")"


  class ShiftedRangeIterator(val s:ShiftedRange) extends Iterator[Long]{
    var currentValue = s.startBy
    var hasNext = true

    def next(): Long = {
      val tmp = currentValue
      currentValue = s.getNextValue(currentValue)
      if(currentValue == s.startBy) hasNext = false
      tmp
    }
  }
}

class ShiftedSet(s:SortedSet[Long], pivot:Long) extends Iterable[Long] {
  override def iterator: Iterator[Long] = {
    new ShiftedIterator(s, pivot)
  }

  class ShiftedIterator(s:SortedSet[Long], pivot:Long) extends Iterator[Long]{
    var it:Iterator[Long] = s.iteratorFrom(pivot)
    var first=true
    var currentValue:Long = 0L
    var currentValueReady = false

    /** returns true if a next value is available
      *
      * @return
      */
    def internalMoveToNext():Boolean = {
      if(currentValueReady) return true
      if(first){
        if(it.hasNext) {
          currentValue = it.next()
          currentValueReady = true
          return true
        }else{
          //start the second iterator
          it = s.toIterator
          first = false
          //and continue the execution flow
        }
      }
      //second iterator
      if(!it.hasNext) return false
      currentValue = it.next()
      if(currentValue >= pivot) return false
      currentValueReady = true
      true
    }

    override def hasNext: Boolean = internalMoveToNext()

    override def next(): Long = {
      if(!internalMoveToNext()) throw new Error("no more elements to iterate")
      currentValueReady = false
      currentValue
    }
  }
}
