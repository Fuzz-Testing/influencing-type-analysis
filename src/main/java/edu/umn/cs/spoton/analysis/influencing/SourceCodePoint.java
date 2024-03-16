package edu.umn.cs.spoton.analysis.influencing;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.SSAInstruction;

public class SourceCodePoint {

  String fullClassName;
  String methodSignature;
//  SSAInstruction instruction;

  int sourceCodeIndex;

  int bytecodeIndex;

  int iid;

  public SourceCodePoint(IMethod method, SSAInstruction instruction) {
    String[] splittedSignature = TypeUtil.getSplittedSignature(method);
    assert splittedSignature.length == 2 : "unexpected method signature for sourcecode point.";
    fullClassName = splittedSignature[0];
    methodSignature = splittedSignature[1];
//    this.instruction = instruction;
    this.sourceCodeIndex = TypeUtil.getWalaSourceLineNum(method, instruction);
    this.bytecodeIndex = TypeUtil.getWalaByteInstLineNum(method, instruction);
    this.iid = -1;
  }

  public SourceCodePoint(String fullClassName, String methodSignature, int sourceCodeIndex,
      int iid) {
    this.fullClassName = fullClassName;
    this.methodSignature = methodSignature;
    this.sourceCodeIndex = sourceCodeIndex;
    this.iid = iid;
  }

  public SourceCodePoint(String scpStr) {
    scpStr = scpStr.substring(0, scpStr.lastIndexOf("_"));
    int indexLocation = scpStr.lastIndexOf("_L");
    this.sourceCodeIndex = Integer.parseInt(scpStr.substring(indexLocation + 2));
    this.methodSignature = scpStr.substring(scpStr.lastIndexOf(".") + 1, indexLocation);
    this.fullClassName = scpStr.substring(0, scpStr.lastIndexOf("."));
    this.iid = -1;
  }

  public int getIid() {
    return iid;
  }

  public String noIidToString() {

    return fullClassName + "." + methodSignature + "_L" + sourceCodeIndex;
  }

  @Override
  public String toString() {

    return fullClassName + "." + methodSignature + "_L" + sourceCodeIndex + "_" + iid;
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SourceCodePoint))
      return false;

    return toString().equals(obj.toString());
  }

  public String getFullClassName() {
    return fullClassName;
  }
}
