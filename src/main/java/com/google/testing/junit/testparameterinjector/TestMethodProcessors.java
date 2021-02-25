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

import com.google.common.collect.ImmutableList;
import org.junit.runners.model.TestClass;

/** Factory for all {@link TestMethodProcessor} implementations that this package supports. */
final class TestMethodProcessors {

  /**
   * Returns a new instance of every {@link TestMethodProcessor} implementation that this package
   * supports.
   *
   * <p>Note that this includes support for {@link org.junit.runners.Parameterized}.
   */
  public static ImmutableList<TestMethodProcessor>
      createNewParameterizedProcessorsWithLegacyFeatures(TestClass testClass) {
    return ImmutableList.of(
        new ParameterizedTestMethodProcessor(testClass),
        new TestParametersMethodProcessor(testClass),
        TestParameterAnnotationMethodProcessor.forAllAnnotationPlacements(testClass));
  }

  /**
   * Returns a new instance of every {@link TestMethodProcessor} implementation that this package
   * supports, except the following legacy features:
   *
   * <ul>
   *   <li>No support for {@link org.junit.runners.Parameterized}
   *   <li>No support for class and method-level parameters, except for @TestParameters
   * </ul>
   */
  public static ImmutableList<TestMethodProcessor> createNewParameterizedProcessors(
      TestClass testClass) {
    return ImmutableList.of(
        new TestParametersMethodProcessor(testClass),
        TestParameterAnnotationMethodProcessor.onlyForFieldsAndParameters(testClass));
  }

  private TestMethodProcessors() {}
}
