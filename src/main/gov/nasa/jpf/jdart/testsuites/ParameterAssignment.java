package gov.nasa.jpf.jdart.testsuites;

import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.jdart.config.ParamConfig;

import java.util.List;

public class ParameterAssignment {
    private final Object[] defaultParams;
    private final List<ParamConfig> params;
    private final Valuation val;

    public ParameterAssignment(Object[] defaultParams, List<ParamConfig> params, Valuation val) {
        this.defaultParams = defaultParams;
        this.params = params;
        this.val = val;
    }

    public String getParameterString() {
        StringBuilder call = new StringBuilder("(");

        if (params.size() <= 0) {
            call.append(")");
            return call.toString();
        }

        for (int i = 0; i < params.size(); i++) {
            ParamConfig pc = params.get(i);
            Object objVal = val.getValue(pc.getName());
            if (objVal == null) {  //the parameter is treated as concrete
                objVal = defaultParams[i];
            }
            call.append(objVal).append((objVal instanceof Float) ? "f" : "").append(",");
        }
        call = new StringBuilder(call.substring(0, call.length() - 1));

        call.append(")");
        return call.toString();
    }

}