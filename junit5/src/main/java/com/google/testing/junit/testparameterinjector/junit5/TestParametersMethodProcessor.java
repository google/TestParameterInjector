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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import com.google.auto.value.AutoAnnotation;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.TypeToken;
import com.google.testing.junit.testparameterinjector.junit5.TestInfo.TestInfoParameter;
import com.google.testing.junit.testparameterinjector.junit5.TestParameterInjectorUtils.JavaCompatibilityExecutable;
import com.google.testing.junit.testparameterinjector.junit5.TestParameterInjectorUtils.JavaCompatibilityParameter;
import com.google.testing.junit.testparameterinjector.junit5.TestParameters.RepeatedTestParameters;
import com.google.testing.junit.testparameterinjector.junit5.TestParameters.TestParametersValues;
import com.google.testing.junit.testparameterinjector.junit5.TestParametersValuesProvider.Context;
import com.google.testing.junit.testparameterinjector.junit5.TestParameters.TestParametersValuesProvider;
import com.google.testing.junit.testparameterinjector.junit5.TestParameters.DefaultTestParametersValuesProvider;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/** {@code TestMethodProcessor} implementation for supporting {@link TestParameters}. */
final class TestParametersMethodProcessor implements TestMethodProcessor {

  private final Cache<Object, ImmutableList<TestParametersValues>>
      parameterValuesByConstructorOrMethodCache =
          CacheBuilder.newBuilder().maximumSize(1000).build();

  @Override
  public ExecutableValidationResult validateConstructor(Constructor<?> constructor) {
    JavaCompatibilityExecutable constructorExecutable =
        JavaCompatibilityExecutable.create(constructor);
    if (hasRelevantAnnotation(constructorExecutable)) {
      try {
        // This method throws an exception if there is a validation error
        ImmutableList<TestParametersValues> unused =
            getExecutableParameters(constructorExecutable, constructor.getDeclaringClass());
      } catch (Throwable t) {
        return ExecutableValidationResult.validated(t);
      }
      return ExecutableValidationResult.valid();
    } else {
      return ExecutableValidationResult.notValidated();
    }
  }

  @Override
  public ExecutableValidationResult validateTestMethod(Method testMethod, Class<?> testClass) {
    JavaCompatibilityExecutable testMethodExecutable =
        JavaCompatibilityExecutable.create(testMethod);
    if (hasRelevantAnnotation(testMethodExecutable)) {
      try {
        // This method throws an exception if there is a validation error
        ImmutableList<TestParametersValues> unused =
            getExecutableParameters(testMethodExecutable, testClass);
      } catch (Throwable t) {
        return ExecutableValidationResult.validated(t);
      }
      return ExecutableValidationResult.valid();
    } else {
      return ExecutableValidationResult.notValidated();
    }
  }

  @Override
  public List<TestInfo> calculateTestInfos(TestInfo originalTest) {
    JavaCompatibilityExecutable constructorExecutable =
        JavaCompatibilityExecutable.create(
            TestParameterInjectorUtils.getOnlyConstructor(originalTest.getTestClass()));
    JavaCompatibilityExecutable testMethodExecutable =
        JavaCompatibilityExecutable.create(originalTest.getMethod());

    if (!hasRelevantAnnotation(constructorExecutable)
        && !hasRelevantAnnotation(testMethodExecutable)) {
      return ImmutableList.of(originalTest);
    }

    ImmutableList.Builder<TestInfo> testInfos = ImmutableList.builder();

    ImmutableList<Optional<TestParametersValues>> constructorParametersList =
        getExecutableParametersOrSingleAbsentElement(
            constructorExecutable, originalTest.getTestClass());
    ImmutableList<Optional<TestParametersValues>> methodParametersList =
        getExecutableParametersOrSingleAbsentElement(
            testMethodExecutable, originalTest.getTestClass());
    for (int constructorParametersIndex = 0;
        constructorParametersIndex < constructorParametersList.size();
        ++constructorParametersIndex) {
      Optional<TestParametersValues> constructorParameters =
          constructorParametersList.get(constructorParametersIndex);

      for (int methodParametersIndex = 0;
          methodParametersIndex < methodParametersList.size();
          ++methodParametersIndex) {
        Optional<TestParametersValues> methodParameters =
            methodParametersList.get(methodParametersIndex);

        // Making final copies of non-final integers for use in lambda
        int constructorParametersIndexCopy = constructorParametersIndex;
        int methodParametersIndexCopy = methodParametersIndex;

        testInfos.add(
            originalTest
                .withExtraParameters(
                    FluentIterable.of(
                            constructorParameters.transform(
                                param ->
                                    TestInfoParameter.create(
                                        param.name(),
                                        param.parametersMap(),
                                        constructorParametersIndexCopy)),
                            methodParameters.transform(
                                param ->
                                    TestInfoParameter.create(
                                        param.name(),
                                        param.parametersMap(),
                                        methodParametersIndexCopy)))
                        .filter(Optional::isPresent)
                        .transform(Optional::get)
                        .toList())
                .withExtraAnnotation(
                    TestIndexHolderFactory.create(
                        constructorParametersIndex, methodParametersIndex)));
      }
    }
    return testInfos.build();
  }

  private ImmutableList<Optional<TestParametersValues>>
      getExecutableParametersOrSingleAbsentElement(
          JavaCompatibilityExecutable executable, Class<?> testClass) {
    return hasRelevantAnnotation(executable)
        ? FluentIterable.from(getExecutableParameters(executable, testClass))
            .transform(Optional::of)
            .toList()
        : ImmutableList.of(Optional.absent());
  }

  @Override
  public Optional<List<Object>> maybeGetConstructorParameters(
      Constructor<?> constructor, TestInfo testInfo) {
    return maybeGetExecutableParameters(
        JavaCompatibilityExecutable.create(constructor),
        testInfo.getTestClass(),
        () -> testInfo.getAnnotation(TestIndexHolder.class).constructorParametersIndex());
  }

  @Override
  public Optional<List<Object>> maybeGetTestMethodParameters(TestInfo testInfo) {
    return maybeGetExecutableParameters(
        JavaCompatibilityExecutable.create(testInfo.getMethod()),
        testInfo.getTestClass(),
        () -> testInfo.getAnnotation(TestIndexHolder.class).methodParametersIndex());
  }

  private Optional<List<Object>> maybeGetExecutableParameters(
      JavaCompatibilityExecutable executable,
      Class<?> testClass,
      Supplier<Integer> parametersIndex) {
    if (hasRelevantAnnotation(executable)) {
      ImmutableList<TestParametersValues> parameterValuesList =
          getExecutableParameters(executable, testClass);
      TestParametersValues parametersValues = parameterValuesList.get(parametersIndex.get());

      ImmutableList<JavaCompatibilityParameter> parameters =
          getParametersWithKotlinFallback(executable);
      return Optional.of(
          FluentIterable.from(parameters)
              .transform(
                  parameter -> parametersValues.parametersMap().get(parameter.maybeGetName().get()))
              .copyInto(new ArrayList<>(parameters.size())));
    } else {
      return Optional.absent();
    }
  }

  @Override
  public void postProcessTestInstance(Object testInstance, TestInfo testInfo) {}

  private ImmutableList<TestParametersValues> getExecutableParameters(
      JavaCompatibilityExecutable executable, Class<?> testClass) {
    try {
      return parameterValuesByConstructorOrMethodCache.get(
          executable.getJavaReflectVersion(), () -> toParameterValuesList(executable, testClass));
    } catch (ExecutionException e) {
      // Rethrow IllegalStateException because they can be caused by user mistakes and the user
      // doesn't need to know that the caching layer is in between.
      Throwables.throwIfInstanceOf(e.getCause(), IllegalStateException.class);
      throw new RuntimeException(e);
    }
  }

  private static ImmutableList<TestParametersValues> toParameterValuesList(
      JavaCompatibilityExecutable executable, Class<?> testClass) {
    ImmutableList<JavaCompatibilityParameter> parametersList =
        getParametersWithKotlinFallback(executable);
    checkParameterNamesArePresent(parametersList, executable);

    if (executable.isAnnotationPresent(TestParameters.class)) {
      checkState(
          !executable.isAnnotationPresent(RepeatedTestParameters.class),
          "Unexpected situation: Both @TestParameters and @RepeatedTestParameters annotating the"
              + " same method");
      TestParameters annotation = executable.getAnnotation(TestParameters.class);
      boolean valueIsSet = annotation.value().length > 0;
      boolean valuesProviderIsSet =
          !annotation.valuesProvider().equals(DefaultTestParametersValuesProvider.class);

      checkState(
          !(valueIsSet && valuesProviderIsSet),
          "%s: It is not allowed to specify both value and valuesProvider in"
              + " @TestParameters(value=%s, valuesProvider=%s)",
          executable.getHumanReadableNameSummary(),
          Arrays.toString(annotation.value()),
          annotation.valuesProvider().getSimpleName());
      checkState(
          valueIsSet || valuesProviderIsSet,
          "%s: Either a value or a valuesProvider must be set in @TestParameters",
          executable.getHumanReadableNameSummary());
      if (!annotation.customName().isEmpty()) {
        checkState(
            annotation.value().length == 1,
            "%s: Setting @TestParameters.customName is only allowed if there is exactly one YAML"
                + " string in @TestParameters.value",
            executable.getHumanReadableNameSummary());
      }

      if (valueIsSet) {
        return FluentIterable.from(annotation.value())
            .transform(
                yamlMap -> toParameterValues(yamlMap, parametersList, annotation.customName()))
            .toList();
      } else {
        return toParameterValuesList(
            annotation.valuesProvider(), parametersList, executable, testClass);
      }
    } else { // Not annotated with @TestParameters
      verify(
          executable.isAnnotationPresent(RepeatedTestParameters.class),
          "This method should only be called for executables with at least one relevant"
              + " annotation");

      return FluentIterable.from(executable.getAnnotation(RepeatedTestParameters.class).value())
          .transform(
              annotation ->
                  toParameterValues(
                      validateAndGetSingleValueFromRepeatedAnnotation(annotation, executable),
                      parametersList,
                      annotation.customName()))
          .toList();
    }
  }

  private static ImmutableList<TestParametersValues> toParameterValuesList(
      Class<? extends TestParametersValuesProvider> valuesProvider,
      List<JavaCompatibilityParameter> parameters,
      JavaCompatibilityExecutable executable,
      Class<?> testClass) {
    try {
      Constructor<? extends TestParametersValuesProvider> constructor =
          valuesProvider.getDeclaredConstructor();
      constructor.setAccessible(true);
      TestParametersValuesProvider provider = constructor.newInstance();
      Context context = new Context(GenericParameterContext.create(executable, testClass));
      List<TestParametersValues> testParametersValues =
          provider
                  instanceof
                  com.google.testing.junit.testparameterinjector.junit5.TestParametersValuesProvider
              ? ((com.google.testing.junit.testparameterinjector.junit5.TestParametersValuesProvider)
                      provider)
                  .provideValues(context)
              : provider.provideValues();
      boolean valuesListCanBeEmpty =
          provider
                  instanceof
                  com.google.testing.junit.testparameterinjector.junit5.TestParametersValuesProvider
              ? ((com.google.testing.junit.testparameterinjector.junit5.TestParametersValuesProvider)
                      provider)
                  .valuesListCanBeEmptyWhichMeansThatTheTestWillBeSkipped()
              : false;
      if (!valuesListCanBeEmpty) {
        checkState(
            !testParametersValues.isEmpty(),
            "%s: %s returned an empty list of TestParametersValues",
            executable.getHumanReadableNameSummary(),
            valuesProvider.getSimpleName());
      }
      for (TestParametersValues testParametersValue : testParametersValues) {
        validateThatValuesMatchParameters(testParametersValue, parameters);
      }
      return ImmutableList.copyOf(testParametersValues);
    } catch (NoSuchMethodException e) {
      if (!Modifier.isStatic(valuesProvider.getModifiers()) && valuesProvider.isMemberClass()) {
        throw new IllegalStateException(
            String.format(
                "Could not find a no-arg constructor for %s, probably because it is a not-static"
                    + " inner class. You can fix this by making %s static.",
                valuesProvider.getSimpleName(), valuesProvider.getSimpleName()),
            e);
      } else {
        throw new IllegalStateException(
            String.format(
                "Could not find a no-arg constructor for %s.", valuesProvider.getSimpleName()),
            e);
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void checkParameterNamesArePresent(
      List<JavaCompatibilityParameter> parameters, JavaCompatibilityExecutable executable) {
    checkState(
        FluentIterable.from(parameters).allMatch(p -> p.maybeGetName().isPresent()),
        ""
            + "No parameter name could be found for %s, which likely means that parameter names"
            + " aren't available at runtime. Please ensure that the this test was built with the"
            + " -parameters compiler option.\n"
            + "\n"
            + "In Maven, you do this by adding <parameters>true</parameters> to the"
            + " maven-compiler-plugin's configuration. For example:\n"
            + "\n"
            + "<build>\n"
            + "  <plugins>\n"
            + "    <plugin>\n"
            + "      <groupId>org.apache.maven.plugins</groupId>\n"
            + "      <artifactId>maven-compiler-plugin</artifactId>\n"
            + "      <version>3.8.1</version>\n"
            + "      <configuration>\n"
            + "        <compilerArgs>\n"
            + "          <arg>-parameters</arg>\n"
            + "        </compilerArgs>\n"
            + "      </configuration>\n"
            + "    </plugin>\n"
            + "  </plugins>\n"
            + "</build>\n"
            + "\n"
            + "Don't forget to run `mvn clean` after making this change.",
        executable.getHumanReadableNameSummary());
  }

  private static String validateAndGetSingleValueFromRepeatedAnnotation(
      TestParameters annotation, JavaCompatibilityExecutable executable) {
    checkState(
        annotation.valuesProvider().equals(DefaultTestParametersValuesProvider.class),
        "%s: Setting a valuesProvider is not supported for methods/constructors with"
            + " multiple @TestParameters annotations",
        executable.getHumanReadableNameSummary());
    checkState(
        annotation.value().length > 0,
        "%s: Either a value or a valuesProvider must be set in @TestParameters",
        executable.getHumanReadableNameSummary());
    checkState(
        annotation.value().length == 1,
        "%s: When specifying more than one @TestParameter for a method/constructor, each annotation"
            + " must have exactly one value. Instead, got %s values: %s",
        executable.getHumanReadableNameSummary(),
        annotation.value().length,
        Arrays.toString(annotation.value()));

    return annotation.value()[0];
  }

  private static void validateThatValuesMatchParameters(
      TestParametersValues testParametersValues, List<JavaCompatibilityParameter> parameters) {
    ImmutableMap<String, JavaCompatibilityParameter> parametersByName =
        Maps.uniqueIndex(parameters, p -> p.maybeGetName().get());

    checkState(
        testParametersValues.parametersMap().keySet().equals(parametersByName.keySet()),
        "Cannot map the given TestParametersValues to parameters %s (Given TestParametersValues"
            + " are %s)",
        parametersByName.keySet(),
        testParametersValues);

    testParametersValues
        .parametersMap()
        .forEach(
            (paramName, paramValue) -> {
              Class<?> expectedClass = Primitives.wrap(parametersByName.get(paramName).getType());
              if (paramValue != null) {
                checkState(
                    expectedClass.isInstance(paramValue),
                    "Cannot map value '%s' (class = %s) to parameter %s (class = %s) (for"
                        + " TestParametersValues %s)",
                    paramValue,
                    paramValue.getClass(),
                    paramName,
                    expectedClass,
                    testParametersValues);
              }
            });
  }

  private static TestParametersValues toParameterValues(
      String yamlString, List<JavaCompatibilityParameter> parameters, String maybeCustomName) {
    Object yamlMapObject = ParameterValueParsing.parseYamlStringToObject(yamlString);
    checkState(
        yamlMapObject instanceof Map,
        "Cannot map YAML string '%s' to parameters because it is not a mapping",
        yamlString);
    Map<?, ?> yamlMap = (Map<?, ?>) yamlMapObject;

    ImmutableMap<String, JavaCompatibilityParameter> parametersByName =
        Maps.uniqueIndex(parameters, p -> p.maybeGetName().get());
    checkState(
        yamlMap.keySet().equals(parametersByName.keySet()),
        "Cannot map YAML string '%s' to parameters %s",
        yamlString,
        parametersByName.keySet());

    @SuppressWarnings("unchecked")
    Map<String, Object> checkedYamlMap = (Map<String, Object>) yamlMap;

    return TestParametersValues.builder()
        .name(maybeCustomName.isEmpty() ? yamlString : maybeCustomName)
        .addParameters(
            Maps.transformEntries(
                checkedYamlMap,
                (parameterName, parsedYaml) ->
                    ParameterValueParsing.parseYamlObjectToJavaType(
                        parsedYaml,
                        TypeToken.of(parametersByName.get(parameterName).getParameterizedType()))))
        .build();
  }

  private static boolean hasRelevantAnnotation(JavaCompatibilityExecutable executable) {
    return executable.isAnnotationPresent(TestParameters.class)
        || executable.isAnnotationPresent(RepeatedTestParameters.class);
  }

  @SuppressWarnings("KotlinInternal")
  private static ImmutableList<JavaCompatibilityParameter> getParametersWithKotlinFallback(
      JavaCompatibilityExecutable executable) {
    return executable.getParametersWithFallback(
        TestParameterInjectorUtils.isKotlinClass(executable.getDeclaringClass())
            ? KotlinHooksForTestParameterInjector.getParameterNames(executable)
            : Optional.absent());
  }

  /**
   * This mechanism is a workaround to be able to store the test index in the annotation list of the
   * {@link TestInfo}, since we cannot carry other information through the test runner.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @interface TestIndexHolder {
    int constructorParametersIndex();

    int methodParametersIndex();
  }

  /** Factory for {@link TestIndexHolder}. */
  static class TestIndexHolderFactory {
    @AutoAnnotation
    static TestIndexHolder create(int constructorParametersIndex, int methodParametersIndex) {
      return new AutoAnnotation_TestParametersMethodProcessor_TestIndexHolderFactory_create(
          constructorParametersIndex, methodParametersIndex);
    }

    private TestIndexHolderFactory() {}
  }
}
