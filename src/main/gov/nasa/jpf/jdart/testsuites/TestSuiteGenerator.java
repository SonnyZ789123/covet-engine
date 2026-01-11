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

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.jdart.CompletedAnalysis;
import gov.nasa.jpf.jdart.config.ConcolicMethodConfig;
import gov.nasa.jpf.jdart.constraints.Path;
import gov.nasa.jpf.util.JPFLogger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

/**
 *
 */
public class TestSuiteGenerator {

  private static JPFLogger logger = JPF.getLogger("jdart.testsuites");
  
  private final TestSuite suite;
  
  private final String suiteName;
  
  private final String packageName;
  
  private final String outDir;

  public TestSuiteGenerator(TestSuite suite, String suiteName, String packageName, String outDir) {
    this.suite = suite;
    this.suiteName = suiteName;
    this.packageName = packageName;
    this.outDir = outDir;
  }  
  
  public void generate() throws IOException {
    for (TestSubSuite sub : suite) {
      TestSuiteFileST writer = new TestSuiteFileST(sub, outDir);
      writer.writeTestSuiteFile(
              TestSuiteGenerator.class.getResourceAsStream("/gov/nasa/jpf/jdart/testsuites/TestSuite.st"));
    }
        
  }
  
  public void run() {
    throw new IllegalStateException("not implemented yet.");
  }
  
  public static TestSuiteGenerator fromAnalysis(CompletedAnalysis analysis, Config conf) {
    String dir = conf.getString("jdart.tests.dir");
    String pkg = conf.getString("jdart.tests.pkg");
    ConcolicMethodConfig mc = analysis.getMethodConfig();
    String suiteName = conf.getString("jdart.tests.suitename",
            Character.toUpperCase(mc.getMethodName().charAt(0)) +
                    mc.getMethodName().substring(1) + "Test");

    Method targetMethod = getTargetMethod(mc, conf.getStringArray("classpath"));

    ArrayList<TestCase> tests = new ArrayList<>();
    for (Path p : analysis.getConstraintsTree().getAllPaths()) {
      logger.finest("[TestSuiteGenerator] Generating test case for path: " + p);
      Valuation val = p.getValuation();
      if (val == null) {
        // dont know cases
        continue;
      }

      MethodWrapper mw = new MethodWrapper(
              targetMethod, new ParameterAssignment(analysis.getInitParams(), mc.getParams(), val));
      TestCase tc = new TestCase(mw);
      tests.add(tc);
    }

    TestSuite suite = new TestSuite(pkg, suiteName, tests);
    return new TestSuiteGenerator(suite, suiteName, pkg, dir);
  }

  private static Method getTargetMethod(ConcolicMethodConfig mc, String[] classpath) {
    try {
      URL[] urls = new URL[classpath.length];
      int i = 0;
      for(String p : classpath)
        urls[i++] = new File(p).toURI().toURL();

      URLClassLoader uc = new URLClassLoader(urls);
      Class<?> cls = uc.loadClass(mc.getClassName());
      //Class<?> cls = uc.loadClass(conf.getTarget());
      //overloading not supported -- if necessary, make map from types in
      //jdart method spec to parametertypes in Class.getmethod
      Method[] ms = cls.getMethods();

      Method targetMethod = null;
      for(Method m : ms) {
        if(m.getName().equals(mc.getMethodName())) {
          targetMethod = m;
          break;
        }
      }

      uc.close();

      assert targetMethod != null;
      return targetMethod;
    } catch (ClassNotFoundException | IOException e) {
      throw new RuntimeException(e);
    }
  }

}
