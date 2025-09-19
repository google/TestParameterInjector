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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

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
public @interface TestParameter {

  /**
   * Array of stringified values for the annotated type.
   *
   * <p>Types that are supported:
   *
   * <ul>
   *   <li>String: No parsing happens, except that {@code "null"} parses as null
   *   <li>boolean: Specified as YAML boolean
   *   <li>long and int: Specified as YAML integer
   *   <li>float and double: Specified as YAML floating point or integer
   *   <li>Enum value: Specified as a String that can be parsed by {@code Enum.valueOf()}, or {@code
   *       "null"} for null
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
   * import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider;
   *
   * {@literal @}Test
   * public void matchesAllOf_throwsOnNull(
   *     {@literal @}TestParameter(valuesProvider = CharMatcherProvider.class)
   *         CharMatcher charMatcher) {
   *   assertThrows(NullPointerException.class, () -&gt; charMatcher.matchesAllOf(null));
   * }
   *
   * private static final class CharMatcherProvider extends TestParameterValuesProvider {
   *   {@literal @}Override
   *   public {@literal List<CharMatcher>} provideValues(Context context) {
   *     return ImmutableList.of(CharMatcher.any(), CharMatcher.ascii(), CharMatcher.whitespace());
   *   }
   * }
   * </pre>
   */
  Class<? extends TestParameterValuesProvider> valuesProvider() default
      DefaultTestParameterValuesProvider.class;

  /**
   * Interface for custom providers of test parameter values.
   *
   * @deprecated Use {@link
   *     com.google.testing.junit.testparameterinjector.TestParameterValuesProvider} instead. The
   *     replacement implements this same interface, but with an additional Context parameter.
   */
  @Deprecated
  interface TestParameterValuesProvider {
    java.util.List<?> provideValues();

    /**
     * Wraps the given value in an object that allows you to give the parameter value a different
     * name. The TestParameterInjector framework will recognize the returned {@link
     * TestParameterValue} instances and unwrap them at injection time.
     *
     * <p>Usage: {@code value(file.content).withName(file.name)}.
     *
     * <p>Do not override this method.
     */
    default TestParameterValue value(@javax.annotation.Nullable Object wrappedValue) {
      return TestParameterValue.wrap(wrappedValue);
    }
  }

  /** Default {@link TestParameterValuesProvider} implementation that does nothing. */
  class DefaultTestParameterValuesProvider implements TestParameterValuesProvider {
    @Override
    public java.util.List<Object> provideValues() {
      return com.google.common.collect.ImmutableList.of();
    }
  }
}
