package oscar.des.flow.lib

import oscar.des.engine.Model
import oscar.des.flow.core.ItemClassHelper._
import oscar.des.flow.core.{ItemClassTransformWitAdditionalOutput, ItemClassTransformFunction, Putable, Fetchable}
import oscar.des.flow.modeling.{MultipleParsingError, MultipleParsingSuccess, ListenerParser}
import scala.collection.immutable.SortedMap
import scala.language.implicitConversions

trait implicitConvertors {
  implicit def doubleToConstantDoubleFunction(f: Double): (() => Double) = () => f
  implicit def intToConstantDoubleFunction(f: Int): (() => Double) = () => f
  implicit def doubleToConstantIntFunction(f: Double): (() => Int) = () => f.toInt
  implicit def intToConstantIntFunction(f: Int): (() => Int) = () => f
  implicit def constantFetchableToFunctionFetchable(l: Array[(Int, Fetchable)]): Array[(() => Int, Fetchable)] = l.map(v => (() => v._1, v._2))
  implicit def constantPutableToFunctionPutable(l: Array[(Int, Putable)]): Array[(() => Int, Putable)] = l.map(v => (() => v._1, v._2))
}


/**
 *
 * @param verbosity where verbosities should be sent, can be null
 */
class FactoryModel(verbosity:String=>Unit) {

  val m:Model = new Model

  var ms:MetricsStore = null

  private var storages:List[Storage] = List.empty
  private var processes:List[ActivableProcess] = List.empty

  def getStorages:List[Storage] = storages
  def getProcesses:List[ActivableProcess] = processes

  def simulate(horizon:Float, verbosity:String=>Unit, abort:()=>Boolean = ()=>false){
    m.simulate(horizon,verbosity,abort)
  }

  /**
   * creates a clone of this factory model where everything has been reset
   * so that you can simulate the clone as well
   * @return
   */
  def cloneReset:FactoryModel = {
    SortedMap.empty[Storage,Storage] ++ storages.map((s:Storage) => (s,s.cloneReset))
  }


  def setQueriesToParse(queriesNameAndExpression:List[(String,String)]){
    require(ms == null)
    val parser = ListenerParser(storages,processes)
    parser.parseAllListeners(queriesNameAndExpression) match{
      case  MultipleParsingSuccess(expressions:List[(String,Expression)]) =>
        ms = new MetricsStore(expressions,verbosity)
      case  MultipleParsingError(s:String) =>
        throw new Error(s)
    }
  }

  /**
   * a process inputs some inputs, and produces its outputs at a given rate.
   * notice that inputs and outputs are performed in parallel (thus might cause some deadlocks)
   *
   * @param batchDuration the duration of a batch starting from all inputs being inputted, and ending with the beginning of the outputting
   * @param inputs the set of inputs (number of parts to input, storage)
   * @param outputs the set of outputs (number of parts, storage)
   * @param name the name of this process, for pretty printing
   * @author renaud.delandtsheer@cetic.be
   * */
  def singleBatchProcess(batchDuration:() => Double,
                         inputs:Array[(() => Int, Fetchable)],
                         outputs:Array[(()=>Int,Putable)],
                         transformFunction:ItemClassTransformFunction,
                         name:String,
                         costFunction:String = "0") = {
    val toReturn = SingleBatchProcess(m,batchDuration,inputs,outputs,transformFunction,name,verbosity)
    toReturn.cost = ListenerParser.processCostParser(toReturn).applyAndExpectDouble(costFunction)
    processes = toReturn :: processes
    toReturn
  }

  /**
   * This represents a batch process (see [[SingleBatchProcess]]) with multiple batch running in parallel.
   * @param numberOfBatches the number of batches running in parallel.
   * @param batchDuration the duration of a batch starting from all inputs being inputted, and ending with the beginning of the outputting
   * @param inputs the set of inputs (number of parts to input, storage)
   * @param outputs the set of outputs (number of parts, storage)
   * @param name the name of this process, for pretty printing, bath are named "name chain i" where i is the identifier of the batch process
   * @author renaud.delandtsheer@cetic.be
   * */
  def batchProcess(numberOfBatches:Int,
                   batchDuration:() => Double,
                   inputs:Array[(() => Int, Fetchable)],
                   outputs:Array[(() => Int,Putable)],
                   name:String,
                   transformFunction:ItemClassTransformFunction,
                   costFunction:String = "0") ={
    val toReturn = new BatchProcess(m,numberOfBatches,batchDuration,inputs,outputs,name,transformFunction,verbosity)
    toReturn.cost = ListenerParser.processCostParser(toReturn).applyAndExpectDouble(costFunction)
    processes = toReturn :: processes
    toReturn
  }

  /**
   *  A rolling (in a conveyor belt) Process means that if the output is blocked, no new batch is started
   * (imagine an industrial rolling band oven where croissants are cooked)
   * and if the input is blocked, the output still proceeds, (as if we were starting empty batches) there is no catch up for the waited time
   * batch only start when they ave their complete inputs.
   * if the output is blocked, the process stops, thus does not perform new inputs either (we cannot model that croissants will eventually burn in the oven)
   *
   * @param processDuration the duration between inputting a batch and outputting the batch
   * @param minimalSeparationBetweenBatches the minimal separation between two consecutive batches
   * @param inputs the set of inputs (number of parts to input, storage)
   * @param outputs the set of outputs (number of parts, storage)
   * @param name the name of this process, for pretty printing
   * @author renaud.delandtsheer@cetic.be
   */
  def conveyorBeltProcess(processDuration:() => Double,
                          minimalSeparationBetweenBatches:Double,
                          inputs:Array[(() => Int, Fetchable)],
                          outputs:Array[(() => Int, Putable)],
                          transformFunction:ItemClassTransformFunction,
                          name:String,
                          costFunction:String  = "0") = {
    val toReturn = new ConveyorBeltProcess(m:Model,processDuration,minimalSeparationBetweenBatches,inputs,outputs,transformFunction,name,verbosity)
    toReturn.cost = ListenerParser.processCostParser(toReturn).applyAndExpectDouble(costFunction)
    processes = toReturn :: processes
    toReturn
  }

  /**
   * This represents a failing batch process (see [[oscar.des.flow.lib.SplittingBatchProcess]]) with multiple batch running in parallel.
   * @param numberOfBatches the number of batches running in parallel.
   * @param batchDuration the duration of a batch starting from all inputs being inputted, and ending with the beginning of the outputting
   * @param inputs the set of inputs (number of parts to input, storage)
   * @param outputs the set of outputs (number of parts, storage)
   * @param name the name of this process, for pretty printing
   * @author renaud.delandtsheer@cetic.be
   * */
  def splittingBatchProcess(numberOfBatches:Int,
                            batchDuration:() => Double,
                            inputs:Array[(() => Int, Fetchable)],
                            outputs:Array[Array[(()=>Int,Putable)]],
                            name:String,
                            transformFunction:ItemClassTransformWitAdditionalOutput,
                            costFunction:String) = {
    val toReturn = SplittingBatchProcess(m, numberOfBatches, batchDuration, inputs, outputs, name, transformFunction, verbosity)
    toReturn.cost = ListenerParser.processCostParser(toReturn).applyAndExpectDouble(costFunction)
    processes = toReturn :: processes
    toReturn
  }

  /**
   * A process inputs some inputs, and produces its outputs at a given rate.
   * notice that inputs and outputs are performed in parallel (thus might cause some deadlocks)
   * this process might fail. In this case, failure is assessed at the end of the batch duration,
   * and produces the failureOutputs
   *
   * @param batchDuration the duration of a batch starting from all inputs being inputted, and ending with the beginning of the outputting
   * @param inputs the set of inputs (number of parts to input, storage)
   * @param outputs the set of outputs (number of parts, storage)
   * @param name the name of this process, for pretty printing
   * @author renaud.delandtsheer@cetic.be
   * */
  def splittingSingleBatchProcess(batchDuration:() => Double,
                                  inputs:Array[(() => Int, Fetchable)],
                                  outputs:Array[Array[(() => Int,Putable)]],
                                  transformFunction:ItemClassTransformWitAdditionalOutput,
                                  name:String,
                                  costFunction:String = "0") = {
    val toReturn = SplittingSingleBatchProcess(m, batchDuration, inputs, outputs, transformFunction, name, verbosity)
    toReturn.cost = ListenerParser.processCostParser(toReturn).applyAndExpectDouble(costFunction)
    processes = toReturn :: processes
    toReturn
  }

  /**
   *this type of storage acts in a LIFO-way.
   * it does matter to know this if you distinguish between different items.
   * @param maxSize the maximal content of the stock. attempting to put more items will block the putting operations
   * @param initialContent the initial content of the stock
   * @param name the name of the stock
   * @param overflowOnInput true if the stock overflows when there are excessing input, false to have it blocking the puts when it is full
   */
  def lIFOStorage(maxSize:Int,
                  initialContent:List[(Int,ItemClass)] = List.empty,
                  name:String,
                  overflowOnInput:Boolean,
                  costFunction:String = "0") = {
    val toReturn = new LIFOStorage(maxSize, initialContent, name, verbosity, overflowOnInput)
    toReturn.cost = ListenerParser.storageCostParser(toReturn).applyAndExpectDouble(costFunction)
    storages = toReturn :: storages
    toReturn
  }

  /**
   *this type of storage acts in a FIFO-way.
   * it does matter to know this if you distinguish between different items.
   * @param maxSize the maximal content of the stock. attempting to put more items will block the putting operations
   * @param initialContent the initial content of the stock
   * @param name the name of the stock
   * @param overflowOnInput true if the stock overflows when there are excessing input, false to have it blocking the puts when it is full
   */
  def fIFOStorage(maxSize:Int,
                  initialContent:List[(Int,ItemClass)],
                  name:String,
                  overflowOnInput:Boolean,
                  costFunction:String = "0") = {
    val toReturn = new FIFOStorage(maxSize, initialContent, name, verbosity, overflowOnInput)
    toReturn.cost = ListenerParser.storageCostParser(toReturn).applyAndExpectDouble(costFunction)
    storages = toReturn :: storages
    toReturn
  }
}