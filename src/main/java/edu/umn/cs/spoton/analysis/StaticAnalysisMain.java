package edu.umn.cs.spoton.analysis;

import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import edu.umn.cs.spoton.IOUtil;
import edu.umn.cs.spoton.SpotOnException;
import edu.umn.cs.spoton.analysis.influencing.GraphsConstruction;
import edu.umn.cs.spoton.analysis.influencing.SourceCodePoint;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class StaticAnalysisMain {

  public static HashSet<String> walaDisregardedTypes = new HashSet<>(
      //TODO: THESE TYPES NEEDS TO BE GENERALIZED IN WALA
      Arrays.asList("I", "Z", "null", "D", "J", "TA", "TE"));
  /**
   * restricts the analysis to walk only the methods that exists in the user's code. Otherwise, it
   * can go to libraries that are not restricted such as the java.util.
   */
  public static HashSet<String> disregardedJavaTypes = new HashSet<>(
      Arrays.asList("Ljava/lang/", "[Ljava/lang/"));

  /**
   * the user defined package on which we want to run the analysis
   */
  public String dependencyPackage;
  /**
   * the main method that we need to start the analysis from, sometimes we need to make a fake main
   * to run the serverless lambda
   */
  public String analysisMainMethod;
  /**
   * the entry class of the program we want to analyze
   */
  String dependencyEntryClass;
  /**
   * the path of the jar file we want to analyze
   */
  public String jarClasspath;
  /**
   * a boolean indicating whether we want to collect only constant strings or we want to compute
   * influencing types too.
   */
  boolean computeInfluencingTypes;
  /**
   * directory of the output
   */
  File outputDir = null;
  /**
   * the file where the stringConstantTable is going to be dumped
   */
  File stringConstantsFile;

  /*
  main datastructure carrying the influencing types of sourcecode points
   */
  static HashMap<SourceCodePoint, Map<String, Integer>> sourcePointsToTypesMap = new HashMap<>();
  /**
   * main datastructure carrying the string constants.
   */
  public static HashSet<String> stringTable = new HashSet<String>();

  public static void main(String[] args)
      throws SpotOnException, ClassHierarchyException, CallGraphBuilderCancelException, IOException {
    StaticAnalysisMain staticAnalysisMain = new StaticAnalysisMain(args);
    staticAnalysisMain.run();
    staticAnalysisMain.dumpOutput();
  }

  private void dumpOutput() throws IOException {
    for (String constStr : stringTable)
      IOUtil.appendLineToFile(stringConstantsFile, constStr);
    ArrayList<Entry<SourceCodePoint, Map<String, Integer>>> scpEntries = new ArrayList<>(
        sourcePointsToTypesMap.entrySet());
    System.out.println("about to print scpEntries");
    System.out.println("outputDir = " + outputDir);
    System.out.println("scpEntries.length = " + scpEntries.size());
    for (int i = 0; i < scpEntries.size(); i++) {
      File singleScpInfoFile = new File(outputDir, "scp_" + i + ".txt");
      dumpScpToFile(scpEntries.get(i), singleScpInfoFile);
    }
  }

  /**
   * dumps for every sourceCodePoint a file with the information about its influencing types.
   *
   * @param sourceCodePointMapEntry
   * @param singleScpInfoFile
   */
  private void dumpScpToFile(Entry<SourceCodePoint, Map<String, Integer>> sourceCodePointMapEntry,
      File singleScpInfoFile)
      throws IOException {
    SourceCodePoint scp = sourceCodePointMapEntry.getKey();
    Map<String, Integer> typesMap = sourceCodePointMapEntry.getValue();
    IOUtil.appendLineToFile(singleScpInfoFile, scp.toString());
    for (Map.Entry typeEntry : typesMap.entrySet())
      IOUtil.appendLineToFile(singleScpInfoFile, typeEntry.toString());
  }

  public StaticAnalysisMain(String[] args) throws IOException {
    parseInput(args);
    stringConstantsFile = new File(outputDir, "StrConstants.txt");
//    sourceCodePointsInfoDir = new File(outputDir, "SourceCodePointsInfo");
    if (outputDir.exists()) {}
    IOUtil.deleteFile(outputDir);
    IOUtil.createDirectory(outputDir);
//    sourceCodePointsInfoDir.mkdirs();
  }

  private void parseInput(String[] args) {
    assert args.length == 6 : "need to enter values for each of "
        + "computeInfluencingTypes\n"
        + "dependencyPackage\n"
        + "dependencyEntryClass\n"
        + "jarClasspath\n"
        + "fakeMain"
        + "outputDir";
    computeInfluencingTypes = Boolean.parseBoolean(args[0]);
    dependencyPackage = args[1].replaceAll("\\.","/");
    dependencyEntryClass = args[2].replaceAll("\\.","/");
    jarClasspath = args[3];
    analysisMainMethod = args[4];
    outputDir = new File(args[5] + "/analysisOutput");

    System.out.println("printing passed parameters");
    System.out.println("args = " + Arrays.toString(args));
    System.out.println("computeInfluencingTypes = " + computeInfluencingTypes);
    System.out.println("dependencyPackage = " + dependencyPackage);
    System.out.println("dependencyEntryClass = " + dependencyEntryClass);
    System.out.println("jarClasspath =" + jarClasspath);
    System.out.println("analysisMainMethod =" + analysisMainMethod);
    System.out.println("outputDir =" + outputDir);
  }

  /**
   * Main entry point to run the static analysis. It computes both the influencing types, if turned
   * on, and the constant string table.
   **/
  public void run()
      throws ClassHierarchyException, CallGraphBuilderCancelException, SpotOnException, IOException {
    GraphsConstruction graphsConstruction = new GraphsConstruction(computeInfluencingTypes);
    List<Object> result = graphsConstruction.runAnalysisPasses(
        dependencyEntryClass,
        analysisMainMethod,
        jarClasspath, dependencyPackage);

    assert result.size() == 2 && result.get(0) instanceof HashMap && result.get(
        1) instanceof HashSet : "Assumptions of the static analysis are voilated.";
    sourcePointsToTypesMap = (HashMap<SourceCodePoint, Map<String, Integer>>) result.get(0);
    stringTable = (HashSet<String>) result.get(1);
    System.out.println("printing sourceCodePointsToTypesMap");
    System.out.println(sourcePointsToTypesMap);
    System.out.println("printing stringTable");
    System.out.println(stringTable);
  }
}
