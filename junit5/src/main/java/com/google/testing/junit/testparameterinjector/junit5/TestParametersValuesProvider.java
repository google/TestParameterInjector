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

package com.google.testing.junit.testparameterinjector.junit5;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.junit5.TestParameters.TestParametersValues;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Abstract class for custom providers of @TestParameters values.
 *
 * <p>This is a replacement for {@link TestParameters.TestParametersValuesProvider}, which is
 * deprecated. The difference with the former interface is that this class provides a {@code
 * Context} instance when invoking {@link #provideValues}.
 */
public abstract class TestParametersValuesProvider
    implements TestParameters.TestParametersValuesProvider {

  protected abstract List<TestParametersValues> provideValues(Context context) throws Exception;

  /**
   * @deprecated This method should never be called as it will simply throw an {@link
   *     UnsupportedOperationException}.
   */
  @Override
  @Deprecated
  public final List<TestParametersValues> provideValues() {
    throw new UnsupportedOperationException(
        "The TestParameterInjector framework should never call this method, and instead call"
            + " #provideValues(Context)");
  }

  /**
   * An immutable value class that contains extra information about the context of the parameter for
   * which values are being provided.
   */
  public static final class Context {

    private final GenericParameterContext delegate;

    Context(GenericParameterContext delegate) {
      this.delegate = delegate;
    }

    /**
     * Returns the only annotation with the given type on the method or constructor that was
     * annotated with @TestParameters.
     *
     * <p>For example, if the test code is as follows:
     *
     * <pre>
     *   {@literal @}Test
     *   {@literal @}TestParameters("{updateRequest: {country_code: BE}, expectedResultType: SUCCESS}")
     *   {@literal @}TestParameters("{updateRequest: {country_code: XYZ}, expectedResultType: FAILURE}")
     *   {@literal @}CustomAnnotation(123)
     *   public void update(UpdateRequest updateRequest, ResultType expectedResultType) {
     *     ...
     *   }
     * </pre>
     *
     * then {@code context.getOtherAnnotation(CustomAnnotation.class).value()} will equal 123.
     *
     * @throws NoSuchElementException if this there is no annotation with the given type
     * @throws IllegalArgumentException if there are multiple annotations with the given type
     * @throws IllegalArgumentException if the argument it TestParameters.class because it is
     *     already handled by the TestParameterInjector framework.
     */
    public <A extends Annotation> A getOtherAnnotation(Class<A> annotationType) {
      checkArgument(
          !TestParameters.class.equals(annotationType),
          "Getting the @TestParameters annotating the method or constructor is not allowed because"
              + " it is already handled by the TestParameterInjector framework.");
      return delegate.getAnnotation(annotationType);
    }

    /**
     * Returns all annotations with the given type on the method or constructor that was annotated
     * with @TestParameter.
     *
     * <pre>
     *   {@literal @}Test
     *   {@literal @}TestParameters("{updateRequest: {country_code: BE}, expectedResultType: SUCCESS}")
     *   {@literal @}TestParameters("{updateRequest: {country_code: XYZ}, expectedResultType: FAILURE}")
     *   {@literal @}CustomAnnotation(123)
     *   {@literal @}CustomAnnotation(456)
     *   public void update(UpdateRequest updateRequest, ResultType expectedResultType) {
     *     ...
     *   }
     * </pre>
     *
     * then {@code context.getOtherAnnotations(CustomAnnotation.class)} will return the annotations
     * with 123 and 456.
     *
     * <p>Returns an empty list if this there is no annotation with the given type.
     *
     * @throws IllegalArgumentException if the argument it TestParameters.class because it is
     *     already handled by the TestParameterInjector framework.
     */
    public <A extends Annotation> ImmutableList<A> getOtherAnnotations(Class<A> annotationType) {
      checkArgument(
          !TestParameters.class.equals(annotationType),
          "Getting the @TestParameters annotating the method or constructor is not allowed because"
              + " it is already handled by the TestParameterInjector framework.");
      return delegate.getAnnotations(annotationType);
    }

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
    public Class<?> testClass() {
      return delegate.testClass();
    }

    /** A list of all annotations on the method or constructor. */
    @VisibleForTesting
    ImmutableList<Annotation> annotationsOnParameter() {
      return delegate.annotationsOnParameter();
    }

    @Override
    public String toString() {
      return delegate.toString();
    }
  }
}
