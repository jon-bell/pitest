/*
 * Copyright 2011 Henry Coles
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
package org.pitest.mutationtest;

import java.util.LinkedList;

import org.pitest.functional.Option;

public final class MutationStatusTestPair {

  private final int             numberOfTestsRun;
  private final DetectionStatus status;
  private final Option<String>  killingTest;
  private final LinkedList<String> allKillingTests;
  private final boolean            includeAllFailedTests;
  
  public MutationStatusTestPair(final int numberOfTestsRun,
      final DetectionStatus status) {
    this(numberOfTestsRun, status, null,null,false);
  }
  public MutationStatusTestPair(final int numberOfTestsRun,
	      final DetectionStatus status, final String killingTest) {
		this(numberOfTestsRun, status, killingTest, null,false);
	  }

  public MutationStatusTestPair(final int numberOfTestsRun,
      final DetectionStatus status, final String killingTest,
      final LinkedList<String> allKillingTests,
      final boolean includeAllKilledTests) {
    this.status = status;
    this.killingTest = Option.some(killingTest);
    this.numberOfTestsRun = numberOfTestsRun;
    this.allKillingTests = allKillingTests;
    this.includeAllFailedTests = includeAllKilledTests;
  }

  public DetectionStatus getStatus() {
    return this.status;
  }

  public Option<String> getKillingTest() {
    if (includeAllFailedTests && allKillingTests != null
        && allKillingTests.size() > 0) {
      StringBuilder sb = new StringBuilder();
      for (String s : allKillingTests) {
        sb.append(s);
        sb.append(",");
      }
      sb.deleteCharAt(sb.length() - 1);
      return Option.some(sb.toString());
    } else
      return this.killingTest;
  }

  public LinkedList<String> getAllKillingTests() {
	return allKillingTests;
}

  public int getNumberOfTestsRun() {
    return this.numberOfTestsRun;
  }

  @Override
  public String toString() {
    if (this.killingTest.hasNone()) {
      return this.status.name();
    } else {
      return this.status.name() + " by " + this.killingTest.value();
    }

  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = (prime * result)
        + ((this.killingTest == null) ? 0 : this.killingTest.hashCode());
    result = (prime * result) + this.numberOfTestsRun;
    result = (prime * result)
        + ((this.status == null) ? 0 : this.status.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final MutationStatusTestPair other = (MutationStatusTestPair) obj;
    if (this.killingTest == null) {
      if (other.killingTest != null) {
        return false;
      }
    } else if (!this.killingTest.equals(other.killingTest)) {
      return false;
    }
    if (this.numberOfTestsRun != other.numberOfTestsRun) {
      return false;
    }
    if (this.status != other.status) {
      return false;
    }
    return true;
  }

}
