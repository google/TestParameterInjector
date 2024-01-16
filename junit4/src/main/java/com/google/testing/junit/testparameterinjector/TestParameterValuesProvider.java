/*
 * Copyright 2024 Google Inc.
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

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Annotation;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Abstract class for custom providers of @TestParameter values.
 *
 * <p>This is a replacement for {@link TestParameter.TestParameterValuesProvider}, which will soon
 * be deprecated. The difference with the former interface is that this class provides a {@code
 * Context} instance when invoking {@link #provideValues}.
 */
public abstract class TestParameterValuesProvider
    implements TestParameter.TestParameterValuesProvider {

  protected abstract List<?> provideValues(Context context);

  @Override
  public final List<?> provideValues() {
    throw new UnsupportedOperationException(
        "The TestParameterInjector framework should never call this method, and instead call"
            + " #provideValues(Context)");
  }

  /**
   * Wraps the given value in an object that allows you to give the parameter value a different
   * name. The TestParameterInjector framework will recognize the returned {@link
   * TestParameterValue} instances and unwrap them at injection time.
   *
   * <p>Usage: {@code value(file.content).withName(file.name)}.
   */
  @Override
  public final TestParameterValue value(@Nullable Object wrappedValue) {
    // Overriding this method as final because it is not supposed to be overwritten
    return TestParameter.TestParameterValuesProvider.super.value(wrappedValue);
  }

  /**
   * An immutable value class that contains extra information about the context of the parameter for
   * which values are being provided.
   */
  @AutoValue
  public abstract static class Context {
    /**
     * A list of all other annotations on the field or parameter that was annotated
     * with @TestParameter.
     *
     * <p>For example, if the test code is as follows:
     *
     * <pre>{@code
     * @Test
     * public void myTest_success(
     *     @CustomAnnotation(123) @TestParameter(valuesProvider=MyProvider.class) Foo foo) {
     *   ...
     * }
     * }</pre>
     *
     * then this list will contain a single element: @CustomAnnotation(123).
     */
    public abstract ImmutableList<Annotation> otherAnnotations();

    /**
     * The class that contains the test that is currently being run.
     *
     * <p>Having this can be useful when sharing providers between tests that have the same base
     * class. In those cases, an abstract method can be called as follows:
     *
     * <pre>
     *   ((MyBaseClass) context.testClass().newInstance()).myAbstractMethod()
     * </pre>
     */
    public abstract Class<?> testClass();

    static Context create(ImmutableList<Annotation> otherAnnotations, Class<?> testClass) {
      return new AutoValue_TestParameterValuesProvider_Context(otherAnnotations, testClass);
    }

    @Override
    public final String toString() {
      return String.format(
          "Context(otherAnnotations=[%s],testClass=%s)",
          FluentIterable.from(otherAnnotations()).join(Joiner.on(',')),
          testClass().getSimpleName());
    }

    Context() {} // Prevent implementations outside of this package
  }
}
