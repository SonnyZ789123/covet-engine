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
import gov.nasa.jpf.jdart.constraints.PathState;
import org.stringtemplate.v4.ST;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class TestExecutionPath {

  private final Path path;
  private final Method method;
  private final boolean isStaticMethod;
  private final String callBase;
  private final ParameterAssignment parameterAssignment;
  private final List<String> assertions = new ArrayList<>();

  public TestExecutionPath(
          Path path,
          Method method,
          ParameterAssignment parameterAssignment
  ) {
    this.path = path;
    this.method = method;
    this.isStaticMethod = Modifier.isStatic(method.getModifiers());
    this.callBase = isStaticMethod ? method.getDeclaringClass().getName() + "." + method.getName() : method.getName();
    this.parameterAssignment = parameterAssignment;
    fillMethodAssertions();
  }

  /**
   * @return the call
   */
  public String getCall() {
    return this.callBase + this.parameterAssignment.getParameterString();
  }

  /**
   * Used by the string template
   *
   * @return the assertions for the method
   */
  public List<String> getAssertions() {
    return this.assertions;
  }

  /**
   * Used by the string template
   *
   * @return the return type of the method
   */
  public String getReturnType() {
    return method.getReturnType().getName();
  }

  /**
   * Used by the string template
   *
   * @return whether the test is expected to throw an exception
   */
  public boolean isThrowsExceptionTest() {
    return this.path.getState() == PathState.ERROR;
  }

  /**
   * Used by the string template
   *
   * @return whether the method under test is static
   */
  public boolean isStaticMethod() {
    return this.isStaticMethod;
  }

  /**
   * Used by the string template
   *
   * @return the class name where the method under test is declared
   */
  public String getClassName() {
    return method.getDeclaringClass().getName();
  }

  private void fillMethodAssertions() {
    ST st;

    switch (this.path.getState()) {
      case OK:
        st = new ST("assertEquals(<expected>, result)");
        st.add("expected", path.getPostCondition().getReturn().conc);
        break;

      case ERROR:
        st = new ST(
                "assertThrows(<exception>.class, () -> {\n" +
                        "    <call>;\n" +
                        "})"
        );

        st.add("exception", path.getErrorResult().getExceptionClass());
        st.add("call", this.getCall());
        break;

      default:
        return;
    }

    assertions.add(st.render());
  }

}
