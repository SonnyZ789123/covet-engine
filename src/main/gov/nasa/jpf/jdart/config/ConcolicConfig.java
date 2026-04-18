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
import gov.nasa.jpf.jdart.exploration.CoverageHeuristicStrategy;
import gov.nasa.jpf.jdart.exploration.DFSStrategy;
import gov.nasa.jpf.jdart.exploration.ExplorationStrategy;
import gov.nasa.jpf.jdart.exploration.coverage.CfgCoverageTracker;
import gov.nasa.jpf.jdart.termination.BranchCoverageTermination;
import gov.nasa.jpf.jdart.termination.NeverTerminate;
import gov.nasa.jpf.jdart.termination.TerminationStrategy;
import gov.nasa.jpf.jdart.termination.TimedOrBranchCoverageTermination;
import com.google.gson.Gson;
import com.kuleuven.blockmap.model.BlockMapDTO;
import java.io.FileReader;
import java.io.Reader;

import java.util.*;

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
  
  private final TypeContext types = new TypeContext(true);
  
  protected final AnalysisConfig defaultAnalysisConfig = new AnalysisConfig();
  
  private final Map<String, ConcolicMethodConfig> concolicMethods = new HashMap<>();
  
    
  /**
   * strategy for terminating jdart
   */
  private TerminationStrategy termination;

  /**
   * strategy for exploring the symbolic execution tree
   */
  private ExplorationStrategy explorationStrategy;

  /**
   * shared CFG coverage tracker (optional). Populated when the coverage
   * heuristic is in use, or when {@code jdart.coverage.block_map_path} is
   * set in the JPF config so DFS/BFS can also collect coverage telemetry.
   */
  private CfgCoverageTracker coverageTracker;

  /** Whether to mark already-covered paths as IGNORE. */
  private boolean ignoreCoveredPaths;


  /**
   *
   * @param conf the jpf config file to read from
   */
  public ConcolicConfig(Config conf) {
    initialize(conf);
  }
  
  /* ******************************************************************************
   * 
   * api
   * 
   */

  /**
   * get method config for concolic method
   * 
   * @return 
   */
  public ConcolicMethodConfig getMethodConfig(String id) {
    return concolicMethods.get(id);
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

  /**
   * @return the shared CFG coverage tracker, or {@code null} if no block map
   *         was loaded.
   */
  public CfgCoverageTracker getCoverageTracker() {
    return this.coverageTracker;
  }

  /**
   * @return whether the explorer should mark already-covered paths as IGNORE.
   *         Only meaningful when {@link #getCoverageTracker()} is non-null.
   */
  public boolean shouldIgnoreCoveredPaths() {
    return this.ignoreCoveredPaths;
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

    // resolve the shared coverage tracker. The coverage heuristic builds its
    // own tracker (back-compat); for DFS/BFS the user can opt in via the
    // top-level JPF config keys jdart.coverage.block_map_path and
    // jdart.coverage.ignore_covered_paths.
    if (this.explorationStrategy instanceof CoverageHeuristicStrategy) {
      CoverageHeuristicStrategy chs = (CoverageHeuristicStrategy) this.explorationStrategy;
      this.coverageTracker = chs.getCoverageTracker();
      this.ignoreCoveredPaths = chs.shouldIgnoreCoveredPaths;
    } else if (conf.hasValue("jdart.coverage.block_map_path")) {
      String path = conf.getString("jdart.coverage.block_map_path");
      try (Reader reader = new FileReader(path)) {
        BlockMapDTO bm = new Gson().fromJson(reader, BlockMapDTO.class);
        this.coverageTracker = new CfgCoverageTracker(bm);
      } catch (Exception e) {
        throw new RuntimeException("Failed to load block map at " + path, e);
      }
      this.ignoreCoveredPaths = conf.getBoolean("jdart.coverage.ignore_covered_paths", false);
    }

    // wire termination strategies that depend on the coverage tracker
    if (this.termination instanceof BranchCoverageTermination && this.coverageTracker != null) {
      ((BranchCoverageTermination) this.termination).setCoverageTracker(this.coverageTracker);
    }
    if (this.termination instanceof TimedOrBranchCoverageTermination && this.coverageTracker != null) {
      ((TimedOrBranchCoverageTermination) this.termination).setCoverageTracker(this.coverageTracker);
    }

    if (!strategiesLogged) {
      JPF.getLogger("jdart").info(String.format(
              "%n"
            + "  +-- JDart configuration -----------------------------+%n"
            + "  |  Exploration strategy : %-25s |%n"
            + "  |  Termination strategy : %-25s |%n"
            + "  |  Coverage tracker     : %-25s |%n"
            + "  |  Ignore covered paths : %-25s |%n"
            + "  +----------------------------------------------------+",
              this.explorationStrategy.getClass().getSimpleName(),
              this.termination.getClass().getSimpleName(),
              this.coverageTracker != null ? "loaded" : "none",
              this.ignoreCoveredPaths));
      strategiesLogged = true;
    }
  }

  private static boolean strategiesLogged = false;
  
  private static TerminationStrategy parseTerminationStrategy(Config conf) {
    if (!conf.hasValue("jdart.termination")) {
      return new NeverTerminate();     
    }
    return parseTerminationStrategy(conf.getProperty("jdart.termination").trim());
  }
    
  private static TerminationStrategy parseTerminationStrategy(String line) {
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

  private static ExplorationStrategy parseExplorationStrategy(Config conf) {
    if (!conf.hasValue("jdart.exploration")) {
      return new DFSStrategy();
    }
    return parseExplorationStrategy(conf.getProperty("jdart.exploration").trim(), conf);
  }

  private static ExplorationStrategy parseExplorationStrategy(String propertyString, Config conf) {
    String className = parseClassName(propertyString);
    String[] strArgs = parseConstructorArgs(propertyString);

    Class<?>[] argTypes = new Class<?>[strArgs.length];
    Arrays.fill(argTypes, String.class);

    Object[] args = strArgs;

    return conf.getInstance(
            "jdart.exploration",
            className,
            ExplorationStrategy.class,
            argTypes,
            args
    );
  }

  private static String parseClassName(String spec) {
    int lparen = spec.indexOf('(');
    return (lparen < 0) ? spec.trim() : spec.substring(0, lparen).trim();
  }

  private static String[] parseConstructorArgs(String spec) {
    int lparen = spec.indexOf('(');
    int rparen = spec.lastIndexOf(')');

    if (lparen < 0 || rparen < lparen) {
      return new String[0];
    }

    String argsPart = spec.substring(lparen + 1, rparen).trim();
    if (argsPart.isEmpty()) {
      return new String[0];
    }

    String[] rawArgs = argsPart.split(",");
    for (int i = 0; i < rawArgs.length; i++) {
      rawArgs[i] = rawArgs[i].trim();
    }
    return rawArgs;
  }

}
