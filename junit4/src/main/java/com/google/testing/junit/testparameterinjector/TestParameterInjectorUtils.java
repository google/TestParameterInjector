/*
 * Copyright 2023 Google Inc.
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.common.collect.ImmutableList;
import java.lang.reflect.Constructor;

/** Shared utility methods. */
class TestParameterInjectorUtils {

  /**
   * Return the only public constructor of the given test class. If there is none, return the only
   * constructor.
   *
   * <p>Normally, there should be exactly one constructor (public or other), but some frameworks
   * introduce an extra non-public constructor (see
   * https://github.com/google/TestParameterInjector/issues/40).
   */
  static Constructor<?> getOnlyConstructor(Class<?> testClass) {
    ImmutableList<Constructor<?>> constructors = ImmutableList.copyOf(testClass.getConstructors());
    if (constructors.isEmpty()) {
      // There are no public constructors. This is likely a JUnit5 test, so we should take the only
      // non-public constructor instead.
      constructors = ImmutableList.copyOf(testClass.getDeclaredConstructors());
    }
    checkState(
        constructors.size() == 1, "Expected exactly one constructor, but got %s", constructors);
    return getOnlyElement(constructors);
  }

  private TestParameterInjectorUtils() {}
}
