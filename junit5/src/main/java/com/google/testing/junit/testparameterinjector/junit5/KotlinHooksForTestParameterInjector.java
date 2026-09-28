/*
 * Copyright 2026 Google Inc.
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

package com.google.testing.junit.testparameterinjector.junit5;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.junit5.TestParameterInjectorUtils.JavaCompatibilityExecutable;
import javax.annotation.Nullable;

/**
 * Functionality for the TestParameterInjector implementation that can only be implemented using
 * Kotlin. See {@code KotlinHooksForTestParameterInjectorImpl} for the implementation.
 *
 * <p>Note that this interface is written in Java (as opposed to Kotlin) because its signatures
 * refer to package-private Java types, which internal Kotlin declarations may not expose (see
 * https://youtrack.jetbrains.com/issue/KTLC-271).
 */
interface KotlinHooksForTestParameterInjector {

  /** Returns the parameter names of the given Kotlin executable, if they can be determined. */
  Optional<ImmutableList<String>> getParameterNames(JavaCompatibilityExecutable executable);

  /**
   * Returns whether the given Kotlin executable has at least one parameter with a default value.
   */
  boolean hasOptionalParameters(JavaCompatibilityExecutable executable);

  /**
   * Returns all combinations of test parameter values for the given executable.
   *
   * <p>Every element of the returned list contains exactly one value for each parameter of {@code
   * executable}, in declaration order. The default value of a parameter is evaluated once for every
   * combination of the parameters that precede it, which is what allows a default value to depend
   * on those earlier parameters.
   */
  ImmutableList<ImmutableList<IndexedTestParameterValue>> extractValueCombinations(
      @Nullable Object testInstance,
      JavaCompatibilityExecutable executable,
      Function<Integer, Optional<ImmutableList<TestParameterValue>>> getExplicitValuesByIndex,
      Function<Integer, ImmutableList<TestParameterValue>> getImplicitValuesByIndex);
}
