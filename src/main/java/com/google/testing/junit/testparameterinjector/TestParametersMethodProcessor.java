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
import static java.util.Arrays.stream;

import com.google.auto.value.AutoAnnotation;
import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.TypeToken;
import com.google.testing.junit.testparameterinjector.TestInfo.TestInfoParameter;
import com.google.testing.junit.testparameterinjector.TestParameters.DefaultTestParametersValuesProvider;
import com.google.testing.junit.testparameterinjector.TestParameters.TestParametersValues;
import com.google.testing.junit.testparameterinjector.TestParameters.TestParametersValuesProvider;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

/** {@code TestMethodProcessor} implementation for supporting {@link TestParameters}. */
@SuppressWarnings("AndroidJdkLibsChecker") // Parameter is not available on old Android SDKs.
class TestParametersMethodProcessor implements TestMethodProcessor {

  private final TestClass testClass;

  private final LoadingCache<Object, ImmutableList<TestParametersValues>>
      parameterValuesByConstructorOrMethodCache =
          CacheBuilder.newBuilder()
              .maximumSize(1000)
              .build(
                  CacheLoader.from(
                      methodOrConstructor ->
                          (methodOrConstructor instanceof Constructor)
                              ? toParameterValuesList(
                                  methodOrConstructor,
                                  ((Constructor<?>) methodOrConstructor)
                                      .getAnnotation(TestParameters.class),
                                  ((Constructor<?>) methodOrConstructor).getParameters())
                              : toParameterValuesList(
                                  methodOrConstructor,
                                  ((Method) methodOrConstructor)
                                      .getAnnotation(TestParameters.class),
                                  ((Method) methodOrConstructor).getParameters())));

  public TestParametersMethodProcessor(TestClass testClass) {
    this.testClass = testClass;
  }

  @Override
  public ValidationResult validateConstructor(TestClass testClass, List<Throwable> exceptions) {
    if (testClass.getOnlyConstructor().isAnnotationPresent(TestParameters.class)) {
      try {
        // This method throws an exception if there is a validation error
        getConstructorParameters();
      } catch (Throwable t) {
        exceptions.add(t);
      }
      return ValidationResult.HANDLED;
    } else {
      return ValidationResult.NOT_HANDLED;
    }
  }

  @Override
  public ValidationResult validateTestMethod(
      TestClass testClass, FrameworkMethod testMethod, List<Throwable> exceptions) {
    if (testMethod.getMethod().isAnnotationPresent(TestParameters.class)) {
      try {
        // This method throws an exception if there is a validation error
        getMethodParameters(testMethod.getMethod());
      } catch (Throwable t) {
        exceptions.add(t);
      }
      return ValidationResult.HANDLED;
    } else {
      return ValidationResult.NOT_HANDLED;
    }
  }

  @Override
  public List<TestInfo> processTest(Class<?> clazz, TestInfo originalTest) {
    boolean constructorIsParameterized =
        testClass.getOnlyConstructor().isAnnotationPresent(TestParameters.class);
    boolean methodIsParameterized =
        originalTest.getMethod().isAnnotationPresent(TestParameters.class);

    if (!constructorIsParameterized && !methodIsParameterized) {
      return ImmutableList.of(originalTest);
    }

    ImmutableList.Builder<TestInfo> testInfos = ImmutableList.builder();

    ImmutableList<Optional<TestParametersValues>> constructorParametersList =
        getConstructorParametersOrSingleAbsentElement();
    ImmutableList<Optional<TestParametersValues>> methodParametersList =
        getMethodParametersOrSingleAbsentElement(originalTest.getMethod());
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
                    Stream.of(
                            constructorParameters
                                .transform(
                                    param ->
                                        TestInfoParameter.create(
                                            param.name(),
                                            param.parametersMap(),
                                            constructorParametersIndexCopy))
                                .orNull(),
                            methodParameters
                                .transform(
                                    param ->
                                        TestInfoParameter.create(
                                            param.name(),
                                            param.parametersMap(),
                                            methodParametersIndexCopy))
                                .orNull())
                        .filter(Objects::nonNull)
                        .collect(toImmutableList()))
                .withExtraAnnotation(
                    TestIndexHolderFactory.create(
                        constructorParametersIndex, methodParametersIndex)));
      }
    }
    return testInfos.build();
  }

  private ImmutableList<Optional<TestParametersValues>>
      getConstructorParametersOrSingleAbsentElement() {
    return testClass.getOnlyConstructor().isAnnotationPresent(TestParameters.class)
        ? getConstructorParameters().stream().map(Optional::of).collect(toImmutableList())
        : ImmutableList.of(Optional.absent());
  }

  private ImmutableList<Optional<TestParametersValues>> getMethodParametersOrSingleAbsentElement(
      Method method) {
    return method.isAnnotationPresent(TestParameters.class)
        ? getMethodParameters(method).stream().map(Optional::of).collect(toImmutableList())
        : ImmutableList.of(Optional.absent());
  }

  @Override
  public Statement processStatement(Statement originalStatement, Description finalTestDescription) {
    return originalStatement;
  }

  @Override
  public Optional<Object> createTest(
      TestClass testClass, FrameworkMethod method, Optional<Object> test) {
    if (testClass.getOnlyConstructor().isAnnotationPresent(TestParameters.class)) {
      ImmutableList<TestParametersValues> parameterValuesList = getConstructorParameters();
      TestParametersValues parametersValues =
          parameterValuesList.get(
              method.getAnnotation(TestIndexHolder.class).constructorParametersIndex());

      try {
        Constructor<?> constructor = testClass.getOnlyConstructor();
        return Optional.of(
            constructor.newInstance(
                toParameterArray(
                    parametersValues, testClass.getOnlyConstructor().getParameters())));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else {
      return test;
    }
  }

  @Override
  public Optional<Statement> createStatement(
      TestClass testClass,
      FrameworkMethod method,
      Object testObject,
      Optional<Statement> statement) {
    if (method.getMethod().isAnnotationPresent(TestParameters.class)) {
      ImmutableList<TestParametersValues> parameterValuesList =
          getMethodParameters(method.getMethod());
      TestParametersValues parametersValues =
          parameterValuesList.get(
              method.getAnnotation(TestIndexHolder.class).methodParametersIndex());

      return Optional.of(
          new Statement() {
            @Override
            public void evaluate() throws Throwable {
              method.invokeExplosively(
                  testObject,
                  toParameterArray(parametersValues, method.getMethod().getParameters()));
            }
          });
    } else {
      return statement;
    }
  }

  private ImmutableList<TestParametersValues> getConstructorParameters() {
    return parameterValuesByConstructorOrMethodCache.getUnchecked(testClass.getOnlyConstructor());
  }

  private ImmutableList<TestParametersValues> getMethodParameters(Method method) {
    return parameterValuesByConstructorOrMethodCache.getUnchecked(method);
  }

  private static ImmutableList<TestParametersValues> toParameterValuesList(
      Object methodOrConstructor, TestParameters annotation, Parameter[] invokableParameters) {
    boolean valueIsSet = annotation.value().length > 0;
    boolean valuesProviderIsSet =
        !annotation.valuesProvider().equals(DefaultTestParametersValuesProvider.class);

    checkState(
        !(valueIsSet && valuesProviderIsSet),
        "It is not allowed to specify both value and valuesProvider on annotation %s",
        annotation);
    checkState(
        valueIsSet || valuesProviderIsSet,
        "Either value or valuesProvider must be set on annotation %s",
        annotation);

    ImmutableList<Parameter> parametersList = ImmutableList.copyOf(invokableParameters);
    checkState(
        parametersList.stream().allMatch(Parameter::isNamePresent),
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
        methodOrConstructor);
    if (valueIsSet) {
      return stream(annotation.value())
          .map(yamlMap -> toParameterValues(yamlMap, parametersList))
          .collect(toImmutableList());
    } else {
      return toParameterValuesList(annotation.valuesProvider(), parametersList);
    }
  }

  private static ImmutableList<TestParametersValues> toParameterValuesList(
      Class<? extends TestParametersValuesProvider> valuesProvider, List<Parameter> parameters) {
    try {
      Constructor<? extends TestParametersValuesProvider> constructor =
          valuesProvider.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance().provideValues().stream()
          .peek(values -> validateThatValuesMatchParameters(values, parameters))
          .collect(toImmutableList());
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
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void validateThatValuesMatchParameters(
      TestParametersValues testParametersValues, List<Parameter> parameters) {
    ImmutableMap<String, Parameter> parametersByName =
        Maps.uniqueIndex(parameters, Parameter::getName);

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
      String yamlString, List<Parameter> parameters) {
    Object yamlMapObject = ParameterValueParsing.parseYamlStringToObject(yamlString);
    checkState(
        yamlMapObject instanceof Map,
        "Cannot map YAML string '%s' to parameters because it is not a mapping",
        yamlString);
    Map<?, ?> yamlMap = (Map<?, ?>) yamlMapObject;

    ImmutableMap<String, Parameter> parametersByName =
        Maps.uniqueIndex(parameters, Parameter::getName);
    checkState(
        yamlMap.keySet().equals(parametersByName.keySet()),
        "Cannot map YAML string '%s' to parameters %s",
        yamlString,
        parametersByName.keySet());

    @SuppressWarnings("unchecked")
    Map<String, Object> checkedYamlMap = (Map<String, Object>) yamlMap;

    return TestParametersValues.builder()
        .name(yamlString)
        .addParameters(
            Maps.transformEntries(
                checkedYamlMap,
                (parameterName, parsedYaml) ->
                    ParameterValueParsing.parseYamlObjectToJavaType(
                        parsedYaml,
                        TypeToken.of(parametersByName.get(parameterName).getParameterizedType()))))
        .build();
  }

  private static Object[] toParameterArray(
      TestParametersValues parametersValues, Parameter[] parameters) {
    return stream(parameters)
        .map(parameter -> parametersValues.parametersMap().get(parameter.getName()))
        .toArray();
  }

  // Immutable collectors are re-implemented here because they are missing from the Android
  // collection library.
  private static <E> Collector<E, ?, ImmutableList<E>> toImmutableList() {
    return Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf);
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
