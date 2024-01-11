package edu.umn.cs.spoton.analysis.influencing;


import static edu.umn.cs.spoton.analysis.StaticAnalysisMain.disregardedJavaTypes;
import static edu.umn.cs.spoton.analysis.StaticAnalysisMain.walaDisregardedTypes;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.MethodReference;
import edu.umn.cs.spoton.analysis.AnalysisPasses;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * computes the dependency type order of a condition. This has to be done after all other
 * instructions are visited since order is not guaranteed in the traversal, thus this inference
 * needs to happen as the last thing after all instructions are visited as another pass over the
 * entire instructions, unless we use the cfg to enforce traversing order.
 */
public class ConditionInfluencingTypeVisitor extends SSAInstruction.Visitor {

  private final LinkedHashMap<UniqueVar, List<UniqueVar>> dependencyMap;

  static HashMap<SourceCodePoint, Map<String, Integer>> sourceCodeToTypeDependencyMap = new HashMap<>();
  static HashSet<String> visitedClassesMethod = new HashSet<>();
  private CallGraph cg;


  //packageName and className of the currently being analyzed method.
  IR ir;
  String className;
  String methodSig;

  IClassHierarchy cha;

  String rootPackageName;
  IAnalysisCacheView cache;

  public ConditionInfluencingTypeVisitor(IMethod method,
      LinkedHashMap<UniqueVar, List<UniqueVar>> dependencyMap, IAnalysisCacheView cache,
      IClassHierarchy cha, String rootPackageName, CallGraph cg) {
    this.cha = cha;
    this.cache = cache;
    this.rootPackageName = rootPackageName;
    this.cg = cg;
    ir = cache.getIR(method, Everywhere.EVERYWHERE);
    className = method.getDeclaringClass().getName().getClassName().toString();
    methodSig = method.getSelector().toString();
    this.dependencyMap = dependencyMap;
  }

  ConditionInfluencingTypeVisitor(IMethod method,
      LinkedHashMap<UniqueVar, List<UniqueVar>> dependencyMap, IR ir, IAnalysisCacheView cache,
      IClassHierarchy cha, String rootPackageName, CallGraph cg) {
    this.cha = cha;
    this.cache = cache;
    this.rootPackageName = rootPackageName;
    this.ir = ir;
    this.cg = cg;
    className = method.getDeclaringClass().getName().getClassName().toString();
    methodSig = method.getSelector().toString();
    this.dependencyMap = dependencyMap;
  }

  @Override
  public void visitConditionalBranch(SSAConditionalBranchInstruction instruction) {
    boolean methodInUserPackage = TypeUtil.getWalaPackageName(ir.getMethod()).startsWith(
        rootPackageName);
    System.out.println("inferring influencing types for: " + instruction);
    if (methodInUserPackage) {
      inferUsefulType(ir.getMethod(), instruction,
                      getUseUniqueVars(instruction, 0, 1));
    }
  }


  @Override
  public void visitInvoke(SSAInvokeInstruction instruction) {

    MethodReference mr = instruction.getDeclaredTarget();
    IMethod methodToInvoke = cha.resolveMethod(mr);
    boolean methodIsResolved = methodToInvoke != null;

    boolean methodInUserPackage;
    methodInUserPackage =
        methodIsResolved && (TypeUtil.getWalaPackageName(methodToInvoke).startsWith(
            rootPackageName));

    boolean methodAlreadyVisited = visitedClassesMethod.contains(
        instruction.toString());

    IR newIR = TypeUtil.resolveMethod(methodToInvoke, instruction, this.ir,cache,this.cg);
    if (methodInUserPackage && !methodAlreadyVisited && newIR != null) {
      ConditionInfluencingTypeVisitor methodWalker =
          new ConditionInfluencingTypeVisitor(
              cha.resolveMethod(mr), dependencyMap, newIR, this.cache, this.cha,
              this.rootPackageName, this.cg);
      visitedClassesMethod.add(instruction.toString());
      methodWalker.walk();
    }
  }

  public UniqueVar[] getUseUniqueVars(SSAInstruction instruction, Integer... useVals) {
    UniqueVar[] uniqueVars = new UniqueVar[useVals.length];
    int uniqueVarIndex = 0;
    for (int useVal : useVals) {
      int useVar = instruction.getUse(useVal);
      uniqueVars[uniqueVarIndex++] = AnalysisPasses.createUniqueVarObj(ir, useVar);
    }
    return uniqueVars;
  }


  /**
   * breadth-first search over dependant types.
   *
   * @param method
   * @param instruction
   * @param useUniqueVars
   */
  public void inferUsefulType(IMethod method, SSAInstruction instruction,
      UniqueVar[] useUniqueVars) {
    assert
        useUniqueVars.length != 0 : "useUniqueVars cannot be empty for a conditional instruction.";
    LinkedHashMap<String, Integer> dependentTypeToDepthMap = new LinkedHashMap<>();

    int depth = 0;

    // book-keeping to avoid revisiting variables which we have already collected their
    // dependent variables
    HashSet<UniqueVar> visitedUniqueVars = new HashSet<>();

    LinkedList<UniqueVar> uniqueVarQueue = new LinkedList();
    LinkedList<UniqueVar> nextUniqueVarQueue = new LinkedList();

    uniqueVarQueue.addAll(List.of(useUniqueVars));

    while (!uniqueVarQueue.isEmpty()) {
      int depthIndex = uniqueVarQueue.size();
      for (int i = 0; i < depthIndex; i++) {
        UniqueVar uniqueVar = uniqueVarQueue.pop();
        if (!uniqueVar.equals(
            AnalysisPasses.DUMMY_LEAF_UNIQUEVAR)) { //we skip if we are hitting the dummy leaf
          if (!visitedUniqueVars.contains(
              uniqueVar)) { // only visit if we have not already visited this unique var
            Set<String> dependentTypes = uniqueVar.type;
            for (String dependentType : dependentTypes) {
              if (dependentTypeToDepthMap.get(dependentType)
                  == null) { //type may exist on a shallower depth, so we do not want to change the record in this case.
                dependentTypeToDepthMap.put(dependentType, depth);

                System.out.println("unique var = " + uniqueVar.type + " , at depth = " + depth);
              }
            }
            List<UniqueVar> dependsOnList = dependencyMap.get(uniqueVar);
            if (dependsOnList != null) {
              assert !dependsOnList.isEmpty() : "dependsOnList cannot be empty, but it can not exist indicating a leaf node";
              nextUniqueVarQueue.addAll(dependsOnList);
            }
            visitedUniqueVars.add(uniqueVar);
          }
        }
      }
      depth++;
      uniqueVarQueue = nextUniqueVarQueue;
      nextUniqueVarQueue = new LinkedList<>();
    }

    SourceCodePoint sourceCodePoint = new SourceCodePoint(method, instruction);

    System.out.println("for SourceCodePoint = " + sourceCodePoint);
    Map<String, Integer> lineOfCodeTypeDependencies = sourceCodeToTypeDependencyMap.getOrDefault(
        sourceCodePoint, new LinkedHashMap<>());

    LinkedHashMap<String, Integer> filteredTypesToDepthMap = filterDependentTypes(
        dependentTypeToDepthMap);
    if (filteredTypesToDepthMap.isEmpty()) //skip adding a sourceCodePoint that has no useful type
      return;

    lineOfCodeTypeDependencies.putAll(filteredTypesToDepthMap);

    sourceCodeToTypeDependencyMap.put(sourceCodePoint,
                                      lineOfCodeTypeDependencies);
  }

  private LinkedHashMap<String, Integer> filterDependentTypes(
      LinkedHashMap<String, Integer> dependentTypeToDepthMap) {

    LinkedHashMap<String, Integer> filteredType = new LinkedHashMap<>();

    for (Map.Entry e : dependentTypeToDepthMap.entrySet()) {
      String type = (String) e.getKey();

      boolean isJavaDisregardedType = disregardedJavaTypes.stream()
          .anyMatch(t -> type.startsWith(t));

      boolean isWalaDisregardedType = walaDisregardedTypes.stream()
          .anyMatch(t -> type.equals(t));

      if (!isJavaDisregardedType && !isWalaDisregardedType) {
        filteredType.put((String) e.getKey(), (Integer) e.getValue());
      }
    }

    return filteredType;
  }

  public HashMap<SourceCodePoint, Map<String, Integer>> walk() {
    SSAInstruction[] instructions = ir.getInstructions();

    for (int irInstIndex = 0; irInstIndex < instructions.length; irInstIndex++) {
      SSAInstruction ins = instructions[irInstIndex];
      if (ins != null) {
        ins.visit(this);
      }
    }
    return sourceCodeToTypeDependencyMap;
  }

  public static void clear() {
    visitedClassesMethod = new HashSet<>();
  }
}
