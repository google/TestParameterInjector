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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

/**
 * Interface to change the list of methods used in a test.
 *
 * <p>Note: Implementations of this interface are expected to be immutable, i.e. they no longer
 * change after construction.
 */
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
   * If this processor can handle the given test, returns the parameters with which that method
   * should be invoked.
   */
  Optional<List<Object>> maybeGetTestMethodParameters(TestInfo testInfo);

  /** Optionally validates the given constructor. */
  ValidationResult validateConstructor(Constructor<?> constructor);

  /** Optionally validates the given method. */
  ValidationResult validateTestMethod(Method testMethod);

  /**
   * Value class that captures the result of a validating a single constructor or test method.
   *
   * <p>If the validation is not validated by any processor, it will be validated using the default
   * validator. If a processor validates a constructor/test method, the remaining processors will
   * *not* be called.
   */
  @AutoValue
  abstract class ValidationResult {

    /** Returns true if the properties of the given constructor/test method were validated. */
    public abstract boolean wasValidated();

    /** Returns the validation errors, if any. */
    public abstract ImmutableList<Throwable> validationErrors();

    static ValidationResult notValidated() {
      return of(/* wasValidated= */ false, /* validationErrors= */ ImmutableList.of());
    }

    static ValidationResult validated(Collection<Throwable> errors) {
      return of(/* wasValidated= */ true, /* validationErrors= */ errors);
    }

    static ValidationResult validated(Throwable error) {
      return of(/* wasValidated= */ true, /* validationErrors= */ ImmutableList.of(error));
    }

    static ValidationResult valid() {
      return of(/* wasValidated= */ true, /* validationErrors= */ ImmutableList.of());
    }

    private static ValidationResult of(
        boolean wasValidated, Collection<Throwable> validationErrors) {
      checkArgument(wasValidated || validationErrors.isEmpty());
      return new AutoValue_TestMethodProcessor_ValidationResult(
          wasValidated, ImmutableList.copyOf(validationErrors));
    }
  }
}
