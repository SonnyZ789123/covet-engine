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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 *
 */
public class MethodWrapper {

  private final Method method;
  private final String callBase;
  private final ParameterAssignment parameterAssignment;
  private final String precondition;
  private final MethodChecks checks;

  public MethodWrapper(
          Method method,
          String precondition,
          MethodChecks checks,
          ParameterAssignment parameterAssignment
  ) {
    this.method = method;
    boolean isStaticMethod = Modifier.isStatic(method.getModifiers());
    this.callBase = isStaticMethod ? method.getDeclaringClass().getName() + "." + method.getName() : method.getName();
    this.parameterAssignment = parameterAssignment;
    this.precondition = precondition;
    this.checks = checks;
  }

  /**
   * @return the call
   */
  public String getCall() {
    return this.callBase + this.parameterAssignment.getParameterString();
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
  
}
