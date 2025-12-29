package gov.nasa.jpf.jdart.constraints.coverage;

import gov.nasa.jpf.jdart.constraints.CoverageHeuristic;

import java.util.Objects;

public final class CoverageEdge implements CoverageGraphEdge {
    private final String from;
    private final String to;
    private final String label;
    private final double weight;
    private final Integer branchIdx;

    private CoverageEdge(String from, String to, String label, double weight, Integer branchIdx) {
        this.from = from;
        this.to = to;
        this.label = label;
        this.weight = weight;
        this.branchIdx = branchIdx;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getLabel() {
        return label;
    }

    public double getWeight() {
        return weight;
    }

    public Integer getBranchIdx() {
        return branchIdx;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CoverageEdge that = (CoverageEdge) o;
        return Objects.equals(from, that.from) &&
                Objects.equals(to, that.to) &&
                Objects.equals(branchIdx, that.branchIdx) &&
                Objects.equals(label, that.label) &&
                Double.compare(weight, that.weight) == 0;
    }
}
