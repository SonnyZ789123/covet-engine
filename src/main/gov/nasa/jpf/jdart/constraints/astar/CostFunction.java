package gov.nasa.jpf.jdart.constraints.astar;

public interface CostFunction<T extends GraphNode> {
    double computeCost(T from, T to);
}
