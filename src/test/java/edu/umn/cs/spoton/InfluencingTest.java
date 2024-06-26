package edu.umn.cs.spoton;

import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import edu.umn.cs.spoton.analysis.influencing.GraphsConstruction;
import edu.umn.cs.spoton.analysis.influencing.CodeTarget;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class InfluencingTest {

//  private GraphsConstruction graphsConstruction;

  @Before
  public void createAnalysisDriver() {

  }

  @Test
  public void testDependency()
      throws ClassHierarchyException, CallGraphBuilderCancelException, IOException, StaticAnalysisException {

    GraphsConstruction graphsConstruction = new GraphsConstruction(true);
    String classpath = this.getClass().getResource("/aws-serverless-lambda-1.0-SNAPSHOT.jar")
        .getPath();
    HashMap<CodeTarget, Map<String, Integer>> typeMap = (HashMap<CodeTarget, Map<String, Integer>>) graphsConstruction.runAnalysisPasses(
        "lambda.ContractDynamodbHandler",
        "fakeMain()V;",
        classpath, "lambda").get(0);
    CodeTarget expectedCodeTarget = new CodeTarget(
        "Llambda/dynamodb/ContractsDynamoDBQuery", "convert(Ljava/util/Iterator;)Ljava/util/List;",
        45,-1);
    Map<String, Integer> dependentTypes = typeMap.get(expectedCodeTarget);
    assert dependentTypes != null;
    assert dependentTypes.toString().contains("document/Item");
    assert dependentTypes.toString().contains("ItemCollection");
    assert dependentTypes.toString().contains("DynamoDB");
    assert dependentTypes.toString().contains("S3EventNotification");
    assert dependentTypes.toString().contains("S3Event");
    System.out.println("finished the test");
  }


  @Test
  public void testDependency1()
      throws ClassHierarchyException, CallGraphBuilderCancelException, IOException, StaticAnalysisException {
    GraphsConstruction graphsConstruction = new GraphsConstruction(true);
    String classpath = this.getClass().getResource("/aws-serverless-lambda-1.0-SNAPSHOT.jar")
        .getPath();
    HashMap<CodeTarget, Map<String, Integer>> typeMap = (HashMap<CodeTarget, Map<String, Integer>>) graphsConstruction.runAnalysisPasses(
        "lambda.ContractDynamodbHandler",
        "fakeMain1()V;",
        classpath, "lambda").get(0);
    CodeTarget expectedCodeTarget = new CodeTarget(
        "Llambda/dependency/DependencyTestingClass",
        "inUserPackageNotVisited(Ljava/lang/String;Ljava/util/Date;)Ljava/lang/Integer;", 18,-1);
    Map<String, Integer> dependentTypes = typeMap.get(expectedCodeTarget);
    assert dependentTypes != null;
    assert dependentTypes.toString().contains("Llambda/dependency/B");
    System.out.println("finished the test");
  }

  @Test
  public void testDependency2()
      throws ClassHierarchyException, CallGraphBuilderCancelException, IOException, StaticAnalysisException {
    GraphsConstruction graphsConstruction = new GraphsConstruction(true);
    String classpath = this.getClass().getResource("/aws-serverless-lambda-1.0-SNAPSHOT.jar")
        .getPath();
    HashMap<CodeTarget, Map<String, Integer>> typeMap = (HashMap<CodeTarget, Map<String, Integer>>) graphsConstruction.runAnalysisPasses(
        "lambda.ContractDynamodbHandler",
        "fakeMain2()V;",
        classpath, "lambda").get(0);
    CodeTarget expectedCodeTarget = new CodeTarget("Llambda/ContractDynamodbHandler",
                                                   "nestedGetsofS3(Lcom/amazonaws/services/lambda/runtime/events/S3Event;Ljava/lang/Object;)V",
                                                   112, -1);
    Map<String, Integer> dependentTypes = typeMap.get(expectedCodeTarget);
    assert dependentTypes != null;
    assert dependentTypes.toString().contains(
        "Lcom/amazonaws/services/lambda/runtime/events/models/s3/S3EventNotification$S3BucketEntity");
    assert dependentTypes.toString().contains(
        "Lcom/amazonaws/services/lambda/runtime/events/models/s3/S3EventNotification$S3Entity");
    assert dependentTypes.toString()
        .contains("Lcom/amazonaws/services/lambda/runtime/events/S3Event");
    System.out.println("finished the test");
  }


  @Test
  public void testDependency3()
      throws ClassHierarchyException, CallGraphBuilderCancelException, IOException, StaticAnalysisException {
    GraphsConstruction graphsConstruction = new GraphsConstruction(true);
    String classpath = this.getClass().getResource("/aws-serverless-lambda-1.0-SNAPSHOT.jar")
        .getPath();
    HashMap<CodeTarget, Map<String, Integer>> typeMap = (HashMap<CodeTarget, Map<String, Integer>>) graphsConstruction.runAnalysisPasses(
        "lambda.ContractDynamodbHandler",
        "fakeMain3()V;",
        classpath, "lambda").get(0);
    CodeTarget expectedCodeTarget = new CodeTarget("Llambda/ContractDynamodbHandler",
                                                   "dummyCheckItemExists(Ljava/lang/String;)Z",
                                                   101, -1);
    Map<String, Integer> dependentTypes = typeMap.get(expectedCodeTarget);
    assert dependentTypes != null;
    assert dependentTypes.toString().contains("Lcom/amazonaws/services/dynamodbv2/document/Table");
    assert dependentTypes.toString()
        .contains("Lcom/amazonaws/services/dynamodbv2/document/DynamoDB");
    assert dependentTypes.toString().contains(
        "Lcom/amazonaws/services/lambda/runtime/events/models/s3/S3EventNotification$S3BucketEntity");
    assert dependentTypes.toString().contains(
        "Lcom/amazonaws/services/lambda/runtime/events/models/s3/S3EventNotification$S3Entity");
    assert dependentTypes.toString()
        .contains("Lcom/amazonaws/services/lambda/runtime/events/S3Event");
    System.out.println("finished the test");
  }

  //  dependency test that ensures that types of instructions are propagated properly.
  @Test
  public void testDependency5()
      throws ClassHierarchyException, CallGraphBuilderCancelException, IOException, StaticAnalysisException {
    GraphsConstruction graphsConstruction = new GraphsConstruction(true);
    String classpath = this.getClass()
        .getResource("/aws-s3-lambda-dynamodb-csv-loader-0.0.1-SNAPSHOT.jar")
        .getPath();
    HashMap<CodeTarget, Map<String, Integer>> typeMap = (HashMap<CodeTarget, Map<String, Integer>>) graphsConstruction.runAnalysisPasses(
        "de.dengpeng.projects.LambdaFunctionHandler",
        "fakeMain()V;",
        classpath, "de/dengpeng/projects").get(0);

    CodeTarget expectedCodeTarget = new CodeTarget(
        "Lde/dengpeng/projects/LambdaFunctionHandler",
        "handleRequest(Lcom/amazonaws/services/lambda/runtime/events/S3Event;Lcom/amazonaws/services/lambda/runtime/Context;)Lde/dengpeng/projects/Report;",
        73,-1);
    Map<String, Integer> dependentTypes = typeMap.get(expectedCodeTarget);
    assert dependentTypes != null;
    assert dependentTypes.toString().contains("Lau/com/bytecode/opencsv/CSVReader");
    System.out.println("finished the test");
  }
}
