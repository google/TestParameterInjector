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

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.internal.runners.model.ReflectiveCallable;
import org.junit.internal.runners.statements.Fail;
import org.junit.rules.MethodRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

/**
 * Class to substitute JUnit4 runner in JUnit4 tests, adding additional functionality.
 *
 * <p>See {@link TestParameterInjector} for an example implementation.
 */
abstract class PluggableTestRunner extends BlockJUnit4ClassRunner {

  /**
   * A {@link ThreadLocal} is used to handle cases where multiple tests are executing in the same
   * java process in different threads.
   *
   * <p>A null value indicates that the TestInfo hasn't been set yet, which would typically happen
   * if the test hasn't yet started, or the {@link PluggableTestRunner} is not the test runner.
   */
  private static final ThreadLocal<TestInfo> currentTestInfo = new ThreadLocal<>();

  private TestMethodProcessorList testMethodProcessors;

  protected PluggableTestRunner(Class<?> klass) throws InitializationError {
    super(klass);
  }

  /** Returns the TestMethodProcessorList to use. This is meant to be overridden by subclasses. */
  protected abstract TestMethodProcessorList createTestMethodProcessorList();

  /**
   * This method is run to perform optional additional operations on the test instance, right after
   * it was created.
   */
  protected void finalizeCreatedTestInstance(Object testInstance) {
    // Do nothing by default
  }

  /**
   * If true, all test methods (across different TestMethodProcessors) will be sorted in a
   * deterministic way.
   *
   * <p>Deterministic means that the order will not change, even when tests are added/removed or
   * between releases.
   *
   * @deprecated Override {@link #sortTestMethods} with preferred sorting strategy.
   */
  @Deprecated
  protected boolean shouldSortTestMethodsDeterministically() {
    return false; // Don't sort methods by default
  }

  /**
   * Sort test methods (across different TestMethodProcessors).
   *
   * <p>This should be deterministic. The order should not change, even when tests are added/removed
   * or between releases.
   */
  protected Stream<FrameworkMethod> sortTestMethods(Stream<FrameworkMethod> methods) {
    if (!shouldSortTestMethodsDeterministically()) {
      return methods;
    }

    return methods.sorted(
        comparing((FrameworkMethod method) -> method.getName().hashCode())
            .thenComparing(FrameworkMethod::getName));
  }

  /**
   * Returns classes used as annotations to indicate test methods.
   *
   * <p>Defaults to {@link Test}.
   */
  protected ImmutableList<Class<? extends Annotation>> getSupportedTestAnnotations() {
    return ImmutableList.of(Test.class);
  }

  /**
   * {@link TestRule}s that will be executed after the ones defined in the test class (but still
   * before all {@link MethodRule}s). This is meant to be overridden by subclasses.
   */
  protected List<TestRule> getInnerTestRules() {
    return ImmutableList.of();
  }

  /**
   * {@link TestRule}s that will be executed before the ones defined in the test class. This is
   * meant to be overridden by subclasses.
   */
  protected List<TestRule> getOuterTestRules() {
    return ImmutableList.of();
  }

  /**
   * {@link MethodRule}s that will be executed after the ones defined in the test class. This is
   * meant to be overridden by subclasses.
   */
  protected List<MethodRule> getInnerMethodRules() {
    return ImmutableList.of();
  }

  /**
   * {@link MethodRule}s that will be executed before the ones defined in the test class (but still
   * after all {@link TestRule}s). This is meant to be overridden by subclasses.
   */
  protected List<MethodRule> getOuterMethodRules() {
    return ImmutableList.of();
  }

  /**
   * Runs a {@code testClass} with the {@link PluggableTestRunner}, and returns a list of test
   * {@link Failure}, or an empty list if no failure occurred.
   */
  @VisibleForTesting
  public static ImmutableList<Failure> run(PluggableTestRunner testRunner) throws Exception {
    final ImmutableList.Builder<Failure> failures = ImmutableList.builder();
    RunNotifier notifier = new RunNotifier();
    notifier.addFirstListener(
        new RunListener() {
          @Override
          public void testFailure(Failure failure) throws Exception {
            failures.add(failure);
          }
        });
    testRunner.run(notifier);
    return failures.build();
  }

  @Override
  protected final ImmutableList<FrameworkMethod> computeTestMethods() {
    Stream<FrameworkMethod> processedMethods =
        getSupportedTestAnnotations().stream()
            .flatMap(annotation -> getTestClass().getAnnotatedMethods(annotation).stream())
            .flatMap(method -> processMethod(method).stream());

    processedMethods = sortTestMethods(processedMethods);

    return processedMethods.collect(toImmutableList());
  }

  /** Implementation of a JUnit FrameworkMethod where the name and annotation list is overridden. */
  private static class OverriddenFrameworkMethod extends FrameworkMethod {

    private final TestInfo testInfo;

    public OverriddenFrameworkMethod(Method method, TestInfo testInfo) {
      super(method);
      this.testInfo = testInfo;
    }

    public TestInfo getTestInfo() {
      return testInfo;
    }

    @Override
    public String getName() {
      return testInfo.getName();
    }

    @Override
    public Annotation[] getAnnotations() {
      List<Annotation> annotations = testInfo.getAnnotations();
      return annotations.toArray(new Annotation[0]);
    }

    @Override
    public <T extends Annotation> T getAnnotation(final Class<T> annotationClass) {
      return testInfo.getAnnotation(annotationClass);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof PluggableTestRunner.OverriddenFrameworkMethod)) {
        return false;
      }

      OverriddenFrameworkMethod other = (OverriddenFrameworkMethod) obj;
      return super.equals(other) && other.testInfo.equals(testInfo);
    }

    @Override
    public int hashCode() {
      return super.hashCode() * 37 + testInfo.hashCode();
    }
  }

  private ImmutableList<FrameworkMethod> processMethod(FrameworkMethod initialMethod) {
    return getTestMethodProcessors()
        .calculateTestInfos(initialMethod.getMethod(), getTestClass().getJavaClass())
        .stream()
        .map(testInfo -> new OverriddenFrameworkMethod(testInfo.getMethod(), testInfo))
        .collect(toImmutableList());
  }

  // Note: This is a copy of the parent implementation, except that instead of calling
  // #createTest(), this method calls #createTestForMethod(method).
  @Override
  protected final Statement methodBlock(final FrameworkMethod method) {
    Object testObject;
    try {
      testObject =
          new ReflectiveCallable() {
            @Override
            protected Object runReflectiveCall() throws Throwable {
              return createTestForMethod(method);
            }
          }.run();
    } catch (Throwable e) {
      return new Fail(e);
    }

    Statement statement = methodInvoker(method, testObject);
    statement = possiblyExpectingExceptions(method, testObject, statement);
    statement = withPotentialTimeout(method, testObject, statement);
    statement = withBefores(method, testObject, statement);
    statement = withAfters(method, testObject, statement);
    statement = withRules(method, testObject, statement);
    return statement;
  }

  @Override
  protected final Statement methodInvoker(FrameworkMethod frameworkMethod, Object testObject) {
    TestInfo testInfo = ((OverriddenFrameworkMethod) frameworkMethod).getTestInfo();

    if (testInfo.getMethod().getParameterTypes().length == 0) {
      return super.methodInvoker(frameworkMethod, testObject);
    } else {
      List<Object> parameters = getTestMethodProcessors().getTestMethodParameters(testInfo);
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          frameworkMethod.invokeExplosively(testObject, parameters.toArray());
        }
      };
    }
  }

  /** Modifies the statement with each {@link MethodRule} and {@link TestRule} */
  private Statement withRules(FrameworkMethod method, Object target, Statement statement) {
    ImmutableList<TestRule> testRules =
        Stream.of(
                getInnerTestRules().stream(),
                getTestRules(target).stream(),
                getOuterTestRules().stream())
            .flatMap(x -> x)
            .collect(toImmutableList());

    Iterable<MethodRule> methodRules =
        Iterables.concat(
            Lists.reverse(getInnerMethodRules()),
            rules(target),
            Lists.reverse(getOuterMethodRules()));
    for (MethodRule methodRule : methodRules) {
      // For rules that implement both TestRule and MethodRule, only apply the TestRule.
      if (!testRules.contains(methodRule)) {
        statement = methodRule.apply(statement, method, target);
      }
    }
    Description testDescription = describeChild(method);
    for (TestRule testRule : testRules) {
      statement = testRule.apply(statement, testDescription);
    }
    return new ContextMethodRule().apply(statement, method, target);
  }

  private Object createTestForMethod(FrameworkMethod method) throws Exception {
    TestInfo testInfo = ((OverriddenFrameworkMethod) method).getTestInfo();
    Constructor<?> constructor = getTestClass().getOnlyConstructor();

    // Construct a test instance
    Object testInstance;
    if (constructor.getParameterTypes().length == 0) {
      testInstance = createTest();
    } else {
      List<Object> constructorParameters =
          getTestMethodProcessors().getConstructorParameters(constructor, testInfo);
      try {
        testInstance = constructor.newInstance(constructorParameters.toArray());
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }

    // Run all post processors on the newly created instance
    getTestMethodProcessors().postProcessTestInstance(testInstance, testInfo);

    finalizeCreatedTestInstance(testInstance);

    return testInstance;
  }

  @Override
  protected final void validateZeroArgConstructor(List<Throwable> errorsReturned) {
    ExecutableValidationResult validationResult =
        getTestMethodProcessors().validateConstructor(getTestClass().getOnlyConstructor());

    if (validationResult.wasValidated()) {
      errorsReturned.addAll(validationResult.validationErrors());
    } else {
      super.validateZeroArgConstructor(errorsReturned);
    }
  }

  @Override
  protected final void validateTestMethods(List<Throwable> errorsReturned) {
    List<FrameworkMethod> testMethods =
        getSupportedTestAnnotations().stream()
            .flatMap(annotation -> getTestClass().getAnnotatedMethods(annotation).stream())
            .collect(toImmutableList());
    for (FrameworkMethod testMethod : testMethods) {
      ExecutableValidationResult validationResult =
          getTestMethodProcessors()
              .validateTestMethod(testMethod.getMethod(), getTestClass().getJavaClass());

      if (Modifier.isStatic(testMethod.getMethod().getModifiers())) {
        errorsReturned.add(
            new Exception(String.format("Method %s() should not be static", testMethod.getName())));
      }
      if (!Modifier.isPublic(testMethod.getMethod().getModifiers())) {
        errorsReturned.add(
            new Exception(String.format("Method %s() should be public", testMethod.getName())));
      }

      if (validationResult.wasValidated()) {
        errorsReturned.addAll(validationResult.validationErrors());
      } else {
        testMethod.validatePublicVoidNoArg(/* isStatic= */ false, errorsReturned);
      }
    }
  }

  // Fix for ParentRunner bug:
  // Overriding this method because a superclass (ParentRunner) is calling this in its constructor
  // and then throwing an InitializationError that doesn't have any of the causes in the exception
  // message.
  @Override
  protected final void collectInitializationErrors(List<Throwable> errors) {
    super.collectInitializationErrors(errors);
    if (!errors.isEmpty()) {
      throw new RuntimeException(
          String.format(
              "Found %s issues while initializing the test runner:\n\n  - %s\n\n\n",
              errors.size(),
              errors.stream()
                  .map(Throwables::getStackTraceAsString)
                  .collect(joining("\n\n\n  - "))));
    }
  }

  // Override this test as final because it is not (always) invoked
  @Override
  protected final Object createTest() throws Exception {
    return super.createTest();
  }

  // Override this test as final because it is not (always) invoked
  @Override
  protected final void validatePublicVoidNoArgMethods(
      Class<? extends Annotation> annotation, boolean isStatic, List<Throwable> errors) {
    super.validatePublicVoidNoArgMethods(annotation, isStatic, errors);
  }

  private synchronized TestMethodProcessorList getTestMethodProcessors() {
    if (testMethodProcessors == null) {
      testMethodProcessors = createTestMethodProcessorList();
    }
    return testMethodProcessors;
  }

  /** {@link MethodRule} that sets up the Context for each test. */
  private static class ContextMethodRule implements MethodRule {
    @Override
    public Statement apply(Statement statement, FrameworkMethod method, Object o) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          currentTestInfo.set(((OverriddenFrameworkMethod) method).getTestInfo());
          try {
            statement.evaluate();
          } finally {
            currentTestInfo.set(null);
          }
        }
      };
    }
  }

  private static <E> Collector<E, ?, ImmutableList<E>> toImmutableList() {
    return Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf);
  }
}
