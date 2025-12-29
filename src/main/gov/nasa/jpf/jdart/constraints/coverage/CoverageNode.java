package gov.nasa.jpf.jdart.constraints.coverage;

import java.util.Objects;

public final class CoverageNode implements CoverageGraphNode {
    private final String id;
    private final String label;
    private final double weight;
    private final boolean isCovered;

    private CoverageNode(String id, String label, double weight, boolean isCovered) {
        this.id = id;
        this.label = label;
        this.weight = weight;
        this.isCovered = isCovered;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public double getWeight() {
        return weight;
    }

    public boolean getIsCovered() {
        return isCovered;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CoverageNode that = (CoverageNode) o;
        return Objects.equals(id, that.id);
    }
}