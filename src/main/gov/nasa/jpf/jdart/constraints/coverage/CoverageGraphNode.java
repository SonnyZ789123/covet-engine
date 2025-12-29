package gov.nasa.jpf.jdart.constraints.coverage;

public interface CoverageGraphNode {
    String getId();

    double getWeight();

    String getLabel();

    boolean getIsCovered();
}
