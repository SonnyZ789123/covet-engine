package gov.nasa.jpf.jdart.constraints.tree;

import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.jdart.constraints.PathResult;
import gov.nasa.jpf.vm.Instruction;

public final class Node {
    private final Node parent;
    private final int depth;
    /** Alternative depth - how many times have we explored this node */
    private int altDepth;

    private NodeData data;


    public Node(Node parent) {
        this.parent = parent;
        this.depth = (parent != null) ? parent.depth + 1 : 0;
        this.altDepth = (parent != null) ? parent.altDepth : 0;
    }

    public int incAltDepth() {
        return ++altDepth;
    }

    public boolean isVirgin() {
        return (data == null);
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

    public boolean hasData() {
        return (data != null);
    }

    public boolean dataIsDecisionData() {
        return hasData() && data.getClass().getName().equals(DecisionData.class.getName());
    }

    public boolean dataIsUnsatisfiableData() {
        return hasData() && data.getClass().getName().equals(UnsatisfiableData.class.getName());
    }

    public boolean dataIsResultData() {
        return hasData() && data.getClass().getName().equals(ResultData.class.getName());
    }

    public boolean dataIsDontKnowData() {
        return hasData() && data.getClass().getName().equals(DontKnowData.class.getName());
    }

    public DecisionData decisionData() {
        if(data == null || data.getClass() != DecisionData.class)
            return null;
        return (DecisionData)data;
    }

    public boolean isOpen() {
        DecisionData dec = decisionData();
        if(dec == null)
            return (data == null);
        return dec.hasOpen();
    }

    public boolean isExhausted() {
        DecisionData dec = decisionData();
        if(dec == null)
            return (data != null && data.getClass() != DontKnowData.class); // Dont know is not exhausted, all other forms of data are

        return !dec.hasUnexhausted();
    }

    public boolean hasDecisionData() {
        if(data == null || data.getClass() == DontKnowData.class)
            return false;

        //if(data.getClass() == DecisionData.class)
        return true;
        //throw new IllegalArgumentException("Querying non-decision node (depth: "+ depth +
        //        ") about decision data! " + data.getClass());
    }

    public DecisionData decision(Instruction branchInsn, Expression<Boolean>[] constraints, boolean explore) {
        if(!hasDecisionData()) {
            DecisionData dec = new DecisionData(this, branchInsn, constraints, explore);
            data = dec;
            return dec;
        }

        DecisionData dec = (DecisionData)data;
        dec.verifyDecision(branchInsn, constraints);

        return dec;
    }

    public ResultData result(PathResult result) {
        if(data == null || data.getClass() == DontKnowData.class) {
            ResultData res = new ResultData(result);
            data = res;
            return res;
        }

        //throw new IllegalStateException("Attempting to finish already explored path (data = " + data.getClass().getName() + "!");
        return null;
    }

    public DontKnowData dontKnow() {
        if(data == null) {
            DontKnowData dk = DontKnowData.getInstance();
            data = dk;
            return dk;
        }

        if(data.getClass() != DontKnowData.class) {
            //System.err.println("Attempting to fail already explored path!");
            return null;
        }
        return (DontKnowData)data;
    }

    public UnsatisfiableData unsatisfiable() {
        if(data == null) {
            UnsatisfiableData dk = UnsatisfiableData.getInstance();
            data = dk;
            return dk;
        }

        //throw new IllegalStateException("Attempting to fail already explored path!");
        return null;
    }

}
