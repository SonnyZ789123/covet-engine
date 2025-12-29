package gov.nasa.jpf.jdart.constraints.coverage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CoverageHeuristic<N extends CoverageGraphNode, E extends CoverageGraphEdge> {
    private final CoverageGraph<N, E> coverageGraph;

    public CoverageHeuristic(CoverageGraph<N, E> coverageGraph) {
        this.coverageGraph = coverageGraph;
    }

    public Set<List<E>> getAllPathsToUncoveredNodes(N startNode, N goalNode) {
        Set<List<E>> allPaths = new HashSet<>();
        Set<N> uncoveredNodes = coverageGraph.getNodes().stream()
                .filter(node -> !node.getIsCovered())
                .collect(Collectors.toSet());
        N root = coverageGraph.getRoot();

        // TODO: descend the graph from the root to find all paths to uncovered nodes

        return allPaths;
    }

    public List<E> findShortestPath(N startNode, N goalNode) {
        // Implement Dijkstra's or A* algorithm to find the shortest path
        return null;
    }
}
