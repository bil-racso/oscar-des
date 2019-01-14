package oscar.cbls.algo.seq

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

import oscar.cbls.algo.fun.{LinearTransform, PiecewiseLinearBijectionNaive, PiecewiseLinearFun, Pivot}
import oscar.cbls.algo.quick.{IterableQList, QList}
import oscar.cbls.algo.rb.{RedBlackTreeMap, RedBlackTreeMapExplorer}

import scala.collection.immutable.SortedSet
import scala.language.implicitConversions

object IntSequence{
  def apply(values:Iterable[Long]):IntSequence = {
    val valuesArray = values.toArray
    val forwardRedBlack = RedBlackTreeMap.makeFromSortedContinuousArray(values.toArray)
    val backwardRedBlack:RedBlackTreeMap[RedBlackTreeMap[Long]] = aggregatePosOnValToInternalPosFrom(valuesArray)

    new ConcreteIntSequence(
      forwardRedBlack,
      backwardRedBlack,
      PiecewiseLinearBijectionNaive.identity,
      valuesArray.length
    )
  }

  private def aggregatePosOnValToInternalPosFrom(values:Array[Long]):RedBlackTreeMap[RedBlackTreeMap[Long]] = {
    var valToPoses = RedBlackTreeMap.empty[RedBlackTreeMap[Long]]
    for(pos <- values.indices){
      val value = values(pos)
      val existingPos = valToPoses.getOrElse(value,RedBlackTreeMap.empty[Long])
      valToPoses = valToPoses.insert(value,existingPos.insert(pos,pos))
    }
    valToPoses
  }

  def empty():IntSequence = new ConcreteIntSequence(
    RedBlackTreeMap.empty[Long],
    RedBlackTreeMap.empty[RedBlackTreeMap[Long]],
    PiecewiseLinearBijectionNaive.identity,
    0L
  )

  implicit def toIterable(seq:IntSequence):IterableIntSequence = new IterableIntSequence(seq)
}

class IterableIntSequence(sequence:IntSequence) extends Iterable[Long]{
  override def iterator : Iterator[Long] = sequence.iterator

  override def head : Long = sequence.valueAtPosition(0L).head

  override def headOption : Option[Long] = sequence.valueAtPosition(0L)

  override def last : Long = sequence.valueAtPosition(sequence.size-1L).head

  override def lastOption : Option[Long] = sequence.valueAtPosition(sequence.size-1L)
}

class Token()
object Token{
  def apply():Token = new Token()
}

abstract class IntSequence(protected[cbls] val token: Token = Token()) {

  def size : Long

  def isEmpty : Boolean = size == 0L

  def nonEmpty:Boolean = !isEmpty

  def iterator : Iterator[Long] = new IntSequenceIterator(this.explorerAtPosition(0L))

  def iterateFromAnyOccurrenceOfValue(value:Long):Iterator[Long] = new IntSequenceIterator(this.explorerAtAnyOccurrence(value))

  def iterable : Iterable[Long] = new IterableIntSequence(this)

  def nbOccurrence(value:Long):Long

  def unorderedContentNoDuplicate : List[Long]

  def unorderedContentNoDuplicateWithNBOccurences : List[(Long,Long)]

  def valueAtPosition(position : Long) : Option[Long]

  final def positionsOfValue(value : Long) : Iterable[Long] = {
    new IterableQList[Long](positionsOfValueQ(value))
  }

  final def positionsOfValueSet(value : Long) : SortedSet[Long] = {
    SortedSet.empty[Long] ++ positionsOfValue(value)
  }

  def positionsOfValueQ(value : Long) : QList[Long]

  def contains(value : Long) : Boolean

  def explorerAtPosition(position : Long) : Option[IntSequenceExplorer]

  def map(fun:Long=>Long):IntSequence = {
    val l:List[Long] = this.iterator.toList
    val l2 = l.map(fun)
    IntSequence.apply(l2)
  }

  def valuesBetweenPositionsSet(fromPositionIncluded:Long,toPositionIncluded:Long):SortedSet[Long] = {
    var toReturn = SortedSet.empty[Long]
    var e = explorerAtPosition(fromPositionIncluded)
    while(e match{
      case None => false
      case Some(explorer) =>
        if (explorer.position <= toPositionIncluded){
          toReturn = toReturn + explorer.value
          e = explorer.next
          true
        }else false
    }){}
    toReturn
  }

  def valuesBetweenPositionsQList(fromPositionIncluded:Long,toPositionIncluded:Long):QList[Long] = {
    var toReturn:QList[Long] = null
    var e = explorerAtPosition(fromPositionIncluded)
    while(e match{
      case None => false
      case Some(explorer) =>
        if (explorer.position <= toPositionIncluded){
          toReturn = QList(explorer.value,toReturn)
          e = explorer.next
          true
        }else false
    }){}
    toReturn
  }

  //List[(position,value)]
  def positionsBetweenFromToAndTheirValues(fromPositionIncluded:Long,toPositionIncluded:Long):QList[(Long,Long)] = {
    var toReturn:QList[(Long,Long)] = null
    var e = explorerAtPosition(fromPositionIncluded)
    while(true){  //TODO: maybe this approach has less overhead than the "while" above?
      e match{
        case None => return toReturn
        case Some(explorer) =>
          if (explorer.position > toPositionIncluded) {
            return toReturn
          }
          toReturn = QList(((explorer.position,explorer.value)),toReturn)
          e = explorer.next
      }
    }
    return null
  }

  def explorerAtFirstOccurrence(value : Long) : Option[IntSequenceExplorer] = {
    positionOfFirstOccurrence(value : Long) match {
      case None => None
      case Some(x) => explorerAtPosition(x)
    }
  }

  def explorerAtLastOccurrence(value : Long) : Option[IntSequenceExplorer] = {
    positionOfLastOccurrence(value : Long) match {
      case None => None
      case Some(x) => explorerAtPosition(x)
    }
  }

  def explorerAtAnyOccurrence(value : Long) : Option[IntSequenceExplorer] = {
    positionOfAnyOccurrence(value) match {
      case None => None
      case Some(x) => explorerAtPosition(x)
    }
  }

  def positionOfFirstOccurrence(value : Long) : Option[Long] = {
    positionsOfValue(value) match {
      case null => None
      case x => Some(x.min)
    }
  }

  def positionOfLastOccurrence(value : Long) : Option[Long] = {
    positionsOfValue(value) match {
      case null => None
      case x => Some(x.max)
    }
  }

  def positionOfAnyOccurrence(value:Long):Option[Long] = {
    positionsOfValue(value) match {
      case null => None
      case x if x.isEmpty => None
      case x => Some(x.head)
    }
  }

  def insertAtPosition(value:Long, pos:Long, fast:Boolean = false, autoRework:Boolean = true):IntSequence
  def delete(pos:Long, fast:Boolean=false,autoRework:Boolean = false):IntSequence
  def moveAfter(startPositionIncluded:Long, endPositionIncluded:Long, moveAfterPosition:Long, flip:Boolean, fast:Boolean = false, autoRework:Boolean = true):IntSequence

  def flip(fast:Boolean = false, autoRework:Boolean = true):IntSequence =
    if(this.isEmpty) this
    else moveAfter(0L, this.size-1L, -1L, flip = true, fast, autoRework)

  def regularizeToMaxPivot(maxPivotPerValuePercent: Long, targetToken: Token = this.token) :ConcreteIntSequence

  def regularize(targetToken:Token = this.token):ConcreteIntSequence
  def commitPendingMoves:IntSequence

  def check{}

  def quickEquals(that:IntSequence):Boolean = that != null && this.token == that.token
  def equals(that:IntSequence):Boolean = {
    quickEquals(that) || (that != null && (this.toList equals that.toList))
  }

  override def toString : String = {
    "IntSequence(size:" + size + ")[" + this.iterator.toList.mkString(",") + "]_impl:" + descriptorString
  }

  def descriptorString : String

  def predecessorPos2Val(position:Long):Option[Long] = {
    valueAtPosition(position-1L)
  }

  def successorPos2Val(position:Long):Option[Long] = {
    valueAtPosition(position+1L)
  }
}


class ConcreteIntSequence(private[seq] val internalPositionToValue:RedBlackTreeMap[Long],
                          private[seq] val valueToInternalPositions:RedBlackTreeMap[RedBlackTreeMap[Long]],
                          private[seq] val externalToInternalPosition:PiecewiseLinearBijectionNaive,
                          private[seq] val startFreeRangeForInternalPosition:Long,
                          token:Token = Token()) extends IntSequence(token) {

  //TODO: replace internalPositionToValue by an immutable Array, or an immutable array + a small RBTree + size

  def bij = externalToInternalPosition
  override def descriptorString : String = "[" + this.iterator.toList.mkString(",") + "]_impl:concrete"

  override def toString : String = {
    "ConcreteIntSequence(size:" + size + ")" + descriptorString
  }

  override def check {
    externalToInternalPosition.checkBijection()
    require(internalPositionToValue.content.sortBy(_._1) equals valueToInternalPositions.content.flatMap({case (a, b) => b.keys.map(x => (x, a))}).sortBy(_._1),
      "internalPositionToValue:" + internalPositionToValue.content.sortBy(_._1) + " valueToInternalPositions:" + valueToInternalPositions.content.flatMap({case (a, b) => b.keys.map(x => (x, a))}).sortBy(_._1)
    )
  }

  override val size : Long = internalPositionToValue.size
  
  override def isEmpty : Boolean = internalPositionToValue.isEmpty

  override def nbOccurrence(value : Long) : Long = valueToInternalPositions.get(value) match {
    case None => 0L
    case Some(p) => p.size
  }

  def largestValue : Option[Long] = valueToInternalPositions.biggest match {
    case None => None
    case Some((k, _)) => Some(k)
  }

  def smallestValue : Option[Long] = valueToInternalPositions.smallest match {
    case None => None
    case Some((k, _)) => Some(k)
  }

  def contains(value : Long) : Boolean = valueToInternalPositions.contains(value)

  def valueAtPosition(position : Long) : Option[Long] = {
    val internalPosition : Long = externalToInternalPosition.forward(position)
    internalPositionToValue.get(internalPosition)
  }

  override def positionsOfValueQ(value : Long) : QList[Long] = {
    valueToInternalPositions.get(value) match {
      case None => null
      case Some(internalPositions) =>
        var toReturn:QList[Long] = null
        var toDigest:QList[Long] = internalPositions.qKeys
        while(toDigest != null){
          toReturn = QList(externalToInternalPosition.backward(toDigest.head),toReturn)
          toDigest = toDigest.tail
        }
        toReturn
    }
  }

  def explorerAtPosition(position : Long) : Option[IntSequenceExplorer] = {
    if (position >= this.size) None
    else {
      val currentPivotPosition = externalToInternalPosition.forward.pivotWithPositionApplyingTo(position)
      val (pivotAbovePosition : Option[RedBlackTreeMapExplorer[Pivot]], internalPosition) = currentPivotPosition match {
        case None => (externalToInternalPosition.forward.firstPivotAndPosition, position)
        case Some(p) => (p.next, p.value.f(position))
      }

      Some(new ConcreteIntSequenceExplorer(this,
        position,
        internalPositionToValue.positionOf(internalPosition).get,
        currentPivotPosition,
        pivotAbovePosition)()
      )
    }
  }

  private def internalInsertToValueToInternalPositions(value : Long, internalPosition : Long, valueToInternalPositions : RedBlackTreeMap[RedBlackTreeMap[Long]]) : RedBlackTreeMap[RedBlackTreeMap[Long]] = {
    valueToInternalPositions.get(value) match {
      case None => valueToInternalPositions.insert(value, RedBlackTreeMap(List((internalPosition, internalPosition))))
      case Some(l) => valueToInternalPositions.insert(value, l.insert(internalPosition, internalPosition))
    }
  }

  private def internalRemoveFromValueToInternalPositions(value : Long, internalPosition : Long,
                                                         valueToInternalPositions : RedBlackTreeMap[RedBlackTreeMap[Long]])
  : RedBlackTreeMap[RedBlackTreeMap[Long]] = {
    valueToInternalPositions.get(value) match {
      case None => valueToInternalPositions
      case Some(l) =>
        assert(l.contains(internalPosition))
        val newSet = l.remove(internalPosition)
        if (newSet.isEmpty) valueToInternalPositions.remove(value)
        else valueToInternalPositions.insert(value, newSet)
    }
  }

  def insertAtPosition(value : Long, pos : Long, fast : Boolean, autoRework : Boolean) : IntSequence = {

    //println(this + ".insertAtPosition(value:" + value + " pos:" + pos + ")")
    require(pos <= size, "inserting past the end of the sequence (size:" + size + " pos:" + pos + ")")

    if (fast) return new InsertedIntSequence(this, value, pos)

    //insert into red blacks
    val newInternalPositionToValue = internalPositionToValue.insert(startFreeRangeForInternalPosition, value)
    val newValueToInternalPosition = internalInsertToValueToInternalPositions(value, startFreeRangeForInternalPosition, valueToInternalPositions)

    //move sequence after position, one upward
    //move inserted point at its position
    val oldExternalPosRelatedToFreeInternalPos = externalToInternalPosition.backward(startFreeRangeForInternalPosition)

    val newExternalToInternalPosition = if (pos == size) {
      //inserting at end of the sequence
      externalToInternalPosition.updateBefore(
        (size, size, LinearTransform(oldExternalPosRelatedToFreeInternalPos - pos, false)))
      //TODO: this might be always identity, actually, so useless!
    } else {
      //inserting somewhere within the sequence, need to shift upper part

      val tmp = externalToInternalPosition.swapAdjacentZonesShiftFirst(pos,size-1L,size,false)

      assert(tmp.forward equals externalToInternalPosition.updateBefore(
        (pos + 1L, size, LinearTransform(-1L, false)),
        (pos, pos, LinearTransform(oldExternalPosRelatedToFreeInternalPos - pos, false))).forward)
      tmp
    }

    new ConcreteIntSequence(
      newInternalPositionToValue,
      newValueToInternalPosition,
      newExternalToInternalPosition,
      startFreeRangeForInternalPosition + 1L)
  }

  def delete(pos : Long, fast : Boolean, autoRework : Boolean) : IntSequence = {
    //println(this + ".delete(pos:" + pos + ")")
    require(pos < size, "deleting past the end of the sequence (size:" + size + " pos:" + pos + ")")
    require(pos >= 0L, "deleting at negative pos:" + pos)

    if (fast) return new RemovedIntSequence(this, pos)

    val internalPosition = externalToInternalPosition(pos)
    val value = internalPositionToValue.get(internalPosition).head
    val largestInternalPosition = startFreeRangeForInternalPosition - 1L

    val valueAtLargestInternalPosition : Long = internalPositionToValue.get(largestInternalPosition).head

    val deleteIsAtLargestInternalPosition = internalPosition == largestInternalPosition

    val newInternalPositionToValue = if(deleteIsAtLargestInternalPosition) {
      internalPositionToValue.remove(largestInternalPosition)
    } else{
      internalPositionToValue.
        insert(internalPosition, valueAtLargestInternalPosition).
        remove(largestInternalPosition)
    }

    val newValueToInternalPositions = if(deleteIsAtLargestInternalPosition) {
      internalRemoveFromValueToInternalPositions(value, internalPosition, valueToInternalPositions)
    }else {
      internalInsertToValueToInternalPositions(valueAtLargestInternalPosition, internalPosition,
        internalRemoveFromValueToInternalPositions(valueAtLargestInternalPosition, largestInternalPosition,
          internalRemoveFromValueToInternalPositions(value, internalPosition, valueToInternalPositions)))
    }

    //now, update the fct knowing the move and remove
    val externalPositionAssociatedToLargestInternalPosition = externalToInternalPosition.backward(largestInternalPosition)

    //TODO: this is overly complex and probably very slow
    val newExternalToInternalPosition = externalToInternalPosition.updateBefore(
      (externalPositionAssociatedToLargestInternalPosition,
        externalPositionAssociatedToLargestInternalPosition,
        LinearTransform(pos - externalPositionAssociatedToLargestInternalPosition, false)),
      (pos, pos, LinearTransform(externalPositionAssociatedToLargestInternalPosition - pos, false))).updateBefore(
      (pos, size - 2L, LinearTransform(1L, false)),
      (size - 1L, size - 1L, LinearTransform(pos - size + 1L, false)))

    new ConcreteIntSequence(
      newInternalPositionToValue,
      newValueToInternalPositions,
      newExternalToInternalPosition,
      startFreeRangeForInternalPosition - 1L)
  }


  def moveAfter(startPositionIncluded : Long, endPositionIncluded : Long, moveAfterPosition : Long, flip : Boolean, fast : Boolean, autoRework : Boolean) : IntSequence = {
    //println(this + ".moveAfter(startPositionIncluded:" + startPositionIncluded + " endPositionIncluded:" + endPositionIncluded + " moveAfterPosition:" + moveAfterPosition + " flip:" + flip + ")")
    require(startPositionIncluded >= 0L && startPositionIncluded < size, "startPositionIncluded should be in [0L,size[ in UniqueIntSequence.moveAfter")
    require(endPositionIncluded >= 0L && endPositionIncluded < size, "endPositionIncluded(=" + endPositionIncluded+ ") should be in [0L,size(="+size+")[ in UniqueIntSequence.moveAfter")
    require(moveAfterPosition >= -1L && moveAfterPosition < size, "moveAfterPosition=" + moveAfterPosition + " should be in [-1L,size=" + size+"[ in UniqueIntSequence.moveAfter")

    require(
      moveAfterPosition < startPositionIncluded || moveAfterPosition > endPositionIncluded,
      "moveAfterPosition=" + moveAfterPosition + " cannot be between startPositionIncluded=" + startPositionIncluded + " and endPositionIncluded=" + endPositionIncluded)
    require(startPositionIncluded <= endPositionIncluded, "startPositionIncluded=" + startPositionIncluded + " should be <= endPositionIncluded=" + endPositionIncluded)

    if (fast) return new MovedIntSequence(this, startPositionIncluded, endPositionIncluded, moveAfterPosition, flip)

    if (moveAfterPosition + 1L == startPositionIncluded) {
      //not moving
      if (flip) {
        //just flipping
        val newExternalToInternalPosition = externalToInternalPosition.flipInInterval(startPositionIncluded,endPositionIncluded)

        //val newExternalToInternalPositionSlow = externalToInternalPosition.updateBefore(
        //  (startPositionIncluded, endPositionIncluded, LinearTransform(endPositionIncluded + startPositionIncluded, true)))

        //equire(newExternalToInternalPosition.forward.equals(newExternalToInternalPositionSlow.forward),
        //  "newExternalToInternalPosition.forward:" + newExternalToInternalPosition.forward + " newExternalToInternalPositionSlow.forward:" + newExternalToInternalPositionSlow.forward)

        //println("passed")

        new ConcreteIntSequence(
          internalPositionToValue,
          valueToInternalPositions,
          newExternalToInternalPosition,
          startFreeRangeForInternalPosition)

      } else {
        this //nop
      }
    } else {
      if (moveAfterPosition > startPositionIncluded) {
        //move upwards
        val newExternalToInternalPosition = if(!flip) {
          externalToInternalPosition.swapAdjacentZonesShiftBest(
            startPositionIncluded,
            endPositionIncluded,
            moveAfterPosition)

        }else{

          val tmp = externalToInternalPosition.swapAdjacentZonesShiftSecond(
            startPositionIncluded,
            endPositionIncluded,
            moveAfterPosition:Long,
            true)

          assert(tmp.forward equals externalToInternalPosition.updateBefore(
            (startPositionIncluded,
              moveAfterPosition + startPositionIncluded - endPositionIncluded - 1L,
              LinearTransform(endPositionIncluded + 1L - startPositionIncluded, false)),
            (startPositionIncluded + moveAfterPosition - endPositionIncluded,
              moveAfterPosition,
              LinearTransform(if (flip) startPositionIncluded + moveAfterPosition
              else endPositionIncluded - moveAfterPosition, flip))).forward)

          tmp
        }

        assert(newExternalToInternalPosition.forward equals externalToInternalPosition.updateBefore(
          (startPositionIncluded,
            moveAfterPosition + startPositionIncluded - endPositionIncluded - 1L,
            LinearTransform(endPositionIncluded + 1L - startPositionIncluded, false)),
          (startPositionIncluded + moveAfterPosition - endPositionIncluded,
            moveAfterPosition,
            LinearTransform(if (flip) startPositionIncluded + moveAfterPosition
            else endPositionIncluded - moveAfterPosition, flip))).forward)

        new ConcreteIntSequence(
          internalPositionToValue,
          valueToInternalPositions,
          newExternalToInternalPosition,
          startFreeRangeForInternalPosition)

      } else {
        //move downwards
        val newExternalToInternalPosition = if(!flip) {
          externalToInternalPosition.swapAdjacentZonesShiftBest(
            moveAfterPosition+1L,
            startPositionIncluded-1L,
            endPositionIncluded)
        }else{
          externalToInternalPosition.swapAdjacentZonesShiftFirst(
            moveAfterPosition+1L,
            startPositionIncluded-1L,
            endPositionIncluded,true)
        }

        assert(externalToInternalPosition.updateBefore(
          (moveAfterPosition + 1L,
            moveAfterPosition + endPositionIncluded - startPositionIncluded + 1L,
            LinearTransform(if (flip) endPositionIncluded + moveAfterPosition + 1L else startPositionIncluded - moveAfterPosition - 1L, flip)),
          (moveAfterPosition + endPositionIncluded - startPositionIncluded + 2L,
            endPositionIncluded,
            LinearTransform(startPositionIncluded - endPositionIncluded - 1L, false))).forward equals newExternalToInternalPosition.forward)


        new ConcreteIntSequence(
          internalPositionToValue,
          valueToInternalPositions,
          newExternalToInternalPosition,
          startFreeRangeForInternalPosition)
      }
    }
  }

  override def regularizeToMaxPivot(maxPivotPerValuePercent: Long, targetToken: Token = this.token) :ConcreteIntSequence = {
    if(this.externalToInternalPosition.forward.nbPivot * 100L > maxPivotPerValuePercent * this.size){
      regularize(targetToken)
    }else{
      if (targetToken != this.token){
        new ConcreteIntSequence(internalPositionToValue,
          valueToInternalPositions,
          externalToInternalPosition,
          size, targetToken)
      }else this
    }
  }

  def regularize(targetToken : Token = this.token) : ConcreteIntSequence = {
    var explorerOpt = this.explorerAtPosition(0L)
    val newInternalPositionToValues:Array[(Long,Long)] = Array.ofDim[(Long,Long)](this.size)
    val oldInternalPosToNewInternalPos:Array[Long] = Array.ofDim[Long](this.size)

    while (explorerOpt match {
      case None => false
      case Some(explorer) =>
        newInternalPositionToValues(explorer.position) = (explorer.position,explorer.value)
        oldInternalPosToNewInternalPos(explorer.asInstanceOf[ConcreteIntSequenceExplorer].internalPos) = explorer.position
        explorerOpt = explorer.next
        true
    }) {}

    new ConcreteIntSequence(RedBlackTreeMap.makeFromSortedArray(newInternalPositionToValues),
      valueToInternalPositions.updateAll(0L,
        oldInternalPositions => {
          val newPositions = oldInternalPositions.keys.map(oldInt => {
            val newInternalPosition = oldInternalPosToNewInternalPos(oldInt)
            (newInternalPosition,newInternalPosition)})
          RedBlackTreeMap(newPositions)}),
      PiecewiseLinearBijectionNaive.identity,
      newInternalPositionToValues.length, targetToken)
  }

  override def commitPendingMoves : IntSequence = this

  override def unorderedContentNoDuplicate : List[Long] = valueToInternalPositions.keys

  override def unorderedContentNoDuplicateWithNBOccurences : List[(Long,Long)] = valueToInternalPositions.content.map({case ((value,positions)) => ((value,positions.size))})
}

class IntSequenceIterator(var crawler:Option[IntSequenceExplorer]) extends Iterator[Long] {

  override def hasNext : Boolean =
    crawler match{
      case None => false
      case Some(_) => true}

  override def next() : Long = {
    val position = crawler.head
    crawler = position.next
    position.value
  }
}

abstract class IntSequenceExplorer{
  val value:Long
  def position:Long
  def next:Option[IntSequenceExplorer]
  def prev:Option[IntSequenceExplorer]
}


class ConcreteIntSequenceExplorer(sequence:ConcreteIntSequence,
                                  override val position:Long,
                                  positionInRB:RedBlackTreeMapExplorer[Long],
                                  currentPivotPosition:Option[RedBlackTreeMapExplorer[Pivot]],
                                  pivotAbovePosition:Option[RedBlackTreeMapExplorer[Pivot]])(
                                   limitAboveForCurrentPivot:Long = pivotAbovePosition match{
                                     case None => Long.MaxValue
                                     case Some(p) => p.value.fromValue-1L},
                                   limitBelowForCurrentPivot:Long = currentPivotPosition match{
                                     case None => Long.MinValue
                                     case Some(p) => p.value.fromValue},
                                   slopeIsPositive:Boolean = currentPivotPosition match{
                                     case None => true
                                     case Some(p) => !p.value.f.minus}
                                   ) extends IntSequenceExplorer{

  override def toString : String = "ConcreteIntSequenceExplorer(position:" + position + " value:" + value + " currentPivotPosition:" + currentPivotPosition + " pivotAbovePosition:" + pivotAbovePosition + " positionInRB:" + positionInRB + ")"

  override val value : Long = positionInRB.value

  private[seq] def internalPos = positionInRB.key

  override def next : Option[IntSequenceExplorer] = {
    if(position == sequence.size-1L) return None
    if(position == limitAboveForCurrentPivot){
      //change pivot, we are also sure that there is a next, so use .head
      val newPivotAbovePosition = pivotAbovePosition.head.next
      val newPosition = position + 1L
      val newPositionInRBOpt = sequence.internalPositionToValue.positionOf(pivotAbovePosition.head.value.f(newPosition))
      newPositionInRBOpt match{
        case None => None
        case Some(newPositionInRB) =>
          Some(new ConcreteIntSequenceExplorer(sequence,
            newPosition,
            newPositionInRB,
            pivotAbovePosition,
            newPivotAbovePosition)(limitBelowForCurrentPivot = newPosition))
      }
    }else{
      //do not change pivot

      (if(slopeIsPositive) positionInRB.next else positionInRB.prev) match{
        case None => None
        case Some(newPositionInRB) =>
          Some(new ConcreteIntSequenceExplorer(sequence,
            position + 1L,
            newPositionInRB,
            currentPivotPosition,
            pivotAbovePosition)(
            limitAboveForCurrentPivot,
            limitBelowForCurrentPivot,
            slopeIsPositive))
      }
    }
  }

  override def prev : Option[IntSequenceExplorer] = {
    if (position == 0L) None
    else if (position  == limitBelowForCurrentPivot) {
      //change pivot

      val newPosition = position - 1L
      val newCurrentPivotPosition = currentPivotPosition.head.prev
      val newInternalPosition = newCurrentPivotPosition match{case None => newPosition case Some(position2) => position2.value.f(newPosition)}
      val newCurrentPositionInRB = sequence.internalPositionToValue.positionOf(newInternalPosition).head
      //println("change pivot newPosition:" + newPosition + " newCurrentPivotPosition:" + newCurrentPivotPosition + " oldPosition:" + currentPivotPosition)
      Some(new ConcreteIntSequenceExplorer(sequence,
        newPosition,
        newCurrentPositionInRB,
        newCurrentPivotPosition,
        currentPivotPosition)(limitAboveForCurrentPivot = limitBelowForCurrentPivot-1L
      ))
    }else{
      //do not change pivot
      //println("not change pivot")
      Some(new ConcreteIntSequenceExplorer(sequence,
        position-1L,
        if(slopeIsPositive) positionInRB.prev.head else positionInRB.next.head,
        currentPivotPosition,
        pivotAbovePosition)(
        limitAboveForCurrentPivot,
        limitBelowForCurrentPivot,
        slopeIsPositive))
    }
  }
}

abstract class StackedUpdateIntSequence extends IntSequence(){
  override def delete(pos : Long, fast:Boolean,autoRework:Boolean) : IntSequence = {
    require(pos >= 0L, "pos=" + pos + " for delete on UniqueIntSequence should be >= 0L")
    require(pos < size, "cannot delete past end of sequence in UniqueIntSequence")
    new RemovedIntSequence(this,pos)
  }

  override def moveAfter(startPositionIncluded : Long, endPositionIncluded : Long, moveAfterPosition : Long, flip : Boolean, fast:Boolean,autoRework:Boolean) : IntSequence = {
    require(startPositionIncluded >= 0L && startPositionIncluded < size , "startPositionIncluded=" + startPositionIncluded + " should be in [0L,size" + size + "[ in UniqueIntSequence.moveAfter")
    require(endPositionIncluded >= 0L && endPositionIncluded < size , "endPositionIncluded=" + endPositionIncluded +" should be in [0L,size"+size+"[ in UniqueIntSequence.moveAfter")
    require(moveAfterPosition >= -1L && moveAfterPosition < size , "moveAfterPosition=" + moveAfterPosition + " should be in [-1L,size="+size+"[ in UniqueIntSequence.moveAfter")

    new MovedIntSequence(this,startPositionIncluded,endPositionIncluded,moveAfterPosition,flip)
  }

  override def insertAtPosition(value : Long, pos : Long, fast:Boolean,autoRework:Boolean) : IntSequence = {
    require(pos >= 0L && pos <= size , "pos=" + pos + " should be in [0L,size="+size+"] in IntSequence.insertAt")
    new InsertedIntSequence(this,value:Long,pos:Long)
  }


  override def regularizeToMaxPivot(maxPivotPerValuePercent: Long, targetToken: Token = this.token) : ConcreteIntSequence =
    commitPendingMoves.regularizeToMaxPivot(maxPivotPerValuePercent, targetToken)

  override def regularize(targetToken:Token = this.token) : ConcreteIntSequence = commitPendingMoves.regularize(targetToken)
}

object MovedIntSequence{

  @inline
  def bijectionForMove(startPositionIncluded:Long,
                       endPositionIncluded:Long,
                       moveAfterPosition:Long,
                       flip:Boolean):PiecewiseLinearBijectionNaive = {
    if(moveAfterPosition + 1L == startPositionIncluded) {
      //not moving
      if(flip) {
        //just flipping
        if (startPositionIncluded == 0L) {
          PiecewiseLinearBijectionNaive(new PiecewiseLinearFun(RedBlackTreeMap.makeFromSortedArray(Array(
            (0L, new Pivot(0L, new LinearTransform(endPositionIncluded, true))),
            (endPositionIncluded + 1L, new Pivot(endPositionIncluded + 1L, LinearTransform.identity))))))
        } else {
          PiecewiseLinearBijectionNaive(new PiecewiseLinearFun(RedBlackTreeMap.makeFromSortedArray(Array(
            (0L, new Pivot(0L, LinearTransform.identity)),
            (startPositionIncluded, new Pivot(startPositionIncluded, new LinearTransform(endPositionIncluded + startPositionIncluded, true))),
            (endPositionIncluded + 1L, new Pivot(endPositionIncluded + 1L, LinearTransform.identity))))))
        }

      }else{
        //nop
        PiecewiseLinearBijectionNaive.identity
      }
    }else{
      if (moveAfterPosition > startPositionIncluded) {
        //move upwards
        PiecewiseLinearBijectionNaive(new PiecewiseLinearFun(RedBlackTreeMap.makeFromSortedArray(Array(
          (startPositionIncluded, new Pivot(startPositionIncluded, LinearTransform(endPositionIncluded + 1L - startPositionIncluded, false))),
          (moveAfterPosition + startPositionIncluded - endPositionIncluded, new Pivot(moveAfterPosition + startPositionIncluded - endPositionIncluded,
            LinearTransform(if (flip) startPositionIncluded + moveAfterPosition else endPositionIncluded - moveAfterPosition, flip))),
          (moveAfterPosition + 1L, new Pivot(moveAfterPosition + 1L, LinearTransform.identity))))))
      } else {
        //move downwards
        PiecewiseLinearBijectionNaive(new PiecewiseLinearFun(RedBlackTreeMap.makeFromSortedArray(Array(
          (moveAfterPosition + 1L,
            new Pivot(moveAfterPosition + 1L,LinearTransform(if (flip) endPositionIncluded + moveAfterPosition + 1L
            else startPositionIncluded - moveAfterPosition - 1L, flip))),
          (moveAfterPosition + endPositionIncluded - startPositionIncluded + 2L,
            new Pivot(moveAfterPosition + endPositionIncluded - startPositionIncluded + 2L,
              LinearTransform(startPositionIncluded - endPositionIncluded - 1L, false))),
          (endPositionIncluded +1L,
            new Pivot(endPositionIncluded +1L, LinearTransform.identity))
        ))))
      }
    }
  }

  @inline
  def oldPosToNewPos(oldPos : Long, fromIncluded:Long, toIncluded:Long, after:Long, flip:Boolean) : Long = {
    //println("oldPosToNewPos(oldPos:"  + oldPos + " fromIncluded:" + fromIncluded + " toIncluded:" + toIncluded + " after:" + after +" flip:" + flip + ")")

    if(after+1L == fromIncluded && !flip) oldPos
    else if(after+1L == fromIncluded && flip){
      if(oldPos < fromIncluded || oldPos > toIncluded) oldPos
      else fromIncluded + toIncluded - oldPos
    }else if(fromIncluded < after){
      //println("move upwards")
      if(oldPos < fromIncluded || oldPos > after) oldPos
      else if(oldPos <= toIncluded){
        //println("in the moved segment")
        if(flip){
          fromIncluded - oldPos + after
        }else{
          oldPos + after - toIncluded
        }
      }else{
        //println("not in the moved segment")
        oldPos - toIncluded + fromIncluded - 1L
      }
    }else{
      //println("move downwards")
      if(oldPos <= after || oldPos > toIncluded) oldPos
      else if(oldPos < fromIncluded){
        //println("not in the moved segment")
        oldPos + toIncluded - fromIncluded + 1L
      }else{
        //println("in the moved segment")
        if(flip){
          after + 1L + toIncluded - oldPos
        }else{
          oldPos + after - fromIncluded + 1L
        }
      }
    }
  }
}

class MovedIntSequence(val seq:IntSequence,
                       val startPositionIncluded:Long,
                       val endPositionIncluded:Long,
                       val moveAfterPosition:Long,
                       val flip:Boolean)
  extends StackedUpdateIntSequence{

  //TODO: provide a cache on the values at the boundary of the move

  override def unorderedContentNoDuplicate : List[Long] = seq.unorderedContentNoDuplicate

  override def unorderedContentNoDuplicateWithNBOccurences : List[(Long, Long)] = seq.unorderedContentNoDuplicateWithNBOccurences

  override def descriptorString : String = seq.descriptorString + ".moved(startPos:" + startPositionIncluded + " endPos:" + endPositionIncluded + " targetPos:" + moveAfterPosition + " flip:" + flip + ")"

  val localBijection = MovedIntSequence.bijectionForMove(startPositionIncluded, endPositionIncluded, moveAfterPosition, flip)

  override val size : Long = seq.size

  override def nbOccurrence(value : Long) : Long = seq.nbOccurrence(value)

  override def commitPendingMoves:IntSequence = seq.commitPendingMoves.moveAfter(startPositionIncluded,endPositionIncluded,moveAfterPosition,flip,fast=false,autoRework = false)

  override def explorerAtPosition(position : Long) : Option[IntSequenceExplorer] = {
    val positionOfCurrentPivot = localBijection.forward.pivotWithPositionApplyingTo(position)
    seq.explorerAtPosition(localBijection.forward(position)) match{
      case None => None
      case Some(explorerInBasicSequence) =>
        Some(new MovedIntSequenceExplorer(this,position,
          explorerInBasicSequence,
          positionOfCurrentPivot,
          positionOfCurrentPivot match{case None => localBijection.forward.firstPivotAndPosition case Some(x) => x.next})())
    }
  }

  def oldPosToNewPos(oldPos : Long) :Long = {
    val tmp = MovedIntSequence.oldPosToNewPos(oldPos, startPositionIncluded, endPositionIncluded, moveAfterPosition, flip)
    assert(tmp == localBijection.backward(oldPos), "oldPosToNewPos got" + tmp + " expected " + localBijection.backward(oldPos))
    tmp
  }

  override def positionsOfValueQ(value : Long) : QList[Long] = {
    var positionsBefore = seq.positionsOfValueQ(value)
    var toReturn:QList[Long] = null
    while(positionsBefore != null){
      val oldPos = positionsBefore.head
      positionsBefore = positionsBefore.tail
      val newPos = oldPosToNewPos(oldPos)
      toReturn = QList(newPos,toReturn)
    }
    toReturn
  }

  override def contains(value : Long) : Boolean = seq.contains(value)

  override def isEmpty : Boolean = seq.isEmpty

  override def valueAtPosition(position : Long) : Option[Long] = {
    seq.valueAtPosition(localBijection.forward(position))
  }
}

class MovedIntSequenceExplorer(sequence:MovedIntSequence,
                               override val position:Long,
                               positionInBasicSequence:IntSequenceExplorer,
                               currentPivotPosition:Option[RedBlackTreeMapExplorer[Pivot]],
                               pivotAbovePosition:Option[RedBlackTreeMapExplorer[Pivot]])(
                                limitAboveForCurrentPivot:Long = pivotAbovePosition match{
                                  case None => Long.MaxValue
                                  case Some(p) => p.value.fromValue-1L},
                                limitBelowForCurrentPivot:Long = currentPivotPosition match{
                                  case None => Long.MinValue
                                  case Some(p) => p.value.fromValue},
                                slopeIsPositive:Boolean = currentPivotPosition match{
                                  case None => true
                                  case Some(p) => !p.value.f.minus}
                                ) extends IntSequenceExplorer{

  override val value : Long = positionInBasicSequence.value

  override def next : Option[IntSequenceExplorer] = {
    if(position == sequence.size-1L) return None
    if(position == limitAboveForCurrentPivot){
      //change pivot, we are also sure that there is a next, so use .head
      val newPivotAbovePosition = pivotAbovePosition.head.next
      val newPosition = position + 1L
      val newPositionInBasicSequence = sequence.seq.explorerAtPosition(pivotAbovePosition.head.value.f(newPosition))
      newPositionInBasicSequence match{
        case None => None
        case Some(newPositionInRB) =>
          Some(new MovedIntSequenceExplorer(sequence,
            newPosition,
            newPositionInRB,
            pivotAbovePosition,
            newPivotAbovePosition)(limitBelowForCurrentPivot = newPosition))
      }
    }else{
      //do not change pivot

      (if(slopeIsPositive) positionInBasicSequence.next else positionInBasicSequence.prev) match{
        case None => None
        case Some(newPositionInRB) =>
          Some(new MovedIntSequenceExplorer(sequence,
            position + 1L,
            newPositionInRB,
            currentPivotPosition,
            pivotAbovePosition)(
            limitAboveForCurrentPivot,
            limitBelowForCurrentPivot,
            slopeIsPositive))
      }
    }
  }

  override def prev : Option[IntSequenceExplorer] = {
    if (position == 0L) None
    else if (position  == limitBelowForCurrentPivot) {
      //change pivot

      val newPosition = position - 1L
      val newCurrentPivotPosition = currentPivotPosition.head.prev
      val newInternalPosition = newCurrentPivotPosition match{case None => newPosition case Some(position2) => position2.value.f(newPosition)}

      //println("change pivot newPosition:" + newPosition + " newCurrentPivotPosition:" + newCurrentPivotPosition + " oldPosition:" + currentPivotPosition)
      Some(new MovedIntSequenceExplorer(sequence,
        newPosition,
        sequence.seq.explorerAtPosition(newInternalPosition).head,
        newCurrentPivotPosition,
        currentPivotPosition)(limitAboveForCurrentPivot = limitBelowForCurrentPivot-1L
      ))
    }else{
      //do not change pivot
      //println("not change pivot")
      Some(new MovedIntSequenceExplorer(sequence,
        position-1L,
        if(slopeIsPositive) positionInBasicSequence.prev.head else positionInBasicSequence.next.head,
        currentPivotPosition,
        pivotAbovePosition)(
        limitAboveForCurrentPivot,
        limitBelowForCurrentPivot,
        slopeIsPositive))
    }
  }
}

class InsertedIntSequence(seq:IntSequence,
                          val insertedValue:Long,
                          val pos:Long)
  extends StackedUpdateIntSequence {
  override val size : Long = seq.size + 1L

  override def nbOccurrence(value : Long) : Long = if(value == this.insertedValue) seq.nbOccurrence(value) + 1L else seq.nbOccurrence(value)

  override def unorderedContentNoDuplicateWithNBOccurences : List[(Long, Long)] =
    unorderedContentNoDuplicate.map(value => (value,if(value == insertedValue) seq.nbOccurrence(value) +1L else seq.nbOccurrence(value)))

  override def descriptorString : String = seq.descriptorString + ".inserted(val:" + insertedValue + " pos:" + pos + ")"

  override def unorderedContentNoDuplicate : List[Long] = if(seq.nbOccurrence(insertedValue) == 0L) insertedValue :: seq.unorderedContentNoDuplicate else seq.unorderedContentNoDuplicate

  override def positionsOfValueQ(value : Long) : QList[Long] = {
    var positionsBefore = seq.positionsOfValueQ(value)
    var toReturn:QList[Long] = null
    while(positionsBefore != null){
      val oldPos = positionsBefore.head
      positionsBefore = positionsBefore.tail
      val newPos = oldPos2NewPos(oldPos)
      toReturn = QList(newPos,toReturn)
    }
    if(value == insertedValue) QList(pos,toReturn)
    else toReturn
  }

  @inline
  private def oldPos2NewPos(oldPOs:Long):Long = {
    if(oldPOs < pos) oldPOs else oldPOs +1L
  }

  override def explorerAtPosition(position : Long) : Option[IntSequenceExplorer] = {
    if (position == this.pos) {
      if (position == 0L) {
        Some(new InsertedIntSequenceExplorer(this, position, seq.explorerAtPosition(0L), true, true))
      } else {
        Some(new InsertedIntSequenceExplorer(this, position, seq.explorerAtPosition(position - 1L), true, false))
      }
    } else if (position < this.pos) {
      seq.explorerAtPosition(position) match{
        case None => None
        case Some(p) => Some(new InsertedIntSequenceExplorer(this, position, Some(p), false, false))
      }
    } else {
      seq.explorerAtPosition(position-1L) match{
        case None => None
        case Some(p) => Some(new InsertedIntSequenceExplorer(this, position, Some(p), false, false))
      }
    }
  }

  override def contains(value : Long) : Boolean = value == this.insertedValue || seq.contains(value)

  override def commitPendingMoves : IntSequence = seq.commitPendingMoves.insertAtPosition(insertedValue, pos, fast = false, autoRework = false)

  override def isEmpty : Boolean = false

  override def valueAtPosition(position : Long) : Option[Long] = {
    if (position == pos) Some(insertedValue)
    else if (position < pos) seq.valueAtPosition(position)
    else seq.valueAtPosition(position - 1L)
  }
}

class InsertedIntSequenceExplorer(seq:InsertedIntSequence,
                                  val position:Long,
                                  explorerInOriginalSeq:Option[IntSequenceExplorer],
                                  atInsertedValue:Boolean,
                                  originalExplorerIsAbove:Boolean)
  extends IntSequenceExplorer {
  override val value : Long = if(atInsertedValue) seq.insertedValue else explorerInOriginalSeq.head.value

  override def next : Option[IntSequenceExplorer] = {
    if (atInsertedValue) {
      //we are leaving the inserted position
      explorerInOriginalSeq match{
        case None => None
        case Some(p) =>
          if (originalExplorerIsAbove) Some(new InsertedIntSequenceExplorer(seq, position + 1L, explorerInOriginalSeq, atInsertedValue = false, originalExplorerIsAbove = false))
          else {
            p.next match {
              case None => None
              case Some(next1) =>
                Some(new InsertedIntSequenceExplorer(seq, position + 1L, Some(next1), atInsertedValue = false, originalExplorerIsAbove = false))
            }
          }
      }
    }else {
      val nextPosition = position + 1L
      if (nextPosition == seq.pos) {
        //we are getting into the inserted position
        Some(new InsertedIntSequenceExplorer(seq, position + 1L, explorerInOriginalSeq, atInsertedValue = true, originalExplorerIsAbove = false))
      } else {
        //nothing special
        explorerInOriginalSeq.head.next match {
          case None => None
          case Some(next) => Some(new InsertedIntSequenceExplorer(seq, position + 1L, Some(next), atInsertedValue = false, originalExplorerIsAbove = false))
        }
      }
    }
  }

  override def prev : Option[IntSequenceExplorer] = {
    if (atInsertedValue) {
      explorerInOriginalSeq match {
        case None => None
        case Some(p) =>
          //we are leaving the inserted position
          if (!originalExplorerIsAbove) Some(new InsertedIntSequenceExplorer(seq, position - 1L, explorerInOriginalSeq, atInsertedValue = false, originalExplorerIsAbove = false))
          else {
            p.prev match {
              case None => None
              case Some(prev1) => Some(new InsertedIntSequenceExplorer(seq, position - 1L, Some(prev1), atInsertedValue = false, originalExplorerIsAbove = false))
            }
          }
      }
    } else {
      val prevPosition = position - 1L
      if (prevPosition == seq.pos) {
        //we are getting into the inserted position
        Some(new InsertedIntSequenceExplorer(seq, position - 1L, explorerInOriginalSeq, atInsertedValue = true, originalExplorerIsAbove = true))
      } else {
        //nothing special
        explorerInOriginalSeq.head.prev match {
          case None => None
          case Some(prev) => Some(new InsertedIntSequenceExplorer(seq, position - 1L, Some(prev), atInsertedValue = false, originalExplorerIsAbove = false))
        }
      }
    }
  }
}

class RemovedIntSequence(val seq:IntSequence,
                         val positionOfDelete:Long)
  extends StackedUpdateIntSequence{

  val removedValue = seq.valueAtPosition(positionOfDelete).head

  override def descriptorString : String = seq.descriptorString + ".removed(pos:" + positionOfDelete + " val:" + removedValue + ")"

  override def nbOccurrence(value : Long) : Long = if(value == this.removedValue) seq.nbOccurrence(value) - 1L else seq.nbOccurrence(value)

  override def unorderedContentNoDuplicate : List[Long] =
    if(seq.nbOccurrence(removedValue) > 1L) seq.unorderedContentNoDuplicate
    else seq.unorderedContentNoDuplicate.filter(_ != removedValue)

  override def unorderedContentNoDuplicateWithNBOccurences : List[(Long, Long)] =
    unorderedContentNoDuplicate.flatMap(value => if(value == removedValue) {
      val occurencesBefore = seq.nbOccurrence(value)
      if (occurencesBefore == 1L) None
      else Some((value, occurencesBefore - 1L))
    }else Some((value, seq.nbOccurrence(value))))

  override val size : Long = seq.size - 1L

  override def explorerAtPosition(position : Long) : Option[IntSequenceExplorer] = {
    seq.explorerAtPosition(if (position < this.positionOfDelete) position else position + 1L) match {
      case None => None
      case Some(e) => Some(new RemovedIntSequenceExplorer(this, position, e))
    }
  }

  override def positionsOfValueQ(value : Long) : QList[Long] = {
    var positionsBefore = seq.positionsOfValueQ(value)
    var toReturn:QList[Long] = null
    while(positionsBefore != null){
      val oldPos = positionsBefore.head
      positionsBefore = positionsBefore.tail
      if (oldPos < this.positionOfDelete) {
        toReturn = QList(oldPos, toReturn)
      }else if (oldPos > positionOfDelete) {
        toReturn = QList(oldPos - 1L, toReturn)
      }
    }
    toReturn
  }

  def oldPos2NewPos(oldPos:Long) = {
    if (oldPos < this.positionOfDelete) oldPos else oldPos - 1L
  }

  override def contains(value : Long) : Boolean = {
    if(value == removedValue) seq.nbOccurrence(value)>1L
    else seq.contains(value)
  }

  override def commitPendingMoves : IntSequence = seq.commitPendingMoves.delete(this.positionOfDelete,fast=false,autoRework=false)

  override def valueAtPosition(position : Long) : Option[Long] = {
    if(position >= this.positionOfDelete) seq.valueAtPosition(position+1L)
    else seq.valueAtPosition(position)
  }
}

class RemovedIntSequenceExplorer(seq:RemovedIntSequence,
                                 val position:Long,
                                 explorerInOriginalSeq:IntSequenceExplorer)
  extends IntSequenceExplorer{
  override val value : Long = explorerInOriginalSeq.value

  override def prev : Option[IntSequenceExplorer] = {
    explorerInOriginalSeq.prev match {
      case None => None
      case Some(tentativePos) =>
        if(tentativePos.position == seq.positionOfDelete)
          tentativePos.prev match {
            case None => None
            case Some(secondTentativePos) => Some(new RemovedIntSequenceExplorer(seq, position - 1L, secondTentativePos))
          }
        else Some(new RemovedIntSequenceExplorer(seq, position - 1L, tentativePos))
    }
  }

  override def next : Option[IntSequenceExplorer] = {
    explorerInOriginalSeq.next match {
      case None => None
      case Some(tentativePos) =>
        if(tentativePos.position == seq.positionOfDelete)
          tentativePos.next match {
            case None => None
            case Some(secondTentativePos) => Some(new RemovedIntSequenceExplorer(seq, position + 1L, secondTentativePos))
          }
        else Some(new RemovedIntSequenceExplorer(seq, position + 1L, tentativePos))
    }
  }
}

