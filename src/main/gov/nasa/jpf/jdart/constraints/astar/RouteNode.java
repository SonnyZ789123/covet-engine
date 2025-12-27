package gov.nasa.jpf.jdart.constraints.astar;

class RouteNode<T extends GraphNode> implements Comparable<RouteNode<T>> {
    private final T current;
    private T previous;
    /**
     * Exact score from start to this node, the g(n) value in A* algorithm
     */
    private double routeScore;
    /**
     * Estimated score from this node to the destination, the f(n) value in A* algorithm
     */
    private double estimatedScore;

    RouteNode(T current) {
        this(current, null, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    RouteNode(T current, T previous, double routeScore, double estimatedScore) {
        this.current = current;
        this.previous = previous;
        this.routeScore = routeScore;
        this.estimatedScore = estimatedScore;
    }

    public T getCurrent() {
        return current;
    }

    public T getPrevious() {
        return previous;
    }

    public void setPrevious(T previous) {
        this.previous = previous;
    }

    public double getRouteScore() {
        return routeScore;
    }

    public void setRouteScore(double routeScore) {
        this.routeScore = routeScore;
    }

    public double getEstimatedScore() {
        return estimatedScore;
    }

    public void setEstimatedScore(double estimatedScore) {
        this.estimatedScore = estimatedScore;
    }

    @Override
    public int compareTo(RouteNode other) {
        double f = this.routeScore + this.estimatedScore;
        double of = other.routeScore + other.estimatedScore;

        return Double.compare(f, of);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RouteNode<?> routeNode = (RouteNode<?>) o;

        // Two RouteNodes are considered equal if they refer to the same graph node
        return current.getId().equals(routeNode.current.getId());
    }
}