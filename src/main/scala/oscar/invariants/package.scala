/**
 * *****************************************************************************
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
 * ****************************************************************************
 */

package oscar

import scala.collection.mutable._
import scala.util.continuations._

class SuspendableIterable[T](iter: scala.collection.immutable.Iterable[T]) {
  def foreach[U](f: T => Unit @cpsParam[U, U]): Unit @cpsParam[U, U] = {
    val i = iter.iterator
    while (i.hasNext) {
      f(i.next)
    }
  }
  def suspendable = this
}

package object invariants {
  def cpsunit: Unit @cps[Unit] = ()
  def cpsfalse: Boolean @cps[Unit] = false
  def cpstrue: Boolean @cps[Unit] = true

  @inline def when[A](d: Occuring[A])(f: A => Boolean): Reaction[A] = {
    d.foreach(f)
  }

  implicit def iter2susp[T](iter: scala.collection.immutable.Iterable[T]) = new SuspendableIterable(iter)
  implicit def bl2f[A, B](block: => B) = { a: A => block }
  implicit def rd2r[A](rd: ReactionDescription[A]) = rd.post()
  implicit def occuring2desc[A](occ: Occuring[A]) = occ ~> { _ => }
  @inline def perform[A](rd: ReactionDescription[A]): Reaction[A] = {
    for (msg <- rd.occuring) {
      rd.f(msg)
      true
    }
  }
  @inline def whenever[A](e: Occuring[A])(f: A => Unit): Reaction[A] = {
    for (msg <- e) {
      f(msg)
      true
    }
  }

  @inline def once[A, T](d: Occuring[A])(f: A => T) = {
    //var res: T
    when(d) { (x: A) =>
      f(x)
      false
    }
  }

  @inline def waitFor[A, T](d: Occuring[A]): A @cpsParam[SuspendableResult[T], SuspendableResult[T]] = {
    val e = (new Throwable()).getStackTrace()
    shift { k: (A => SuspendableResult[T]) =>
      once(d) { msg: A =>
        println("Executing Reaction from ")
        for (el <- e) {
          println("   " + el.getClassName() + "->" + el.getMethodName() + "(" + el.getFileName() + ":" + el.getLineNumber() + ")")
        }
        k(msg)
      }
      Suspend
    }
  }

  implicit def Var2Val[A](v: Var[A]) = { v() }
  implicit def array2ElementArray[A](at: scala.collection.immutable.IndexedSeq[Var[A]]) = {
    new ElementArray(at)
  }
  def sum(l: VarInt*) = { (v: VarInt) => new SumInvariant(v, l.toList) }
  def sumOnList(l: VarList[Int]) = { (v: VarInt) => new SumInvariantOnList(v, l) }
  def sumOnListOfVars(l: VarList[VarInt]) = { (v: VarInt) => new SumInvariantOnListOfVars(v, l) }

  //  implicit def array2ElementArray2[A](at: IndexedSeq[IndexedSeq[Var[A]]]) = {
  //    new ElementArray2(at)
  //  }
}
