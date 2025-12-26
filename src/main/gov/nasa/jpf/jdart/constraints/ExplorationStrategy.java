package gov.nasa.jpf.jdart.constraints;

import gov.nasa.jpf.constraints.api.Valuation;

public interface ExplorationStrategy {

    Valuation findNext(InternalConstraintsTree ctx);

}
