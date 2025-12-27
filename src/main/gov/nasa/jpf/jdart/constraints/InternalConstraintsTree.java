/*
 * Copyright (C) 2015, United States Government, as represented by the 
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 * 
 * The PSYCO: A Predicate-based Symbolic Compositional Reasoning environment 
 * platform is licensed under the Apache License, Version 2.0 (the "License"); you 
 * may not use this file except in compliance with the License. You may obtain a 
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0. 
 * 
 * Unless required by applicable law or agreed to in writing, software distributed 
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR 
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the 
 * specific language governing permissions and limitations under the License.
 */
package gov.nasa.jpf.jdart.constraints;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.jdart.config.AnalysisConfig;
import gov.nasa.jpf.jdart.config.ConcolicValues;
import gov.nasa.jpf.jdart.constraints.tree.*;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.vm.Instruction;

import java.util.*;

public class InternalConstraintsTree {
  
  private final JPFLogger logger = JPF.getLogger("jdart");

  private final JPFLogger debugLogger = JPF.getLogger("jdart.debug");
  
  
  final Node root = new Node(null);
  /** This is the current node in our EXPLORATION */
  Node current = root;
  /**
   * This is the node that the concrete execution should reach by
   * the valuation computed by the constraint solver.
   * This should only be set by the decision(...) method, which is invoked during
   * concrete execution when a symbolic branch is encountered.
   */
  Node currentTarget = root; // This is the node the valuation computed by the constraint solver SHOULD reach
  
  final AnalysisConfig anaConf;

  /** the expected path (list of branch indexes) through the constraints tree */
  ArrayList<Integer> expectedPath = new ArrayList<>();
  /** whether we have diverged from the expected path */
  boolean diverged = false;
  /** the solver context used to check path condition satisfiability */
  final SolverContext solverCtx;
  /**
   * Controls whether a branch creates new symbolic alternatives in the constraints tree.
   * If explore==false, we immediately mark all the child nodes as DONT_KNOW and set the num_open to 0.
   * */
  boolean explore;

  /** Preset concolic values to be used when no new target can be found */
  final ConcolicValues preset;
  /** Whether we are currently replaying preset values, this means that we don't want to check for divergence  */
  boolean replay = false;

  /** The previous valuation that was found by the constraint solver */
  Valuation prev = null;
  
  public InternalConstraintsTree(SolverContext solverCtx, AnalysisConfig anaConf) {
    this(solverCtx, anaConf, null);   
  }

  public InternalConstraintsTree(SolverContext solverCtx, AnalysisConfig anaConf, ConcolicValues preset) {
    this.solverCtx = solverCtx;
    this.anaConf = anaConf;
    this.explore = anaConf.isExploreInitially();
    this.preset = preset;
  }

  /**
   * Retrieves the node in the constraints tree that would be reached using
   * the given valuation.
   * 
   * @param valuation the valuation
   * @return the node in the tree that would be reached by the given valuation
   */
  public Node simulate(Valuation valuation) {
	  Node curr = root;
	  
	  while(curr.decisionData() != null) {
		  DecisionData dd = curr.decisionData();
		  int branchIdx = -1;
		  for(int i = 0; i < dd.getConstraints().length; i++) {
			  Expression<Boolean> constraint = dd.getConstraint(i);
			  try {
				  if(constraint.evaluate(valuation)) {
					  branchIdx = i;
					  break;
				  }
			  }
			  catch(RuntimeException ex) {
				  // e.g. due to function with undefined semantics
				  return null;
			  }
		  }
		  if(branchIdx < 0) {
			  throw new IllegalStateException("Non-complete set of constraints at constraints tree node!");
		  }
		  if(!dd.hasChild(branchIdx)) {
			  break;
		  }
		  curr = dd.getOrCreateChild(branchIdx);
	  }
	  
	  return curr;
  }

  /**
   * Records and processes a symbolic branch decision encountered during concrete execution.
   *
   * <p>This method is invoked whenever a conditional bytecode instruction is executed
   * and symbolic exploration is active. It aligns the concrete execution with the
   * internally planned symbolic path, updates the constraints tree, and manages
   * solver state and divergence detection.</p>
   *
   * <p><b>High-level responsibilities:</b></p>
   * <ul>
   *   <li>Create or validate {@link DecisionData} at the current tree node.</li>
   *   <li>Advance the {@code current} pointer to the selected branch child.</li>
   *   <li>Extend the symbolic path condition when exploring a new branch.</li>
   *   <li>Detect divergence between concrete execution and the expected symbolic path.</li>
   * </ul>
   *
   * <p><b>Symbolic path tracking:</b></p>
   * <ul>
   *   <li>If the current execution depth is smaller than {@code expectedPath.size()},
   *       this execution is considered a <em>replay</em> of an existing symbolic path.
   *       The chosen branch must match the previously recorded branch index.</li>
   *   <li>If the depth equals {@code expectedPath.size()}, a new symbolic decision
   *       is discovered. The corresponding constraint is pushed to the solver,
   *       and the branch index is appended to {@code expectedPath}.</li>
   * </ul>
   *
   * <p><b>Divergence handling:</b></p>
   * <ul>
   *   <li>If the concrete branch differs from the expected branch during replay,
   *       {@code diverged} is set and {@link BranchEffect#UNEXPECTED} is returned.</li>
   *   <li>If the execution reaches an exhausted subtree (and not in {@code replay} mode),
   *       {@code diverged} is set and {@link BranchEffect#INCONCLUSIVE} is returned.</li>
   * </ul>
   *
   * <p><b>Solver state effects:</b></p>
   * <ul>
   *   <li>On discovering a new symbolic decision, {@link SolverContext#push()} is called
   *       and the selected branch constraint is added.</li>
   *   <li>No solver state is popped in this method; cleanup is handled by backtracking.</li>
   * </ul>
   *
   * <p><b>Modes:</b></p>
   * <ul>
   *   <li>{@code explore = true}: alternative branches are opened and explored symbolically.</li>
   *   <li>{@code explore = false}: branches are executed concretely but marked as {@code dontKnow}.</li>
   *   <li>{@code replay = true}: divergence is tolerated because inputs originate from preset values.</li>
   * </ul>
   *
   * @param insn
   *   the branching bytecode instruction being executed
   * @param branchIdx
   *   the index of the concrete branch taken by execution
   * @param decisions
   *   the symbolic branch constraints corresponding to each branch alternative
   *
   * @return
   *   {@link BranchEffect#NORMAL} if execution matches the symbolic plan,
   *   {@link BranchEffect#UNEXPECTED} if a replay deviates from the expected path,
   *   or {@link BranchEffect#INCONCLUSIVE} if execution enters an exhausted subtree
   */
  public BranchEffect decision(Instruction insn, int branchIdx, Expression<Boolean>[] decisions) {
    debugLogger.finest("[decision] entry -> insn=" + insn,
            ", branchIdx=" + branchIdx +
            ", current node depth=" + current.getDepth() +
            ", expectedPath=" + expectedPath);
    if(anaConf.maxDepthExceeded(current.getDepth())) {
      //System.err.println("DEPTH EXCEEDED");
      return BranchEffect.NORMAL; // just ignore it
    }
    
    DecisionData data;
    try {
      data = current.decision(insn, decisions, explore);
    } catch(IllegalStateException e) {
      logger.severe(e.getMessage());
      // FIXME: this indicates a bug //
      return BranchEffect.INCONCLUSIVE;
    }
    
    int depth = current.getDepth();
    current = data.getOrCreateChild(branchIdx);
    
    if(current.isExhausted() && !replay) { // FALK: check how exhaustion is computed, maybe replay check is not necessary
      diverged = true;
      return BranchEffect.INCONCLUSIVE;
    }
    
    if(!diverged) {
      if(depth < expectedPath.size()) {
        int expected = expectedPath.get(depth).intValue();
        debugLogger.finest("[decision] node depth < expected path size -> checking if expected branch " +
                expected + " == " + branchIdx);
        if(expected != branchIdx) {
            diverged = true;
            return BranchEffect.UNEXPECTED;
          } 
      }
      else {
        Expression<Boolean> constraint = data.getConstraint(branchIdx);
        debugLogger.finest("[decision] insn=" + insn + ", adding constraint=" + constraint.toString());
        solverCtx.push();
        try {
          solverCtx.add(constraint);
        }
        catch(RuntimeException ex) {
          logger.finer(ex.getMessage());
          //ex.printStackTrace();
        }
       
        expectedPath.add(branchIdx);
        // Set current target to later be used for finding next node to explore
        setCurrentTarget(current);
      }
    }
    
    return BranchEffect.NORMAL;
  }

  public void findNextInit() {
    handleDivergence();
    replay = false;
    current = root;
  }

  public void handleDivergence() {
    if (diverged) {
      debugLogger.finest("[findNext] divergence detected -> backtrack without pop");
      backtrackToFirstOpenNode(false);
      diverged = false;
    }
  }

  public boolean checkDepthLimit(Node node) {
    int ad = node.incAltDepth();
    return anaConf.maxAltDepthExceeded(ad) || anaConf.maxDepthExceeded(node.getDepth());
  }

  public void backtrackToFirstOpenNode(boolean popPathConditions) {
    current = backtrack(current, popPathConditions);
  }

  public Node backtrack(Node node, boolean popPathConditions) {
    if (node == null)
      return null;

    Node currentNode = node;
    while (!currentNode.isOpen()) {
      boolean exh = currentNode.isExhausted();
      currentNode = currentNode.getParent();

      if (currentNode == null) {
        debugLogger.finest("[backtrack] reached root parent -> stop");
        break;
      }

      if (popPathConditions) {
        solverCtx.pop();
        int removed = expectedPath.remove(expectedPath.size() - 1);
        debugLogger.finest(
            "[backtrack] pop -> removed branch " + removed +
                ", new expectedPath=" + expectedPath);
      }

      DecisionData dec = currentNode.decisionData();
      if (dec != null) {
        dec.decrementOpen();

        if (exh) {
          dec.decrementUnexhausted();
          debugLogger.finest("[backtrack] exhausted child -> decrement unexhausted");
        }
      }
    }

    debugLogger.finest("[backtrack] new expectedPath=" + expectedPath);
    return currentNode;
  }

  public Valuation getPresetValues() {
    // ----- PRESET FALLBACK -----
    //We fall back on the preset values that might be specified in the
    //jpf config -- this only happens when we cannot find a new target
    //node from exercising the constraints tree
    if (preset != null && preset.hasNext()) {
      current = root;
      assert expectedPath.isEmpty();
      replay = true;

      return preset.next();
    }

    return null;
  }

  private void setCurrentTarget(Node target) {
    this.currentTarget = target;
  }

  public Node getRoot() {
    return root;
  }

  public void setExplore(boolean explore) {
    this.explore = explore;
  }

  public void finish(PathResult result) {
    current.markResultNode(result);
  }

  public void failCurrentTarget() {
    currentTarget.markDontKnowNode();
  }

  public boolean isExplore() {
    return explore;
  }

  public boolean needsDecision() {
    return !current.hasDecisionData();
  }

}
