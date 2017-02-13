package oscar.modeling.solvers.mip

import org.rogach.scallop.Subcommand
import oscar.modeling.solvers.{SolveHolder, SolverApp, SolverAppModule}

/**
 * Module for SolverApp that solves models using a LP solver
 * @param app the SolverApp
 */
class MIPAppModule(app: SolverApp[_]) extends SolverAppModule {
  class SequentialCPSubcommand extends Subcommand("mip") {
    descr("Solves the model using a MIP solver.")
  }
  override val subcommand = new SequentialCPSubcommand

  override def solve[RetVal](): List[RetVal] = {
    val pg = new MIPProgram[RetVal](app.modelDeclaration)
    val onSolution: () => RetVal = app.asInstanceOf[SolveHolder[RetVal]].onSolution
    if(onSolution == null)
      throw new RuntimeException("No onSolution defined in the SolverApp or in the ModelDeclaration")
    pg.onSolution{onSolution()}

    val result = pg.solve()
    result.toList
  }
}