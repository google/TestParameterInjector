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

import static com.google.common.collect.Iterables.getOnlyElement;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
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
    ImmutableList<Failure> failures = runTestsAndGetFailures(testRunner);

    if (failures.size() == 1) {
      throw new AssertionError(getOnlyElement(failures).getException());
    } else if (failures.size() > 1) {
      throw new AssertionError(
          String.format(
              "Test failed unexpectedly:\n\n%s",
              failures.stream()
                  .map(
                      f ->
                          String.format(
                              "<<%s>> %s",
                              f.getDescription(),
                              Throwables.getStackTraceAsString(f.getException())))
                  .collect(joining("\n------------------------------------\n"))));
    }
  }

  /**
   * Runs the given test runner.
   *
   * @return all failures reported by the test instance.
   */
  static ImmutableList<Failure> runTestsAndGetFailures(Runner testRunner) {
    final ImmutableList.Builder<Failure> failures = ImmutableList.builder();
    RunNotifier notifier = new RunNotifier();
    notifier.addFirstListener(
        new RunListener() {
          @Override
          public void testFailure(Failure failure) throws Exception {
            failures.add(failure);
          }
        });

    testRunner.run(notifier);

    return failures.build();
  }

  private SharedTestUtilitiesJUnit4() {}
}
