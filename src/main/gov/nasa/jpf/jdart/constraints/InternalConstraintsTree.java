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
  Node current = root; // This is the current node in our EXPLORATION
  Node currentTarget = root; // This is the node the valuation computed by the constraint solver SHOULD reach
  
  final AnalysisConfig anaConf;

  ArrayList<Integer> expectedPath = new ArrayList<>();
  boolean diverged = false;
  final SolverContext solverCtx;
  boolean explore;
  
  final ConcolicValues preset;
  boolean replay = false;
  
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
		  curr = dd.getAndCreateChild(branchIdx);
	  }
	  
	  return curr;
  }
  
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
    current = data.getAndCreateChild(branchIdx);
    
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
        currentTarget = current;
      }
    }
    
    return BranchEffect.NORMAL;
  }

  public Node getRoot() {
    return root;
  }

  public void setExplore(boolean explore) {
    this.explore = explore;
  }

  public void finish(PathResult result) {
    current.result(result);
  }

  public void failCurrentTarget() {
    currentTarget.dontKnow();
  }

  public boolean isExplore() {
    return explore;
  }

  public boolean needsDecision() {
    return !current.hasDecisionData();
  }

}
