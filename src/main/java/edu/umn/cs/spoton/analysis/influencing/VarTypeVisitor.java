package edu.umn.cs.spoton.analysis.influencing;


import static edu.umn.cs.spoton.analysis.influencing.TypeUtil.getWalaPackageName;
import static edu.umn.cs.spoton.analysis.influencing.TypeUtil.resolveMethod;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.ShrikeCTMethod;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.SyntheticIR;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAConversionInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
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
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import edu.umn.cs.spoton.analysis.AnalysisPasses;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Visit "Use" variables in all user defined code, and add their UniqueVar objects to the
 * globalSymbolTable in the DependencyManager. It also populates the dependencyMap in the
 * DependencyManager.
 */
public class VarTypeVisitor extends SSAInstruction.Visitor {

  //packageName and className of the currently being analyzed method.
  IR ir;
  String className;
  String methodSig;


  IClassHierarchy cha;
  CallGraph cg;

  String rootPackageName;
  //keeps track of all methods we have seen so far
  static HashSet<String> visitedClassesMethod = new HashSet<>();

  List<UniqueVar> returnVars = new ArrayList<>();
  IAnalysisCacheView cache;

  public VarTypeVisitor(IMethod method, UniqueVar[] actualParmUniqueVars,
      IAnalysisCacheView cache,
      IClassHierarchy cha, CallGraph cg, String rootPackageName) {
    this.cha = cha;
    this.cache = cache;
    this.rootPackageName = rootPackageName;
    this.ir = cache.getIR(method, Everywhere.EVERYWHERE);
    this.cg = cg;
    this.className = method.getDeclaringClass().getName().getClassName().toString();
    this.methodSig = method.getSelector().toString();
    assert actualParmUniqueVars.length == 0 || actualParmUniqueVars.length
        == ir.getNumberOfParameters() : "parameters do not match. Failing.";
    for (int i = 0; i < actualParmUniqueVars.length; i++) {
      UniqueVar formalParmUniqueVar = AnalysisPasses.createUniqueVarObj(ir,
                                                                        ir.getParameter(i));
      AnalysisPasses.addDependency(formalParmUniqueVar, actualParmUniqueVars[i]);
    }
  }


  public VarTypeVisitor(IMethod method, UniqueVar[] actualParmUniqueVars, IR ir,
      IAnalysisCacheView cache,
      IClassHierarchy cha, CallGraph cg, String rootPackageName) {
    this.ir = ir;
    this.cha = cha;
    this.cache = cache;
    this.cg = cg;
    this.rootPackageName = rootPackageName;
    className = method.getDeclaringClass().getName().getClassName().toString();
    methodSig = method.getSelector().toString();

    assert actualParmUniqueVars.length == 0 || actualParmUniqueVars.length
        == ir.getNumberOfParameters() : "parameters do not match. Failing.";
    for (int i = 0; i < actualParmUniqueVars.length; i++) {
      UniqueVar formalParmUniqueVar = AnalysisPasses.createUniqueVarObj(ir,
                                                                        ir.getParameter(i));
      formalParmUniqueVar.updateType(ir.getParameterType(i).getName().toString());
      AnalysisPasses.addDependency(formalParmUniqueVar, actualParmUniqueVars[i]);
    }
  }

  @Override
  public void visitGoto(SSAGotoInstruction instruction) {}

  @Override
  public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
    UniqueVar defUniqueVar = getDefUniqueVar(instruction);
    UniqueVar refUniqueVar = getRefUniqueVar(instruction);
    UniqueVar indexUniqueVar = getIndexUniqueVars(instruction);
    AnalysisPasses.addDependency(defUniqueVar, refUniqueVar, indexUniqueVar);
  }


  //  arrayStore defines a dependency between the index to the array reference.
  @Override
  public void visitArrayStore(SSAArrayStoreInstruction instruction) {
    UniqueVar refUniqueVar = getRefUniqueVar(instruction);
    UniqueVar indexUniqueVar = getIndexUniqueVars(instruction);
    UniqueVar valueUniqueVar = AnalysisPasses.createUniqueVarObj(ir, instruction.getValue());
    AnalysisPasses.addDependency(refUniqueVar, indexUniqueVar, valueUniqueVar);
  }

  @Override
  public void visitBinaryOp(SSABinaryOpInstruction instruction) {
    UniqueVar defUniqueVar = getDefUniqueVar(instruction);
    UniqueVar[] useUniqueVars = getUseUniqueVars(instruction, 0, 1);
    AnalysisPasses.addDependency(defUniqueVar, useUniqueVars);
  }

  @Override
  public void visitUnaryOp(SSAUnaryOpInstruction instruction) {
    UniqueVar defUniqueVar = getDefUniqueVar(instruction);
    UniqueVar[] useUniqueVars = getUseUniqueVars(instruction, 0);
    AnalysisPasses.addDependency(defUniqueVar, useUniqueVars);
  }

  @Override
  public void visitConversion(SSAConversionInstruction instruction) {
    UniqueVar defUniqueVar = getDefUniqueVar(instruction);
    UniqueVar[] useUniqueVars = getUseUniqueVars(instruction, 0);
    AnalysisPasses.addDependency(defUniqueVar, useUniqueVars);
  }

  @Override
  public void visitComparison(SSAComparisonInstruction instruction) {
    UniqueVar defUniqueVar = getDefUniqueVar(instruction);
    UniqueVar[] useUniqueVars = getUseUniqueVars(instruction, 0, 1);
    AnalysisPasses.addDependency(defUniqueVar, useUniqueVars);
  }

  //no def var needed here
  @Override
  public void visitConditionalBranch(SSAConditionalBranchInstruction instruction) {
  }

  @Override
  public void visitSwitch(SSASwitchInstruction instruction) {}

  @Override
  public void visitReturn(SSAReturnInstruction instruction) {
    if (instruction.getNumberOfUses() > 0) {
      UniqueVar currentReturnUniqueVar = getReturnValUniqueVars(instruction);
      returnVars.add(currentReturnUniqueVar);
    }
  }

  @Override
  public void visitGet(SSAGetInstruction instruction) {
    UniqueVar defUniqueVar = getDefUniqueVar(instruction);
    if (!instruction.isStatic()) { // this will in fact add both ref and use unique vars to the set
      UniqueVar refUniqueVar = getRefUniqueVar(instruction);
      AnalysisPasses.addDependency(defUniqueVar, refUniqueVar);
    }
  }

  @Override
  public void visitPut(SSAPutInstruction instruction) {
    if (!instruction.isStatic()) { // this will in fact add both ref and use unique vars to the set
      UniqueVar refUniqueVar = getRefUniqueVar(instruction);
      UniqueVar useUniqueVar = getUseUniqueVars(instruction, 1)[0];
      AnalysisPasses.addDependency(refUniqueVar, useUniqueVar);
    } else { // no ref to add in this case
//      addUseToUniqueVars(instruction, 0);
//      assert false : "not handled";
    }
  }

  @Override
  public void visitInvoke(SSAInvokeInstruction instruction) {
    boolean isVoidMethod = instruction.getNumberOfReturnValues() == 0;
    MethodReference mr = instruction.getDeclaredTarget();
    IMethod methodToInvoke = cha.resolveMethod(mr);
    boolean methodIsResolved = methodToInvoke != null;
    boolean methodHasParameters = instruction.getNumberOfUses() > 0;
//    UniqueVar returnValUniqueVar = isVoidMethod ? null : getInvokeReturnUniqueVar(instruction,
//                                                                                  instruction.getDeclaredResultType());

    IR calleeIR = resolveMethod(methodToInvoke, instruction, this.ir, this.cache, this.cg);
    UniqueVar returnValUniqueVar = isVoidMethod ? null : getReturnUniqueVar(calleeIR, instruction);
    UniqueVar[] parameterUniqueVars;

    if (methodHasParameters)
      parameterUniqueVars = getParametersUniqueVars(calleeIR, instruction);
    else
      parameterUniqueVars = new UniqueVar[]{};

    boolean methodInUserPackage;
    methodInUserPackage =
        methodIsResolved && (getWalaPackageName(methodToInvoke).startsWith(rootPackageName));

    boolean methodAlreadyVisited = visitedClassesMethod.contains(instruction.toString());

    if (methodInUserPackage && calleeIR != null) {
      if (!methodAlreadyVisited) {
        VarTypeVisitor methodWalker = new VarTypeVisitor(methodToInvoke,
                                                         parameterUniqueVars, calleeIR,
                                                         this.cache, this.cha, this.cg,
                                                         this.rootPackageName);
        visitedClassesMethod.add(instruction.toString());
        methodWalker.walk();
//      visitedClassesMethod.remove(instruction.toString());
        if (!isVoidMethod) {
          AnalysisPasses.addDependency(returnValUniqueVar,
                                       methodWalker.returnVars.stream()
                                           .toArray(UniqueVar[]::new));
        }
      } else { // already visited method but is in the user's package
        if (!isVoidMethod)
          AnalysisPasses.addDefDependencyOnCalleeReturnVars(returnValUniqueVar, calleeIR);
        if (methodHasParameters)  // we need to bind the def to the passed parameters for the methods we cannot walk
          AnalysisPasses.addFormaParameterDependency(parameterUniqueVars, calleeIR);
      }
    } else { //not in the user's package case, does not matter if we have seen it before or not. we just collect outer dependency
      if (!isVoidMethod)
        if (methodHasParameters) // we need to bind the def to the passed parameters for the methods we cannot walk
          AnalysisPasses.addDependency(returnValUniqueVar, parameterUniqueVars);
    }

    if (!isVoidMethod) //add dependency of the def based on the return type defined by the signature of the method
      AnalysisPasses.addDependencyToDummy(returnValUniqueVar);
    if ((instruction.isSpecial() || instruction.isDispatch())
        && !instruction.isStatic()) { //dealing with the case where we want to use the object reference for the invocation, and make it depends on the parameters passed if any
      parameterUniqueVars = getUseUniqueVars(instruction,
                                             IntStream.range(1,
                                                             instruction.getNumberOfUses()).boxed()
                                                 .toArray(Integer[]::new));
      UniqueVar[] refUniqueVar = getUseUniqueVars(instruction, 0);
      assert refUniqueVar.length == 1 : "unexpected length for reference";
      if (parameterUniqueVars.length != 0)
        AnalysisPasses.addDependency(refUniqueVar[0], parameterUniqueVars);
      else
        AnalysisPasses.addDependencyToDummy(refUniqueVar[0]);
    }
  }

  /**
   * discovers the for each parameter, and not a function reference, the right type by checking the
   * generic types of the parameters. It works by fist finding all the indexes of the outermost ";"
   * which defines the number of parameter passed within (...). then for each of them, it splits the
   * type includes by [;<>,] to get the list of types referred by the type., then it updates the
   * list of types of the corresponding paramUniqueVar, again skipping updating the reference if it
   * was identified as the first arrgument of the parameter.
   *
   * @param calleeIR
   * @param invokeInstruction
   * @return
   */
  private UniqueVar[] getParametersUniqueVars(IR calleeIR, SSAInvokeInstruction invokeInstruction) {

    UniqueVar[] paramUniqueVars = getUseUniqueVars(invokeInstruction,
                                                   IntStream.range(0,
                                                                   invokeInstruction.getNumberOfUses())
                                                       .boxed()
                                                       .toArray(Integer[]::new));
    if (calleeIR != null) {
      String genericSig = getGenericSignature(calleeIR);
      if (genericSig != null) {
        int paramIndexBegin = genericSig.indexOf("(") + 1;
        int paramIndexEnd = genericSig.indexOf(")");

        if (paramIndexBegin != paramIndexEnd) {//parameters are not empty.
          genericSig = genericSig.substring(paramIndexBegin, paramIndexEnd);

          //paramSplits contains the parameters withing the (...)
          Integer[] paramSplits = findSplitsForParameters(genericSig,
                                                          invokeInstruction.isStatic()
                                                              // if it is static then we need to exclude the reference from being a parameter to the function
                                                              ? invokeInstruction.getNumberOfUses()
                                                              : invokeInstruction.getNumberOfUses()
                                                                  - 1);

          ArrayList<String> genericSigParams = new ArrayList<>();
          int lastSplit = 0;
          for (Integer i : paramSplits) {
            genericSigParams.add(genericSig.substring(lastSplit, i));
            lastSplit = i;
          }
          int parameterToChange = invokeInstruction.isStatic() ? 0
              : 1; //skip updating the reference type which can be one of the parameters that wala sees
          for (int i = 0; i < genericSigParams.size(); i++) {
            String currentSig = genericSigParams.get(i);
            String[] genericTypes = currentSig.split("[;<>,]");
            Set<String> types = Arrays.stream(genericTypes).filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());
            paramUniqueVars[parameterToChange++].updateType(types);
          }
        }
      }
    }
    return paramUniqueVars;
  }

  private Integer[] findSplitsForParameters(String genericSig, int numOfParams) {

    ArrayList<Integer> paramSplitIndices = new ArrayList<Integer>();
    int numOfOpenAngleBracket = 0;

    int i = 0;
    while (i < genericSig.length()) {
      char c = genericSig.charAt(i);
      if (c == '<') {
        numOfOpenAngleBracket++;
      } else if (c == '>')
        numOfOpenAngleBracket--;
      else {
        if (numOfOpenAngleBracket == 0
            && c == ';') { // a parameter type is found between lastParamIndex and i
          paramSplitIndices.add(i);
        }
      }
      i++;
    }

    assert paramSplitIndices.size() == numOfParams : "param parser not matching numOfParams";

    return paramSplitIndices.stream().toArray(Integer[]::new);
  }


  private UniqueVar getReturnUniqueVar(IR calleeIR, SSAInvokeInstruction invokeInstruction) {
    UniqueVar returnValUniqueVar = getInvokeReturnUniqueVar(invokeInstruction,
                                                            invokeInstruction.getDeclaredResultType());
    if (calleeIR != null) {
      String genericSig = getGenericSignature(calleeIR);
      if (genericSig != null) {
        genericSig = genericSig.substring(genericSig.indexOf(")") + 1);
        String[] genericTypes = genericSig.split("[;<>,]");
        returnValUniqueVar.updateType(Arrays.stream(genericTypes).collect(Collectors.toSet()));
      }
    }
    return returnValUniqueVar;
  }


  private String getGenericSignature(IR calleeIR) {
    try {
      Method getGenericSignatureMethod = ShrikeCTMethod.class.getDeclaredMethod(
          "getGenericsSignature");
      getGenericSignatureMethod.setAccessible(true);
      // we cannot get the generic signature for synthetic Wala's method, we just return then.
      if (calleeIR.getMethod().toString()
          .startsWith("synthetic < Primordial") || calleeIR instanceof SyntheticIR)
        return null;
      String genericSignature = (String) getGenericSignatureMethod.invoke(calleeIR.getMethod(),
                                                                          null);
      return genericSignature;
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      //do nothing
    }
    return null;
  }


  @Override
  public void visitNew(SSANewInstruction instruction) {
    boolean hasUseVars = instruction.getNumberOfUses() > 0;
    UniqueVar defUniqueVar = getDefUniqueVar(instruction);
    //if Wala's type inferencing did not come up with precise type, we'll use the declared type within the new instruction.
    if (defUniqueVar.findType().equals("Ljava/lang/Object"))
      defUniqueVar.updateType(instruction.getNewSite().getDeclaredType().getName().toString());
    AnalysisPasses.addDependencyToDummy(defUniqueVar);
    if (!hasUseVars)
      return;
    UniqueVar[] useVars = getUseUniqueVars(instruction,
                                           IntStream.range(0,
                                                           instruction.getNumberOfUses()).boxed()
                                               .toArray(Integer[]::new));
    AnalysisPasses.addDependency(defUniqueVar, useVars);
  }

  @Override
  public void visitArrayLength(SSAArrayLengthInstruction instruction) {
    UniqueVar defUniqueVar = getDefUniqueVar(instruction);
    UniqueVar refUniqueVar = getRefUniqueVar(instruction);
    AnalysisPasses.addDependency(defUniqueVar, refUniqueVar);
  }

  @Override
  public void visitThrow(SSAThrowInstruction instruction) {}

  @Override
  public void visitMonitor(SSAMonitorInstruction instruction) {}

  @Override
  public void visitCheckCast(SSACheckCastInstruction instruction) {
    UniqueVar defUniqueVar = AnalysisPasses.createUniqueVarObj(ir, instruction.getDef(),
                                                               instruction.getDeclaredResultTypes()[0]);
    for (int i = 1; i < instruction.getDeclaredResultTypes().length; i++)
      defUniqueVar.updateType(instruction.getDeclaredResultTypes()[i].toString());

    AnalysisPasses.addDependencyToDummy(defUniqueVar);
    UniqueVar[] useUniqueVars = getUseUniqueVars(instruction, 0);
    AnalysisPasses.addDependency(defUniqueVar, useUniqueVars);
  }

  @Override
  public void visitInstanceof(SSAInstanceofInstruction instruction) {
    UniqueVar defUniqueVar = getDefUniqueVar(instruction);
    UniqueVar[] useUniqueVars = getUseUniqueVars(instruction, 0);
    AnalysisPasses.addDependency(defUniqueVar, useUniqueVars);
  }

  @Override
  public void visitPhi(SSAPhiInstruction instruction) {
    UniqueVar defUniqueVar = getDefUniqueVar(instruction);
    UniqueVar[] useVars = getUseUniqueVars(instruction,
                                           IntStream.range(0,
                                                           instruction.getNumberOfUses()).boxed()
                                               .toArray(Integer[]::new));
    AnalysisPasses.addDependency(defUniqueVar, useVars);
  }

  @Override
  public void visitPi(SSAPiInstruction instruction) {}

  @Override
  public void visitGetCaughtException(SSAGetCaughtExceptionInstruction instruction) {}

  @Override
  public void visitLoadMetadata(SSALoadMetadataInstruction instruction) {
//    UniqueVar defUniqueVar = getDefUniqueVar(instruction);
    UniqueVar defUniqueVar = AnalysisPasses.createUniqueVarObj(ir, instruction.getDef(),
                                                               instruction.getType());
    AnalysisPasses.addDependencyToDummy(defUniqueVar);
  }


  public UniqueVar getDefUniqueVar(SSAInstruction instruction) {
    int ssaDefNum = instruction.getDef();
    return AnalysisPasses.createUniqueVarObj(ir, ssaDefNum);
  }


  public UniqueVar getRefUniqueVar(SSAInstruction instruction) {
    assert instruction instanceof SSAFieldAccessInstruction
        || instruction instanceof SSAArrayReferenceInstruction
        || instruction instanceof SSAArrayLengthInstruction;
    int useRefVar;
    if (instruction instanceof SSAFieldAccessInstruction)
      useRefVar = ((SSAFieldAccessInstruction) instruction).getRef();
    else if (instruction instanceof SSAArrayReferenceInstruction)
      useRefVar = ((SSAArrayReferenceInstruction) instruction).getArrayRef();
    else
      useRefVar = ((SSAArrayLengthInstruction) instruction).getArrayRef();

    return AnalysisPasses.createUniqueVarObj(ir, useRefVar);
  }

  public UniqueVar getIndexUniqueVars(SSAArrayReferenceInstruction instruction) {
    int indexVar = instruction.getIndex();
    return AnalysisPasses.createUniqueVarObj(ir, indexVar);
  }

  public UniqueVar getInvokeReturnUniqueVar(SSAInvokeInstruction instruction,
      TypeReference declaredResultType) {
    int returnVar = instruction.getReturnValue(0);
    return AnalysisPasses.createUniqueVarObj(ir, returnVar, declaredResultType);
  }

  public UniqueVar getReturnValUniqueVars(SSAReturnInstruction instruction) {
    int returnValVar = instruction.getResult();
    return AnalysisPasses.createUniqueVarObj(ir, returnValVar);
  }

/*  public UniqueVar getDeclaringClassUniqueVars(SSAInvokeInstruction instruction) {
    int returnValVar = instruction.getCallSite().getDeclaredTarget().getDeclaringClass();
    return DependencyManager.createUniqueVarObj(ir, returnValVar);
  }*/

  public UniqueVar[] getUseUniqueVars(SSAInstruction instruction, Integer... useVals) {
    UniqueVar[] uniqueVars = new UniqueVar[useVals.length];
    int uniqueVarIndex = 0;
    for (int useVal : useVals) {
      int useVar = instruction.getUse(useVal);
      uniqueVars[uniqueVarIndex++] = AnalysisPasses.createUniqueVarObj(ir, useVar);
    }
    return uniqueVars;
  }

  public void walk() {
    SSAInstruction[] instructions = ir.getInstructions();

    for (int irInstIndex = 0; irInstIndex < instructions.length; irInstIndex++) {
      SSAInstruction ins = instructions[irInstIndex];
      if (ins != null) {
        ins.visit(this);
      }
    }
//    iterating over the phis
    Iterator<? extends SSAInstruction> phiItr = ir.iteratePhis();
    while (phiItr.hasNext()) {
      SSAInstruction phiInst = phiItr.next();
      if (phiInst != null) {
        phiInst.visit(this);
      }
    }
  }

  public void clear() {
    visitedClassesMethod = new HashSet<>();
  }
}
