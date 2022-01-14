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
import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Validator interface which allows {@link TestParameterAnnotation} annotations to validate the set
 * of annotation values for a given test instance, and to selectively skip the test.
 */
interface TestParameterValidator {

  /**
   * This interface allows to access information on the current testwhen implementing {@link
   * TestParameterValidator}.
   */
  interface Context {

    /** Returns whether the current test has the {@link TestParameterAnnotation} value(s). */
    boolean has(Class<? extends Annotation> testParameter, Object value);

    /**
     * Returns whether the current test has the two {@link TestParameterAnnotation} values, granted
     * that the value is an enum, and each enum corresponds to a unique annotation.
     */
    <T extends Enum<T>, U extends Enum<U>> boolean has(T value1, U value2);

    /**
     * Returns all the current test value for a given {@link TestParameterAnnotation} annotated
     * annotation.
     */
    Optional<Object> getValue(Class<? extends Annotation> testParameter);

    /**
     * Returns all the values specified for a given {@link TestParameterAnnotation} annotated
     * annotation in the test.
     *
     * <p>For example, if the test annotates '@Foo(a,b,c)', getSpecifiedValues(Foo.class) will
     * return [a,b,c].
     */
    List<Object> getSpecifiedValues(Class<? extends Annotation> testParameter);
  }

  /**
   * Returns whether the test should be skipped based on the annotations' values.
   *
   * <p>The {@code testParameterValues} list contains all {@link TestParameterAnnotation}
   * annotations, including those specified at the class, field, method, method parameter,
   * constructor, and constructor parameter for a given test.
   *
   * <p>This method is not invoked in the context of a running test statement.
   */
  boolean shouldSkip(Context context);
}
