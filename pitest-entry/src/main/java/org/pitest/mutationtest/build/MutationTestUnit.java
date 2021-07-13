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
package org.pitest.mutationtest.build;

import org.pitest.classinfo.ClassName;
import org.pitest.coverage.TestInfo;
import org.pitest.mutationtest.DetectionStatus;
import org.pitest.mutationtest.MutationMetaData;
import org.pitest.mutationtest.MutationStatusMap;
import org.pitest.mutationtest.MutationStatusTestPair;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.execute.MutationTestProcess;
import org.pitest.util.ExitCode;
import org.pitest.util.Log;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MutationTestUnit implements MutationAnalysisUnit {

  private static final Logger               LOG = Log.getLogger();

  private final Collection<MutationDetails> availableMutations;
  private final WorkerFactory               workerFactory;

  private final Collection<ClassName>       testClasses;

  public MutationTestUnit(final Collection<MutationDetails> availableMutations,
      final Collection<ClassName> testClasses, final WorkerFactory workerFactor) {
    this.availableMutations = availableMutations;
    this.testClasses = testClasses;
    this.workerFactory = workerFactor;
  }

  @Override
  public MutationMetaData call() throws Exception {
    final MutationStatusMap mutations = new MutationStatusMap();

    mutations.setStatusForMutations(this.availableMutations,
        DetectionStatus.NOT_STARTED);

    mutations.markUncoveredMutations();

    runTestsInSeperateProcess(mutations);

    return reportResults(mutations);
  }

  @Override
  public int priority() {
    return this.availableMutations.size();
  }

  private void runTestsInSeperateProcess(final MutationStatusMap mutations)
      throws IOException, InterruptedException {
    while (mutations.hasUnrunMutations()) {
      runTestInSeperateProcessForMutationRange(mutations);
    }
  }

  private String dumpUncoveredStatistics(List<MutationDetails> mutants){
   int nMutants = 0;
   int nMutantTestPairs = 0;
   for(MutationDetails d : mutants)
   {
     if(d.getTestsInOrder().size() > 0)
     {
       nMutants++;
       nMutantTestPairs += d.getTestsInOrder().size();
     }
   }
    return "NotCovered: " + nMutants + " mutants, " + nMutantTestPairs + " pairs";
  }

  private void runTestInSeperateProcessForMutationRange(
      final MutationStatusMap mutations) throws IOException,
      InterruptedException {

    List<MutationDetails> remainingMutations = mutations
        .getUnrunMutations();

    // If the option to not run any mutants (but report those that should be run), then set appropriately
    if (System.getenv("PIT_NO_RUN_MUTANTS") != null) {
        setFirstMutationToStatusOfStartedInCaseMinionFailsAtBoot(mutations,
            remainingMutations);
        // Set each mutation tests surviving to those that should be running on it
        for (MutationDetails mut : remainingMutations) {
            // Convert tests in order to strings
            List<String> tests = new LinkedList<String>();
            for (TestInfo ti : mut.getTestsInOrder()) {
                tests.add(ti.getName());
            }
            mutations.setStatusForMutation(mut, new MutationStatusTestPair(0, DetectionStatus.STARTED,
                    new LinkedList<String>(), new LinkedList<String>(), tests, new LinkedList<String>()));
        }
        correctResultForProcessExitCode(mutations, ExitCode.OK);
        return;
    }


    long start, end;
    ExitCode exitCode = null;
    MutationTestProcess worker;
    if (System.getenv("PIT_RUN_MUTANT_FRESH_JVM") != null) {
      //Try to run with each mutant in its own JVM, but all tests against that mutant in the same JVM
      start = System.currentTimeMillis();

      int rerunCount = 5;
      if(System.getenv("PIT_RERUN_COUNT") != null)
        rerunCount = Integer.valueOf(System.getenv("PIT_RERUN_COUNT"));
      for (MutationDetails d : remainingMutations) {
        if (d.getTestsInOrder().size() > 0) {
          //Collections.shuffle(d.getTestsInOrder(), new Random());
          System.out.println("Running: " + d.getId());
          for(int i = 0; i < rerunCount; i++) {
            worker = this.workerFactory
                    .createWorker(Collections.singletonList(d), this.testClasses);
            worker.start();

            exitCode = waitForMinionToDie(worker);
            worker.results(mutations);
          }
        }else {
          System.out.println("Skipping: " + d.getId());
        }
      }
      end = System.currentTimeMillis();
    } else {
      //First, run all mutants normally
      System.out.println("KP_NormalStart: " + dumpUncoveredStatistics(remainingMutations));
      start = System.currentTimeMillis();
      worker = this.workerFactory.createWorker(
              remainingMutations, this.testClasses);
      worker.start();

      setFirstMutationToStatusOfStartedInCaseMinionFailsAtBoot(mutations,
              remainingMutations);

      exitCode = waitForMinionToDie(worker);
      worker.results(mutations);
      end = System.currentTimeMillis();
      System.out.println("KP_NormalEnd: " + dumpUncoveredStatistics(mutations.getUnCoveredMutations()));
      System.out.println("KP_NormalTime: " + (end - start));
    }

    if (System.getenv("PIT_RERUN_FRESH_JVM") != null) {
      for (int i = 0; i < (System.getenv("PIT_RERUN_COUNT") == null ? 5 : Integer.valueOf(System.getenv("PIT_RERUN_COUNT"))); i++) {

        remainingMutations = mutations.getUnFinishedMutations();
        Collections.shuffle(remainingMutations, new Random(i));
        System.out.println(
            "KP_RerunLight" + i + "Start: " + dumpUncoveredStatistics(
                mutations.getUnCoveredMutations()));
        start = System.currentTimeMillis();
        worker = this.workerFactory.createWorker(
            remainingMutations, this.testClasses);
        worker.start();

        exitCode = waitForMinionToDie(worker);
        worker.results(mutations);
        end = System.currentTimeMillis();
        System.out.println(
            "KP_RerunLight" + i + "End: " + dumpUncoveredStatistics(
                mutations.getUnCoveredMutations()));
        System.out.println("KP_RerunLight" + i + "Time: " + (end - start));
        if(mutations.getUnFinishedMutations().size() == 0)
          break;
      }
    }
    //Second, for only uncovered test/mutants: try to run with each mutant in its own JVM, but all tests against that mutant in the same JVM
    //AND shuffle the order of mutants
    //AND shuffle the order of tests
    if (System.getenv("PIT_RERUN_FRESH_JVM") != null) {
      for (int i = 0; i < (System.getenv("PIT_RERUN_COUNT") == null ? 5 : Integer.valueOf(System.getenv("PIT_RERUN_COUNT"))); i++) {
        remainingMutations = mutations.getUnFinishedMutations();
        Collections.shuffle(remainingMutations, new Random(i));
        System.out.println(
            "KP_RerunHeavy" + i + "Start: " + dumpUncoveredStatistics(
                mutations.getUnCoveredMutations()));
        start = System.currentTimeMillis();
        boolean haveWork = false;
        for (MutationDetails d : remainingMutations) {
          if (d.getTestsInOrder().size() > 0) {
            Collections.shuffle(d.getTestsInOrder(), new Random(i));
            worker = this.workerFactory
                .createWorker(Collections.singletonList(d), this.testClasses);
            worker.start();

            exitCode = waitForMinionToDie(worker);
            worker.results(mutations);
            haveWork = true;
          }
        }
        end = System.currentTimeMillis();
        System.out.println(
            "KP_RerunHeavy" + i + "End: " + dumpUncoveredStatistics(
                mutations.getUnCoveredMutations()));
        System.out.println("KP_RerunHeavy" + i + "Time: " + (end - start));
        if (!haveWork)
          break;

      }
    }

    //Last, for test-mutants still uncovered, try to run with each test-mutant pair in its own JVM
    //AND shuffle the order of mutants
    //AND shuffle the order of tests
    int k = 1;
    if (System.getenv("PIT_RERUN_FRESH_JVM") != null) {
      for (int i = 0; i < (System.getenv("PIT_RERUN_COUNT") == null ? 5 : Integer.valueOf(System.getenv("PIT_RERUN_COUNT"))); i++) {
        remainingMutations = mutations.getUnFinishedMutations();
        System.out.println(
            "KP_RerunVeryHeavy" + i + "Start: " + dumpUncoveredStatistics(
                mutations.getUnCoveredMutations()));
        start = System.currentTimeMillis();
        Collections.shuffle(remainingMutations, new Random(i+5));
        boolean haveWork = false;
        for (MutationDetails d : remainingMutations) {
          MutationStatusTestPair result = null;
          if (d.getTestsInOrder().size() > 0) {
            Collections.shuffle(d.getTestsInOrder(), new Random(i + 5));
            for (TestInfo t : d.getTestsInOrder()) {
              MutationDetails justThisOneTest = new MutationDetails(new MutationIdentifier(d.getId().getLocation(), d.getId().getIndexes(),d.getMutator()),
                  d.getFilename(), d.getDescription(), d.getLineNumber(),
                  d.getBlock());
              justThisOneTest.getId().uniq = k;
              k++;
              justThisOneTest.addTestsInOrder(Collections.singletonList(t));
              worker = this.workerFactory
                  .createWorker(Collections.singletonList(justThisOneTest), this.testClasses);
              worker.start();

              exitCode = waitForMinionToDie(worker);
              MutationStatusTestPair r = worker.results(justThisOneTest);
              if (result == null)
                result = r;
              else
                result.accumulate(r, false);
              haveWork = true;
            }
          }
          if (result != null) {
            mutations.setStatusForMutation(d, result);
          }

        }
        end = System.currentTimeMillis();
        System.out.println(
            "KP_RerunVeryHeavy" + i + "End: " + dumpUncoveredStatistics(
                mutations.getUnCoveredMutations()));
        System.out.println("KP_RerunVeryHeavy" + i + "Time: " + (end - start));
        if (!haveWork)
          break;

      }
    }
    correctResultForProcessExitCode(mutations, exitCode);
  }

  private static ExitCode waitForMinionToDie(final MutationTestProcess worker) {
    final ExitCode exitCode = worker.waitToDie();
    LOG.fine("Exit code was - " + exitCode);
    return exitCode;
  }

  private static void setFirstMutationToStatusOfStartedInCaseMinionFailsAtBoot(
      final MutationStatusMap mutations,
      final Collection<MutationDetails> remainingMutations) {
    mutations.setStatusForMutation(remainingMutations.iterator().next(),
        DetectionStatus.STARTED);
  }

  private static void correctResultForProcessExitCode(
      final MutationStatusMap mutations, final ExitCode exitCode) {

    if (!exitCode.isOk()) {
      final Collection<MutationDetails> unfinishedRuns = mutations
          .getUnfinishedRuns();
      final DetectionStatus status = DetectionStatus
          .getForErrorExitCode(exitCode);
      LOG.warning("Minion exited abnormally due to " + status);
      LOG.fine("Setting " + unfinishedRuns.size() + " unfinished runs to "
          + status + " state");
      mutations.setStatusForMutations(unfinishedRuns, status);

    } else {
      LOG.fine("Minion exited ok");
    }

  }

  private static MutationMetaData reportResults(final MutationStatusMap mutationsMap) {
    return new MutationMetaData(mutationsMap.createMutationResults());
  }



}
