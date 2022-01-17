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

package com.google.testing.junit.testparameterinjector.junit5;

import com.google.common.base.Optional;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Interface to change the list of methods used in a test.
 *
 * <p>Note: Implementations of this interface are expected to be immutable, i.e. they no longer
 * change after construction.
 */
interface TestMethodProcessor {

  /** Allows to transform the test information (name and annotations). */
  List<TestInfo> calculateTestInfos(TestInfo originalTest);

  /**
   * If this processor can handle the given constructor, returns the parameters with which it should
   * be invoked.
   *
   * <p>This method is never called for a parameterless constructor.
   */
  Optional<List<Object>> maybeGetConstructorParameters(
      Constructor<?> constructor, TestInfo testInfo);

  /**
   * If this processor can handle the given test, returns the parameters with which {@code
   * testInfo.getMethod()} should be invoked.
   *
   * <p>This method is never called for a parameterless {@code testInfo.getMethod()}.
   */
  Optional<List<Object>> maybeGetTestMethodParameters(TestInfo testInfo);

  /**
   * Optionally process the test instance right after construction to ready it for the given test
   * instance.
   */
  void postProcessTestInstance(Object testInstance, TestInfo testInfo);

  /** Optionally validates the given constructor. */
  ExecutableValidationResult validateConstructor(Constructor<?> constructor);

  /**
   * Optionally validates the given method.
   *
   * <p>Note that the given method is not necessarily declared in the given class because test
   * methods can be inherited.
   */
  ExecutableValidationResult validateTestMethod(Method testMethod, Class<?> testClass);
}
