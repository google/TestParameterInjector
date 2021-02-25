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
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Collections.unmodifiableMap;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Annotation that can be placed on @Test-methods or a test constructor to indicate the sets of
 * parameters that it should be invoked with.
 *
 * <p>For @Test-methods, the method will be invoked for every set of parameters that is specified.
 * For constructors, all the tests in the test class will be invoked on a class instance that was
 * constructed by each set of parameters.
 *
 * <p>Note: If this annotation is used in a test class, the other methods in that class can use
 * other types of parameterization, such as {@linkplain TestParameter @TestParameter}.
 *
 * <p>See {@link #value()} for simple examples.
 */
@Retention(RUNTIME)
@Target({CONSTRUCTOR, METHOD})
public @interface TestParameters {

  /**
   * Array of stringified set of parameters in YAML format. Each element corresponds to a single
   * invocation of a test method.
   *
   * <p>Each element in this array is a full parameter set, formatted as a YAML mapping. The mapping
   * keys must match the parameter names and the mapping values will be converted to the parameter
   * type if possible. See yaml.org for the YAML syntax. Parameter types that are supported:
   *
   * <ul>
   *   <li>YAML primitives:
   *       <ul>
   *         <li>String: Specified as YAML string
   *         <li>boolean: Specified as YAML boolean
   *         <li>long and int: Specified as YAML integer
   *         <li>float and double: Specified as YAML floating point or integer
   *       </ul>
   *   <li>
   *   <li>Parsed types:
   *       <ul>
   *         <li>Enum value: Specified as a String that can be parsed by {@code Enum.valueOf()}
   *       </ul>
   *   <li>
   * </ul>
   *
   * <p>For dynamic sets of parameters or parameter types that are not supported here, use {@link
   * #valuesProvider()} and leave this field empty.
   *
   * <h3>Examples</h3>
   *
   * <pre>
   * {@literal @}Test
   * {@literal @}TestParameters({
   *   "{age: 17, expectIsAdult: false}",
   *   "{age: 22, expectIsAdult: true}",
   * })
   * public void personIsAdult(int age, boolean expectIsAdult) { ... }
   *
   * {@literal @}Test
   * {@literal @}TestParameters({
   *   "{updateRequest: {name: 'Hermione'}, expectedResultType: SUCCESS}",
   *   "{updateRequest: {name: '---'}, expectedResultType: FAILURE}",
   * })
   * public void update(UpdateRequest updateRequest, ResultType expectedResultType) { ... }
   * </pre>
   */
  String[] value() default {};

  /**
   * Sets a provider that will return a list of parameter sets. Each element in the returned list
   * corresponds to a single invocation of a test method.
   *
   * <p>If this field is set, {@link #value()} must be empty and vice versa.
   *
   * <h3>Example</h3>
   *
   * <pre>
   * {@literal @}Test
   * {@literal @}TestParameters(valuesProvider = IsAdultValueProvider.class)
   * public void personIsAdult(int age, boolean expectIsAdult) { ... }
   *
   * private static final class IsAdultValueProvider implements TestParametersValuesProvider {
   *   {@literal @}Override public List<TestParametersValues> provideValues() {
   *     return ImmutableList.of(
   *       TestParametersValues.builder()
   *         .name("teenager")
   *         .addParameter("age", 17)
   *         .addParameter("expectIsAdult", false)
   *         .build(),
   *       TestParametersValues.builder()
   *         .name("young adult")
   *         .addParameter("age", 22)
   *         .addParameter("expectIsAdult", true)
   *         .build()
   *     );
   *   }
   * }
   * </pre>
   */
  Class<? extends TestParametersValuesProvider> valuesProvider() default
      DefaultTestParametersValuesProvider.class;

  /** Interface for custom providers of test parameter values. */
  interface TestParametersValuesProvider {
    List<TestParametersValues> provideValues();
  }

  /** A set of parameters for a single method invocation. */
  @AutoValue
  abstract class TestParametersValues {

    /**
     * A name for this set of parameters that will be used for describing this test.
     *
     * <p>Example: If a test method is called "personIsAdult" and this name is "teenager", the name
     * of the resulting test will be "personIsAdult[teenager]".
     */
    public abstract String name();

    /** A map, mapping parameter names to their values. */
    @SuppressWarnings("AutoValueImmutableFields") // intentional to allow null values
    public abstract Map<String, Object> parametersMap();

    public static Builder builder() {
      return new Builder();
    }

    // Avoid instantiations other than the AutoValue one.
    TestParametersValues() {}

    /** Builder for {@link TestParametersValues}. */
    public static final class Builder {
      private String name;
      private final LinkedHashMap<String, Object> parametersMap = new LinkedHashMap<>();

      /**
       * Sets a name for this set of parameters that will be used for describing this test.
       *
       * <p>Example: If a test method is called "personIsAdult" and this name is "teenager", the
       * name of the resulting test will be "personIsAdult[teenager]".
       */
      public Builder name(String name) {
        this.name = name;
        return this;
      }

      /**
       * Adds a parameter by its name.
       *
       * @param parameterName The name of the parameter of the test method
       * @param value A value of the same type as the method parameter
       */
      public Builder addParameter(String parameterName, @Nullable Object value) {
        this.parametersMap.put(parameterName, value);
        return this;
      }

      /** Adds parameters by thris names. */
      public Builder addParameters(Map<String, Object> parameterNameToValueMap) {
        this.parametersMap.putAll(parameterNameToValueMap);
        return this;
      }

      public TestParametersValues build() {
        checkState(name != null, "This set of parameters needs a name (%s)", parametersMap);
        return new AutoValue_TestParameters_TestParametersValues(
            name, unmodifiableMap(new LinkedHashMap<>(parametersMap)));
      }
    }
  }

  /** Default {@link TestParametersValuesProvider} implementation that does nothing. */
  class DefaultTestParametersValuesProvider implements TestParametersValuesProvider {
    @Override
    public List<TestParametersValues> provideValues() {
      return ImmutableList.of();
    }
  }
}
