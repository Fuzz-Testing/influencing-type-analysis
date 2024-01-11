package edu.umn.cs.spoton.analysis.influencing;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.core.util.strings.StringStuff;
import com.ibm.wala.core.util.warnings.Warnings;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.io.CommandLine;
import edu.umn.cs.spoton.SpotOnException;
import edu.umn.cs.spoton.analysis.AnalysisPasses;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * Main class that analyzes all the variables' in branches and identifies the type most related to
 * them. the output of the analysis is a pair of a unique line number, and an ordered list of
 * important types affecting the decision at this line number.
 */
public class GraphsConstruction {

  IClassHierarchy cha;
  CallGraph cg;

  String rootPackageName;

  AnalysisOptions options;
  IAnalysisCacheView cache;
  boolean computeInfluencingTypes;

  public GraphsConstruction(boolean computeInfluencingTypes) {
    this.computeInfluencingTypes = computeInfluencingTypes;
  }


  public static void main(String[] args)
      throws IOException, ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException, SpotOnException {

    Properties p = CommandLine.parse(args);
    String entryClass = p.getProperty("entryClass");
    String entryMethod = p.getProperty("entryMethod");
    String classpath = p.getProperty("classpath");
    String rootPackageName = p.getProperty("rootPackageName");

    GraphsConstruction graphsConstruction = new GraphsConstruction(true);
    graphsConstruction.runAnalysisPasses(entryClass, entryMethod, classpath, rootPackageName);
  }

  public List<Object> runAnalysisPasses(String entryClass, String entryMethod,
      String classpath, String rootPackageName)
      throws IOException, ClassHierarchyException, CallGraphBuilderCancelException, SpotOnException {
    Instant beforeDate = Instant.now();
    IMethod m = prepareGraphs(entryClass, entryMethod, classpath, rootPackageName);
//    run analysis pass, whether we want to run the influencingTypes dependency or just the StringConstants pass.
    List analysisResult = AnalysisPasses.start(m, cache, cha, rootPackageName, cg,
                                               computeInfluencingTypes);
    Instant afterDate = Instant.now();
    long diff = Duration.between(beforeDate, afterDate).toMillis();
    System.out.println("duration for the analysis (ms) = " + diff);
    return analysisResult;
  }

  public IMethod prepareGraphs(String entryClass, String entryMethod,
      String classpath, String rootPackageName)
      throws ClassHierarchyException, IOException, CallGraphBuilderCancelException, SpotOnException {

    long start = System.currentTimeMillis();
    this.rootPackageName = rootPackageName;
//    if (rootPackageName != null)
//      entryClass = rootPackageName + "." + entryClass;
    if (entryClass == null || entryMethod == null) {
      throw new IllegalArgumentException(
          "specify both an entryClass and an entryMethod for the analysis");
    }
    File exclusionFile = new File(
        this.getClass().getResource("/WalaExclusions.txt").getFile());

    AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(classpath,
                                                                                   exclusionFile);
//    addDefaultExclusions(scope);
    cha = ClassHierarchyFactory.make(scope);
    options = new AnalysisOptions();
//    options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
    cache = new AnalysisCacheImpl(options.getSSAOptions());

    cg = makeCallGraph(entryClass, entryMethod, scope);
    long end = System.currentTimeMillis();

    System.out.println("took " + (end - start) + "ms");
    cha = cg.getClassHierarchy();
    IMethod m = ((CGNode) cg.getEntrypointNodes().toArray()[0]).getMethod();
    assert m != null : "entry point method cannot be null";
    return m;
  }

  public CallGraph makeCallGraph(String beginningClass, String entryMethod,
      AnalysisScope scope)
      throws CallGraphBuilderCancelException {
    System.out.println(cha.getNumberOfClasses() + " classes");
    System.out.println("beginningClass = " + beginningClass);
    Warnings.clear();

    MethodReference mr = StringStuff.makeMethodReference(beginningClass + "." + entryMethod);
    IMethod m = cha.resolveMethod(mr);
    Collection<Entrypoint> entrypoints = new ArrayList<>();
    if (m != null) {
      entrypoints.add(new DefaultEntrypoint(m, cha));
      System.out.println("entryMethod=" + entryMethod);
    } else {//collect entry Java main entry points
      Util.makeMainEntrypoints(cha)
          .forEach(e -> { //only add entry point that match the class we are looking at.
            if (e.getMethod().getDeclaringClass()
                .getName()
                .toString().contains(beginningClass.substring(beginningClass.lastIndexOf(".") + 1)))
              entrypoints.add(e);
          }); //adding all entry poitns
      System.out.println("entryMethods=" + entrypoints);
    }
    assert entrypoints.size()
        == 1 : "expected a single entry point for the call graph, but found many.";

    AnalysisOptions options = new AnalysisOptions();
    options.setEntrypoints(entrypoints);
    // you can dial down reflection handling if you like
//    options.setReflectionOptions(ReflectionOptions.NONE);
    AnalysisCache cache = new AnalysisCacheImpl();
    // other builders can be constructed with different Util methods
//    CallGraphBuilder<InstanceKey> builder = Util.makeRTABuilder(options, cache,
//                                                                cha);
    CallGraphBuilder<InstanceKey> builder = Util.makeZeroCFABuilder(Language.JAVA, options, cache,
                                                                    cha);

//    CallGraphBuilder builder = Util.makeNCFABuilder(2, options, cache, cha, scope);
//    CallGraphBuilder builder = Util.makeVanillaNCFABuilder(2, options, cache, cha, scope);
//    CallGraphBuilder builder = Util.makeZeroCFABuilder(Language.JAVA, options, cache, cha, scope);
    System.out.println("building call graph...");
    CallGraph cg = builder.makeCallGraph(options, null);
    System.out.println("done");
//    System.out.println(CallGraphStats.getStats(cg));
    return cg;
  }

}
