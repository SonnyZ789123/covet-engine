package gov.nasa.jpf.jdart.constraints.tree;

import gov.nasa.jpf.constraints.api.ConstraintSolver.Result;
import gov.nasa.jpf.constraints.api.Valuation;

public class SolverContextSolveResult {
  public final Valuation val;
  public final Result result;

  public SolverContextSolveResult(Valuation val, Result result) {
    this.val = val;
    this.result = result;
  }
}
