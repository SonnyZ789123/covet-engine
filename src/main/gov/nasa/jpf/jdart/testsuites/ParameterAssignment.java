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
            if (objVal == null) { // unsupported non-primitive type
                objVal = "null";
            }

            String mappedVal = mapPrimitiveValueToString(objVal);
            call.append(mappedVal).append(", ");
        }
        call = new StringBuilder(call.substring(0, call.length() - 2));

        call.append(")");
        return call.toString();
    }

    private static String mapPrimitiveValueToString(Object val) {
        if (val instanceof Character) {
            return "'" + val + "'";
        } else if (val instanceof Float) {
            return val + "f";
        } else if (val instanceof Long) {
            return val + "L";
        } else if (val instanceof Short) {
            return "(short) " + val;
        } else if (val instanceof Byte) {
            return "(byte) " + val;
        } else {
            return String.valueOf(val);
        }
    }

}