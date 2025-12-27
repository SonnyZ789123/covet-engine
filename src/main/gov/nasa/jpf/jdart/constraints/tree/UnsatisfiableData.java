package gov.nasa.jpf.jdart.constraints.tree;

public final class UnsatisfiableData extends NodeData {
    private static final UnsatisfiableData INSTANCE = new UnsatisfiableData();

    public static UnsatisfiableData getInstance() {
        return INSTANCE;
    }
}
