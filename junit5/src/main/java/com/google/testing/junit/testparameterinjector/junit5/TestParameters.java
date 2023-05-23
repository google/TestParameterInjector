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

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Collections.unmodifiableMap;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.junit5.TestParameters.TestParametersValuesProvider;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Annotation that can be placed (repeatedly) on @Test-methods or a test constructor to indicate the
 * sets of parameters that it should be invoked with.
 *
 * <p>For @Test-methods, the method will be invoked for every set of parameters that is specified.
 * For constructors, all the tests in the test class will be invoked on a class instance that was
 * constructed by each set of parameters.
 *
 * <p>Note: If this annotation is used in a test class, the other methods in that class can still
 * use other types of parameterization, such as {@linkplain TestParameter @TestParameter}.
 *
 * <p>See {@link #value()} for simple examples.
 *
 * <p>Warning: This annotation can only be used if the compiled java code contains the parameter
 * names. This is typically done by passing the {@code -parameters} option to the Java compiler,
 * which requires using Java 8 or higher and may not be available on Android.
 */
@Retention(RUNTIME)
@Target({CONSTRUCTOR, METHOD})
@Repeatable(TestParameters.RepeatedTestParameters.class)
public @interface TestParameters {

  /**
   * Specifies one or more stringified sets of parameters in YAML format. Each set corresponds to a
   * single invocation of a test method.
   *
   * <p>Each element in this array is a full parameter set, formatted as a YAML mapping. The mapping
   * keys must match the parameter names and the mapping values will be converted to the parameter
   * type if possible. See yaml.org for the YAML syntax and the section below on the supported
   * parameter types.
   *
   * <p>There are two distinct ways of using this annotation: repeated vs single:
   *
   * <p><b>Recommended usage: Separate annotation per parameter set</b>
   *
   * <p>This approach uses multiple @TestParameters annotations, one for each set of parameters, for
   * example:
   *
   * <pre>
   * {@literal @}Test
   * {@literal @}TestParameters("{age: 17, expectIsAdult: false}")
   * {@literal @}TestParameters("{age: 22, expectIsAdult: true}")
   * public void personIsAdult(int age, boolean expectIsAdult) { ... }
   *
   * {@literal @}Test
   * {@literal @}TestParameters("{updateRequest: {country_code: BE}, expectedResultType: SUCCESS}")
   * {@literal @}TestParameters("{updateRequest: {country_code: XYZ}, expectedResultType: FAILURE}")
   * public void update(UpdateRequest updateRequest, ResultType expectedResultType) { ... }
   * </pre>
   *
   * <p><b>Old discouraged usage: Single annotation with all parameter sets</b>
   *
   * <p>This approach uses a single @TestParameter annotation for all parameter sets, for example:
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
   *   "{updateRequest: {country_code: BE}, expectedResultType: SUCCESS}",
   *   "{updateRequest: {country_code: XYZ}, expectedResultType: FAILURE}",
   * })
   * public void update(UpdateRequest updateRequest, ResultType expectedResultType) { ... }
   * </pre>
   *
   * <p><b>Supported parameter types</b>
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
   *         <li>Byte array or com.google.protobuf.ByteString: Specified as an UTF8 String or YAML
   *             bytes (example: "!!binary 'ZGF0YQ=='")
   *       </ul>
   *   <li>
   * </ul>
   *
   * <p>For dynamic sets of parameters or parameter types that are not supported here, use {@link
   * #valuesProvider()} and leave this field empty.
   */
  String[] value() default {};

  /**
   * Overrides the name of the parameter set that is used in the test name.
   *
   * <p>This can only be set if {@link #value()} has exactly one element. If not set, the YAML
   * string in {@link #value()} is used in the test name.
   *
   * <p>For example: If this name is set to "young adult", then the test name might be
   * "personIsAdult[young adult]" where the default might have been "personIsAdult[{age: 17,
   * expectIsAdult: false}]".
   */
  String customName() default "";

  /**
   * Sets a provider that will return a list of parameter sets. Each element in the returned list
   * corresponds to a single invocation of a test method.
   *
   * <p>If this field is set, {@link #value()} must be empty and vice versa.
   *
   * <p><b>Example</b>
   *
   * <pre>
   * {@literal @}Test
   * {@literal @}TestParameters(valuesProvider = IsAdultValueProvider.class)
   * public void personIsAdult(int age, boolean expectIsAdult) { ... }
   *
   * private static final class IsAdultValueProvider implements TestParametersValuesProvider {
   *   {@literal @}Override public {@literal List<TestParametersValues>} provideValues() {
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
       * <p>Setting a name is optional. If unset, one will be generated from the parameter values.
       *
       * <p>Example: If a test method is called "personIsAdult" and this name is "teenager", the
       * name of the resulting test will be "personIsAdult[teenager]".
       */
      public Builder name(String name) {
        this.name = name.replaceAll("\\s+", " ");
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
        if (name == null) {
          // Name is not set. Auto-generate one based on the parameter name and values
          StringBuilder nameBuilder = new StringBuilder();
          nameBuilder.append('{');
          for (String parameterName : parametersMap.keySet()) {
            if (nameBuilder.length() > 1) {
              nameBuilder.append(", ");
            }
            nameBuilder.append(
                ParameterValueParsing.formatTestNameString(
                    Optional.of(parameterName), parametersMap.get(parameterName)));
          }
          nameBuilder.append('}');
          name = nameBuilder.toString();
        }
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

  /**
   * Holder annotation for multiple @TestParameters annotations. This should never be used directly.
   */
  @Retention(RUNTIME)
  @Target({CONSTRUCTOR, METHOD})
  @interface RepeatedTestParameters {
    TestParameters[] value();
  }
}
