package edu.umn.cs.spoton.analysis.influencing;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.config.FileOfClasses;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TypeUtil {

  static String UNKNOWN_PACKAGE = "UNDEFINED";

  private static final String EXCLUSIONS =
      "java\\/awt\\/.*\n"
          + "java\\/net\\/.*\n"
          + "java\\/security\\/.*\n"
          + "java\\/rmi\\/.*\n"
          + "java\\/time\\/.*\n"
          + "apple\\/.*\n"
          + "jdk\\/.*\n"
          + "oracle\\/.*\n"
          + "javax\\/.*\n"
          + "javafx\\/.*\n"
          + "sun\\/.*\n"
          + "com\\/sun\\/.*\n"
          + "com\\/apple\\/.*\n"
          + "com\\/oracle\\/.*\n"
          + "org\\/netbeans\\/.*\n"
          + "org\\/openide\\/.*\n"
          + "org\\/w3c\\/.*\n"
          + "org\\/xml\\/.*\n"
          + "org\\/omg\\/.*\n"
          + "com\\/ibm\\/.*\n"
//          + "com\\/amazonaws\\/.*\n"
          + "edu\\/umn\\/cs\\/.*\n"
          + "edu\\/berkeley\\/cs\\/.*\n"
          + "com\\/fasterxml\\/.*\n"
          + "org\\/apache\\/.*\n"
          + "org\\/joda\\/.*\n"
          + "org\\/apache\\/.*\n"
          + "com\\/alibaba\\/.*\n"
          + "org\\/eclipse\\/.*\n"
          + "com\\/google\\/.*\n";

  public static void addDefaultExclusions(
      AnalysisScope scope) throws UnsupportedEncodingException, IOException {
    scope.setExclusions(new FileOfClasses(new ByteArrayInputStream(EXCLUSIONS.getBytes("UTF-8"))));
  }

/*
  public static String classUniqueName(String packageName, String classsName, String methodSig) {
    packageName = packageName == null ? UNKNOWN_PACKAGE : packageName;
    return packageName + "." + classsName + "." + methodSig;
  }
*/


  public static Integer getWalaByteInstLineNum(IMethod iMethod, SSAInstruction inst) {
    Integer instLine;
    try {
      instLine = (((IBytecodeMethod) (iMethod)).getBytecodeIndex(inst.iIndex()));
      return instLine;
    } catch (InvalidClassFileException e) {
      return null;
    }
  }

  public static Integer getWalaSourceLineNum(IMethod iMethod, SSAInstruction inst) {
    Integer bytecodeIndex;
    bytecodeIndex = getWalaByteInstLineNum(iMethod, inst);
    assert bytecodeIndex != null;
    int sourceLineNum = iMethod.getLineNumber(bytecodeIndex);
    return sourceLineNum;
  }

/*
  public static String constructWalaSign(IMethod method) {
    String walaPackageName = getWalaPackageName(method);
    String methodSig = method.getSelector().toString();
//    String refinedMethodSig = methodSig.replaceAll(";", "");
    String className = method.getDeclaringClass().getName().getClassName().toString();
    if (walaPackageName.equals(UNKNOWN_PACKAGE))
      return "L" + className + "." + methodSig;
    else
      return "L" + walaPackageName + "/" + className + "." + methodSig;
  }
*/


  public static String[] getSplittedSignature(IMethod method) {
    String walaPackageName = getWalaPackageName(method);
    String methodSig = method.getSelector().toString();
//    String refinedMethodSig = methodSig.replaceAll(";", "");
    String className = method.getDeclaringClass().getName().getClassName().toString();
    if (walaPackageName.equals(UNKNOWN_PACKAGE))
      return new String[]{"L" + className, methodSig};
    else
      return new String[]{"L" + walaPackageName +  File.separator + className, methodSig};
  }

  public static String getWalaPackageName(IMethod m) {
    return m.getDeclaringClass().getName().getPackage() != null ? m.getDeclaringClass().getName()
        .getPackage().toString() : UNKNOWN_PACKAGE;
  }


  public static IR resolveMethod(IMethod methodToInvoke, SSAInvokeInstruction instruction,
      IR instructionIR, IAnalysisCacheView cache, CallGraph cg) {
    IR ir = null;
    if (methodToInvoke != null)
      ir = cache.getIR(methodToInvoke, Everywhere.EVERYWHERE);
    if (ir == null) {
      CGNode callerNode = cg.getNode(instructionIR.getMethod(), Everywhere.EVERYWHERE);
      if (callerNode != null) {
        Set<CGNode> targetNodes = cg.getPossibleTargets(callerNode, instruction.getCallSite());
        List<IR> irs;
        irs = targetNodes.stream().map(target -> target.getIR()).collect(Collectors.toList());
        if (!irs.isEmpty()) {
          ir = irs.get(0);
//          since we already see instances of this happening we at this point just pick one of the possible targets
//          to analyze. In this case, we chose it to be the first one.
//          assert
//              irs.size() == 1 : "unexpected number of call targets. need to look at this instance";
        }
      }
    }
    return ir;
  }

/*  public static String constructWalaMethodSign(String walaPackageName, String methodSig,
      String className) {
    String refinedMethodSig = methodSig.replaceAll(";", "");
    if (walaPackageName.equals(UNKNOWN_PACKAGE))
      return "L" + className + "." + refinedMethodSig;
    else
      return "L" + walaPackageName + "/" + className + "." + refinedMethodSig;
  }


  //an abstract method is a method which we cannot have an IR for it.
  public static boolean isAbstractInvoke(SSAInstruction instruction) {
    if (!(instruction instanceof SSAInvokeInstruction))
      return false;
    MethodReference mr = ((SSAInvokeInstruction) instruction).getDeclaredTarget();
    IMethod m = DependencyAnalysisDriver.cha.resolveMethod(mr);
//        assert m != null : "imethod cannot be null for instruction:" + instruction.toString() + "target = "+ mr.toString();
 *//*       if (m == null)
            return false;*//*
    AnalysisOptions options = new AnalysisOptions();
    options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
    IAnalysisCacheView cache = new AnalysisCacheImpl(options.getSSAOptions());
    IR ir = cache.getIR(m, Everywhere.EVERYWHERE);
    if (ir == null)//case of an abstract method
      return true;

    return false;
  }*/
}
