package gov.nasa.jpf.jdart.constraints.coverage;

import java.util.Set;
import java.util.stream.Collectors;

public class CoverageGraph<N extends CoverageGraphNode, E extends CoverageGraphEdge> {
    private final Set<N> nodes;

    private final Set<E> edges;

    public CoverageGraph(Set<N> nodes, Set<E> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    public Set<N> getNodes() {
        return nodes;
    }

    public Set<E> getEdges() {
        return edges;
    }

    public N getNode(String id) {
        return nodes.stream()
                .filter(node -> node.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public Set<E> getEdgesFrom(N node) {
        return edges.stream()
                .filter(edge -> edge.getFrom().equals(node.getId()))
                .collect(Collectors.toSet());
    }

    public N getRoot() {
        Set<N> nodesWithParent = edges.stream()
                .map(E::getTo)
                .map(this::getNode)
                .collect(Collectors.toSet());

        return nodes.stream()
                .filter(node -> !nodesWithParent.contains(node))
                .findFirst()
                .orElse(null);
    }
}

