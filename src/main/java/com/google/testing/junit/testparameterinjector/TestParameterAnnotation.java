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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Primitives;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;

/**
 * Annotation to define a test annotation used to have parameterized methods, in either a
 * parameterized or non parameterized test.
 *
 * <p>Parameterized tests enabled by defining a annotation (see {@link TestParameter} as an example)
 * for the type of the parameter, defining a member variable annotated with this annotation, and
 * specifying the parameter with the same annotation for each test, or for the whole class, for
 * example:
 *
 * <pre>{@code
 * @RunWith(ParameterizedTestRunner.class)
 * public class ColorTest {
 *     @Retention(RUNTIME)
 *     @Target({TYPE, METHOD, FIELD})
 *     @TestParameterAnnotation
 *     public @interface ColorParameter {
 *       Color[] value() default {};
 *     }
 *
 *     @ColorParameter({BLUE, WHITE, RED}) private Color color;
 *
 *     @Test
 *     public void test() {
 *       assertThat(paint(color)).isSuccessful();
 *     }
 * }
 * }</pre>
 *
 * <p>An alternative is to use a method parameter for injection:
 *
 * <pre>{@code
 * @RunWith(ParameterizedTestRunner.class)
 * public class ColorTest {
 *     @Retention(RUNTIME)
 *     @Target({TYPE, METHOD, FIELD})
 *     @TestParameterAnnotation
 *     public @interface ColorParameter {
 *       Color[] value() default {};
 *     }
 *
 *     @Test
 *     @ColorParameter({BLUE, WHITE, RED})
 *     public void test(Color color) {
 *       assertThat(paint(color)).isSuccessful();
 *     }
 * }
 * }</pre>
 *
 * <p>Yet another alternative is to use a method parameter for injection, but with the annotation
 * specified on the parameter itself, which helps when multiple arguments share the
 * same @TestParameterAnnotation annotation.
 *
 * <pre>{@code
 * @RunWith(ParameterizedTestRunner.class)
 * public class ColorTest {
 *     @Retention(RUNTIME)
 *     @Target({TYPE, METHOD, FIELD})
 *     @TestParameterAnnotation
 *     public @interface ColorParameter {
 *       Color[] value() default {};
 *     }
 *
 *     @Test
 *     public void test(@ColorParameter({BLUE, WHITE}) Color color1,
 *                      @ColorParameter({WHITE, RED}) Color color2) {
 *       assertThat(paint(color1. color2)).isSuccessful();
 *     }
 * }
 * }</pre>
 *
 * <p>Class constructors can also be annotated with @TestParameterAnnotation annotations, as shown
 * below:
 *
 * <pre>{@code
 * @RunWith(ParameterizedTestRunner.class)
 * public class ColorTest {
 *     @Retention(RUNTIME)
 *     @Target({TYPE, METHOD, FIELD})
 *     public @TestParameterAnnotation
 *     public @interface ColorParameter {
 *       Color[] value() default {};
 *     }
 *
 *     public ColorTest(@ColorParameter({BLUE, WHITE}) Color color) {
 *       ...
 *     }
 *
 *     @Test
 *     public void test() {...}
 * }
 * }</pre>
 *
 * <p>Each field that needs to be injected from a parameter requires its dedicated distinct
 * annotation.
 *
 * <p>If the same annotation is defined both on the class and method, the method parameter values
 * take precedence.
 *
 * <p>If the same annotation is defined both on the class and constructor, the constructor parameter
 * values take precedence.
 *
 * <p>Annotations cannot be duplicated between the constructor or constructor parameters and a
 * method or method parameter.
 *
 * <p>Since the parameter values must be specified in an annotation return value, they are
 * restricted to the annotation method return type set (primitive, Class, Enum, String, etc...). If
 * parameters have to be dynamically generated, the conventional Parameterized mechanism with {@code
 * Parameters} has to be used instead.
 */
@Retention(RUNTIME)
@Target({ANNOTATION_TYPE})
@interface TestParameterAnnotation {
  /**
   * Pattern of the {@link MessageFormat} format to derive the test's name from the parameters.
   *
   * @see {@code Parameters#name()}
   */
  String name() default "{0}";

  /** Specifies a validator for the parameter to determine whether test should be skipped. */
  Class<? extends TestParameterValidator> validator() default DefaultValidator.class;

  /**
   * Specifies a processor for the parameter to invoke arbitrary code before and after the test
   * statement's execution.
   */
  Class<? extends TestParameterProcessor> processor() default DefaultProcessor.class;

  /** Specifies a value provider for the parameter to provide the values to test. */
  Class<? extends TestParameterValueProvider> valueProvider() default DefaultValueProvider.class;

  /** Default {@link TestParameterValidator} implementation which skips no test. */
  class DefaultValidator implements TestParameterValidator {

    @Override
    public boolean shouldSkip(Context context) {
      return false;
    }
  }

  /** Default {@link TestParameterProcessor} implementation which does nothing. */
  class DefaultProcessor implements TestParameterProcessor {
    @Override
    public void before(Object testParameterValue) {}

    @Override
    public void after(Object testParameterValue) {}
  }

  /**
   * Default {@link TestParameterValueProvider} implementation that gets its values from the
   * annotation's `value` method.
   */
  class DefaultValueProvider implements TestParameterValueProvider {

    @Override
    public List<Object> provideValues(Annotation annotation, Optional<Class<?>> parameterClass) {
      Object parameters = getParametersAnnotationValues(annotation, annotation.annotationType());
      checkState(
          parameters.getClass().isArray(),
          "The return value of the value method should be an array");

      int parameterCount = Array.getLength(parameters);
      ImmutableList.Builder<Object> resultBuilder = ImmutableList.builder();
      for (int i = 0; i < parameterCount; i++) {
        Object value = Array.get(parameters, i);
        if (parameterClass.isPresent()) {
          verify(
              Primitives.wrap(parameterClass.get()).isInstance(value),
              "Found %s annotation next to a parameter of type %s which doesn't match"
                  + " (annotation = %s)",
              annotation.annotationType().getSimpleName(),
              parameterClass.get().getSimpleName(),
              annotation);
        }
        resultBuilder.add(value);
      }
      return resultBuilder.build();
    }

    @Override
    public Class<?> getValueType(
        Class<? extends Annotation> annotationType, Optional<Class<?>> parameterClass) {
      try {
        Method valueMethod = annotationType.getMethod("value");
        return valueMethod.getReturnType().getComponentType();
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(
            "The @TestParameterAnnotation annotation should have a single value() method.", e);
      }
    }

    /**
     * Returns the parameters of the test parameter, by calling the {@code value} method on the
     * annotation.
     */
    private static Object getParametersAnnotationValues(
        Annotation annotation, Class<? extends Annotation> annotationType) {
      Method valueMethod;
      try {
        valueMethod = annotationType.getMethod("value");
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(
            "The @TestParameterAnnotation annotation should have a single value() method.", e);
      }
      Object parameters;
      try {
        parameters = valueMethod.invoke(annotation);
      } catch (InvocationTargetException e) {
        if (e.getCause() instanceof IllegalAccessError) {
          // There seems to be a bug or at least something weird with the JVM that causes
          // IllegalAccessError to be thrown because the return value is not visible when it is a
          // non-public nested type. See
          // http://mail.openjdk.java.net/pipermail/core-libs-dev/2014-January/024180.html for more
          // info.
          throw new RuntimeException(
              String.format(
                  "Could not access %s.value(). This is probably because %s is not visible to the"
                      + " annotation proxy. To fix this, make %s public.",
                  annotationType.getSimpleName(),
                  valueMethod.getReturnType().getSimpleName(),
                  valueMethod.getReturnType().getSimpleName()));
          // Note: Not chaining the exception to reduce the clutter for the reader
        } else {
          throw new RuntimeException("Unexpected exception while invoking " + valueMethod, e);
        }
      } catch (Exception e) {
        throw new RuntimeException("Unexpected exception while invoking " + valueMethod, e);
      }
      return parameters;
    }
  }
}
