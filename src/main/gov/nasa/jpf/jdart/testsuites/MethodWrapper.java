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

import gov.nasa.jpf.jdart.constraints.Path;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 *
 */
public class MethodWrapper {

  private final Path path;
  private final Method method;
  private final String callBase;
  private final ParameterAssignment parameterAssignment;
  private final MethodChecks checks;

  public MethodWrapper(
          Path path,
          Method method,
          ParameterAssignment parameterAssignment
  ) {
    this.path = path;
    this.method = method;
    boolean isStaticMethod = Modifier.isStatic(method.getModifiers());
    this.callBase = isStaticMethod ? method.getDeclaringClass().getName() + "." + method.getName() : method.getName();
    this.parameterAssignment = parameterAssignment;
    this.checks = createChecks();
  }

  /**
   * @return the call
   */
  public String getCall() {
    return this.callBase + this.parameterAssignment.getParameterString();
  }
  
  public MethodChecks getCheck() {
    return this.checks;
  }

  public String getReturnType() {
    return method.getReturnType().getName();
  }

  private MethodChecks createChecks() {
    boolean isStaticMethod = Modifier.isStatic(method.getModifiers());

    MethodChecks checks = new MethodChecks();

    StringBuilder sb = new StringBuilder();
    switch (this.path.getState()) {
      case OK:
        sb.append("assertEquals(").append(path.getPostCondition().getReturn().conc).append(", result)");
        checks.addCheck(sb.toString());
        break;
      case ERROR:
        checks.setExpectedException(path.getErrorResult().getExceptionClass());
        break;
    }

    if (!isStaticMethod) {
      checks.setClassName(method.getDeclaringClass().getName());
    }

    return checks;
  }
  
}
