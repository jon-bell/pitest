/*
 * Copyright 2012 Henry Coles
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

package org.pitest.coverage;

import java.io.FileWriter;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.pitest.classinfo.ClassInfo;
import org.pitest.classinfo.ClassName;
import org.pitest.classpath.CodeSource;
import org.pitest.functional.FCollection;
import org.pitest.testapi.Description;
import org.pitest.util.Log;

public class CoverageData implements CoverageDatabase, Serializable {


  private static final Logger LOG              = Log
      .getLogger();
  private static final long   serialVersionUID = 3125192005418727605L;

  // We calculate block coverage, but everything currently runs on line
  // coverage. Ugly mess of maps below should go when
  // api changed to work via blocks
  private final Map<InstructionLocation, Map<String, TestInfo>> instructionCoverage;
  private final Map<BlockLocation, Set<Integer>>              blocksToLines = new LinkedHashMap<>();
  private final Map<ClassName, Map<ClassLine, Set<TestInfo>>> lineCoverage  = new LinkedHashMap<>();
  private final Map<String, Collection<ClassInfo>>            classesForFile;

  private transient CodeSource                                    code;

  private transient LineMap                                       lm;

  private final List<Description>                             failingTestDescriptions = new ArrayList<>();

  public CoverageData(final CodeSource code, final LineMap lm) {
    this(code, lm, new LinkedHashMap<InstructionLocation, Set<TestInfo>>());
  }

  public void setCode(CodeSource code) {
    this.code = code;
  }

  public void setLm(LineMap lm) {
    this.lm = lm;
  }

  public CoverageData(final CodeSource code, final LineMap lm, Map<InstructionLocation, Set<TestInfo>> instructionCoverage) {
    this.instructionCoverage = new HashMap<>();
    for (Entry<InstructionLocation, Set<TestInfo>> e : instructionCoverage.entrySet()) {
      HashMap<String, TestInfo> tmp = new HashMap<>();
      this.instructionCoverage.put(e.getKey(), tmp);
      for (TestInfo i : e.getValue()) {
        tmp.put(i.toString(), i);
      }
    }
    this.code = code;
    this.lm = lm;
    this.classesForFile = FCollection.bucket(this.code.getCode(),
        keyFromClassInfo());
  }

  @Override
  public Collection<TestInfo> getTestsForInstructionLocation(
      InstructionLocation location) {
    if (this.instructionCoverage.get(location) == null) {
      return null;
    }
    return this.instructionCoverage.get(location).values();
  }

  @Override
  public Collection<TestInfo> getTestsForClassLine(final ClassLine classLine) {
    final Collection<TestInfo> result = getTestsForClassName(
        classLine.getClassName()).get(classLine);
    if (result == null) {
      return Collections.emptyList();
    } else {
      return result;
    }
  }

  public boolean allTestsGreen() {
    return this.failingTestDescriptions.isEmpty();
  }

  public int getCountFailedTests() {
    return this.failingTestDescriptions.size();
  }

  public List<Description> getFailingTestDescriptions() {
    return failingTestDescriptions;
  }

  @Override
  public Collection<ClassInfo> getClassInfo(final Collection<ClassName> classes) {
    return this.code.getClassInfo(classes);
  }

  @Override
  public int getNumberOfCoveredLines(final Collection<ClassName> mutatedClass) {
    return FCollection.fold(numberCoveredLines(), 0, mutatedClass);
  }

  @Override
  public Collection<TestInfo> getTestsForClass(final ClassName clazz) {
    final Set<TestInfo> tis = new TreeSet<>(
        new TestInfoNameComparator());
    tis.addAll(this.instructionCoverage.entrySet().stream().filter(isFor(clazz))
        .flatMap(toTests())
        .collect(Collectors.toList())
        );
    return tis;
  }


  private static HashSet<String> failedTests = new HashSet<>();

  private static LinkedList<String> messages = new LinkedList<>();

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      @Override
      public void run() {
        String outputFile = System.getenv("KP_PIT_COV_LOG");
        if (outputFile == null) {
          System.err.println(
              "NOT logging output from pit! set KP_PIT_COV_LOG to a file name if you want it");
        } else {
          try {
            FileWriter fw = new FileWriter(outputFile + System.currentTimeMillis(), false);
            for (String s : messages)
              fw.write(s);
            fw.close();
          } catch (Throwable t) {
            t.printStackTrace();
          }
        }
      }
    }));
  }
  public void calculateClassCoverage(final CoverageResult cr) {

    checkForFailedTest(cr);
    final TestInfo ti = this.createTestInfo(cr.getTestUnitDescription(),
        cr.getExecutionTime(), cr.getNumberOfCoveredBlocks(), !cr.isGreenTest());
    synchronized (messages) {
      if (cr.isGreenTest())
        messages.add("PASS: " + ti.getName() + "\n");
      else
        messages.add("FAIL: " + ti.getName() + "\n");
    }
    if(!cr.isGreenTest())
      failedTests.add(ti.getName());
    for (final BlockLocation each : cr.getCoverage()) {
      for (int i = each.getFirstInsnInBlock();
           i <= each.getLastInsnInBlock(); i++) {
        addTestsToBlockMap(ti, new InstructionLocation(each, i));
      }
    }
  }

  private void addTestsToBlockMap(final TestInfo ti, InstructionLocation each) {
    Map<String, TestInfo> tests = this.instructionCoverage.get(each);
    if (tests == null) {
      tests = new TreeMap<>();
      this.instructionCoverage.put(each, tests);
    }
    TestInfo existing = tests.get(ti.getName());
    if (existing != null) {
      existing.incrementHitCount();
    } else {
      tests.put(ti.getName(), new TestInfo(ti));
    }
  }

  @Override
  public BigInteger getCoverageIdForClass(final ClassName clazz) {
    final Map<ClassLine, Set<TestInfo>> coverage = getTestsForClassName(clazz);
    if (coverage.isEmpty()) {
      return BigInteger.ZERO;
    }

    return generateCoverageNumber(coverage);
  }

  public List<BlockCoverage> createCoverage() {
    final List<BlockCoverage> ret = new ArrayList<>();
    final HashSet<BlockLocation> visited = new HashSet<>();
    for (Entry<InstructionLocation, Map<String, TestInfo>> each : this.instructionCoverage
        .entrySet()) {
      if (visited.add(each.getKey().getBlockLocation())) {
        ret.add(toBlockCoverage().apply(each));
      }
    }
    return ret;
  }

  private static Function<Entry<InstructionLocation, Map<String, TestInfo>>, BlockCoverage> toBlockCoverage() {
    return a -> new BlockCoverage(a.getKey().getBlockLocation(), a.getValue().values());
  }

  @Override
  public Collection<ClassInfo> getClassesForFile(final String sourceFile,
      String packageName) {
    final Collection<ClassInfo> value = this.getClassesForFileCache().get(
        keyFromSourceAndPackage(sourceFile, packageName));
    if (value == null) {
      return Collections.<ClassInfo> emptyList();
    } else {
      return value;
    }
  }

  private Map<String, Collection<ClassInfo>> getClassesForFileCache() {
    return this.classesForFile;
  }

  @Override
  public CoverageSummary createSummary() {
    return new CoverageSummary(numberOfLines(), coveredLines());
  }

  private BigInteger generateCoverageNumber(
      final Map<ClassLine, Set<TestInfo>> coverage) {
    BigInteger coverageNumber = BigInteger.ZERO;
    final Set<ClassName> testClasses = new HashSet<>();
    FCollection.flatMapTo(coverage.values(), testsToClassName(), testClasses);

    for (final ClassInfo each : this.code.getClassInfo(testClasses)) {
      coverageNumber = coverageNumber.add(each.getDeepHash());
    }

    return coverageNumber;
  }

  private Function<Set<TestInfo>, Iterable<ClassName>> testsToClassName() {
    return a -> FCollection.map(a, TestInfo.toDefiningClassName());
  }

  private static Function<ClassInfo, String> keyFromClassInfo() {

    return c -> keyFromSourceAndPackage(c.getSourceFileName(), c.getName()
        .getPackage().asJavaName());
  }

  private static String keyFromSourceAndPackage(final String sourceFile,
      final String packageName) {

    return packageName + " " + sourceFile;
  }

  private Collection<ClassName> allClasses() {
    return this.code.getCodeUnderTestNames();
  }

  private int numberOfLines() {
    return FCollection.fold(numberLines(), 0,
        this.code.getClassInfo(allClasses()));
  }

  private int coveredLines() {
    return FCollection.fold(numberCoveredLines(), 0, allClasses());
  }

  private BiFunction<Integer, ClassInfo, Integer> numberLines() {
    return (a, clazz) -> a + clazz.getNumberOfCodeLines();
  }

  private void checkForFailedTest(final CoverageResult cr) {
    if (!cr.isGreenTest()) {
      recordTestFailure(cr.getTestUnitDescription());
      LOG.severe(cr.getTestUnitDescription()
          + " did not pass without mutation.");
    }
  }

  private TestInfo createTestInfo(final Description description,
      final int executionTime, final int linesCovered, final boolean failed) {
    final Optional<ClassName> testee = this.code
        .findTestee(description.getFirstTestClass());
    return new TestInfo(description.getFirstTestClass(),
        description.getQualifiedName(), executionTime, testee, linesCovered, failed);
  }

  private BiFunction<Integer, ClassName, Integer> numberCoveredLines() {
    return (a, clazz) -> a + getNumberOfCoveredLines(clazz);
  }

  private int getNumberOfCoveredLines(final ClassName clazz) {
    final Map<ClassLine, Set<TestInfo>> map = getTestsForClassName(clazz);
    if (map != null) {
      return map.size();
    } else {
      return 0;
    }

  }

  private Map<ClassLine, Set<TestInfo>> getTestsForClassName(
      final ClassName clazz) {
    // Use any test that provided some coverage of the class
    // This fails to consider tests that only accessed a static variable
    // of the class in question as this does not register as coverage.
    final Map<ClassLine, Set<TestInfo>> map = this.lineCoverage.get(clazz);
    if (map != null) {
      return map;
    }

    return convertInstructionCoverageToLineCoverageForClass(clazz);

  }

  private Map<ClassLine, Set<TestInfo>> convertInstructionCoverageToLineCoverageForClass(
      ClassName clazz) {
    final List<Entry<InstructionLocation, Map<String, TestInfo>>> tests = FCollection.filter(
        this.instructionCoverage.entrySet(), isFor(clazz));

    final Map<ClassLine, Set<TestInfo>> linesToTests = new LinkedHashMap<>(
        0);

    for (final Entry<InstructionLocation, Map<String, TestInfo>> each : tests) {
      for (final int line : getLinesForBlock(each.getKey().getBlockLocation())) {
        final Set<TestInfo> tis = getLineTestSet(clazz, linesToTests, each, line, failedTests);
        for (final Entry<String, TestInfo> notEach : each.getValue().entrySet()) {
          if (!failedTests.contains(notEach.getKey())) {
            tis.add(notEach.getValue());
          }
        }
      }
    }

    this.lineCoverage.put(clazz, linesToTests);
    return linesToTests;
  }

  private static Set<TestInfo> getLineTestSet(ClassName clazz,
      Map<ClassLine, Set<TestInfo>> linesToTests,
      Entry<InstructionLocation, Map<String, TestInfo>> each, int line,
      HashSet<String> failedTests) {
    final ClassLine cl = new ClassLine(clazz, line);
    Set<TestInfo> tis = linesToTests.get(cl);
    if (tis == null) {
      tis = new TreeSet<>(new TestInfoNameComparator());
      linesToTests.put(new ClassLine(clazz, line), tis);
    }
    return tis;
  }

  public Set<Integer> getLinesForBlock(BlockLocation bl) {
    Set<Integer> lines = this.blocksToLines.get(bl);
    if (lines == null) {
      calculateLinesForBlocks(bl.getLocation().getClassName());
      lines = this.blocksToLines.get(bl);
      if (lines == null) {
        lines = Collections.emptySet();
      }
    }

    return lines;
  }

  private void calculateLinesForBlocks(ClassName className) {
    final Map<BlockLocation, Set<Integer>> lines = this.lm.mapLines(className);
    this.blocksToLines.putAll(lines);
  }

  private void recordTestFailure(final Description testDescription) {
    this.failingTestDescriptions.add(testDescription);
  }

  private Function<Entry<InstructionLocation, Map<String,TestInfo>>, Stream<TestInfo>> toTests() {
    return a -> a.getValue().values().stream().filter(isFailed());
  }

  private Predicate<TestInfo> isFailed(){
    return a -> !failedTests.contains(a.getName());
  }

  private Predicate<Entry<InstructionLocation, Map<String, TestInfo>>> isFor(
      final ClassName clazz) {
    return a -> a.getKey().isFor(clazz);
  }

}
