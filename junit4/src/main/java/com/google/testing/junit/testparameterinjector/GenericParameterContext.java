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

import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import java.lang.annotation.Annotation;
import java.util.NoSuchElementException;

/** A value class that contains extra information about the context of a field or parameter. */
final class GenericParameterContext {

  private final ImmutableList<Annotation> annotationsOnParameter;
  private final Class<?> testClass;

  GenericParameterContext(ImmutableList<Annotation> annotationsOnParameter, Class<?> testClass) {
    this.annotationsOnParameter = annotationsOnParameter;
    this.testClass = testClass;
  }

  /**
   * Returns the only annotation with the given type on the field or parameter.
   *
   * @throws NoSuchElementException if this there is no annotation with the given type
   * @throws IllegalArgumentException if there are multiple annotations with the given type
   */
  @SuppressWarnings("unchecked") // Safe because of the filter operation
  <A extends Annotation> A getAnnotation(Class<A> annotationType) {
    return (A)
        getOnlyElement(
            FluentIterable.from(annotationsOnParameter)
                .filter(annotation -> annotation.annotationType().equals(annotationType))
                .toList());
  }

  // TODO: b/317524353 - Add support for repeated annotations

  /** The class that contains the test that is currently being run. */
  Class<?> testClass() {
    return testClass;
  }

  /** A list of all annotations on the field or parameter. */
  ImmutableList<Annotation> annotationsOnParameter() {
    return annotationsOnParameter;
  }

  @Override
  public String toString() {
    return String.format(
        "context(annotationsOnParameter=[%s],testClass=%s)",
        FluentIterable.from(
                ImmutableList.sortedCopyOf(
                    Ordering.natural().onResultOf(Annotation::toString), annotationsOnParameter))
            .transform(
                annotation -> String.format("@%s", annotation.annotationType().getSimpleName()))
            .join(Joiner.on(',')),
        testClass().getSimpleName());
  }
}
