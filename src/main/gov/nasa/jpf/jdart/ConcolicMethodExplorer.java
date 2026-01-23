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
package gov.nasa.jpf.jdart;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.api.ValuationEntry;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.java.ObjectConstraints;
import gov.nasa.jpf.constraints.parser.ParserUtil;
import gov.nasa.jpf.constraints.types.Type;
import gov.nasa.jpf.jdart.ConcolicUtil.Pair;
import gov.nasa.jpf.jdart.config.AnalysisConfig;
import gov.nasa.jpf.jdart.config.ConcolicConfig;
import gov.nasa.jpf.jdart.config.ConcolicMethodConfig;
import gov.nasa.jpf.jdart.config.ConcolicValues;
import gov.nasa.jpf.jdart.config.ParamConfig;
import gov.nasa.jpf.jdart.constraints.*;
import gov.nasa.jpf.jdart.constraints.tree.BranchEffect;
import gov.nasa.jpf.jdart.constraints.tree.InstructionBranch;
import gov.nasa.jpf.jdart.exploration.CoverageHeuristicStrategy;
import gov.nasa.jpf.jdart.exploration.ExplorationStrategy;
import gov.nasa.jpf.jdart.exploration.coverage.ClassCoverage;
import gov.nasa.jpf.jdart.exploration.coverage.CoverageReport;
import gov.nasa.jpf.jdart.exploration.coverage.CoverageType;
import gov.nasa.jpf.jdart.objects.SymbolicObjectsContext;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.Heap;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.Types;
import gov.nasa.jpf.vm.UncaughtException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.antlr.runtime.RecognitionException;

/**
 *
 */
public class ConcolicMethodExplorer {
  
  private static final class RestoreExploreState {
    public final boolean explore;
    
    public RestoreExploreState(boolean explore) {
      this.explore = explore;
    }
    
    public RestoreExploreState clone() {
      return this;
    }
  }

  public static ConcolicMethodExplorer getCurrentAnalysis(ThreadInfo ti) {
    return ti.getAttr(ConcolicMethodExplorer.class);
  }
  
  
  /**
   * logger
   */
  private final JPFLogger logger = JPF.getLogger("jdart");

  /**
   * explored method
   */
  private MethodInfo methodInfo;

  /**
   * concolic config for method
   */
  private final ConcolicMethodConfig methodConfig;
  private final AnalysisConfig anaConf;
  
  /**
   * constraints tree from exploring method
   */
  private InternalConstraintsTree constraintsTree;
  
  /**
   * the original initial valuation for symbolic vars
   */
  private Valuation initValuation;
  
  private Valuation currValuation;
  private Valuation nextValuation;
  
  /**
   * The original values of the parameters
   */
  private Object[] initParams;
  
  private SymbolicObjectsContext symContext;

  /**
   * execution info about method
   */
  
  private final SolverContext solverCtx;

  /**
   * exploration strategy
   */
  private final ExplorationStrategy explorationStrategy;
  

  public ConcolicMethodExplorer(ConcolicConfig config, String id, MethodInfo mi) {
    // store method info and config
    this.methodInfo = mi;
    this.methodConfig = config.getMethodConfig(id);
    this.anaConf = methodConfig.getAnalysisConfig();
    
    // get preset values
    ConcolicValues vals = methodConfig.getConcolicValues();
    
    // create a constraints tree
    this.solverCtx = config.getSolver().createContext();    
    this.constraintsTree = new InternalConstraintsTree(solverCtx, anaConf, vals);
    this.explorationStrategy = config.getExplorationStrategy();
  }
  
  public void setExplore(boolean explore) {
    constraintsTree.setExplore(explore);
  }
  
  public String getId() {
    return methodConfig.getId();
  }
  
  public boolean isRootFrame(StackFrame sf) {
    return sf.getPrevious().hasFrameAttr(RootFrame.class);
  }
  
  private PostCondition collectPostCondition(ThreadInfo ti) {
    PostCondition pc = new PostCondition();
    for(SymbolicVariable<?> sv : symContext.getSymbolicVars()) {
      sv.addToPC(pc);
    }
    collectSymbolicReturn(pc, ti);
    
    return pc;
  }
  
  private void collectSymbolicReturn(PostCondition pc, ThreadInfo ti) {
    byte rtc = methodInfo.getReturnTypeCode();
    if(rtc == Types.T_VOID)
      return;
    
    
    StackFrame sf = ti.getTopFrame();
    if(rtc == Types.T_ARRAY || rtc == Types.T_REFERENCE) {
      int ref = sf.peek();
      ElementInfo ei = ti.getHeap().get(ref);
      if(ei == null)
        return;

      // TODO: handle non-primitive return types -> see ConcolicUtil
    }
    else { // primitive
      Type<?> type = ConcolicUtil.forTypeCode(rtc);
      Pair<?> cr = ConcolicUtil.peek(sf, type);
      pc.setReturn(cr);
      // TODO: maybe we can always add the return value for primitive types e.g., [return:=6]
      if(!cr.isConcrete()) {
        pc.addReturnCondition(cr);
      }
    }
  }

  public boolean checkCoveredPathOnCompletion() {
    if (explorationStrategy instanceof CoverageHeuristicStrategy) {
      CoverageHeuristicStrategy coverageHeuristicStrategy = (CoverageHeuristicStrategy) explorationStrategy;

      if (!coverageHeuristicStrategy.shouldIgnoreCoveredPaths) {
        return false;
      }

      CoverageReport coverageReport = coverageHeuristicStrategy.coverageReport;
      InstructionBranch instructionBranch = constraintsTree.getCurrentTarget().getInstructionBranch();
      if (instructionBranch == null) {
        return false;
      }

      Instruction insn = instructionBranch.getInstruction();
      if (insn == null) { // This is possible for branches created by uncaught exceptions (e.g., div by zero)
        return false;
      }

      MethodInfo mi = insn.getMethodInfo();
      ClassCoverage cov = coverageReport.getClassCoverage(mi.getClassName());

      if (cov.getLineCoverageType(insn.getLineNumber()) == CoverageType.FULL) {
        constraintsTree.finish(PathResult.ignore());
        return true;
      }
    }
    return false;
  }
      
  public void completePathOk(ThreadInfo ti) {
    if (checkCoveredPathOnCompletion()) {
      return;
    }

    PostCondition pc = collectPostCondition(ti);
    PathResult res = PathResult.ok(currValuation, pc);
    constraintsTree.finish(res);
  }
  
  public void completePathError(ThreadInfo ti) {
    if (checkCoveredPathOnCompletion()) {
      return;
    }

    ElementInfo exElem = ti.getPendingException().getException();
    StringWriter sw = new StringWriter();
    try(PrintWriter pw = new PrintWriter(sw)) {
      ti.printStackTrace(pw, exElem.getObjectRef());
    }
    String st = sw.toString();
    PathResult res = PathResult.error(currValuation, exElem.getClassInfo().getName(), st);
    constraintsTree.finish(res);
  }

  public boolean hasMoreChoices() {
    // Before first execution
    if (initValuation == null) {
      return true;
    }

    // Compute next valuation if needed
    if (nextValuation == null) {
      nextValuation = explorationStrategy.findNext(constraintsTree, methodInfo);
    }

    return nextValuation != null;
  }


  public boolean advanceValuation() {
    // Ensure a next valuation exists
    if (nextValuation == null) {
      nextValuation = explorationStrategy.findNext(constraintsTree, methodInfo);
    }

    if (nextValuation != null) {
      for (Variable v : currValuation.getVariables()) {
        if (!nextValuation.containsValueFor(v)) {
          nextValuation.addEntry(
                  new ValuationEntry(v, nextValuation.getValue(v))
          );
        }
      }
    }
    currValuation = nextValuation;
    nextValuation = null;

    return currValuation != null;
  }


  /**
   * registers method for concolic execution. Puts symbolic input values onto
   * the stack ...
   */
  public void initializeMethod(ThreadInfo ti, StackFrame sf) {
    logger.finest("Initializing concolic execution of " + methodInfo.getFullName());

    // mark root frame
    sf.setFrameAttr(RootFrame.getInstance());
    
    symContext = new SymbolicObjectsContext(ti.getHeap(), anaConf.getSymbolicFieldsExclude(), anaConf.getSymbolicFieldsInclude(), anaConf.getSpecialExclude());
    
    initializeSymbolicStatic(ti);
    initializeSymbolicParams(ti, sf);
    
    List<Variable<?>> vlist = new ArrayList<>();
    logger.finest("Symbolic variables:");
    logger.finest("===================");
    for(SymbolicVariable<?> var : symContext.getSymbolicVars()) {
      logger.finest(var.getVariable().getName());
      vlist.add(var.getVariable());
    }
    logger.finest();
    
    for(String constraintStr : anaConf.getConstraints()) {
      logger.finer("Adding constraint ", constraintStr);
      try {
        Expression<Boolean> constrExpr = ParserUtil.parseLogical(constraintStr,
            ObjectConstraints.getJavaTypes(),
            vlist);
        try {
          solverCtx.add(constrExpr);
        }
        catch(Exception ex) {
          logger.severe("Could not add constraint to solver: ", ex);
        }
      } catch (RecognitionException ex) {
        logger.severe("Could not parse constraint: ", ex);
      }
    }
    
  }
  
  private void initializeSymbolicStatic(ThreadInfo ti) {
    List<String> symbStatics = anaConf.getSymbolicStatics();
    for(String clazz : symbStatics) {
      ClassInfo ci = ClassInfo.getInitializedClassInfo(clazz, ti);
      ElementInfo ei = ci.getStaticElementInfo();
      symContext.processObject(ei, ci.getName());
    }
  }

  private void initializeSymbolicParams(ThreadInfo ti, StackFrame sf) {
    List<ParamConfig> pconfig = methodConfig.getParams();
    int argSize = pconfig.size();
    this.initParams = new Object[argSize];
    int stackIdx = methodInfo.getArgumentsSize();
    
    Heap heap = ti.getHeap();
    
    if(!methodInfo.isStatic()) {
      stackIdx--;
      int thisRef = sf.peek(stackIdx);
      ElementInfo thisEi = heap.get(thisRef);
      symContext.processObject(thisEi, "this", true);
    }
    
    byte[] argTypes = methodInfo.getArgumentTypes();
    
    for(int i = 0; i < argSize; i++) {
      ParamConfig pc = pconfig.get(i);
      String name = pc.getName();
      
      byte tc = argTypes[i];
      stackIdx--;
      if(tc == Types.T_LONG || tc == Types.T_DOUBLE)
        stackIdx--;
      
      this.initParams[i] = getVal(sf, stackIdx, tc);
      
      if(name == null)
        continue; // null name indicates non-symbolic param
      
      if(tc == Types.T_REFERENCE || tc == Types.T_ARRAY) {
        int ref = sf.peek(stackIdx);
        ElementInfo ei = heap.get(ref);
        if(ei != null)
          symContext.processObject(ei, name, true);
      }
      else { // primitive type
        Type<?> t = ConcolicUtil.forTypeCode(tc);
        Variable<?> var = Variable.create(t, name);
        SymbolicParam<?> sp = new SymbolicParam<>(var, stackIdx);
        symContext.addStackVar(sp);
      }
    }
  }
  
  private static Object getVal(StackFrame sf, int offset, byte type) {
    switch(type) {
    case Types.T_LONG:
      return sf.peekLong(offset);
    case Types.T_DOUBLE:
      return sf.peekDouble(offset);
    case Types.T_FLOAT:
      return sf.peekFloat(offset);
    case Types.T_INT:
      default:
      return sf.peek(offset);
    }
  }
  
  public void prepareFirstExecution(StackFrame sf) {
    initValuation = new Valuation();
    for(SymbolicVariable<?> sv : symContext.getSymbolicVars())
      sv.readInitial(initValuation, sf);
    currValuation = initValuation;
    logger.finest("\n\n>>>>>>>>>>>>>>>> First execution with valuation " + currValuation);
  }
  
  public void prepareReexecution(StackFrame sf) {
    logger.finest("\n\n>>>>>>>>>>>>>>>> Reexecuting with valuation " + currValuation);
    for(SymbolicVariable<?> sv : symContext.getSymbolicVars())
      sv.apply(currValuation, sf);
  }
 
  
  public void uncaughtException(ThreadInfo ti, UncaughtException uex) {
    completePathError(ti);
    ti.clearPendingException();
    ti.breakTransition(true);
  }
  
  public boolean needsDecisions() {
    return constraintsTree.needsDecision();
  }
  
  public final void decision(ThreadInfo ti, Instruction branchInsn, int chosenIdx, InstructionBranch[] nextInstructions) {
    BranchEffect eff = constraintsTree.decision(branchInsn, chosenIdx, nextInstructions);
    switch(eff) {
    case INCONCLUSIVE:
      logger.severe("Aborting current execution due to inconclusive divergence...");
      constraintsTree.failCurrentTarget();
      ti.breakTransition(true);
      break;
    case UNEXPECTED:
      logger.warning("Unexpected divergence in execution of current valuation ...");
      constraintsTree.failCurrentTarget(); // TODO: Here, we could make more effort ...
      break;
    default:
    }
  }
 
    
  public ConcolicMethodConfig getMethodConfig() {
    return this.methodConfig;
  }
  
  public InternalConstraintsTree getInternalConstraintsTree() {
    return this.constraintsTree;
  }
  
  public CompletedAnalysis finish() {
    return new CompletedAnalysis(methodConfig, initValuation, initParams, ConstraintsTree.fromInternalCT(constraintsTree.getRoot()));
  }

  public void newPath(StackFrame sf) {
    if(initValuation == null) { // first execution
      prepareFirstExecution(sf);
    }
    else { // reexecution
      advanceValuation();
      prepareReexecution(sf);
    }
  }
  
  
  public void makeCurrentAnalysis(ThreadInfo ti) {
    ti.setAttr(this);
  }

  public AnalysisConfig getAnalysisConfig() {
    return anaConf;
  }
  
  public void methodExited(ThreadInfo ti, MethodInfo mi) {
    RestoreExploreState r = ti.getTopFrame().getFrameAttr(RestoreExploreState.class);
    if(r != null) {
      constraintsTree.setExplore(r.explore);
      logger.finer("Restored exploration state after leaving method ", mi.getFullName());
    }
  }
  
  public void methodEntered(ThreadInfo ti, MethodInfo mi) {
    boolean explore = constraintsTree.isExplore();
    
    if(explore) {
      if(anaConf.suspendExploration(mi)) {
        ti.getTopFrame().setFrameAttr(new RestoreExploreState(explore));
        logger.finer("Suspending exploration in method " + mi.getFullName());
        constraintsTree.setExplore(false);
      }
    }
    else {
      if(anaConf.resumeExploration(mi)) {
        ti.getTopFrame().setFrameAttr(new RestoreExploreState(explore));
        logger.finer("Resuming exploration in method " + mi.getFullName());
        constraintsTree.setExplore(true);
      }
    }
  }
  
  
  // LEGACY API
  
  @Deprecated
  public ConstraintsTree getConstraintsTree() {
    return ConstraintsTree.fromInternalCT(constraintsTree.getRoot());
  }
  
  @Deprecated
  public Valuation getOriginalInitialValuation() {
    return initValuation;
  }
}
