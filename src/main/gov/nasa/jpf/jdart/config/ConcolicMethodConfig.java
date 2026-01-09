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
package gov.nasa.jpf.jdart.config;

import gov.nasa.jpf.Config;

import java.io.IOException;
import java.util.List;

/**
 * Configuration for a single concolic (symbolic) method.
 *
 * <p>A {@code ConcolicMethodConfig} is created from properties in the JPF
 * configuration file using a method-specific prefix:
 *
 * <pre>
 *   concolic.method.&lt;id&gt;
 * </pre>
 *
 * where {@code <id>} is an arbitrary identifier (e.g. {@code foo}) used to
 * reference this method throughout JDart.
 *
 * <h2>Supported Configuration Properties</h2>
 *
 * <ul>
 *   <li><b>{@code concolic.method.<id>}</b><br>
 *       Type: {@code String}<br>
 *       Required: yes<br>
 *       Meaning: Method specification identifying the symbolic entry point.<br>
 *       Format:
 *       <pre>
 *         fully.qualified.Class.method(paramName:type,paramName:type,...)
 *       </pre>
 *       Example:
 *       <pre>
 *         concolic.method.foo = gov.nasa.jpf.MyClass.foo(c:char[], i:int)
 *       </pre>
 *   </li>
 *
 *   <li><b>{@code concolic.method.<id>.location}</b><br>
 *       Type: {@code String}<br>
 *       Required: no<br>
 *       Meaning: Optional bytecode or source location qualifier restricting
 *       where perturbation is applied.<br>
 *       Default: {@code null} (no restriction)
 *   </li>
 *
 *   <li><b>{@code concolic.method.<id>.config}</b><br>
 *       Type: {@code String}<br>
 *       Required: no<br>
 *       Meaning: Identifier of the {@link AnalysisConfig} block associated with
 *       this method.<br>
 *       Resolution:
 *       <pre>
 *         jdart.configs.&lt;configId&gt;.*
 *       </pre>
 *       If omitted, the analysis configuration defaults are used.
 *   </li>
 *
 *   <li><b>{@code concolic.method.<id>.values}</b><br>
 *       Type: {@code String}<br>
 *       Required: no<br>
 *       Meaning: Inline specification of initial concrete values or domains for
 *       method parameters.<br>
 *       Parsed by {@link ConcolicValuesFromConfig}.<br>
 *       Default: fully symbolic inputs
 *   </li>
 *
 *   <li><b>{@code concolic.method.<id>.valfile}</b><br>
 *       Type: {@code String} (file path)<br>
 *       Required: no<br>
 *       Meaning: Load concrete input values from an external file.<br>
 *       Parsed by {@link ConcolicValuesFromFile}.<br>
 *       Priority: Overrides {@code .values} if both are present.
 *   </li>
 * </ul>
 *
 * <h2>Notes</h2>
 *
 * <ul>
 *   <li>If neither {@code .values} nor {@code .valfile} is provided, all method
 *       parameters are treated as symbolic.</li>
 *   <li>The method {@code &lt;id&gt;} is used to generate JPF perturbation keys
 *       (e.g. {@code perturb.&lt;id&gt;.*}) and to group completed analyses.</li>
 *   <li>Validation of method specifications and value formats is delegated to
 *       {@link MethodSpec} and {@link ConcolicValues} implementations.</li>
 * </ul>
 */
public class ConcolicMethodConfig {

  /**
   * Reads a method configuration from the given config
   *
   * @param id The identifier (e.g., "foo") for this method config
   * @param prefix The prefix in the .jpf config file (e.g., "concolic.method.foo")
   * @param config The .jpf config to read from
   * @return the method configuration
   */
  public static ConcolicMethodConfig read(String id, String prefix, Config config) {
    String methodSpec = config.getProperty(prefix);
    // methodSpec is something like concolic.method.foo=gov.nasa.jpf.MyClass.foo(c:char[],i:int)
    MethodSpec ms = MethodSpec.parse(methodSpec);
    
    String location = config.getProperty(prefix + ".location");
    
    String configId = config.getProperty(prefix + ".config");
    
    AnalysisConfig ac = AnalysisConfig.read("jdart.configs." + configId, config);
    
    
    String valConf = config.getProperty(prefix + ".values");   
    ConcolicValues values = new ConcolicValuesFromConfig(ms, valConf);
    
    if (config.containsKey(prefix + ".valfile")) {
      values = new ConcolicValuesFromFile(config.getProperty(prefix + ".valfile"), ms);
    }
    
    return new ConcolicMethodConfig(id, ms, location, ac, values);
  }
  
  private final String id;
  private MethodSpec methodSpec;
  private String location;
  private AnalysisConfig analysisConfig;
  private final ConcolicValues concValues;
  
  public ConcolicMethodConfig(String id, MethodSpec methodSpec, String location, AnalysisConfig analysisConfig, ConcolicValues vals) {
    this.id = id;
    this.methodSpec = methodSpec;
    this.location = location;
    this.analysisConfig = analysisConfig;
    this.concValues = vals;
  }
  
  public String getId() {
    return id;
  }
  
  public AnalysisConfig getAnalysisConfig() {
    return analysisConfig;
  }
  
  public void setAnalysisConfig(AnalysisConfig ac) {
    this.analysisConfig = ac;
  }
  
  /**
   * @return the className
   */
  public String getClassName() {
    return methodSpec.getClassName();
  }
  

  /**
   * @return the methodName
   */
  public String getMethodName() {
    return methodSpec.getMethodName();
  }

  public List<ParamConfig> getParams() {
    return methodSpec.getParams();
  }
  
  public void setMethod(MethodSpec methodSpec) {
    this.methodSpec = methodSpec;
  }
  
  public String getLocation() {
    return location;
  }
  
  public void setLocation(String location) {
    this.location = location;
  }
  
  public void printJPFPerturb(Appendable a) throws IOException {
    print(a, false);
  }
  
  public String toJPFPerturbString() {
    StringBuilder sb = new StringBuilder();
    try {
      printJPFPerturb(sb);
      return sb.toString();
    }
    catch(IOException ex) {
      throw new RuntimeException(ex); // SHOULD NOT HAPPEN
    }
  }
  
  public void print(Appendable a, boolean includeNames) throws IOException {
    methodSpec.print(a, includeNames);
    if(location != null)
      a.append('@').append(location);
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    try {
      print(sb, true);
      return sb.toString();
    }
    catch(IOException ex) {
      throw new RuntimeException(ex); // SHOULD NOT HAPPEN
    } 
  }

  public static ConcolicMethodConfig create(String id, MethodSpec methodSpec, AnalysisConfig ac) {
    return new ConcolicMethodConfig(id, methodSpec, null, ac, new ConcolicValuesFromConfig(methodSpec, null));
  }

  public ConcolicValues getConcolicValues() {
    return this.concValues;
  }
}