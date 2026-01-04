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
import gov.nasa.jpf.constraints.expressions.LogicalOperator;
import gov.nasa.jpf.constraints.expressions.PropositionalCompound;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import gov.nasa.jpf.jdart.constraints.tree.DecisionData;
import gov.nasa.jpf.jdart.constraints.tree.Node;
import gov.nasa.jpf.jdart.constraints.tree.NodeType;
import gov.nasa.jpf.jdart.constraints.tree.ResultData;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.util.Pair;
import java.util.ArrayList;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class TrimmedConstraintsTree {
  
  static abstract class Node {
    public abstract boolean isInner();
  }
  
  static class ResultNode extends Node {
    private final PathResult result;
    
    public ResultNode(PathResult result) {
      this.result = result;
    }
    
    @Override
    public boolean isInner() {
      return false;
    }
    
    public PathResult getResult() {
      return result;
    }
  }
  
  static class InnerNode extends Node {
    private final Node[] children;
    private final Expression<Boolean>[] constraints;
    
    @SuppressWarnings("unchecked")
    public InnerNode(Collection<Node> children, List<Expression<Boolean>> constraints) {
      this(children.toArray(new Node[children.size()]), constraints.toArray(new Expression[constraints.size()]));
    }
    
    public InnerNode(Node[] children, Expression<Boolean>[] constraints) {
      assert children.length == constraints.length;
      this.children = children;
      this.constraints = constraints;
    }
    
    @Override
    public boolean isInner() {
      return true;
    }
    
    public int getNumChildren() {
      return children.length;
    }
    
    public Node getChild(int idx) {
      return children[idx];
    }
    
    public Expression<Boolean> getConstraint(int idx) {
      return constraints[idx];
    }
  }
  
  static final ResultNode DONT_KNOW_NODE = new ResultNode(PathResult.dontKnow());
  
  private static final JPFLogger logger = JPF.getLogger("jdart");

  
  static ConstraintsTree.Node toBinaryCTree(TrimmedConstraintsTree.Node root) {
    ConstraintsTree.Node done = null;
    Expression<Boolean> pc = null;
    LinkedList<Pair<TrimmedConstraintsTree.Node, ArrayList<ConstraintsTree.Node>>> stack = new LinkedList<>();
    Pair<TrimmedConstraintsTree.Node, ArrayList<ConstraintsTree.Node>> p = null;
    boolean down = true;
    TrimmedConstraintsTree.Node curr = root;
    int depth = 0;
    int maxdepth = 0;
    while (!stack.isEmpty() || down) {
      // moving down
      if (down) {
        // moving further down
        if (curr.isInner()) {
          p = new Pair<>(curr, new ArrayList<ConstraintsTree.Node>());
          stack.push(p);
          depth++;
          pc = (pc == null) ? ((InnerNode)curr).constraints[0] : 
                  new PropositionalCompound(((InnerNode)curr).constraints[0], LogicalOperator.AND, pc);
          curr = ((InnerNode)curr).children[0];
        } 
        // moving back up
        else {
          down = false;
          done = new ConstraintsTree.Node(
                  pc != null ? pc : ExpressionUtil.TRUE, ((ResultNode)curr).result); 
          maxdepth = java.lang.Math.max(depth, maxdepth);
        }
      }
      // moving up
      else {
        p = stack.pop();
        depth--;
        pc = (pc instanceof PropositionalCompound) ? ((PropositionalCompound)pc).getRight() : null;
        p._2.add(done);
        curr = p._1;
        int idx = p._2.size();
        // moving further up        
        if (idx == ((InnerNode)curr).children.length) {          
          done = toBinaryCTree((InnerNode)curr, p._2.toArray(new ConstraintsTree.Node[] {}), 0);
        }
        // moving down again
        else {
          stack.push(p);
          depth++;
          pc = (pc == null) ? ((InnerNode)curr).constraints[idx] :
                  new PropositionalCompound(((InnerNode)curr).constraints[idx], LogicalOperator.AND, pc);
          curr = ((InnerNode)curr).children[idx];
          down = true;
        }
      }
    }
    logger.fine("Constraints Tree had depth " + maxdepth);
    return done;
  }
  
  private static ConstraintsTree.Node toBinaryCTree(TrimmedConstraintsTree.InnerNode node, 
          ConstraintsTree.Node[] children, int index) {

      if(index == children.length)
        return null;
      
      ConstraintsTree.Node succTrue = children[index];
      ConstraintsTree.Node succFalse = toBinaryCTree(node, children, index+1);
      if(succFalse == null)
        return succTrue;
      return new ConstraintsTree.Node(node.constraints[index], succTrue, succFalse);    
  }
  
  public TrimmedConstraintsTree() {
    // TODO Auto-generated constructor stub
  }

  public static TrimmedConstraintsTree.Node trim(gov.nasa.jpf.jdart.constraints.tree.Node root) {
    LinkedList<Pair<Integer,TrimmedConstraintsTree.Node[]>> stack = new LinkedList<>();
    gov.nasa.jpf.jdart.constraints.tree.Node curr = root;
    gov.nasa.jpf.jdart.constraints.tree.Node prev = null;
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
            gov.nasa.jpf.jdart.constraints.tree.Node tmp = curr; curr = prev; prev = tmp;
            continue;
          }

          // moving further down
          Pair<Integer, TrimmedConstraintsTree.Node[]> p = new Pair<>(idx, arr);
          stack.push(p);
          prev = curr; curr = d.getChild(idx);
          continue;
        }

        // moving back up
        if (curr.getDataType() == NodeType.VIRGIN || curr.getDataType() == NodeType.UNSATISFIABLE) {
          done = null;
        }
        else if (curr.getDataType() == NodeType.RESULT) {
          ResultData resultData = (ResultData) curr.getData();
          done = new TrimmedConstraintsTree.ResultNode(resultData.getResult());
        }
        else if (curr.getDataType() == NodeType.DONT_KNOW) {
          done = TrimmedConstraintsTree.DONT_KNOW_NODE;
        }
        gov.nasa.jpf.jdart.constraints.tree.Node tmp = curr; curr = prev; prev = tmp;
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

  private static TrimmedConstraintsTree.Node generateTrimmedNode(DecisionData d, TrimmedConstraintsTree.Node[] arr) {
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

}
