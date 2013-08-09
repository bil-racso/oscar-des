package oscar.cp.minizinc

import oscar.cp.core.CPVarInt

class VarArrayInt (
    //val range: Range,
    val annotations: List[Annotation],
    val cpvar: Array[CPVarInt],
    override val name: String
    ) extends FZObject (name) {

	override def toString() = name + " " + annotations + " " + cpvar.mkString(",")
	
}