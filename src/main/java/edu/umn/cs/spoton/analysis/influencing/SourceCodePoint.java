package edu.umn.cs.spoton.analysis.influencing;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.SSAInstruction;

public class SourceCodePoint {

  String fullClassName;
  String methodSignature;
//  SSAInstruction instruction;

  int sourceCodeIndex;

  int bytecodeIndex;

  public SourceCodePoint(IMethod method, SSAInstruction instruction) {
    String[] splittedSignature = TypeUtil.getSplittedSignature(method);
    assert splittedSignature.length == 2 : "unexpected method signature for sourcecode point.";
    fullClassName = splittedSignature[0];
    methodSignature = splittedSignature[1];
//    this.instruction = instruction;
    this.sourceCodeIndex = TypeUtil.getWalaSourceLineNum(method, instruction);
    this.bytecodeIndex = TypeUtil.getWalaByteInstLineNum(method, instruction);
  }

  public SourceCodePoint(String fullClassName, String methodSignature, int sourceCodeIndex) {
    this.fullClassName = fullClassName;
    this.methodSignature = methodSignature;
    this.sourceCodeIndex = sourceCodeIndex;
  }


  @Override
  public String toString() {

    return fullClassName + "." + methodSignature + "_L" + sourceCodeIndex;
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