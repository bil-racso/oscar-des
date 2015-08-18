package oscar.des.flow.lib

//This file is about thing we want to measure on the factory process

//Variables have values evey time something is happening.
class BoolExpr
class IntExpr

//probe on simulation elements
class Empty(s:Storage) extends BoolExpr
class Productive(p:Process) extends BoolExpr
class Content(s:Storage) extends IntExpr

//logical properties
//boolean is whet it means: a boolean value at each state. there is no notion of event there; they are like fluents.
//we only consider temporal operators of the past, easy to evaluate
class Not(f:BoolExpr) extends BoolExpr
class And(f:BoolExpr, g:BoolExpr) extends BoolExpr
class Or(f:BoolExpr, g:BoolExpr) extends BoolExpr

class HasAlwaysBeen(f:BoolExpr) extends BoolExpr
class HasBeen(f:BoolExpr) extends BoolExpr
class Since(a:BoolExpr,b:BoolExpr) extends BoolExpr  //the counterpart is only differing with its initial value
class BecomesTrue(p:BoolExpr) extends BoolExpr
class BecomesFalse(p:BoolExpr) extends BoolExpr
class Changes(p:BoolExpr) extends BoolExpr

//variables always have a value.
class CumulatedDuration(start:BoolExpr, end:BoolExpr) extends IntExpr
class Sum(s:IntExpr) extends IntExpr
class Mult(a:IntExpr,b:IntExpr) extends IntExpr
class Plus(a:IntExpr,b:IntExpr) extends IntExpr
class PonderateWithDuration(s:IntExpr) extends IntExpr

//relational operators to get back to Propositions
class G(a:IntExpr,b:IntExpr) extends BoolExpr
class GE(a:IntExpr,b:IntExpr) extends BoolExpr
class LE(a:IntExpr,b:IntExpr) extends BoolExpr
class EQ(a:IntExpr,b:IntExpr) extends BoolExpr
class NEQ(a:IntExpr,b:IntExpr) extends BoolExpr

//To estimate over different runs
//how to find names that are obviously statistics over different runs

class Statistics
//this only considers the latest valuee of e; at the end of the simulation run, and performs an average over several runs
class Mean(e:IntExpr) extends Statistics
class Variance(e:IntExpr) extends Statistics