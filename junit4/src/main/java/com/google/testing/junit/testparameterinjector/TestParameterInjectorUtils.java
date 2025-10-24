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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameterInjectorUtils.JavaCompatibilityParameter;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import javax.annotation.Nullable;

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
        constructors.size() == 1,
        "%s: Expected exactly one constructor, but got %s",
        testClass.getSimpleName(),
        constructors);
    return getOnlyElement(constructors);
  }

  static boolean isKotlinClass(Class<?> clazz) {
    return FluentIterable.from(clazz.getDeclaredAnnotations())
        .anyMatch(annotation -> annotation.annotationType().getName().equals("kotlin.Metadata"));
  }

  static ImmutableList<Annotation> filterSingleAndRepeatedAnnotations(
      Annotation[] allAnnotations, Class<? extends Annotation> annotationType) {
    ImmutableList<Annotation> candidates =
        FluentIterable.from(allAnnotations)
            .filter(annotation -> annotation.annotationType().equals(annotationType))
            .toList();
    if (candidates.isEmpty() && getContainerType(annotationType).isPresent()) {
      ImmutableList<Annotation> containerAnnotations =
          filterSingleAndRepeatedAnnotations(
              allAnnotations, getContainerType(annotationType).get());
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

    abstract Type[] getGenericParameterTypes();

    abstract ImmutableList<JavaCompatibilityParameter> getParameters();

    /**
     * Returns the same as {@link #getParameters}, with a fallback for old Android SDKs and with the
     * given names from Kotlin reflection.
     */
    final ImmutableList<JavaCompatibilityParameter> getParametersWithFallback(
        Optional<ImmutableList<String>> maybeParameterNamesFromKotlin) {
      if (maybeParameterNamesFromKotlin.isPresent()) {
        ImmutableList<JavaCompatibilityParameter> parameters = getParametersWithFallback();
        ImmutableList<String> parameterNamesFromKotlin = maybeParameterNamesFromKotlin.get();
        checkArgument(
            parameters.size() == parameterNamesFromKotlin.size(),
            "Expected the same number of parameters as parameter names from Kotlin reflection");

        ImmutableList.Builder<JavaCompatibilityParameter> resultBuilder = ImmutableList.builder();
        for (int parameterIndex = 0; parameterIndex < parameters.size(); parameterIndex++) {
          JavaCompatibilityParameter parameter = parameters.get(parameterIndex);
          String parameterNameFromKotlin = parameterNamesFromKotlin.get(parameterIndex);
          if (parameter.maybeGetName().isPresent()) {
            checkState(
                parameterNameFromKotlin.equals(parameter.maybeGetName().get()),
                "%s: Parameter %s has different names in Kotlin (%s) and Java (%s)",
                getHumanReadableNameSummary(),
                parameter,
                parameterNameFromKotlin,
                parameter.maybeGetName().get());
            resultBuilder.add(parameter);
          } else {
            resultBuilder.add(parameter.withName(parameterNameFromKotlin));
          }
        }
        return resultBuilder.build();
      } else {
        return getParametersWithFallback();
      }
    }

    /** Returns the same as {@link #getParameters}, with a fallback for old Android SDKs. */
    final ImmutableList<JavaCompatibilityParameter> getParametersWithFallback() {
      try {
        return getParameters();
      } catch (NoSuchMethodError ignored) {
        // Parameter is not available on old Android SDKs, and isn't desugared
        return JavaCompatibilityParameter.createListFromExecutable(this);
      }
    }

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
        Type[] getGenericParameterTypes() {
          return constructor.getGenericParameterTypes();
        }

        @Override
        ImmutableList<JavaCompatibilityParameter> getParameters() {
          return FluentIterable.from(constructor.getParameters())
              .transform(JavaCompatibilityParameter::create)
              .toList();
        }
      };
    }

    static JavaCompatibilityExecutable create(Method method) {
      return new JavaCompatibilityExecutable() {
        @Override
        String getHumanReadableNameSummary() {
          return method.getDeclaringClass().getSimpleName() + "." + method.getName() + "()";
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

        @Override
        Type[] getGenericParameterTypes() {
          return method.getGenericParameterTypes();
        }

        @Override
        ImmutableList<JavaCompatibilityParameter> getParameters() {
          return FluentIterable.from(method.getParameters())
              .transform(JavaCompatibilityParameter::create)
              .toList();
        }
      };
    }
  }

  /**
   * Represents a constructor or method parameter.
   *
   * <p>This is a replacement for java.lang.reflect.Parameter that is not available on old Android
   * SDKs, and isn't desugared.
   */
  abstract static class JavaCompatibilityParameter {

    abstract Optional<String> maybeGetName();

    abstract Class<?> getType();

    abstract Annotation[] getAnnotations();

    abstract Type getParameterizedType();

    abstract <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationClass);

    @SuppressWarnings("unchecked") // Safe because of the filter operation
    @Nullable
    <A extends Annotation> A getAnnotation(Class<A> annotationType) {
      ImmutableList<Annotation> candidates =
          FluentIterable.from(getAnnotations())
              .filter(annotation -> annotation.annotationType().equals(annotationType))
              .toList();
      if (candidates.size() == 0) {
        return null;
      } else {
        return (A) getOnlyElement(candidates);
      }
    }

    @Override
    public final String toString() {
      if (maybeGetName().isPresent()) {
        return String.format("Parameter(%s %s)", getType().getSimpleName(), maybeGetName().get());
      } else {
        return String.format("Parameter(%s)", getType().getSimpleName());
      }
    }

    JavaCompatibilityParameter withName(String name) {
      return create(
          Optional.of(name),
          getType(),
          getAnnotations(),
          this::getParameterizedType,
          this::getAnnotationsByType);
    }

    @SuppressWarnings("AndroidJdkLibsChecker")
    static JavaCompatibilityParameter create(Parameter parameter) {
      return create(
          parameter.isNamePresent() ? Optional.of(parameter.getName()) : Optional.absent(),
          parameter.getType(),
          parameter.getAnnotations(),
          parameter::getParameterizedType,
          parameter::getAnnotationsByType);
    }

    static JavaCompatibilityParameter create(
        Optional<String> maybeName,
        Class<?> type,
        Annotation[] annotations,
        // Note: This is a Supplier because not all code paths need this value. Since it is a
        // relatively advanced Java feature, we don't want to risk a client not being able to use
        // TestParameterInjector at all because of this dependency.
        Supplier<Type> getParameterizedType,
        Function<Class<? extends Annotation>, Annotation[]> getAnnotationsByType) {
      return new JavaCompatibilityParameter() {
        @Override
        Optional<String> maybeGetName() {
          return maybeName;
        }

        @Override
        Class<?> getType() {
          return type;
        }

        @Override
        Annotation[] getAnnotations() {
          return annotations;
        }

        @Override
        Type getParameterizedType() {
          return getParameterizedType.get();
        }

        @SuppressWarnings("unchecked") // Safe because all (package private) callers are known
        @Override
        <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationClass) {
          return (A[]) getAnnotationsByType.apply(annotationClass);
        }
      };
    }

    static ImmutableList<JavaCompatibilityParameter> createListFromExecutable(
        JavaCompatibilityExecutable executable) {
      Class<?>[] parameterTypes = executable.getParameterTypes();
      Annotation[][] annotations = executable.getParameterAnnotations();
      checkArgument(parameterTypes.length == annotations.length);

      ImmutableList.Builder<JavaCompatibilityParameter> resultBuilder = ImmutableList.builder();
      for (int parameterIndex = 0; parameterIndex < annotations.length; parameterIndex++) {
        Annotation[] parameterAnnotations = executable.getParameterAnnotations()[parameterIndex];
        int parameterIndexCopy = parameterIndex;
        resultBuilder.add(
            create(
                /* maybeName= */ Optional.absent(),
                /* type= */ parameterTypes[parameterIndex],
                /* annotations= */ annotations[parameterIndex],
                /* getParameterizedType= */ () ->
                    executable.getGenericParameterTypes()[parameterIndexCopy],
                /* getAnnotationsByType= */ annotationClass ->
                    filterSingleAndRepeatedAnnotations(parameterAnnotations, annotationClass)
                        .toArray(new Annotation[0])));
      }
      return resultBuilder.build();
    }
  }
}
