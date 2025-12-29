package gov.nasa.jpf.jdart.constraints.coverage;

import java.util.*;

public class CoverageHeuristic<N extends CoverageGraphNode, E extends CoverageGraphEdge> {
    private final CoverageGraph<N, E> coverageGraph;

    public CoverageHeuristic(CoverageGraph<N, E> coverageGraph) {
        this.coverageGraph = coverageGraph;
    }

    public Set<List<E>> getAllPathsToUncoveredNodes() {
        N root = coverageGraph.getRoot();
        return getPathsToUncoveredNodes(root, 0);
    }

    public Set<List<E>> getPathsToUncoveredNodes(N startNode, int depth) {
        Set<E> outgoingEdges = coverageGraph.getEdgesFrom(startNode);
        Set<List<E>> paths = new HashSet<>();

        outgoingEdges.forEach(currentEdge -> {
            // TODO: Check depth limit in AnalysisConfig

            N toNode = coverageGraph.getNode(currentEdge.getTo());
            if (!toNode.getIsCovered()) {
                List<E> path = new ArrayList<>();
                path.add(currentEdge);
                paths.add(path);
            } else {
                // Recursively explore further
                Set<List<E>> nextPaths = getPathsToUncoveredNodes(toNode, depth + 1);

                for (List<E> nextPath : nextPaths) {
                    List<E> newPath = new ArrayList<>();
                    newPath.add(currentEdge);
                    newPath.addAll(nextPath);
                    paths.add(newPath);
                }
            }
        });

        return paths;
    }
}
