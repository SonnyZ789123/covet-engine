package gov.nasa.jpf.jdart.constraints.tree;

import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.vm.Instruction;

public final class DecisionData extends NodeData {
    private final Node node;
    private final Instruction branchInsn;
    private final Expression<Boolean>[] constraints;
    private final Node[] children;
    private int numOpen;
    private int numUnexhausted;


    public DecisionData(Node node, Instruction branchInsn, Expression<Boolean>[] constraints, boolean explore) {
        this.node = node;
        this.branchInsn = branchInsn;
        this.constraints = constraints;
        this.children = new Node[constraints.length];
        this.numUnexhausted = constraints.length;

        if(!explore) {
            for(int i = 0; i < constraints.length; i++) {
                this.children[i] = new Node(node);
                this.children[i].markDontKnowNode();
            }
            this.numOpen = 0;
        }
        else {
            this.numOpen = constraints.length;
        }
    }

    public Expression<Boolean>[] getConstraints() {
        return constraints;
    }

    public int getNumOpen() {
        return numOpen;
    }

    public int getNumUnexhausted() {
        return numUnexhausted;
    }

    public Node[] getChildren() {
        return children;
    }

    public Expression<Boolean> getConstraint(int idx) {
        return constraints[idx];
    }

    public boolean hasChild(int idx) {
        return (children[idx] != null);
    }

    public Node getChild(int idx) {
        return children[idx];
    }

    public Node getOrCreateChild(int idx) {
        if(!hasChild(idx)) {
            children[idx] = new Node(node);
        }
        return children[idx];
    }

    public boolean hasUnexhausted() {
        return (numUnexhausted > 0);
    }

    public boolean hasOpen() {
        return (numOpen > 0);
    }

    public int nextOpenChild() {
        if(numOpen == 0)
            return -1;

        for(int i = 0; i < constraints.length; i++) {
            Node n = children[i];
            if(n == null || n.isOpen())
                return i;
        }

        return -1;
    }

    public void decrementOpen() {
        numOpen--;
    }

    public void decrementUnexhausted() {
        numUnexhausted--;
    }

    public void verifyDecision(Instruction branchInsn, Expression<Boolean>[] constraints) {
        if(branchInsn != this.branchInsn)
            throw new IllegalStateException("Same decision, but different branching instruction!");
        if(constraints != null && constraints.length == this.constraints.length)
            throw new IllegalStateException("Same decision, but different number of constraints!");
    }

}
