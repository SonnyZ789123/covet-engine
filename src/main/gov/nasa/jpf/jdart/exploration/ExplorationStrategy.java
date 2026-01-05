package gov.nasa.jpf.jdart.exploration;

import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.jdart.constraints.InternalConstraintsTree;
import gov.nasa.jpf.vm.MethodInfo;

public interface ExplorationStrategy {

    Valuation findNext(InternalConstraintsTree ctx, MethodInfo methodInfo);

}
