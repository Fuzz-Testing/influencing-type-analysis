package edu.umn.cs.spoton.analysis.influencing;

import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.types.TypeReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class UniqueVar {

  IR ir;
  IMethod method;
//  SSAInstruction inst;

  Integer ssaVarNum;

  Set<String> type;
  String methodSignature; //adding methodSignature as a field to avoid the expense of recomputing it every time

  String fullName; //adding fulling as a field to avoid the expense of recomputing it every time

//  protected UniqueVar(IR ir, SSAInstruction inst, Integer ssaVarNum) {
//    this.ir = ir;
//    this.inst = inst;
//    this.method = ir.getMethod();
//    this.ssaVarNum = ssaVarNum;
//  }

  final String DUMMY_VAR_NAME = "DUMMY_LEAF_VAR";

  public UniqueVar(IR ir, Integer ssaVarNum) {
    this.ir = ir;
    this.method = ir.getMethod();
    this.ssaVarNum = ssaVarNum;
    String walaType = findType();
    assert walaType != null;
    this.type = new HashSet<>(Collections.singleton(walaType));
    this.methodSignature = method == null ? "_DUMMY_SIG_" : method.getSelector().toString();
    this.fullName = getClassName() + "." + methodSignature + "_A" + ssaVarNum;

  }

  public UniqueVar() {
    this.ir = null;
    this.method = null;
    this.ssaVarNum = -1;
    this.type = null;
    this.methodSignature = null;
    this.fullName = DUMMY_VAR_NAME;
  }

  public UniqueVar(IR ir, int ssaVarNum, TypeReference declaredResultType) {
    this.ir = ir;
    this.method = ir.getMethod();
    this.ssaVarNum = ssaVarNum;
    this.type = new HashSet<>(Collections.singleton(declaredResultType.getName().toString()));
    this.methodSignature = method == null ? "_DUMMY_SIG_" : method.getSelector().toString();
    this.fullName = getClassName() + "." + methodSignature + "_A" + ssaVarNum;
  }

  public Set<String> getType() {
    return type;
  }

  String getClassName() {
    return method == null ? "_DUMMY_C_"
        : method.getDeclaringClass().getName().getClassName().toString();
  }

  public String getMethodSig() {
    return methodSignature;
  }


  public void updateType(String newType) {
    this.type.add(newType);
  }

  @Override
  public String toString() {
    return fullName;
  }

  public String getFullName() {
    return fullName;
  }


  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof UniqueVar))
      return false;
    if (this.ir == null && ((UniqueVar) obj).ir == null
        && this.fullName.equals(DUMMY_VAR_NAME)
        && this.fullName.equals(
        ((UniqueVar) obj).fullName))
      return true;

    return toString().equals(obj.toString());
  }

  String findType() {
    boolean doPrimitives = true; // infer types for primitive vars?
    TypeInference ti = TypeInference.make(ir, doPrimitives);

    TypeAbstraction typeAbstraction = ti.getType(ssaVarNum);
//    if (typeAbstraction.equals(TypeAbstraction.TOP))
//      return TypeReference.Null;
//    return ti.getType(ssaVarNum).getTypeReference();

    if (typeAbstraction.equals(TypeAbstraction.TOP))
      return "null";
    return ti.getType(ssaVarNum).getTypeReference().getName().toString();
  }

  public void updateType(Set<String> types) {
    this.type.addAll(types);
  }
}