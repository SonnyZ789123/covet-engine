package gov.nasa.jpf.jdart.constraints.astar;

import java.util.*;

public class AStar<T extends GraphNode> {
    private final Graph<T> graph;
    private final CostFunction<T> nextNodeCostFunction;
    private final CostFunction<T> targetCostFunction;

    public AStar(Graph<T> graph,
                 CostFunction<T> nextNodeCostFunction,
                 CostFunction<T> targetCostFunction) {
        this.graph = graph;
        this.nextNodeCostFunction = nextNodeCostFunction;
        this.targetCostFunction = targetCostFunction;
    }

    public List<T> findPath(T from, T to) {
        // Open set of nodes to consider ordered by estimated score g(n) + f(n)
        Queue<RouteNode<T>> openSet = new PriorityQueue<>();
        // Closed set of fully expanded nodes
        Set<T> closedSet = new HashSet<>();

        Map<T, RouteNode<T>> routeNodeMap = new HashMap<>();

        RouteNode<T> start = new RouteNode<>(
                from,
                null,
                0d,
                targetCostFunction.computeCost(from, to)
        );

        openSet.add(start);
        routeNodeMap.put(from, start);

        while (!openSet.isEmpty()) {
            // Get node with the lowest estimated score, and removes it from the queue
            RouteNode<T> next = openSet.poll();

            // Reached destination
            if (next.getCurrent().equals(to)) {
                List<T> route = new ArrayList<>();
                RouteNode<T> current = next;

                // Reconstruct the path by backtracking
                do {
                    route.add(0, current.getCurrent());
                    current = routeNodeMap.get(current.getPrevious());
                } while (current != null);
                return route;
            }

            // Mark node as fully expanded
            closedSet.add(next.getCurrent());

            graph.getEdges(next.getCurrent()).forEach(connection -> {
                // Do not reopen already expanded nodes
                if (closedSet.contains(connection)) {
                    return;
                }

                RouteNode<T> nextNode =
                        routeNodeMap.computeIfAbsent(connection, RouteNode::new);

                double newScore =
                        next.getRouteScore()
                                + nextNodeCostFunction.computeCost(next.getCurrent(), connection);

                if (newScore < nextNode.getRouteScore()) {
                    nextNode.setPrevious(next.getCurrent());
                    nextNode.setRouteScore(newScore);
                    nextNode.setEstimatedScore(
                            newScore + targetCostFunction.computeCost(connection, to)
                    );

                    // Reinsert to restore PriorityQueue ordering
                    openSet.remove(nextNode); // Sanity check, otherwise it may contain duplicates
                    openSet.add(nextNode);
                }
            });
        }

        return null;
    }
}
