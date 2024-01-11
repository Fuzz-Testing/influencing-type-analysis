package edu.umn.cs.spoton.analysis.string;


import static edu.umn.cs.spoton.analysis.influencing.TypeUtil.getWalaPackageName;
import static edu.umn.cs.spoton.analysis.influencing.TypeUtil.resolveMethod;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAConversionInstruction;
import com.ibm.wala.ssa.SSAGetCaughtExceptionInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.MethodReference;
import java.util.HashSet;

/**
 * Visit "Use" variables in all user defined code, and add their UniqueVar objects to the
 * globalSymbolTable in the DependencyManager. It also populates the dependencyMap in the
 * DependencyManager.
 * Note that this visitor uses the call graph to find methods to collect strings from. Thus
 * the scope of this visitor is all reachable methods that could be reached through the call graph
 */
public class StringConstantsVisitor extends SSAInstruction.Visitor {

  //packageName and className of the currently being analyzed method.
  IR ir;
  String className;
  String methodSig;


  IClassHierarchy cha;
  CallGraph cg;

  String rootPackageName;
  //keeps track of all methods we have seen so far
  static HashSet<String> visitedClassesMethod = new HashSet<>();

  IAnalysisCacheView cache;


  protected HashSet<String> strConstantsTable = new HashSet<>();

  public StringConstantsVisitor(IMethod method,
      IAnalysisCacheView cache,
      IClassHierarchy cha, CallGraph cg, String rootPackageName) {
    this.cha = cha;
    this.cache = cache;
    this.rootPackageName = rootPackageName;
    this.ir = cache.getIR(method, Everywhere.EVERYWHERE);
    this.cg = cg;
    this.className = method.getDeclaringClass().getName().getClassName().toString();
    this.methodSig = method.getSelector().toString();
  }


  public StringConstantsVisitor(IMethod method, IR ir,
      IAnalysisCacheView cache,
      IClassHierarchy cha, CallGraph cg, String rootPackageName) {
    this.ir = ir;
    this.cha = cha;
    this.cache = cache;
    this.cg = cg;
    this.rootPackageName = rootPackageName;
    className = method.getDeclaringClass().getName().getClassName().toString();
    methodSig = method.getSelector().toString();
  }

  @Override
  public void visitGoto(SSAGotoInstruction instruction) {}

  @Override
  public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
  }

  @Override
  public void visitArrayStore(SSAArrayStoreInstruction instruction) {
  }

  @Override
  public void visitBinaryOp(SSABinaryOpInstruction instruction) {
  }

  @Override
  public void visitUnaryOp(SSAUnaryOpInstruction instruction) {
  }

  @Override
  public void visitConversion(SSAConversionInstruction instruction) {
  }

  @Override
  public void visitComparison(SSAComparisonInstruction instruction) {
  }

  //no def var needed here
  @Override
  public void visitConditionalBranch(SSAConditionalBranchInstruction instruction) {
  }

  @Override
  public void visitSwitch(SSASwitchInstruction instruction) {}

  @Override
  public void visitReturn(SSAReturnInstruction instruction) {

  }

  @Override
  public void visitGet(SSAGetInstruction instruction) {
  }

  @Override
  public void visitPut(SSAPutInstruction instruction) {
  }

  @Override
  public void visitInvoke(SSAInvokeInstruction instruction) {
    MethodReference mr = instruction.getDeclaredTarget();
    IMethod methodToInvoke = cha.resolveMethod(mr);
    boolean methodIsResolved = methodToInvoke != null;

    IR calleeIR = resolveMethod(methodToInvoke, instruction, this.ir, this.cache, this.cg);

    boolean methodInUserPackage;
    methodInUserPackage =
        methodIsResolved && (getWalaPackageName(methodToInvoke).startsWith(rootPackageName));

    boolean methodAlreadyVisited = visitedClassesMethod.contains(instruction.toString());

    if (methodInUserPackage && !methodAlreadyVisited && calleeIR != null) {
      StringConstantsVisitor methodWalker = new StringConstantsVisitor(methodToInvoke,
                                                                       calleeIR,
                                                                       this.cache, this.cha,
                                                                       this.cg,
                                                                       this.rootPackageName);
      visitedClassesMethod.add(instruction.toString());
      methodWalker.walk();
      this.strConstantsTable.addAll(methodWalker.strConstantsTable);
    }
  }

  @Override
  public void visitNew(SSANewInstruction instruction) {
  }

  @Override
  public void visitArrayLength(SSAArrayLengthInstruction instruction) {
  }

  @Override
  public void visitThrow(SSAThrowInstruction instruction) {}

  @Override
  public void visitMonitor(SSAMonitorInstruction instruction) {}

  @Override
  public void visitCheckCast(SSACheckCastInstruction instruction) {
  }

  @Override
  public void visitInstanceof(SSAInstanceofInstruction instruction) {
  }

  @Override
  public void visitPhi(SSAPhiInstruction instruction) {
  }

  @Override
  public void visitPi(SSAPiInstruction instruction) {}

  @Override
  public void visitGetCaughtException(SSAGetCaughtExceptionInstruction instruction) {}

  @Override
  public void visitLoadMetadata(SSALoadMetadataInstruction instruction) {}


  public void walk() {
    SSAInstruction[] instructions = ir.getInstructions();

    for (int irInstIndex = 0; irInstIndex < instructions.length; irInstIndex++) {
      SSAInstruction ins = instructions[irInstIndex];
      if (ins != null) {
        ins.visit(this);
      }
    }
    SymbolTable symbolTable = ir.getSymbolTable();
    for (int i = 0; i <= symbolTable.getMaxValueNumber(); i++) {
      if (symbolTable.isConstant(i) && symbolTable.isStringConstant(i))
        strConstantsTable.add((String) symbolTable.getConstantValue(i));
    }
  }

  public void clear() {
    visitedClassesMethod = new HashSet<>();
  }

  public HashSet<String> getStrConstantsTable() {
    return strConstantsTable;
  }
}
