package edu.umn.cs.spoton.analysis;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.TypeReference;
import edu.umn.cs.spoton.analysis.influencing.ConditionInfluencingTypeVisitor;
import edu.umn.cs.spoton.analysis.influencing.CodeTarget;
import edu.umn.cs.spoton.analysis.influencing.VarTypeVisitor;
import edu.umn.cs.spoton.analysis.influencing.UniqueVar;
import edu.umn.cs.spoton.analysis.string.StringConstantsVisitor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A unique representation of a variable that identifies it using the package and the location in
 * the source code that it belongs to.
 */

public class AnalysisPasses {

  public static UniqueVar DUMMY_LEAF_UNIQUEVAR = new UniqueVar();

  static HashSet<UniqueVar> uniqueVarHashSet = new HashSet<>();
  static LinkedHashMap<UniqueVar, List<UniqueVar>> dependencyMap = new LinkedHashMap<>();

  //keeps track of all methods we have seen so far


  /**
   * creates a UniqueVar object and populate the global symbol table.
   *
   * @param ir
   * @param ssaDefNum
   * @return
   */
  public static UniqueVar addUniqueVar(IR ir, Integer ssaDefNum) {
    UniqueVar uniqueVar = createUniqueVarObj(ir, ssaDefNum);

    uniqueVarHashSet.add(uniqueVar);
    return uniqueVar;
  }

  /**
   * create a UniqueVar Object, without populating the global symbol table.
   *
   * @param ir  //   * @param instruction
   * @param def
   * @return
   */
//  public static UniqueVar createUniqueVarObj(IR ir, SSAInstruction instruction, int def) {
//    return new UniqueVar(ir, def);
//  }
  public static UniqueVar createUniqueVarObj(IR ir, int def) {
    UniqueVar newVar = new UniqueVar(ir, def);
    /*This code is important. It tries to maintain/update types of unique vars.
    Since we use some rule-based inference to collect the types, when we creating a unique var with
    the statement above, we only rely on Wala's inference, which we know is not exactly what we are
    usually doing. thus the place with the most type precision is found in the dependencyMap.
    Thus when we are creating a new uniqueVar, we check if we have encountered it before, and collected
    it in the dependencyMap. In which case, we pull the definition of this var and update its type with
    the possibly different type, and this becomes our newVar that we'd return. This way, we do not
    lose the types that we have been collected/inferrencing with our rule.
    * */
    if (AnalysisPasses.dependencyMap.containsKey(newVar)) {
      List<UniqueVar> keyList = dependencyMap.keySet().stream().collect(Collectors.toList());
      UniqueVar uniqueVarInMap = keyList.get(keyList.indexOf(newVar));
      newVar.updateType(uniqueVarInMap.getType());
    }
    return newVar;
  }

  public static UniqueVar createUniqueVarObj(IR ir, int def, TypeReference declaredResultType) {
    UniqueVar newVar = new UniqueVar(ir, def, declaredResultType);
    return newVar;
  }

  public static String prettyDependencyMap() {

    StringBuilder stringBuilder = new StringBuilder();

    for (Map.Entry e : dependencyMap.entrySet()) {
      UniqueVar key = (UniqueVar) e.getKey();
      List dependencyList = (List) e.getValue();
      assert dependencyList.size() > 0;
      int keyLength = key.toString().length();
      stringBuilder.append(key);
      String paddingStr = String.format("%" + keyLength + "c", ' ');
      stringBuilder.append(" ---> " + dependencyList.get(0) + "\n");

      for (int i = 1; i < dependencyList.size(); i++) {
        stringBuilder.append(paddingStr);
        stringBuilder.append(dependencyList.get(i) + "\n");
      }
    }
    return stringBuilder.toString();
  }

  public static void addDependencyToDummy(UniqueVar keyVar) {
    addDependency(keyVar, DUMMY_LEAF_UNIQUEVAR);
  }

  /**
   * since def happens only once, no need to append to an existing dependency list
   *
   * @param uniqueUseVars
   */
  public static void addDependency(UniqueVar keyVar, UniqueVar... uniqueUseVars) {
    assert uniqueUseVars.length != 0;
    List<UniqueVar> dependencies = dependencyMap.getOrDefault(keyVar, new ArrayList<>());
    dependencies.addAll(List.of(uniqueUseVars));
    dependencyMap.put(keyVar, dependencies);
 /*   System.out.println("*** dependency map after adding dependency to " + keyVar + "is \n");
    System.out.println(prettyDependencyMap());*/
  }


  /**
   * Find dependencies of branches based on types. It returns a map between a branch source location
   * and a list of dependent types.
   *
   * @param m
   * @param cg
   * @param computeInfluencingTypes
   * @return
   */
  public static List<Object> start(IMethod m, IAnalysisCacheView cache,
      IClassHierarchy cha, String rootPackageName, CallGraph cg, boolean computeInfluencingTypes) {
    HashMap<CodeTarget, Map<String, Integer>> codeTargetToTypeDependencyMap = new HashMap<>();
    if (computeInfluencingTypes)
      codeTargetToTypeDependencyMap = runInfluencingTypesPass(m, cache, cha, cg, rootPackageName);

    LinkedHashSet<String> stringConstantTable = runStringConstantsPass(m, cache, cha, cg,
                                                                       rootPackageName);

    uniqueVarHashSet=new HashSet<>();
    dependencyMap = new LinkedHashMap<>();
    return Arrays.asList(codeTargetToTypeDependencyMap, stringConstantTable);
  }

  private static LinkedHashSet<String> runStringConstantsPass(IMethod m, IAnalysisCacheView cache,
      IClassHierarchy cha, CallGraph cg, String rootPackageName) {
    StringConstantsVisitor stringConstantsVisitor = new StringConstantsVisitor(m,
                                                                               cache, cha, cg,
                                                                               rootPackageName);
    stringConstantsVisitor.walk();
    return (LinkedHashSet<String>) stringConstantsVisitor.getStrConstantsTable().stream()
        .map(o -> o.replaceAll("[^A-Za-z0-9-_\\s]", ""))
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static HashMap<CodeTarget, Map<String, Integer>> runInfluencingTypesPass(IMethod m,
      IAnalysisCacheView cache,
      IClassHierarchy cha, CallGraph cg, String rootPackageName) {
    //    computes dependencies between variables.
    VarTypeVisitor typeDependencyWalker = new VarTypeVisitor(m, new UniqueVar[]{},
                                                             cache, cha, cg,
                                                             rootPackageName);
    typeDependencyWalker.walk();
    typeDependencyWalker.clear();

//    computes the dependent types for conditions, has the side effect of populating the codeTargetToTypeDependencyMap
    ConditionInfluencingTypeVisitor inferConditionTypeDependencyWalker = new ConditionInfluencingTypeVisitor(
        m, dependencyMap, cache, cha, rootPackageName, cg);
    HashMap<CodeTarget, Map<String, Integer>> codeTargetToTypeDependencyMap = inferConditionTypeDependencyWalker.walk();
    ConditionInfluencingTypeVisitor.clear();
    System.out.println("PRINTING FINAL MAP");
    StringBuilder finalScpToTypeDepMap = new StringBuilder();
    codeTargetToTypeDependencyMap.forEach(
        (key, val) -> {
          finalScpToTypeDepMap.append(key).append("->").append(val).append("\n");
        });
    System.out.println(finalScpToTypeDepMap);
    dependencyMap = new LinkedHashMap<>();
    return codeTargetToTypeDependencyMap;
  }

  /**
   * adds a dependency between a returnValUniqueVar and the unique vars enclosed within the invoke
   * instruction This method is useful, when we have already visited the method and computed its
   * internal dependency now when we encounter it again, all we need to do is make the parameter
   * bindings and the return bindings this method makes the return bindings to the caller method
   *
   * @param returnValUniqueVar
   * @param calleeIR
   */
  public static void addDefDependencyOnCalleeReturnVars(UniqueVar returnValUniqueVar, IR calleeIR) {
    List<SSAInstruction> returnInstructions = Arrays.stream(calleeIR.getInstructions())
        .filter(instruction -> instruction instanceof SSAReturnInstruction)
        .collect(Collectors.toList());

    List<UniqueVar> calleeReturnUniqueVars = returnInstructions.stream()
        .map(inst -> ((SSAReturnInstruction) inst).getUse(0))
        .map(ssaNum -> createUniqueVarObj(calleeIR, ssaNum)).collect(
            Collectors.toList());

    addDependency(returnValUniqueVar,
                  calleeReturnUniqueVars.stream().toArray(UniqueVar[]::new));
  }

  public static void addFormaParameterDependency(UniqueVar[] parameterUniqueVars, IR calleeIR) {
    int parametersNum = calleeIR.getNumberOfParameters();
    assert parameterUniqueVars.length
        == parametersNum : "formal parameters are not matching passed in parameters. failing.";
    for (int i = 0; i < parametersNum; i++) {
      UniqueVar formalParmUniqueVar = AnalysisPasses.createUniqueVarObj(calleeIR,
                                                                        calleeIR.getParameter(
                                                                            i));
      AnalysisPasses.addDependency(formalParmUniqueVar, parameterUniqueVars[i]);
    }
  }
}
