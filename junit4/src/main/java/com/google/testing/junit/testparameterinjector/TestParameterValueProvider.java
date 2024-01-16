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
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Interface which allows {@link TestParameterAnnotation} annotations to provide the values to test
 * in a dynamic way.
 */
interface TestParameterValueProvider {

  /**
   * Returns the parameter values for which the test should run.
   *
   * @param annotation The annotation instance that was encountered in the test class. The
   *     definition of this annotation is itself annotated with the {@link TestParameterAnnotation}
   *     annotation.
   * @param parameterClass The class of the parameter or field that is being annotated. In case the
   *     annotation is annotating a method, constructor or class, {@code parameterClass} is an empty
   *     optional.
   */
  default List<Object> provideValues(Annotation annotation, Optional<Class<?>> parameterClass) {
    throw new UnsupportedOperationException(
        "If this is called by TestParameterInjector, it means that neither of the"
            + " provideValues()-type methods have been implemented");
  }

  /**
   * Extension of {@link #provideValues(Annotation, Optional<Class<?>>)} with extra context.
   *
   * @param annotation The annotation instance that was encountered in the test class. The
   *     definition of this annotation is itself annotated with the {@link TestParameterAnnotation}
   *     annotation.
   * @param otherAnnotations A list of all other annotations on the field or parameter that was
   *     annotated with {@code annotation}.
   *     <p>For example, if the test code is as follows:
   *     <pre>
   *       @Test
   *       public void myTest_success(
   *           @CustomAnnotation(123) @TestParameter(valuesProvider=MyProvider.class) Foo foo) {
   *         ...
   *       }
   *     </pre>
   *     then this list will contain a single element: @CustomAnnotation(123).
   *     <p>In case the annotation is annotating a method, constructor or class, {@code
   *     parameterClass} is an empty list.
   * @param parameterClass The class of the parameter or field that is being annotated. In case the
   *     annotation is annotating a method, constructor or class, {@code parameterClass} is an empty
   *     optional.
   * @param testClass The class that contains the test that is currently being run.
   *     <p>Having this can be useful when sharing providers between tests that have the same base
   *     class. In those cases, an abstract method can be called as follows:
   *     <pre>
   *       ((MyBaseClass) context.testClass().newInstance()).myAbstractMethod()
   *     </pre>
   *
   * @deprecated Don't use this method outside of the testparameterinjector codebase, as it is prone
   *     to being changed.
   */
  @Deprecated
  default List<Object> provideValues(
      Annotation annotation,
      ImmutableList<Annotation> otherAnnotations,
      Optional<Class<?>> parameterClass,
      Class<?> testClass) {
    return provideValues(annotation, parameterClass);
  }

  /**
   * Returns the class of the list elements returned by {@link #provideValues(Annotation,
   * Optional)}.
   *
   * @param annotationType The type of the annotation that was encountered in the test class. The
   *     definition of this annotation is itself annotated with the {@link TestParameterAnnotation}
   *     annotation.
   * @param parameterClass The class of the parameter or field that is being annotated. In case the
   *     annotation is annotating a method, constructor or class, {@code parameterClass} is an empty
   *     optional.
   */
  Class<?> getValueType(
      Class<? extends Annotation> annotationType, Optional<Class<?>> parameterClass);
}
