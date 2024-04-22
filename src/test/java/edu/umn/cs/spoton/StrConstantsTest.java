package edu.umn.cs.spoton;

import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import edu.umn.cs.spoton.analysis.influencing.GraphsConstruction;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

//import org.junit.runner.RunWith;
//import org.mockito.junit.MockitoJUnitRunner;

//@RunWith(MockitoJUnitRunner.class)
public class StrConstantsTest {

  @Before
  public void createAnalysisDriver() {

  }

  @Test
  public void testStrConstantTable()
      throws ClassHierarchyException, CallGraphBuilderCancelException, IOException, StaticAnalysisException {
    GraphsConstruction graphsConstruction = new GraphsConstruction(true);
    String classpath = this.getClass().getResource("/aws-serverless-lambda-1.0-SNAPSHOT.jar")
        .getPath();
    HashSet<String> strConstantsTable = (HashSet<String>) graphsConstruction.runAnalysisPasses(
        "lambda.ContractDynamodbHandler",
        "fakeMain()V;",
        classpath, "lambda").get(1);

    Set<String> expectedSet = Set.of("PutItem succeeded\n",
                                     "SUCCESS",
                                     "object_key",
                                     "contracts are not zero",
                                     "Hongkong",
                                     "bucket_name",
                                     "yyyy-MM-dd HHmmss",
                                     "Adding a new item",
                                     "v_code",
                                     "contract_code",
                                     "contract_code  v_code",
                                     "Error getting object s from bucket s Make sure they exist and your bucket is in the same region as this function",
                                     "The item is already existed in dynamodb",
                                     "Contracts",
                                     "upload_date");
    assert strConstantsTable.stream().sorted()
        .allMatch(o -> expectedSet.contains(o)) : "unexpected entry in strConstantTable";
    assert expectedSet.stream().sorted()
        .allMatch(o -> strConstantsTable.contains(o)) : "unexpected entry in expectedSet";
    System.out.println("finished the test");

  }

  //  dependency test that ensures that types of instructions are propagated properly.
  @Test
  public void testStrConstantTable2()
      throws ClassHierarchyException, CallGraphBuilderCancelException, IOException, StaticAnalysisException {
    GraphsConstruction graphsConstruction = new GraphsConstruction(true);
    String classpath = this.getClass()
        .getResource("/aws-s3-lambda-dynamodb-csv-loader-0.0.1-SNAPSHOT.jar")
        .getPath();
    HashSet<String> strConstantsTable = (HashSet<String>) graphsConstruction.runAnalysisPasses(
        "de.dengpeng.projects.LambdaFunctionHandler",
        "fakeMain()V;",
        classpath, "de/dengpeng/projects").get(1);

    Set<String> expectedSet = new HashSet<>(); // no constant strings are reachable from the fakeMain.
    assert strConstantsTable.stream().sorted()
        .allMatch(o -> expectedSet.contains(o)) : "unexpected entry in strConstantTable";
    assert expectedSet.stream().sorted()
        .allMatch(o -> strConstantsTable.contains(o)) : "unexpected entry in expectedSet";
    System.out.println("finished the test");
  }
}
