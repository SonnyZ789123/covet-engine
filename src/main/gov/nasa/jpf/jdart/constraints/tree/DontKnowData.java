package gov.nasa.jpf.jdart.constraints.tree;

import gov.nasa.jpf.jdart.constraints.PathResult;

public final class DontKnowData extends AbstractResultData {

    private static final DontKnowData INSTANCE = new DontKnowData();

    public static DontKnowData getInstance() {
        return INSTANCE;
    }

    @Override
    public PathResult getResult() {
        return PathResult.dontKnow();
    }

}
