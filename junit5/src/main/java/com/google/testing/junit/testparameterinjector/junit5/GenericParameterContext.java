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

import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.NoSuchElementException;

/** A value class that contains extra information about the context of a field or parameter. */
final class GenericParameterContext {

  private final ImmutableList<Annotation> annotationsOnParameter;

  /** Same contract as #getAnnotations */
  private final Function<Class<? extends Annotation>, ImmutableList<? extends Annotation>>
      getAnnotationsFunction;

  private final Class<?> testClass;

  private GenericParameterContext(
      ImmutableList<Annotation> annotationsOnParameter,
      Function<Class<? extends Annotation>, ImmutableList<? extends Annotation>>
          getAnnotationsFunction,
      Class<?> testClass) {
    this.annotationsOnParameter = annotationsOnParameter;
    this.getAnnotationsFunction = getAnnotationsFunction;
    this.testClass = testClass;
  }

  // Field.getAnnotationsByType() is not available on old Android SDKs. There is a fallback in that
  // case in this method.
  @SuppressWarnings("AndroidJdkLibsChecker")
  static GenericParameterContext create(Field field, Class<?> testClass) {
    return new GenericParameterContext(
        ImmutableList.copyOf(field.getAnnotations()),
        /* getAnnotationsFunction= */ annotationType -> {
          try {
            return ImmutableList.copyOf(field.getAnnotationsByType(annotationType));
          } catch (NoSuchMethodError ignored) {
            return getAnnotationsFallback(
                ImmutableList.copyOf(field.getAnnotations()), annotationType);
          }
        },
        testClass);
  }

  // Parameter is not available on old Android SDKs, and isn't desugared. That's why this method
  // should only be called with a fallback.
  @SuppressWarnings("AndroidJdkLibsChecker")
  static GenericParameterContext create(Parameter parameter, Class<?> testClass) {
    return new GenericParameterContext(
        ImmutableList.copyOf(parameter.getAnnotations()),
        /* getAnnotationsFunction= */ annotationType ->
            ImmutableList.copyOf(parameter.getAnnotationsByType(annotationType)),
        testClass);
  }

  static GenericParameterContext createWithRepeatableAnnotationsFallback(
      Annotation[] annotationsOnParameter, Class<?> testClass) {
    return new GenericParameterContext(
        ImmutableList.copyOf(annotationsOnParameter),
        /* getAnnotationsFunction= */ annotationType ->
            getAnnotationsFallback(ImmutableList.copyOf(annotationsOnParameter), annotationType),
        testClass);
  }

  static GenericParameterContext createWithoutParameterAnnotations(Class<?> testClass) {
    return new GenericParameterContext(
        /* annotationsOnParameter= */ ImmutableList.of(),
        /* getAnnotationsFunction= */ annotationType ->
            getAnnotationsFallback(ImmutableList.of(), annotationType),
        testClass);
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

  /**
   * Returns the annotations with the given type on the field or parameter.
   *
   * <p>Returns an empty list if this there is no annotation with the given type.
   */
  @SuppressWarnings("unchecked") // Safe because of the getAnnotationsFunction contract
  <A extends Annotation> ImmutableList<A> getAnnotations(Class<A> annotationType) {
    return (ImmutableList<A>) getAnnotationsFunction.apply(annotationType);
  }

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

  private static ImmutableList<Annotation> getAnnotationsFallback(
      ImmutableList<Annotation> annotationsOnParameter,
      Class<? extends Annotation> annotationType) {
    ImmutableList<Annotation> candidates =
        FluentIterable.from(annotationsOnParameter)
            .filter(annotation -> annotation.annotationType().equals(annotationType))
            .toList();
    if (candidates.isEmpty() && getContainerType(annotationType).isPresent()) {
      ImmutableList<Annotation> containerAnnotations =
          getAnnotationsFallback(annotationsOnParameter, getContainerType(annotationType).get());
      if (containerAnnotations.size() == 1) {
        Annotation containerAnnotation = getOnlyElement(containerAnnotations);
        try {
          Method annotationValueMethod =
              containerAnnotation.annotationType().getDeclaredMethod("value");
          annotationValueMethod.setAccessible(true);
          return ImmutableList.copyOf(
              (Annotation[])
                  Proxy.getInvocationHandler(containerAnnotation)
                      .invoke(containerAnnotation, annotationValueMethod, null));
        } catch (Throwable e) {
          throw new RuntimeException(e);
        }
      }
      return ImmutableList.of();
    } else {
      return candidates;
    }
  }

  private static Optional<Class<? extends Annotation>> getContainerType(
      Class<? extends Annotation> annotationType) {
    try {
      Repeatable repeatable = annotationType.getAnnotation(Repeatable.class);
      if (repeatable == null) {
        return Optional.absent();
      } else {
        return Optional.of(repeatable.value());
      }
    } catch (NoClassDefFoundError ignored) {
      // If @Repeatable does not exist, then there is no container type by definition
      return Optional.absent();
    }
  }
}
