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
import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

/** Implements the TestParameterInjector logic for JUnit5 (Jupiter). */
class TestParameterInjectorExtension implements TestTemplateInvocationContextProvider {

  private static final TestMethodProcessorList testMethodProcessors =
      TestMethodProcessorList.createNewParameterizedProcessors();

  @Override
  public boolean supportsTestTemplate(ExtensionContext context) {
    return true;
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      ExtensionContext extensionContext) {
    validateTestMethodAndConstructor(
        extensionContext.getRequiredTestMethod(), extensionContext.getRequiredTestClass());
    List<TestInfo> testInfos =
        testMethodProcessors.calculateTestInfos(
            extensionContext.getRequiredTestMethod(), extensionContext.getRequiredTestClass());

    return testInfos.stream().map(CustomInvocationContext::of);
  }

  private void validateTestMethodAndConstructor(Method testMethod, Class<?> testClass) {
    checkState(
        testClass.getDeclaredConstructors().length == 1,
        "Only a single constructor is allowed, but found %s in %s",
        testClass.getDeclaredConstructors().length,
        testClass.getSimpleName());
    Constructor<?> constructor =
        getOnlyElement(ImmutableList.copyOf(testClass.getDeclaredConstructors()));

    testMethodProcessors.validateConstructor(constructor).assertValid();

    testMethodProcessors.validateTestMethod(testMethod, testClass).assertValid();

    checkState(
        testMethod.getAnnotation(TestParameterInjectorTest.class) != null,
        "Each test method handled by this extension should be annotated with"
            + " @TestParameterInjectorTest");
  }

  @AutoValue
  abstract static class CustomInvocationContext implements TestTemplateInvocationContext {

    abstract TestInfo testInfo();

    @Memoized
    List<Object> getConstructorParameters() {
      Constructor<?> constructor =
          getOnlyElement(ImmutableList.copyOf(testInfo().getTestClass().getDeclaredConstructors()));

      return testMethodProcessors.getConstructorParameters(constructor, testInfo());
    }

    @Memoized
    List<Object> getTestMethodParameters() {
      return testMethodProcessors.getTestMethodParameters(testInfo());
    }

    static CustomInvocationContext of(TestInfo testInfo) {
      return new AutoValue_TestParameterInjectorExtension_CustomInvocationContext(testInfo);
    }

    @Override
    public String getDisplayName(int invocationIndex) {
      return testInfo().getName();
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
      return ImmutableList.of(new CustomAdditionalExtension());
    }

    class CustomAdditionalExtension implements ParameterResolver, TestInstancePostProcessor {

      @Override
      public boolean supportsParameter(
          ParameterContext parameterContext, ExtensionContext extensionContext) {
        if (parameterContext.getDeclaringExecutable() instanceof Constructor) {
          return true;
        } else {
          return parameterContext
              .getDeclaringExecutable()
              .isAnnotationPresent(TestParameterInjectorTest.class);
        }
      }

      @Override
      public Object resolveParameter(
          ParameterContext parameterContext, ExtensionContext extensionContext) {
        if (parameterContext.getDeclaringExecutable() instanceof Constructor) {
          return getConstructorParameters().get(parameterContext.getIndex());
        } else {
          return getTestMethodParameters().get(parameterContext.getIndex());
        }
      }

      @Override
      public void postProcessTestInstance(Object testInstance, ExtensionContext extensionContext)
          throws Exception {
        testMethodProcessors.postProcessTestInstance(testInstance, testInfo());
      }
    }
  }
}
