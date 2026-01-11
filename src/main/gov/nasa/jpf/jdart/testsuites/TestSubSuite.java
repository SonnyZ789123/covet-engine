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

import java.util.List;

/**
 * A sub-suite of tests belonging to a given test suite. This class groups the test-cases that are written to the same
 * Java file.
 */
public class TestSubSuite {

  private final String className;
  private final List<TestCase> testCases;

  public TestSubSuite(String className, List<TestCase> subList) {
    this.className = className;
    this.testCases = subList;
  }

  public String getClassName() {
    return this.className;
  }

  public List<TestCase> getTests() {
    return this.testCases;
  }
  
}
