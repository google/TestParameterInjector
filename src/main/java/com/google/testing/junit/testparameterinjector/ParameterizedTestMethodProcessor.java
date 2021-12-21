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

import com.google.auto.value.AutoAnnotation;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.testing.junit.testparameterinjector.TestInfo.TestInfoParameter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.TestClass;

/**
 * {@code TestMethodProcessor} implementation for supporting {@link org.junit.runners.Parameterized}
 * tests.
 *
 * <p>Supports parameterized class if a method with the {@link Parameters} annotation is defined. As
 * opposed to the junit {@link org.junit.runners.Parameterized} class, only one method can have the
 * {@link Parameters} annotation, and has to be both public and static.
 *
 * <p>The {@link Parameters} annotated method can return either a {@code Collection<Object>} or a
 * {@code Collection<Object[]>}.
 *
 * <p>Does not support injected {@link org.junit.runners.Parameterized.Parameter} fields, and
 * instead requires a single class constructor with one argument for each parameter returned by the
 * {@link Parameters} method.
 */
final class ParameterizedTestMethodProcessor implements TestMethodProcessor {

  /**
   * The parameters as returned by the {@link Parameters} annotated method, or {@link
   * Optional#absent()} if the class is not parameterized.
   */
  private final Optional<Iterable<?>> parametersForAllTests;
  /**
   * The test name pattern as defined by the 'name' attribute of the {@link Parameters} annotation,
   * or {@link Optional#absent()} if the class is not parameterized.
   */
  private final Optional<String> testNamePattern;

  ParameterizedTestMethodProcessor(TestClass testClass) {
    Optional<FrameworkMethod> parametersMethod = getParametersMethod(testClass);
    if (parametersMethod.isPresent()) {
      Object parameters;
      try {
        parameters = parametersMethod.get().invokeExplosively(null);
      } catch (Throwable t) {
        throw new RuntimeException(t);
      }
      if (parameters instanceof Iterable) {
        parametersForAllTests = Optional.<Iterable<?>>of((Iterable<?>) parameters);
      } else if (parameters instanceof Object[]) {
        parametersForAllTests =
            Optional.<Iterable<?>>of(ImmutableList.copyOf((Object[]) parameters));
      } else {
        throw new IllegalStateException(
            "Unsupported @Parameters return value type: " + parameters.getClass());
      }
      testNamePattern = Optional.of(parametersMethod.get().getAnnotation(Parameters.class).name());
    } else {
      parametersForAllTests = Optional.absent();
      testNamePattern = Optional.absent();
    }
  }

  @Override
  public ExecutableValidationResult validateConstructor(Constructor<?> constructor) {
    if (parametersForAllTests.isPresent()) {
      Class<?>[] parameterTypes = constructor.getParameterTypes();
      List<Object> testParameters = getTestParameters(0);

      if (parameterTypes.length != testParameters.size()) {
        return ExecutableValidationResult.validated(
            new IllegalStateException(
                "Mismatch constructor parameter count with values"
                    + " returned by the @Parameters method"));
      }
      List<Throwable> errors = new ArrayList<>();
      for (int i = 0; i < testParameters.size(); i++) {
        if (!parameterTypes[i].isAssignableFrom(testParameters.get(i).getClass())) {
          errors.add(
              new IllegalStateException(
                  String.format(
                      "Mismatch constructor parameter type %s with value"
                          + " returned by the @Parameters method: %s",
                      parameterTypes[i], testParameters.get(i))));
        }
      }
      return ExecutableValidationResult.validated(errors);
    } else {
      return ExecutableValidationResult.notValidated();
    }
  }

  @Override
  public ExecutableValidationResult validateTestMethod(Method testMethod) {
    return ExecutableValidationResult.notValidated();
  }

  @Override
  public List<TestInfo> calculateTestInfos(TestInfo originalTest) {
    if (parametersForAllTests.isPresent()) {
      ImmutableList.Builder<TestInfo> tests = ImmutableList.builder();
      int testIndex = 0;
      for (Object parameters : parametersForAllTests.get()) {
        Object[] parametersForOneTest;
        if (parameters instanceof Object[]) {
          parametersForOneTest = (Object[]) parameters;
        } else {
          parametersForOneTest = new Object[] {parameters};
        }
        String namePattern = testNamePattern.get().replace("{index}", Integer.toString(testIndex));
        String testParametersString = MessageFormat.format(namePattern, parametersForOneTest);
        tests.add(
            originalTest
                .withExtraParameters(
                    ImmutableList.of(
                        TestInfoParameter.create(
                            testParametersString, parametersForOneTest, testIndex)))
                .withExtraAnnotation(TestIndexHolderFactory.create(testIndex)));
        testIndex++;
      }
      return tests.build();
    }
    return ImmutableList.of(originalTest);
  }

  @Override
  public Optional<List<Object>> maybeGetConstructorParameters(
      Constructor<?> constructor, TestInfo testInfo) {
    if (parametersForAllTests.isPresent()) {
      return Optional.of(
          getTestParameters(testInfo.getAnnotation(TestIndexHolder.class).testIndex()));
    } else {
      return Optional.absent();
    }
  }

  @Override
  public Optional<List<Object>> maybeGetTestMethodParameters(TestInfo testInfo) {
    return Optional.absent();
  }

  @Override
  public void postProcessTestInstance(Object testInstance, TestInfo testInfo) {}

  /**
   * This mechanism is a workaround to be able to store the test index in the annotation list of the
   * {@link TestInfo}, since we cannot carry other information through the test runner.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @interface TestIndexHolder {
    int testIndex();
  }

  /** Factory for {@link TestIndexHolder}. */
  static class TestIndexHolderFactory {
    @AutoAnnotation
    static TestIndexHolder create(int testIndex) {
      return new AutoAnnotation_ParameterizedTestMethodProcessor_TestIndexHolderFactory_create(
          testIndex);
    }

    private TestIndexHolderFactory() {}
  }

  private List<Object> getTestParameters(int testIndex) {
    Object parameters = Iterables.get(parametersForAllTests.get(), testIndex);
    if (parameters instanceof Object[]) {
      return Arrays.asList((Object[]) parameters);
    } else {
      return Arrays.asList(parameters);
    }
  }

  private Optional<FrameworkMethod> getParametersMethod(TestClass testClass) {
    List<FrameworkMethod> methods = testClass.getAnnotatedMethods(Parameters.class);
    if (methods.isEmpty()) {
      return Optional.absent();
    }
    FrameworkMethod method = Iterables.getOnlyElement(methods);
    checkState(
        method.isPublic() && method.isStatic(),
        "@Parameters method %s should be static and public",
        method.getName());
    return Optional.of(method);
  }
}
