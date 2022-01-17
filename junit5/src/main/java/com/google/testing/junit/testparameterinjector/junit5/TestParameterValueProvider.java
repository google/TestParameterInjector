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

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;

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
  List<Object> provideValues(Annotation annotation, Optional<Class<?>> parameterClass);

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
