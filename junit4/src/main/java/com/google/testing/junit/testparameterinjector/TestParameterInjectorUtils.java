/*
 * Copyright 2023 Google Inc.
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/** Shared utility methods. */
class TestParameterInjectorUtils {

  static Constructor<?> getOnlyConstructor(Class<?> testClass) {
    return getOnlyConstructorInternal(testClass, /* allowNonPublicConstructor= */ true);
  }

  static void validateOnlyOneConstructor(Class<?> testClass, boolean allowNonPublicConstructor) {
    Constructor<?> unused = getOnlyConstructorInternal(testClass, allowNonPublicConstructor);
  }

  /**
   * Return the only public constructor of the given test class. If there is none, return the only
   * constructor.
   *
   * <p>Normally, there should be exactly one constructor (public or other), but some frameworks
   * introduce an extra non-public constructor (see
   * https://github.com/google/TestParameterInjector/issues/40).
   */
  private static Constructor<?> getOnlyConstructorInternal(
      Class<?> testClass, boolean allowNonPublicConstructor) {
    ImmutableList<Constructor<?>> constructors = ImmutableList.copyOf(testClass.getConstructors());

    if (allowNonPublicConstructor && constructors.isEmpty()) {
      // There are no public constructors. This is likely a JUnit5 test, so we should take the only
      // non-public constructor instead.
      constructors = ImmutableList.copyOf(testClass.getDeclaredConstructors());
    }

    constructors =
        FluentIterable.from(constructors)
            .filter(
                c ->
                    // Filter out synthetic constructors introduced by the compiler. This is a
                    // fix to cope with an extra Kotlin-introduced constructor when it has default
                    // parameter values (with a bit mask and a DefaultConstructorMarker).
                    !c.isSynthetic())
            .toList();

    checkState(
        constructors.size() == 1, "Expected exactly one constructor, but got %s", constructors);
    return getOnlyElement(constructors);
  }

  private TestParameterInjectorUtils() {}

  /**
   * Represents a Java method or constructor.
   *
   * <p>This is a replacement for java.lang.reflect.Executable that is not available on old Android
   * SDKs, and isn't desugared.
   */
  abstract static class JavaCompatibilityExecutable {

    private JavaCompatibilityExecutable() {}

    /**
     * A description of this executable that omits likely irrelevant details with the goal of making
     * it a concise representation for human consumption.
     */
    abstract String getHumanReadableNameSummary();

    /**
     * Returns the java.lang.reflect.Constructor or java.lang.reflect.Method that is the equivalent
     * of this instance.
     */
    abstract Object getJavaReflectVersion();

    abstract String getName();

    abstract Class<?> getDeclaringClass();

    abstract boolean isAnnotationPresent(Class<? extends Annotation> annotationClass);

    abstract <A extends Annotation> A getAnnotation(Class<A> annotationClass);

    abstract <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationClass);

    abstract Annotation[] getAnnotations();

    abstract Annotation[][] getParameterAnnotations();

    abstract Class<?>[] getParameterTypes();

    @SuppressWarnings("AndroidJdkLibsChecker")
    abstract Parameter[] getParameters();

    final boolean isMethod() {
      return getJavaReflectVersion() instanceof Method;
    }

    @Override
    public final String toString() {
      return getHumanReadableNameSummary();
    }

    static JavaCompatibilityExecutable create(Constructor<?> constructor) {
      return new JavaCompatibilityExecutable() {
        @Override
        String getHumanReadableNameSummary() {
          return constructor.getDeclaringClass().getSimpleName() + ".constructor";
        }

        @Override
        Object getJavaReflectVersion() {
          return constructor;
        }

        @Override
        String getName() {
          return constructor.getName();
        }

        @Override
        Class<?> getDeclaringClass() {
          return constructor.getDeclaringClass();
        }

        @Override
        boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
          return constructor.isAnnotationPresent(annotationClass);
        }

        @Override
        <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
          return constructor.getAnnotation(annotationClass);
        }

        @Override
        <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationClass) {
          return constructor.getAnnotationsByType(annotationClass);
        }

        @Override
        Annotation[] getAnnotations() {
          return constructor.getAnnotations();
        }

        @Override
        Annotation[][] getParameterAnnotations() {
          return constructor.getParameterAnnotations();
        }

        @Override
        Class<?>[] getParameterTypes() {
          return constructor.getParameterTypes();
        }

        @Override
        @SuppressWarnings("AndroidJdkLibsChecker")
        Parameter[] getParameters() {
          return constructor.getParameters();
        }
      };
    }

    static JavaCompatibilityExecutable create(Method method) {
      return new JavaCompatibilityExecutable() {
        @Override
        String getHumanReadableNameSummary() {
          return method.getDeclaringClass().getSimpleName() + "." + method.getName();
        }

        @Override
        Object getJavaReflectVersion() {
          return method;
        }

        @Override
        String getName() {
          return method.getName();
        }

        @Override
        Class<?> getDeclaringClass() {
          return method.getDeclaringClass();
        }

        @Override
        boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
          return method.isAnnotationPresent(annotationClass);
        }

        @Override
        <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
          return method.getAnnotation(annotationClass);
        }

        @Override
        <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationClass) {
          return method.getAnnotationsByType(annotationClass);
        }

        @Override
        Annotation[] getAnnotations() {
          return method.getAnnotations();
        }

        @Override
        Annotation[][] getParameterAnnotations() {
          return method.getParameterAnnotations();
        }

        @Override
        Class<?>[] getParameterTypes() {
          return method.getParameterTypes();
        }

        @SuppressWarnings("AndroidJdkLibsChecker")
        @Override
        Parameter[] getParameters() {
          return method.getParameters();
        }
      };
    }
  }
}
