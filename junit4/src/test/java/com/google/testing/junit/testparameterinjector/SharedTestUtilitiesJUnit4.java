/*
 * Copyright 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.testing.junit.testparameterinjector;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

/** Shared utility code for TestParameterInjector (JUnit4) tests. */
class SharedTestUtilitiesJUnit4 {

  /**
   * Runs the given test runner.
   *
   * @throws AssertionError if the test instance reports any failures
   */
  static void runTestsAndAssertNoFailures(Runner testRunner) {
    final ImmutableList.Builder<Failure> failuresBuilder = ImmutableList.builder();
    RunNotifier notifier = new RunNotifier();
    notifier.addFirstListener(
        new RunListener() {
          @Override
          public void testFailure(Failure failure) throws Exception {
            failuresBuilder.add(failure);
          }
        });

    testRunner.run(notifier);

    ImmutableList<Failure> failures = failuresBuilder.build();

    if (failures.size() == 1) {
      throw new AssertionError(getOnlyElement(failures).getException());
    } else if (failures.size() > 1) {
      throw new AssertionError(
          String.format(
              "Test failed unexpectedly:\n\n%s",
              FluentIterable.from(failures)
                  .transform(
                      f ->
                          String.format(
                              "<<%s>> %s",
                              f.getDescription(),
                              Throwables.getStackTraceAsString(f.getException())))
                  .join(Joiner.on("\n------------------------------------\n"))));
    }
  }

  private static String toCopyPastableJavaString(Map<String, String> map) {
    StringBuilder resultBuilder = new StringBuilder();
    resultBuilder.append("\n----------------------\n");
    resultBuilder.append("ImmutableMap.<String, String>builder()\n");
    for (Entry<String, String> entry : map.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      resultBuilder.append(String.format("    .put(\"%s\", \"%s\")\n", key, value));
    }
    resultBuilder.append("    .build()\n");
    resultBuilder.append("----------------------\n");
    return resultBuilder.toString();
  }

  /**
   * Base class for a test class that acts as a test case testing a single property of a
   * TestParameterInjector-run test.
   */
  abstract static class SuccessfulTestCaseBase {

    @Rule public TestName testName = new TestName();

    private static Map<String, String> testNameToStringifiedParameters;
    private static ImmutableMap<String, String> expectedTestNameToStringifiedParameters;

    @BeforeClass
    public static void checkStaticFieldAreNull() {
      checkState(testNameToStringifiedParameters == null);
      checkState(expectedTestNameToStringifiedParameters == null);
    }

    final void storeTestParametersForThisTest(Object... params) {
      if (testNameToStringifiedParameters == null) {
        testNameToStringifiedParameters = new LinkedHashMap<>();
        // Copying this into a static field because @AfterAll methods have to be static
        expectedTestNameToStringifiedParameters = expectedTestNameToStringifiedParameters();
      }
      checkState(
          !testNameToStringifiedParameters.containsKey(testName.getMethodName()),
          "Parameters for the test with name '%s' are already stored. This might mean that there"
              + " are duplicate test names",
          testName.getMethodName());
      testNameToStringifiedParameters.put(
          testName.getMethodName(),
          FluentIterable.from(params).transform(String::valueOf).join(Joiner.on(":")));
    }

    abstract ImmutableMap<String, String> expectedTestNameToStringifiedParameters();

    @AfterClass
    public static void completedAllTests() {
      checkNotNull(
          testNameToStringifiedParameters, "storeTestParametersForThisTest() was never called");
      try {
        assertWithMessage(toCopyPastableJavaString(testNameToStringifiedParameters))
            .that(testNameToStringifiedParameters)
            .isEqualTo(expectedTestNameToStringifiedParameters);
      } finally {
        testNameToStringifiedParameters = null;
        expectedTestNameToStringifiedParameters = null;
      }
    }
  }

  private SharedTestUtilitiesJUnit4() {}
}
