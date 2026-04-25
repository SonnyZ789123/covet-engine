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

package gov.nasa.jpf.jdart.exploration;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.jdart.constraints.InternalConstraintsTree;
import gov.nasa.jpf.jdart.constraints.tree.DecisionData;
import gov.nasa.jpf.jdart.constraints.tree.Node;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.vm.MethodInfo;

public class DFSStrategy implements ExplorationStrategy {

    private final JPFLogger debugLogger = JPF.getLogger("jdart.debug");

    public DFSStrategy() {}

    private Node descendDecisionNode(InternalConstraintsTree ctx, DecisionData decisionData) {
        int nextIdx = decisionData.nextOpenChild();
        assert nextIdx != -1; // because of backtrack condition should decisionData have at least one open child

        ctx.extendExpectedPath(decisionData, nextIdx);
        return decisionData.getOrCreateChild(nextIdx);
    }

    @Override
    public Valuation findNext(InternalConstraintsTree ctx, MethodInfo methodInfo) {
        debugLogger.finest("[findNext] ================ finding next path ================");

        ctx.findNextInit();

        Node targetNode = ctx.getCurrentTarget();
        while ((targetNode = ctx.backtrack(targetNode, Node::isOpen)) != null) {

            DecisionData decisionData = targetNode.decisionData();

            // ----- LEAF / VIRGIN NODE -----
            if (decisionData == null) {
                Valuation val = ctx.solvePathOrMarkNode(targetNode);

                if (val != null) {
                    return val;
                }
            }

            // ----- DECISION NODE -----
            else {
                targetNode = descendDecisionNode(ctx, decisionData);
            }
        }

        return ctx.getPresetValues();
    }
}
