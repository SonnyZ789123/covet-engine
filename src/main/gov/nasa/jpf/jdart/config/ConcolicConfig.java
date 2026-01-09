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
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.solvers.ConstraintSolverFactory;
import gov.nasa.jpf.constraints.types.TypeContext;
import gov.nasa.jpf.jdart.ConcolicPerturbator;
import gov.nasa.jpf.jdart.exploration.DFSStrategy;
import gov.nasa.jpf.jdart.exploration.ExplorationStrategy;
import gov.nasa.jpf.jdart.termination.NeverTerminate;
import gov.nasa.jpf.jdart.termination.TerminationStrategy;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuration for the concolic execution
 */
public class ConcolicConfig {

  /**
   * keys used in configuration
   */
  private static final String CONF_PREFIX = "concolic";
  
  /**
   * constraint solver used by method explorers
   */
  private ConstraintSolver solver;
  
  private TypeContext types = new TypeContext(true);
  
  protected final AnalysisConfig globalConfig = new AnalysisConfig();
  
  private final Map<String,ConcolicMethodConfig> concolicMethods
    = new HashMap<>();
  
    
  /**
   * strategy for terminating jdart
   */
  private TerminationStrategy termination;

  /**
   * strategy for exploring the symbolic execution tree
   */
  private ExplorationStrategy explorationStrategy;

  public ConcolicConfig() {
    
  }

  /**
   *
   * @param conf
   */
  public ConcolicConfig(Config conf) {
    initialize(conf);
  }
  
  /* ******************************************************************************
   * 
   * api
   * 
   */  
  
  public ConcolicConfig(ConcolicConfig other) {
    this.solver = other.solver;
    this.concolicMethods.putAll(other.concolicMethods);
    this.termination = other.termination;
    this.explorationStrategy = other.explorationStrategy;
  }

  /**
   * get method config for concolic method
   * 
   * @return 
   */
  public ConcolicMethodConfig getMethodConfig(String id) {
    ConcolicMethodConfig mc = concolicMethods.get(id);
    return mc;
  }
  
  public Collection<ConcolicMethodConfig> getMethodConfigs() {
    return concolicMethods.values();
  }
  
  public void setConstraintSolver(ConstraintSolver solver) {
    this.solver = solver;
  }
  
  /**
   * @return the solver
   */
  public ConstraintSolver getSolver() {
    return solver;
  }
  
  public TypeContext getTypes() {
    return types;
  }
  
  public TerminationStrategy getTerminationStrategy() {
    return this.termination;
  }

  public ExplorationStrategy getExplorationStrategy() {
      return this.explorationStrategy;
  }
  
  public Config generateJPFConfig() {
    return generateJPFConfig(null);
  }
    
  /** 
   * generates a jpf config corresponding to this configuration
   * 
   * @param conf
   * @return 
   */
  public Config generateJPFConfig(Config conf) {
    Config newConf = new Config("");
    if(conf != null) {
      newConf.putAll(conf);
      newConf.setClassLoader(conf.getClassLoader());
    }
    else {
      newConf.initClassLoader(JPF.class.getClassLoader());
    }
    
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for(ConcolicMethodConfig mc : concolicMethods.values()) {
      generatePerturbConfig(mc, newConf);
      if(first)
        first = false;
      else
        sb.append(',');
      sb.append(mc.getId());
    }
    
    newConf.setProperty("perturb.params", sb.toString());
    
    return newConf;
  }  
  
  
  private static void generatePerturbConfig(ConcolicMethodConfig mc, Config conf) {
    String perturbPrefix = "perturb." + mc.getId() + ".";
    String perturbMethod = mc.toJPFPerturbString();
    
    conf.setProperty(perturbPrefix + "method", perturbMethod);
    conf.setProperty(perturbPrefix + "class", ConcolicPerturbator.class.getName());
    
    String loc = mc.getLocation();
    if(loc != null)
      conf.setProperty(perturbPrefix + "location", loc);
  }
  

  public void registerConcolicMethod(ConcolicMethodConfig mc) {
    concolicMethods.put(mc.getId(), mc);
  }
  
  public void addConcolicMethod(String id, MethodSpec methodSpec, AnalysisConfig ac) {
    if(ac == null)
      ac = globalConfig;
    ConcolicMethodConfig cm = ConcolicMethodConfig.create(id, methodSpec, ac);
    registerConcolicMethod(cm);
  }
  
  public void addConcolicMethod(String id, String className, String methodName, AnalysisConfig ac, ParamConfig ...params) {
    addConcolicMethod(id, new MethodSpec(className, methodName, params), ac);
  }
  
  public void addConcolicMethod(String id, String methodSpec, AnalysisConfig ac) {
    addConcolicMethod(id, MethodSpec.parse(methodSpec), ac);
  }
  
  public void clearConcolicMethods() {
    concolicMethods.clear();
  }
  
  /* ******************************************************************************
   * 
   * private helpers
   * 
   */  
  
  
  /**
   * initialize object from config file
   * 
   * @param conf 
   */
  private void initialize(Config conf) {
    // create a constraint solver
    ConstraintSolverFactory cFactory = new ConstraintSolverFactory(conf);
    this.solver = cFactory.createSolver(conf);
    
    // parse symbolic method info
    if(conf.hasValue(CONF_PREFIX + ".method")) {
      String id = conf.getString(CONF_PREFIX + ".method");
      ConcolicMethodConfig mc = ConcolicMethodConfig.read(id, CONF_PREFIX + ".method." + id, conf);
      registerConcolicMethod(mc);
    }
    
    // parse termination
    this.termination = parseTerminationStrategy(conf);

    // parse explorer
    this.explorationStrategy = parseExplorationStrategy(conf);
  }
  
  public static TerminationStrategy parseTerminationStrategy(Config conf) {
    if (!conf.hasValue("jdart.termination")) {
      return new NeverTerminate();     
    }
    return parseTerminationStrategy(conf.getProperty("jdart.termination"));
  }
    
  public static TerminationStrategy parseTerminationStrategy(String line) {
    try {
      String[] opt = line.split("\\,");
      Class clazz = Class.forName(opt[0].trim());
      Object obj = null;
      if (opt.length <= 1) {
        obj = clazz.newInstance();         
      } else {
        Object[] params = new Object[opt.length-1];
        Class<?>[] types = new Class[opt.length-1];
        for (int i=0; i<params.length; i++) {
          types[i] = int.class;
          params[i] = (int) Integer.parseInt(opt[i+1].trim());
        }
        Constructor c = clazz.getConstructor(types);
        obj = c.newInstance(params);
      }
      TerminationStrategy t = (TerminationStrategy)obj;
      return t;
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | 
            NoSuchMethodException | SecurityException | IllegalArgumentException | 
            InvocationTargetException ex) {
      Logger.getLogger("jdart").log(Level.WARNING, 
              "Could not instantiate termination strategy: " + 
              ex.getClass().getSimpleName() + " / " +ex.getMessage(), ex);
    }
    return new NeverTerminate();
  }

  public static ExplorationStrategy parseExplorationStrategy(Config conf) {
    if (!conf.hasValue("jdart.exploration.strategy.class")) {
      return new DFSStrategy();
    }
    return conf.getEssentialInstance("jdart.exploration.strategy.class", ExplorationStrategy.class);
  }
}
