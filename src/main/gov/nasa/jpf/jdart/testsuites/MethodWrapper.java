/*
 * Copyright (C) 2015, United States Government, as represented by the 
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 * 
 * The PSYCO: A Predicate-based Symbolic Compositional Reasoning environment 
 * platform is licensed under the Apache License, Version 2.0 (the "License"); you 
 * may not use this file except in compliance with the License. You may obtain a 
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0. 
 * 
 * Unless required by applicable law or agreed to in writing, software distributed 
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR 
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the 
 * specific language governing permissions and limitations under the License.
 */
package gov.nasa.jpf.jdart.testsuites;

import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.jdart.config.ParamConfig;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 *
 */
public class MethodWrapper {

  private final Method method;
    
  private final String callBase;

  private final String parameterString;

  private final Object[] defaultParams;

  private final List<ParamConfig> params;

  private final Valuation val;

  private final String precondition;

  private final MethodChecks checks;

  public MethodWrapper(
          Method method,
          String precondition,
          MethodChecks checks,
          Object[] defaultParams,
          List<ParamConfig> params,
          Valuation val) {
    this.method = method;
    boolean isStaticMethod = Modifier.isStatic(method.getModifiers());
    this.callBase = isStaticMethod ? method.getDeclaringClass().getName() + "." + method.getName() : method.getName();
    this.parameterString = getParameterString(defaultParams, params, val);
    this.defaultParams = defaultParams;
    this.params = params;
    this.val = val;
    this.precondition = precondition;
    this.checks = checks;
  }

  /**
   * @return the call
   */
  public String getCall() {
    return this.callBase + this.parameterString;
  }

  /**
   * @return the precondition
   */
  public String getPrecondition() {
    return precondition;
  }
  
  public MethodChecks getCheck() {
    return this.checks;
  }

  private String getParameterString(Object[] initParams, List<ParamConfig> params, Valuation val) {
    StringBuilder call = new StringBuilder("(");

    if (params.size() <= 0) {
      call.append(")");
      return call.toString();
    }

    for (int i = 0; i < params.size(); i++) {
      ParamConfig pc = params.get(i);
      Object objVal = val.getValue(pc.getName());
      if (objVal == null) {  //the parameter is treated as concrete
        objVal = initParams[i];
      }
      call.append(objVal).append((objVal instanceof Float) ? "f" : "").append(",");
    }
    call = new StringBuilder(call.substring(0, call.length() - 1));

    call.append(")");
    return call.toString();
  }
  
}
