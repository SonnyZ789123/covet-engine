package gov.nasa.jpf.jdart.constraints.coverage.graph;

public interface CoverageGraphNode {
    String getId();

    double getWeight();

    String getLabel();

    boolean getIsCovered();
}
