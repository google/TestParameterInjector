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

import static java.util.stream.Collectors.toList;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.runners.model.TestClass;

/**
 * Combined version of all {@link TestMethodProcessor} implementations that this package supports.
 */
final class TestMethodProcessorList {

  private final ImmutableList<TestMethodProcessor> testMethodProcessors;

  private TestMethodProcessorList(ImmutableList<TestMethodProcessor> testMethodProcessors) {
    this.testMethodProcessors = testMethodProcessors;
  }

  /**
   * Returns a TestMethodProcessorList that supports all features that this package supports, except
   * the following legacy features:
   *
   * <ul>
   *   <li>No support for {@link org.junit.runners.Parameterized}
   *   <li>No support for class and method-level parameters, except for @TestParameters
   * </ul>
   */
  public static TestMethodProcessorList createNewParameterizedProcessors(TestClass testClass) {
    return new TestMethodProcessorList(
        ImmutableList.of(
            new TestParametersMethodProcessor(),
            TestParameterAnnotationMethodProcessor.onlyForFieldsAndParameters()));
  }

  static TestMethodProcessorList empty() {
    return new TestMethodProcessorList(ImmutableList.of());
  }

  /**
   * Calculates the TestInfo instances for the given test method. Each TestInfo corresponds to a
   * single test.
   *
   * <p>The returned list always contains at least one element. If there is no parameterization,
   * this would be the TestInfo for running the test method without parameters.
   */
  public List<TestInfo> calculateTestInfos(Method testMethod, Class<?> testClass) {
    List<TestInfo> testInfos =
        ImmutableList.of(
            TestInfo.createWithoutParameters(
                testMethod, testClass, ImmutableList.copyOf(testMethod.getAnnotations())));

    for (final TestMethodProcessor testMethodProcessor : testMethodProcessors) {
      testInfos =
          testInfos.stream()
              .flatMap(
                  lastTestInfo -> testMethodProcessor.calculateTestInfos(lastTestInfo).stream())
              .collect(toList());
    }

    testInfos = TestInfo.deduplicateTestNames(TestInfo.shortenNamesIfNecessary(testInfos));

    return testInfos;
  }

  /**
   * Returns the parameters with which it should be invoked.
   *
   * <p>This method is never called for a parameterless constructor.
   */
  public List<Object> getConstructorParameters(Constructor<?> constructor, TestInfo testInfo) {
    return testMethodProcessors.stream()
        .map(processor -> processor.maybeGetConstructorParameters(constructor, testInfo))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Could not generate parameter values for %s. Did you forget an annotation?",
                        constructor)));
  }

  /**
   * Returns the parameters with which {@code testInfo.getMethod()} should be invoked.
   *
   * <p>This method is never called for a parameterless {@code testInfo.getMethod()}.
   */
  public List<Object> getTestMethodParameters(TestInfo testInfo) {
    return testMethodProcessors.stream()
        .map(processor -> processor.maybeGetTestMethodParameters(testInfo))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Could not generate parameter values for %s. Did you forget an annotation?",
                        testInfo.getMethod())));
  }

  /**
   * Optionally process the test instance right after construction to ready it for the given test.
   */
  public void postProcessTestInstance(Object testInstance, TestInfo testInfo) {
    for (TestMethodProcessor testMethodProcessor : testMethodProcessors) {
      testMethodProcessor.postProcessTestInstance(testInstance, testInfo);
    }
  }

  /** Optionally validates the given constructor. */
  public ExecutableValidationResult validateConstructor(Constructor<?> constructor) {
    return testMethodProcessors.stream()
        .map(processor -> processor.validateConstructor(constructor))
        .filter(ExecutableValidationResult::wasValidated)
        .findFirst()
        .orElse(ExecutableValidationResult.notValidated());
  }

  /** Optionally validates the given method. */
  public ExecutableValidationResult validateTestMethod(Method testMethod, Class<?> testClass) {
    return testMethodProcessors.stream()
        .map(processor -> processor.validateTestMethod(testMethod, testClass))
        .filter(ExecutableValidationResult::wasValidated)
        .findFirst()
        .orElse(ExecutableValidationResult.notValidated());
  }
}
