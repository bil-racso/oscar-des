package oscar.cbls.business.routing.invariants.capa

import oscar.cbls.algo.rb.RedBlackTreeMap
import oscar.cbls.algo.seq.IntSequence
import oscar.cbls.business.routing.model.VehicleLocation
import oscar.cbls.core._

import scala.collection.immutable.SortedSet


/**
 * creates a GenericCumulativeIntegerDimensionOnVehicle Invariant
 * @param routes The sequence representing the route associated at each vehicle
 * @param n The maximum number of nodes
 * @param v The number of vehicles
 */
abstract class AbstractForwardCumulativeDimensionOnVehicle(routes:ChangingSeqValue,
                                                           n:Long,
                                                           v:Long,fullDebug:Boolean = false)
  extends AbstractVehicleCapacity(n,v)
  with SeqNotificationTarget{

  private val vehicleLocationAndCheckpointStack = new SeqCheckpointedValueStack[VehicleLocation]()

  protected var toUpdateZonesAndVehicleStartAfter:Option[(RedBlackTreeMap[List[(Long,Long)]],VehicleLocation)] =
    Some(RedBlackTreeMap.empty[List[(Long,Long)]], computeAndAffectContentAndVehicleStartPositionsFromScratch(routes.value,true))

  protected var potentiallyRemovedNodes:SortedSet[Long] = SortedSet.empty

  override def notifySeqChanges(v: ChangingSeqValue, d: Long, changes: SeqUpdate){
    val tmp = digestUpdatesAndUpdateVehicleStartPositionsAndSearchZoneToUpdate(
      changes,
      toUpdateZonesAndVehicleStartAfter,
      potentiallyRemovedNodes,
      v.value)

    if(fullDebug) {
      tmp._1 match {
        case Some((list, _)) =>
            checkZonesToUpdate(list, changes.newValue)
        case _ => ;
      }
    }

    toUpdateZonesAndVehicleStartAfter = tmp._1
    potentiallyRemovedNodes = tmp._2
    scheduleForPropagation()
  }

/*
  private def printToUpdateZonesAndVehicleStartAfter(toUpdateZonesAndVehicleStartAfter:Option[(RedBlackTreeMap[List[(Long,Long)]],VehicleLocation)]):String = {
    toUpdateZonesAndVehicleStartAfter match{
      case None => "None"
      case Some((a,b)) => "Some(" + a.content.map({case (c,l) => c + "->" + l}).mkString(",") + "," + b + ")"
    }
  }
*/

  override def performPropagation(){
    setNodesUnrouted(potentiallyRemovedNodes)

    toUpdateZonesAndVehicleStartAfter match{
      case Some((vehiclesToZonesToUpdate,vehicleLocation)) =>

        updateVehicleContentOnAllVehicle(routes.value,
          vehiclesToZonesToUpdate,
          vehicleLocation)

        toUpdateZonesAndVehicleStartAfter = Some(RedBlackTreeMap.empty[List[(Long,Long)]],vehicleLocation)
      case None =>
        val currentVehicleLocation = computeAndAffectContentAndVehicleStartPositionsFromScratch(routes.value,false)
        toUpdateZonesAndVehicleStartAfter = Some(RedBlackTreeMap.empty[List[(Long,Long)]],currentVehicleLocation)
    }

    potentiallyRemovedNodes = SortedSet.empty
  }


  def digestUpdatesAndUpdateVehicleStartPositionsAndSearchZoneToUpdate(changes:SeqUpdate,
                                                                       toUpdateZonesAndVehicleStartOpt:Option[(RedBlackTreeMap[List[(Long,Long)]],VehicleLocation)],
                                                                       potentiallyRemovedPoints:SortedSet[Long],
                                                                       previousSequence:IntSequence)
  :(Option[(RedBlackTreeMap[List[(Long,Long)]],VehicleLocation)],SortedSet[Long]) = {
    changes match {
      case s@SeqUpdateInsert(value : Long, posOfInsert : Long, prev : SeqUpdate) =>
        digestUpdatesAndUpdateVehicleStartPositionsAndSearchZoneToUpdate(prev, toUpdateZonesAndVehicleStartOpt, potentiallyRemovedPoints, previousSequence) match {
          case (Some((zonesAfterPrev, vehicleLocationAfterPrev)), potentiallyRemovedPointsAfterPrev) =>
            val vehicleLocationAfterInsert = vehicleLocationAfterPrev.push(s.oldPosToNewPos)
            val updatedZones =
              updateZoneToUpdateAfterInsert(
                zonesAfterPrev,
                posOfInsert,
                prev.newValue,
                vehicleLocationAfterPrev,vehicleLocationAfterInsert)

            (Some((updatedZones,  vehicleLocationAfterInsert)), potentiallyRemovedPointsAfterPrev)
          case (None,potentiallyRemovedPointsAfterPrev) =>
            (None, potentiallyRemovedPointsAfterPrev)
        }

      case r@SeqUpdateRemove(pos : Long, prev : SeqUpdate) =>
        digestUpdatesAndUpdateVehicleStartPositionsAndSearchZoneToUpdate(prev, toUpdateZonesAndVehicleStartOpt, potentiallyRemovedPoints, previousSequence) match {
          case (Some((zonesAfterPrev, vehicleLocationAfterPrev)), potentiallyRemovedPointsAfterPrev) =>
            val updatedZonesAfterRemove =
              updateZoneToUpdateAfterRemove(
                zonesAfterPrev,
                pos,
                prev.newValue,
                vehicleLocationAfterPrev)

            (Some((updatedZonesAfterRemove, vehicleLocationAfterPrev.push(r.oldPosToNewPos))),  potentiallyRemovedPointsAfterPrev + r.removedValue)
          case (None,potentiallyRemovedPointsAfterPrev) =>
            (None, potentiallyRemovedPointsAfterPrev + r.removedValue)
        }

      case m@SeqUpdateMove(fromIncluded : Long, toIncluded : Long, after : Long, flip : Boolean, prev : SeqUpdate) =>
        digestUpdatesAndUpdateVehicleStartPositionsAndSearchZoneToUpdate(prev, toUpdateZonesAndVehicleStartOpt, potentiallyRemovedPoints, previousSequence) match {
          case (Some((zonesAfterPrev, vehicleLocationAfterPrev)), potentiallyRemovedPointsAfterPrev) =>
            val vehicleLocationAfterMove = vehicleLocationAfterPrev.push(m.oldPosToNewPos)
            val updatedZones =
              updateZoneToUpdateAfterMove(
                zonesAfterPrev,
                m,
                prev.newValue,
                vehicleLocationAfterPrev,
                vehicleLocationAfterMove)
            (Some((updatedZones, vehicleLocationAfterMove)), potentiallyRemovedPointsAfterPrev)

          case(None,potentiallyRemovedPointsAfterPrev) =>
            (None, potentiallyRemovedPointsAfterPrev)
        }

      case SeqUpdateAssign(value : IntSequence) =>
        (None, potentiallyRemovedPoints ++ previousSequence.unorderedContentNoDuplicate.filter(_>=v))

      case SeqUpdateLastNotified(value : IntSequence) =>
        (toUpdateZonesAndVehicleStartOpt, potentiallyRemovedPoints)

      case s@SeqUpdateDefineCheckpoint(prev : SeqUpdate, isStarMode:Boolean, checkpointLevel:Long) =>
        digestUpdatesAndUpdateVehicleStartPositionsAndSearchZoneToUpdate(prev, toUpdateZonesAndVehicleStartOpt, potentiallyRemovedPoints, previousSequence) match {
          //checkpoints are managed about the vehicleLocation exclusively
          case (Some((zonesAfterPrev, vehicleLocationAfterPrev)), removedPointsAfterPrev) =>
            val vehicleLocationToSave = if(checkpointLevel == 0L) vehicleLocationAfterPrev.regularize else vehicleLocationAfterPrev
            vehicleLocationAndCheckpointStack.defineCheckpoint(prev.newValue,checkpointLevel,vehicleLocationToSave)
            (Some((zonesAfterPrev, vehicleLocationToSave)), removedPointsAfterPrev)
          case (None,potentiallyRemovedPointsAfterPrev) =>

            val vehicleLocationToSave = VehicleLocation.apply(v,node => prev.newValue.positionOfAnyOccurrence(node).get)
            vehicleLocationAndCheckpointStack.defineCheckpoint(prev.newValue,checkpointLevel,vehicleLocationToSave)

            (None, potentiallyRemovedPointsAfterPrev)
        }

      case u@SeqUpdateRollBackToCheckpoint(checkpoint : IntSequence, level:Long) =>
        digestUpdatesAndUpdateVehicleStartPositionsAndSearchZoneToUpdate(u.howToRollBack,toUpdateZonesAndVehicleStartOpt, potentiallyRemovedPoints, previousSequence) match {
          //checkpoints are managed about the vehicleLocation exclusively
          case (Some((zonesAfterPrev, vehicleLocationAfterPrev)), removedPointsAfterPrev) =>
            val regularizedVehicleLocation = vehicleLocationAndCheckpointStack.rollBackAndOutputValue(checkpoint,level)
            (Some((zonesAfterPrev, regularizedVehicleLocation)), removedPointsAfterPrev)
          case (None,potentiallyRemovedPointsAfterPrev) =>
            //in this case, we cannot exploit the regularized info
            //but this is a very strange case,actually
            vehicleLocationAndCheckpointStack.rollBackAndOutputValue(checkpoint,level)
            (None, potentiallyRemovedPointsAfterPrev)
        }
    }
  }
}
