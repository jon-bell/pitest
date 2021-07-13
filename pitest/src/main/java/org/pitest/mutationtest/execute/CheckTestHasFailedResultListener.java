/*
 * Copyright 2010 Henry Coles
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.pitest.mutationtest.execute;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.pitest.mutationtest.DetectionStatus;
import org.pitest.testapi.Description;
import org.pitest.testapi.TestListener;
import org.pitest.testapi.TestResult;

public class CheckTestHasFailedResultListener implements TestListener {

  private final List<Description> coveringTests = new ArrayList<>();
  private final List<Description>   succeedingTests = new ArrayList<>();
  private final List<Description>   failingTests = new ArrayList<>();
  private final List<String>        failingTestExceptions = new ArrayList<>();
  private final boolean       recordPassingTests;
  private int                 testsRun        = 0;

  public CheckTestHasFailedResultListener(boolean recordPassingTests) {
    this.recordPassingTests = recordPassingTests;
  }

  private static String throwableToString(Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    return sw.toString();
  }

  @Override
  public void onTestFailure(final TestResult tr) {
    if (tr.isWasMutantHit()) {
      coveringTests.add(tr.getDescription());
    }
    this.failingTests.add(tr.getDescription());
    this.failingTestExceptions.add(throwableToString(tr.getThrowable()));
  }

  @Override
  public void onTestSkipped(final TestResult tr) {

  }

  @Override
  public void onTestStart(final Description d) {
    this.testsRun++;
  }

  @Override
  public void onTestSuccess(final TestResult tr) {
    if (tr.isWasMutantHit()) {
      coveringTests.add(tr.getDescription());
    }
//    if (recordPassingTests) {
      this.succeedingTests.add(tr.getDescription());
//    }
  }

  public DetectionStatus status() {
    if (!this.failingTests.isEmpty()) {
      if (coveringTests.isEmpty()) {
        return DetectionStatus.KILLED_NOT_COVERED;
      }
      return DetectionStatus.KILLED;
    } else {
      if (coveringTests.isEmpty()) {
        return DetectionStatus.SURVIVED_NOT_COVERED;
      }
      return DetectionStatus.SURVIVED;
    }
  }

  public List<Description> getSucceedingTests() {
    return succeedingTests;
  }

  public List<Description> getFailingTests() {
    return failingTests;
  }

  public List<String> getFailingTestExceptions() {
    return failingTestExceptions;
  }

  public List<Description> getCoveringTests() {
    return coveringTests;
  }

  public int getNumberOfTestsRun() {
    return this.testsRun;
  }

  @Override
  public void onRunEnd() {

  }

  @Override
  public void onRunStart() {

  }

}
