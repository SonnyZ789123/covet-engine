package gov.nasa.jpf.jdart.exploration.coverage;

import gov.nasa.jpf.jdart.constraints.tree.Node;

public class WeightedNode {

    private final Node node;
    private final double weight;

    public WeightedNode(Node node, double weight) {
        this.node = node;
        this.weight = weight;
    }

    public Node getNode() {
        return node;
    }

    public double getWeight() {
        return weight;
    }
}

