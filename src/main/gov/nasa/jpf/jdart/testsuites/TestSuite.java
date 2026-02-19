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

import static java.lang.Math.min;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 *
 */
public class TestSuite implements Iterable<TestSubSuite> {

  private final String packageName;
  private final String suiteName;
  private final Method methodUT;
  /** Maximum number of test cases per sub-suite */
  private int subSuiteSize = 1000;

  private final List<TestCase> testCases = new LinkedList<>();

  public TestSuite(String packageName, String suiteName, Method methodUT, Collection<TestCase> tests) {
    this.packageName = packageName;
    this.suiteName = suiteName;
    this.methodUT = methodUT;
    this.testCases.addAll(tests);
  }

  public TestSuite(String packageName, String suiteName, Method methodUT, Collection<TestCase> tests, int subSuiteSize) {
    this.packageName = packageName;
    this.suiteName = suiteName;
    this.methodUT = methodUT;
    this.subSuiteSize = subSuiteSize;
    this.testCases.addAll(tests);
  }

  public String getPackageName() {
    return packageName;
  }

  public Method getMethodUT() {
    return methodUT;
  }

  // Used for writing test suite files
  public Iterator<TestSubSuite> iterator() {    
    List<TestSubSuite> subSuites = new LinkedList<>();

    // For only one sub-suite, don't append an index to the name
    boolean singleSubSuite = this.testCases.size() <= this.subSuiteSize;

    int i = 0;
    for (int offset=0 ; offset < this.testCases.size() ; offset += this.subSuiteSize) {
      TestSubSuite sub = new TestSubSuite(
              singleSubSuite ? this.suiteName : this.suiteName + i,
              this.testCases.subList(offset, min(offset + this.subSuiteSize, this.testCases.size())
              ));
      subSuites.add(sub);
      i += 1;
    }    
    return subSuites.iterator();
  }
  
  
}
