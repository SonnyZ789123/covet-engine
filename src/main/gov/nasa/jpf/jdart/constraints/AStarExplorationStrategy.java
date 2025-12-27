package gov.nasa.jpf.jdart.constraints;

import gov.nasa.jpf.constraints.api.Valuation;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class AStarExplorationStrategy implements ExplorationStrategy {

    @Override
    public Valuation findNext(InternalConstraintsTree ctx) {
        throw new NotImplementedException();
    }
}
