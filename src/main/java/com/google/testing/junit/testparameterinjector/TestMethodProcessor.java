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

import com.google.common.base.Optional;
import java.util.List;
import org.junit.runner.Description;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

/**
 * Interface to change the list of methods used in a test.
 *
 * <p>Note: Implementations of this interface are expected to be immutable, i.e. they no longer
 * change after construction.
 */
/* copybara:strip_begin(advanced usage) */ public /* copybara:strip_end */
interface TestMethodProcessor {

  /** Allows to transform the test information (name and annotations). */
  List<TestInfo> processTest(Class<?> testClass, TestInfo originalTest);

  /**
   * Allows to change the code executed during the test.
   *
   * @param finalTestDescription the final description calculated taking into account this and all
   *     other test processors
   */
  Statement processStatement(Statement originalStatement, Description finalTestDescription);

  /**
   * This method allows to transform the test object used for {@link #processStatement(Statement,
   * Description)}.
   *
   * @param test the value returned by the previous processor, or {@link Optional#absent()} if this
   *     processor is the first.
   * @return {@link Optional#absent()} if the default test instance will be used from instantiating
   *     the test class with the default constructor.
   *     <p>The default implementation should return {@code test}.
   */
  Optional<Object> createTest(TestClass testClass, FrameworkMethod method, Optional<Object> test);

  /**
   * This method allows to transform the statement object used for {@link
   * #processStatement(Statement, Description)}.
   *
   * @param statement the value returned by the previous processor, or {@link Optional#absent()} if
   *     this processor is the first.
   * @return {@link Optional#absent()} if the default statement will be used from invoking the test
   *     method with no parameters.
   *     <p>The default implementation should return {@code statement}.
   */
  Optional<Statement> createStatement(
      TestClass testClass,
      FrameworkMethod method,
      Object testObject,
      Optional<Statement> statement);

  /**
   * Optionally validates the {@code testClass} constructor, and returns whether the validation
   * should continue or stop.
   *
   * @param errorsReturned A mutable list that any validation error should be added to.
   */
  ValidationResult validateConstructor(TestClass testClass, List<Throwable> errorsReturned);

  /**
   * Optionally validates the {@code testClass} methods, and returns whether the validation should
   * continue or stop.
   *
   * @param errorsReturned A mutable list that any validation error should be added to.
   */
  ValidationResult validateTestMethod(
      TestClass testClass, FrameworkMethod testMethod, List<Throwable> errorsReturned);

  /**
   * Whether the constructor or method validation has been handled or not.
   *
   * <p>If the validation is not handled by a processor, it will be handled using the default {@link
   * BlockJUnit4ClassRunner} validator.
   */
  enum ValidationResult {
    NOT_HANDLED,
    HANDLED,
  }
}
