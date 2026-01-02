package gov.nasa.jpf.jdart.constraints.coverage.graph;

public interface CoverageGraphEdge {
    public String getFrom();

    public String getTo();

    public String getLabel();

    public Integer getBranchIdx();
}
