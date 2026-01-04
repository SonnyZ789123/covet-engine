package gov.nasa.jpf.jdart.constraints.tree;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.jdart.constraints.PathResult;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.vm.Instruction;

public final class Node {
    private final Node parent;
    private final int depth;
    /** Alternative depth - how many times have we explored this node */
    private int altDepth;

    private NodeData data;
    private NodeType dataType;

    private final JPFLogger debugLogger = JPF.getLogger("jdart.debug");

    public Node(Node parent) {
        this.parent = parent;
        this.depth = (parent != null) ? parent.depth + 1 : 0;
        this.altDepth = (parent != null) ? parent.altDepth : 0;
        this.data = null;
        this.dataType = NodeType.VIRGIN;
    }

    public int incAltDepth() {
        return ++altDepth;
    }

    public Node getParent() {
        return parent;
    }

    public int getDepth() {
        return depth;
    }

    public NodeData getData() {
        return data;
    }

    public NodeType getDataType() {
        return dataType;
    }

    public DecisionData decisionData() {
        if (dataType == NodeType.DECISION) {
            assert data != null && data.getClass() == DecisionData.class;
            return (DecisionData) data;
        }

        return null;
    }

    public boolean isOpen() {
        // A node is open if it is a decision node with open branches,
        // or if it is virgin (we still have not explored it)
        if (dataType == NodeType.DECISION) {
            DecisionData dec = (DecisionData) data;
            return dec.hasOpen();
        }

        return dataType == NodeType.VIRGIN;
    }

    public boolean isExhausted() {
        // induction: A node is exhausted if it is a decision node with exhausted child branches,
        // base case: or it is a node that has a result (sat or unsat)
        if (dataType == NodeType.DECISION) {
            DecisionData dec = (DecisionData) data;
            return !dec.hasUnexhausted();
        }

        return (dataType == NodeType.RESULT ||
                dataType == NodeType.UNSATISFIABLE);

    }

    public boolean hasUnknownData() {
        return dataType == NodeType.DONT_KNOW || dataType == NodeType.VIRGIN;
    }

    /**
     * Get decision data, marking this node as decision if it was unknown.
     *
     * @param branchInsn the branching instruction
     * @param nextInstructions the possible next instructions
     * @param explore if false, mark all child nodes as DONT_KNOW
     * @return the decision data
     * @throws IllegalStateException if the node's decision data has a different branching instruction or has a
     * different number of constraints.
     */
    public DecisionData decision(
            Instruction branchInsn,
            InstructionBranch[] nextInstructions,
            boolean explore) throws IllegalStateException {
        if (hasUnknownData()) {
            markDecisionNode(branchInsn, nextInstructions);

            DecisionData dec = (DecisionData) data;
            if (!explore) {
                for(int i = 0; i < dec.getBranchWidth(); i++) {
                    Node childNode = dec.getOrCreateChild(i);
                    childNode.markDontKnowNode();
                }
            }

            return dec;
        }

        assert dataType == NodeType.DECISION;
        DecisionData dec = (DecisionData) data;
        dec.verifyDecision(branchInsn, nextInstructions);
        return dec;
    }

    private void decrementOpenOnParent() {
        if (parent == null) {
            return;
        }
        parent.decrementOpenChildren();
    }

    private void decrementOpenChildren() {
        DecisionData dec = decisionData();
        if (dec == null) {
            return;
        }

        if (!dec.hasOpen()) {
            // This can happen when during an execution, the decision call earlier marked this node as DONT_KNOW,
            // which decremented the numOpen count. So for analysis, we don't care anymore what happens from this
            // decision point.
            // Don't propagate further! We only want to decrement the parent once when !hasOpen().
            debugLogger.finest("[decrementOpenChildren] tried to decrement when numOpen=" + dec.getNumOpen());
            return;
        }
        dec.decrementOpen();

        if (!dec.hasOpen()) {
            decrementOpenOnParent();
        }
    }

    private boolean shouldUpdateNumOpen() {
        return dataType == NodeType.RESULT ||
                dataType == NodeType.UNSATISFIABLE ||
                dataType == NodeType.DONT_KNOW;
    }

    private void markNode(NodeType type, NodeData nodeData) {
        dataType = type;
        data = nodeData;

        if (shouldUpdateNumOpen()) {
            decrementOpenOnParent();
        }
    }

    public void markDecisionNode(Instruction branchInsn, InstructionBranch[] nextInstructions) {
        // Should only mark an unknown node as decision
        assert dataType == NodeType.VIRGIN || dataType == NodeType.DONT_KNOW;

        markNode(NodeType.DECISION, new DecisionData(this, branchInsn, nextInstructions));
    }

    public void markResultNode(PathResult result) {
        // Should only mark an unknown node as result
        assert dataType == NodeType.VIRGIN || dataType == NodeType.DONT_KNOW;

        markNode(NodeType.RESULT, new ResultData(result));
    }

    public void markDontKnowNode() {
        // Should only mark an unexplored node as dont-know
        // Maybe it's possible to mark a DONT_KNOW node again when the decision call invokes failCurrentTarget and
        // the current target was already marked as DONT_KNOW. This will result that we decrement open children
        // multiple times.
        assert dataType == NodeType.VIRGIN;

        markNode(NodeType.DONT_KNOW, DontKnowData.getInstance());
    }

    public void markUnsatisfiableNode() {
        // Should only mark an unexplored node as unsatisfiable
        assert dataType == NodeType.VIRGIN;

        markNode(NodeType.UNSATISFIABLE, UnsatisfiableData.getInstance());
    }

}
