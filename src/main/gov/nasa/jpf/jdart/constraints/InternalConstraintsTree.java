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
import gov.nasa.jpf.util.Pair;
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
  
  public void setExplore(boolean explore) {
    this.explore = explore;
  }
  
  public boolean needsDecision() {
    return !current.hasDecisionData();
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
  
  public void finish(PathResult result) {
    current.result(result);
  }
  
  
  public void failCurrentTarget() {
    currentTarget.dontKnow();
  }

  public ConstraintsTree toFinalCTree() {
    if (root == null) {
      return null;
    }    
    TrimmedConstraintsTree.Node r = trim();    
    if (r == null) {
      return null;
    } 
    return new ConstraintsTree(TrimmedConstraintsTree.toBinaryCTree(r));
  }
    
  TrimmedConstraintsTree.Node trim() {
    LinkedList<Pair<Integer,TrimmedConstraintsTree.Node[]>> stack = new LinkedList<>();
    Node curr = root;
    Node prev = null;
    TrimmedConstraintsTree.Node done = null;
    
    while (curr != null) {
      // moving down
      if (prev == null || prev == curr.getParent()) {
        // moving further down
        DecisionData d = curr.decisionData();
        if (d != null) {
          int cCount = d.getChildren().length;
          assert cCount > 0;
         
          int idx = 0;
          TrimmedConstraintsTree.Node[] arr = new TrimmedConstraintsTree.Node[cCount];
          for (int i=0; i<cCount; i++) {
            if (d.hasChild(i)) {
              break;
            }
            arr[idx++] = null;
          }
          
          // moving back up 
          if (idx == cCount) {
            done = generateTrimmedNode(d, arr);
            Node tmp = curr; curr = prev; prev = tmp;            
            continue;
          }
          
          // moving further down
          Pair<Integer, TrimmedConstraintsTree.Node[]> p = new Pair<>(idx, arr);
          stack.push(p);
          prev = curr; curr = d.getChild(idx);
          continue;
        }
       
        // moving back up
        if (!curr.hasData() || curr.dataIsUnsatisfiableData()) {
          done = null;
        }        
        else if (curr.dataIsResultData()) {
          ResultData resultData = (ResultData) curr.getData();
          done = new TrimmedConstraintsTree.ResultNode(resultData.getResult());
        }
        else if (curr.dataIsDontKnowData()) {
          done = TrimmedConstraintsTree.DONT_KNOW_NODE;
        }
        Node tmp = curr; curr = prev; prev = tmp;
      } 
      // moving up
      else {
        Pair<Integer, TrimmedConstraintsTree.Node[]> p = stack.pop();
        DecisionData data = (DecisionData) curr.getData();
        p._2[p._1] = done;
        
        int idx = p._1;
        while (++idx < p._2.length) {
          if (data.hasChild(idx)) {
            break;
          }
        }
        
        // moving further up
        if (idx == p._2.length) {        
          done = generateTrimmedNode(data, p._2);
          prev = curr; curr = curr.getParent();
          continue;
        }
        // moving down again
        p = new Pair(idx, p._2);
        stack.push(p);
        prev = curr; curr = data.getChild(idx);
      }
    }
    return done;
  }

  private TrimmedConstraintsTree.Node generateTrimmedNode(DecisionData d, TrimmedConstraintsTree.Node[] arr) {
    List<TrimmedConstraintsTree.Node> tchildren = new ArrayList<>();
    List<Expression<Boolean>> tconstraints = new ArrayList<>();
    boolean allDontKnow = true;
      for(int i = 0; i < arr.length; i++) {
        TrimmedConstraintsTree.Node tc = arr[i];
        if(tc == null)
          continue;
        tchildren.add(tc);
        tconstraints.add(d.getConstraint(i));
        if(tc != TrimmedConstraintsTree.DONT_KNOW_NODE)
          allDontKnow = false;
      }
      
      if(tchildren.isEmpty())
        return null;
      if(tchildren.size() == 1)
        return tchildren.iterator().next();
      if(allDontKnow)
        return TrimmedConstraintsTree.DONT_KNOW_NODE;
      return new TrimmedConstraintsTree.InnerNode(tchildren, tconstraints);
    }
  
  

  public boolean isExplore() {
    return explore;
  }
}
