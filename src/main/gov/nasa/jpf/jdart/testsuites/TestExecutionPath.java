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
  private final String callBase;
  private final ParameterAssignment parameterAssignment;
  private final List<String> assertions = new ArrayList<>();

  public TestExecutionPath(
          Path path,
          Method method,
          ParameterAssignment parameterAssignment
  ) {
    this.path = path;
    boolean isStaticMethod = Modifier.isStatic(method.getModifiers());
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
   * @return whether the test is expected to throw an exception
   */
  public boolean isThrowsExceptionTest() {
    return this.path.getState() == PathState.ERROR;
  }

  /**
   * Used by the string template
   *
   * @return the exception class name if the test is expected to throw an exception, null otherwise
   */
  public String getExceptionClass() {
    if (this.path.getState() == PathState.ERROR) {
      return this.path.getErrorResult().getExceptionClass();
    }
    return null;
  }

  private void fillMethodAssertions() {
    if (this.path.getState() == PathState.OK) {
      ST st = new ST("assertEquals(<expected>, result)");
      st.add("expected", path.getPostCondition().getReturn().conc);
      assertions.add(st.render());
    }
  }

}
