package gov.nasa.jpf.jdart.constraints.tree;

import gov.nasa.jpf.jdart.constraints.PathResult;
import gov.nasa.jpf.vm.Instruction;

public final class Node {
    private final Node parent;
    private final int depth;
    /** Alternative depth - how many times have we explored this node */
    private int altDepth;

    private NodeData data;
    private NodeType dataType;

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

    public DecisionData decision(Instruction branchInsn, InstructionBranch[] nextInstructions, boolean explore) {
        if (hasUnknownData()) {
            markDecisionNode(branchInsn, nextInstructions, explore);
            return (DecisionData) data;
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

        dec.decrementOpen();

        if (!dec.hasOpen()) {
            decrementOpenOnParent();
        }
    }

    private boolean isBranchEndNode() {
        return dataType == NodeType.RESULT ||
                dataType == NodeType.UNSATISFIABLE ||
                dataType == NodeType.DONT_KNOW;
    }

    private void markNode(NodeType type, NodeData nodeData) {
        dataType = type;
        data = nodeData;

        if (isBranchEndNode()) {
            decrementOpenOnParent();
        }
    }

    public void markDecisionNode(Instruction branchInsn, InstructionBranch[] nextInstructions, boolean explore) {
        // Should only mark an unknown node as decision
        assert dataType == NodeType.VIRGIN || dataType == NodeType.DONT_KNOW;

        markNode(NodeType.DECISION, new DecisionData(this, branchInsn, nextInstructions, explore));
    }

    public void markResultNode(PathResult result) {
        // Should only mark an unknown node as result
        assert dataType == NodeType.VIRGIN || dataType == NodeType.DONT_KNOW;

        markNode(NodeType.RESULT, new ResultData(result));
    }

    public void markDontKnowNode() {
        // Should only mark an unexplored node as dont-know
        assert dataType == NodeType.VIRGIN;

        markNode(NodeType.DONT_KNOW, data);
    }

    public void markUnsatisfiableNode() {
        // Should only mark an unexplored node as dont-know
        assert dataType == NodeType.VIRGIN;

        markNode(NodeType.UNSATISFIABLE, UnsatisfiableData.getInstance());
    }

}
