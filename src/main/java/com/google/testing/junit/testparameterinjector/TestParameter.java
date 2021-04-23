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
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Primitives;
import com.google.protobuf.MessageLite;
import com.google.testing.junit.testparameterinjector.TestParameter.InternalImplementationOfThisParameter;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Test parameter annotation that defines the values that a single parameter can have.
 *
 * <p>For enums and booleans, the values can be automatically derived as all possible values:
 *
 * <pre>
 * {@literal @}Test
 * public void test1(@TestParameter MyEnum myEnum, @TestParameter boolean myBoolean) {
 *   // ... will run for [(A,false), (A,true), (B,false), (B,true), (C,false), (C,true)]
 * }
 *
 * enum MyEnum { A, B, C }
 * </pre>
 *
 * <p>The values can be explicitly defined as a parsed string:
 *
 * <pre>
 * public void test1(
 *     {@literal @}TestParameter({"{name: Hermione, age: 18}", "{name: Dumbledore, age: 115}"})
 *         UpdateCharacterRequest request,
 *     {@literal @}TestParameter({"1", "4"}) int bookNumber) {
 *   // ... will run for [(Hermione,1), (Hermione,4), (Dumbledore,1), (Dumbledore,4)]
 * }
 * </pre>
 *
 * <p>For more flexibility, see {{@link #valuesProvider()}}. If you don't want to test all possible
 * combinations but instead want to specify sets of parameters explicitly, use @{@link
 * TestParameters}.
 */
@Retention(RUNTIME)
@Target({FIELD, PARAMETER})
@TestParameterAnnotation(valueProvider = InternalImplementationOfThisParameter.class)
public @interface TestParameter {

  /**
   * Array of stringified values for the annotated type.
   *
   * <p>Types that are supported:
   *
   * <ul>
   *   <li>String: No parsing happens
   *   <li>boolean: Specified as YAML boolean
   *   <li>long and int: Specified as YAML integer
   *   <li>float and double: Specified as YAML floating point or integer
   *   <li>Enum value: Specified as a String that can be parsed by {@code Enum.valueOf()}
   *   <li>Byte array or com.google.protobuf.ByteString: Specified as an UTF8 String or YAML bytes
   *       (example: "!!binary 'ZGF0YQ=='")
   * </ul>
   *
   * <p>For dynamic sets of parameters or parameter types that are not supported here, use {@link
   * #valuesProvider()} and leave this field empty.
   *
   * <p>For examples, see {@link TestParameter}.
   */
  String[] value() default {};

  /**
   * Sets a provider that will return a list of parameter values.
   *
   * <p>If this field is set, {@link #value()} must be empty and vice versa.
   *
   * <p><b>Example</b>
   *
   * <pre>
   * {@literal @}Test
   * public void matchesAllOf_throwsOnNull(
   *     {@literal @}TestParameter(valuesProvider = CharMatcherProvider.class)
   *         CharMatcher charMatcher) {
   *   assertThrows(NullPointerException.class, () -&gt; charMatcher.matchesAllOf(null));
   * }
   *
   * private static final class CharMatcherProvider implements TestParameterValuesProvider {
   *   {@literal @}Override
   *   public {@literal List<CharMatcher>} provideValues() {
   *     return ImmutableList.of(CharMatcher.any(), CharMatcher.ascii(), CharMatcher.whitespace());
   *   }
   * }
   * </pre>
   */
  Class<? extends TestParameterValuesProvider> valuesProvider() default
      DefaultTestParameterValuesProvider.class;

  /** Interface for custom providers of test parameter values. */
  interface TestParameterValuesProvider {
    List<?> provideValues();
  }

  /** Default {@link TestParameterValuesProvider} implementation that does nothing. */
  class DefaultTestParameterValuesProvider implements TestParameterValuesProvider {
    @Override
    public List<Object> provideValues() {
      return ImmutableList.of();
    }
  }

  /** Implementation of this parameter annotation. */
  final class InternalImplementationOfThisParameter implements TestParameterValueProvider {
    @Override
    public List<Object> provideValues(
        Annotation uncastAnnotation, Optional<Class<?>> maybeParameterClass) {
      TestParameter annotation = (TestParameter) uncastAnnotation;
      Class<?> parameterClass = getValueType(annotation.annotationType(), maybeParameterClass);

      boolean valueIsSet = annotation.value().length > 0;
      boolean valuesProviderIsSet =
          !annotation.valuesProvider().equals(DefaultTestParameterValuesProvider.class);
      checkState(
          !(valueIsSet && valuesProviderIsSet),
          "It is not allowed to specify both value and valuesProvider on annotation %s",
          annotation);

      if (valueIsSet) {
        return stream(annotation.value())
            .map(v -> parseStringValue(v, parameterClass))
            .collect(toList());
      } else if (valuesProviderIsSet) {
        return getValuesFromProvider(annotation.valuesProvider());
      } else {
        if (Enum.class.isAssignableFrom(parameterClass)) {
          return ImmutableList.copyOf(parameterClass.asSubclass(Enum.class).getEnumConstants());
        } else if (Primitives.wrap(parameterClass).equals(Boolean.class)) {
          return ImmutableList.of(false, true);
        } else {
          throw new IllegalStateException(
              String.format(
                  "A @TestParameter without values can only be placed at an enum or a boolean, but"
                      + " was placed by a %s",
                  parameterClass));
        }
      }
    }

    @Override
    public Class<?> getValueType(
        Class<? extends Annotation> annotationType, Optional<Class<?>> parameterClass) {
      return parameterClass.orElseThrow(
          () ->
              new AssertionError(
                  String.format(
                      "An empty parameter class should not be possible since"
                          + " @TestParameter can only target FIELD or PARAMETER, both"
                          + " of which are supported for annotation %s.",
                      annotationType)));
    }

    private static Object parseStringValue(String value, Class<?> parameterClass) {
      if (parameterClass.equals(String.class)) {
        return value.equals("null") ? null : value;
      } else if (Enum.class.isAssignableFrom(parameterClass)) {
        return value.equals("null") ? null : ParameterValueParsing.parseEnum(value, parameterClass);
      } else if (MessageLite.class.isAssignableFrom(parameterClass)) {
        if (ParameterValueParsing.isValidYamlString(value)) {
          return ParameterValueParsing.parseYamlStringToJavaType(value, parameterClass);
        } else {
          return ParameterValueParsing.parseTextprotoMessage(value, parameterClass);
        }
      } else {
        return ParameterValueParsing.parseYamlStringToJavaType(value, parameterClass);
      }
    }

    private static List<Object> getValuesFromProvider(
        Class<? extends TestParameterValuesProvider> valuesProvider) {
      try {
        Constructor<? extends TestParameterValuesProvider> constructor =
            valuesProvider.getDeclaredConstructor();
        constructor.setAccessible(true);
        return new ArrayList<>(constructor.newInstance().provideValues());
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
