/*
 * Copyright (C) 2025-2026 Yoran Mertens
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package gov.nasa.jpf.jdart.constraints.tree;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.util.Predicate;

import java.util.ArrayList;
import java.util.Stack;

public class ConstraintsPath {
    private final JPFLogger logger = JPF.getLogger("jdart");

    private final JPFLogger debugLogger = JPF.getLogger("jdart.debug");

    /** the expected path (list of branch indexes) through the constraints tree */
    private final ArrayList<Integer> expectedPath = new ArrayList<>();

    /** the solver context used to check path condition satisfiability */
    private final SolverContext solverCtx;

    public ConstraintsPath(SolverContext solverCtx) {
        this.solverCtx = solverCtx;
    }

    public ArrayList<Integer> getExpectedPath() {
        return new ArrayList<>(expectedPath);
    }

    public Node backtrack(Node startNode, Predicate<Node> stopCondition) {
        debugLogger.finest("[backtrack] from expectedPath=" + expectedPath);
        if (startNode == null)
            return null;

        Node currentNode = startNode;
        while (!stopCondition.isTrue(currentNode)) {
            currentNode = currentNode.getParent();

            if (currentNode == null) {
                debugLogger.finest("[backtrack] reached root parent -> stop");
                break;
            }

            popExpectedPath();
        }

        debugLogger.finest("[bracktrack] new expectedPath=" + expectedPath);
        return currentNode;
    }

    public void emptyExpectedPath() {
        while (!expectedPath.isEmpty()) {
            popExpectedPath();
        }
    }

    public void popExpectedPath() {
        solverCtx.pop();
        int removed = expectedPath.remove(expectedPath.size() - 1);
        debugLogger.finest("[popExpectedPath] removed branch " + removed);
    }

    public void extendExpectedPath(DecisionData decisionData, int branchIndex) {
        Expression<Boolean> constraint = decisionData.getConstraint(branchIndex);

        solverCtx.push();

        try {
            solverCtx.add(constraint);
        } catch(RuntimeException ex) {
            logger.finer(ex.getMessage());
        }
        expectedPath.add(branchIndex);

        debugLogger.finest("[extendExpectedPath] extend path, branchIdx=" + branchIndex +
                        ", constraint=" + constraint.toString());
    }

    public void constructExpectedPath(Node node) {
        emptyExpectedPath();
        Stack<DecisionData> decisionDataStack = new Stack<>();
        Stack<Integer> indexStack = new Stack<>();

        while (node.getParent() != null) {
            Node parent = node.getParent();
            DecisionData decisionData = parent.decisionData();

            if (decisionData != null) {
                for (int i = 0; i < decisionData.getBranchWidth(); i++) {
                    if (decisionData.getChild(i) == node) {
                        decisionDataStack.push(decisionData);
                        indexStack.push(i);
                        break;
                    }
                }
            }

            node = parent;
        }

        while (!decisionDataStack.isEmpty()) {
            extendExpectedPath(decisionDataStack.pop(), indexStack.pop());
        }
    }

    public SolverContextSolveResult solveCurrentPath() {
        logger.finest("Finding new valuation for path: " + expectedPath);
        Valuation val = new Valuation();
        ConstraintSolver.Result res = solverCtx.solve(val);
        logger.finest("Found: " + res + " : " + val);
        return new SolverContextSolveResult(val, res);
    }
}
