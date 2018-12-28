package org.pitest.java8.verification;

import static java.util.Arrays.asList;
import static org.pitest.mutationtest.DetectionStatus.KILLED;

import com.example.dependencies.Dependent1Test;
import com.example.dependencies.Dependent2Test;
import com.example.java8.Java8LambdaExpressionTest;
import org.junit.Assert;
import org.junit.Test;
import org.pitest.mutationtest.MutationResult;
import org.pitest.mutationtest.ReportTestBase;

public class VerifyDependenciesIT extends ReportTestBase {
  @Test
  public void worksWithDependentTests() {
    this.data.setFullMutationMatrix(true);
    this.data.setTargetTests(predicateFor(Dependent1Test.class.getName(),
        Dependent2Test.class.getName()));
    this.data
        .setTargetClasses(asList("com.example.dependencies.TargetDependentClass"));
    setMutators("RETURN_VALS");
    createAndRun();
    for(MutationResult r : this.metaDataExtractor.getData()){
      Assert.assertEquals(2,r.getKillingTests().size());
    }
  }
}
