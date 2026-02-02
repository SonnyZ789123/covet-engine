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
import gov.nasa.jpf.jdart.config.ParamConfig;
import gov.nasa.jpf.jdart.constraints.Path;
import gov.nasa.jpf.util.JPFLogger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class TestSuiteGenerator {

  private static final JPFLogger testGenerationLogger = JPF.getLogger("jdart.testsuites");
  private static final JPFLogger logger = JPF.getLogger("jdart");

  private final TestSuite suite;
  
  private final String outDir;

  public TestSuiteGenerator(TestSuite suite, String outDir) {
    this.suite = suite;
    this.outDir = outDir;
  }  
  
  public void generate() throws IOException {
    TestSuiteSTWriter writer = new TestSuiteSTWriter(suite, outDir);
    writer.write();
    logger.info("[TestSuiteGenerator] Test suite generated at: " + outDir);
  }
  
  public void run() {
    throw new IllegalStateException("not implemented yet.");
  }
  
  public static TestSuiteGenerator fromAnalysis(CompletedAnalysis analysis, Config conf) {
    String dir = conf.getString("jdart.tests.dir");
    String pkg = conf.getString("jdart.tests.pkg");
    ConcolicMethodConfig mc = analysis.getMethodConfig();

    Method targetMethod = getTargetMethod(mc, conf.getStringArray("classpath"));

    String suiteName = conf.getString("jdart.tests.suitename",
            targetMethod.getDeclaringClass().getSimpleName() +
                    Character.toUpperCase(mc.getMethodName().charAt(0)) +
                    mc.getMethodName().substring(1) + "Test");

    if (pkg == null || pkg.isEmpty()) {
      pkg = targetMethod.getDeclaringClass().getPackage().getName();
    }

    ArrayList<TestCase> tests = new ArrayList<>();
    for (Path p : analysis.getConstraintsTree().getAllPaths()) {
      Valuation val = p.getValuation();
      testGenerationLogger.finest("[TestSuiteGenerator] Generating test case for path: " + p + ((val == null) ? " -> ignore" : ""));
      if (val == null) {
        // dont know and ignore path results
        continue;
      }

      TestExecutionPath mw = new TestExecutionPath(
              p, targetMethod, new ParameterAssignment(analysis.getInitParams(), mc.getParams(), val));
      TestCase tc = new TestCase(mw);
      tests.add(tc);
    }

    TestSuite suite = new TestSuite(pkg, suiteName, targetMethod, tests);
    return new TestSuiteGenerator(suite, dir);
  }

  private static Method getTargetMethod(ConcolicMethodConfig mc, String[] classpath) {
    try {
      URL[] urls = new URL[classpath.length];
      for (int i = 0; i < classpath.length; i++) {
        urls[i] = new File(classpath[i]).toURI().toURL();
      }

      try (URLClassLoader cl = new URLClassLoader(urls)) {
        Class<?> cls = cl.loadClass(mc.getClassName());

        List<ParamConfig> params = mc.getParams();
        Class<?>[] paramTypes = new Class<?>[params.size()];

        for (int i = 0; i < params.size(); i++) {
          paramTypes[i] = toJavaClass(params.get(i).getType(), cl);
        }

        return cls.getDeclaredMethod(mc.getMethodName(), paramTypes);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Class<?> toJavaClass(String type, ClassLoader cl) throws ClassNotFoundException {
    switch (type) {
      case "boolean": return boolean.class;
      case "byte":    return byte.class;
      case "char":    return char.class;
      case "short":   return short.class;
      case "int":     return int.class;
      case "long":    return long.class;
      case "float":   return float.class;
      case "double":  return double.class;
    }

    if (type.endsWith("[]")) {
      String elem = type.substring(0, type.length() - 2);
      return java.lang.reflect.Array
              .newInstance(toJavaClass(elem, cl), 0)
              .getClass();
    }

    // reference type
    return Class.forName(type, false, cl);
  }


}
