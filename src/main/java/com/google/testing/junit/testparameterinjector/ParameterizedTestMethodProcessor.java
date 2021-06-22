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
import java.text.MessageFormat;
import java.util.List;
import org.junit.runner.Description;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
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
class ParameterizedTestMethodProcessor implements TestMethodProcessor {

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
  public ValidationResult validateConstructor(TestClass testClass, List<Throwable> list) {
    if (parametersForAllTests.isPresent()) {
      if (testClass.getJavaClass().getConstructors().length != 1) {
        list.add(
            new IllegalStateException("Test class should have exactly one public constructor"));
        return ValidationResult.HANDLED;
      }
      Constructor<?> constructor = testClass.getOnlyConstructor();
      Class<?>[] parameterTypes = constructor.getParameterTypes();
      Object[] testParameters = getTestParameters(0);
      if (parameterTypes.length != testParameters.length) {
        list.add(
            new IllegalStateException(
                "Mismatch constructor parameter count with values"
                    + " returned by the @Parameters method"));
        return ValidationResult.HANDLED;
      }
      for (int i = 0; i < testParameters.length; i++) {
        if (!parameterTypes[i].isAssignableFrom(testParameters[i].getClass())) {
          list.add(
              new IllegalStateException(
                  String.format(
                      "Mismatch constructor parameter type %s with value"
                          + " returned by the @Parameters method: %s",
                      parameterTypes[i], testParameters[i])));
        }
      }
      return ValidationResult.HANDLED;
    }
    return ValidationResult.NOT_HANDLED;
  }

  @Override
  public ValidationResult validateTestMethod(
      TestClass testClass, FrameworkMethod testMethod, List<Throwable> errorsReturned) {
    return ValidationResult.NOT_HANDLED;
  }

  @Override
  public List<TestInfo> processTest(Class<?> testClass, TestInfo originalTest) {
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
  public Statement processStatement(Statement originalStatement, Description finalTestDescription) {
    return originalStatement;
  }

  @Override
  public Optional<Object> createTest(
      TestClass testClass, FrameworkMethod method, Optional<Object> test) {
    if (parametersForAllTests.isPresent()) {
      Object[] testParameters =
          getTestParameters(method.getAnnotation(TestIndexHolder.class).testIndex());
      try {
        Constructor<?> constructor = testClass.getOnlyConstructor();
        return Optional.<Object>of(constructor.newInstance(testParameters));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return test;
  }

  @Override
  public Optional<Statement> createStatement(
      TestClass testClass,
      FrameworkMethod method,
      Object testObject,
      Optional<Statement> statement) {
    return statement;
  }

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

  private Object[] getTestParameters(int testIndex) {
    Object parameters = Iterables.get(parametersForAllTests.get(), testIndex);
    if (parameters instanceof Object[]) {
      return (Object[]) parameters;
    } else {
      return new Object[] {parameters};
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
