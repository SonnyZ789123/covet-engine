package gov.nasa.jpf.jdart.constraints.tree;

import gov.nasa.jpf.jdart.constraints.PathResult;

public final class ResultData extends AbstractResultData {

    private final PathResult result;

    public ResultData(PathResult result) {
        this.result = result;
    }

    @Override
    public PathResult getResult() {
        return result;
    }

}