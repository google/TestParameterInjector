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
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.testing.junit.testparameterinjector.TestMethodProcessor.ValidationResult;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
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

  private ImmutableList<TestRule> testRules;
  private List<TestMethodProcessor> testMethodProcessors;

  protected PluggableTestRunner(Class<?> klass) throws InitializationError {
    super(klass);
  }

  /**
   * Returns the list of {@link TestMethodProcessor}s to use. This is meant to be overridden by
   * subclasses.
   */
  protected abstract List<TestMethodProcessor> createTestMethodProcessorList();

  /**
   * This method is run to perform optional additional operations on the test instance, right after
   * it was created.
   */
  protected void finalizeCreatedTestInstance(Object testInstance) {
    // Do nothing by default
  }

  /**
   * If true, all test methods (across different TestMethodProcessors) will be sorted in a
   * deterministic way by their test name.
   *
   * <p>Deterministic means that the order will not change, even when tests are added/removed or
   * between releases.
   */
  protected boolean shouldSortTestMethodsDeterministically() {
    return false; // Don't sort methods by default
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
        super.computeTestMethods().stream().flatMap(method -> processMethod(method).stream());

    if (shouldSortTestMethodsDeterministically()) {
      processedMethods =
          processedMethods.sorted(
              comparing((FrameworkMethod method) -> method.getName().hashCode())
                  .thenComparing(FrameworkMethod::getName));
    }

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
    ImmutableList<FrameworkMethod> methods = ImmutableList.of(initialMethod);
    for (final TestMethodProcessor testMethodProcessor : getTestMethodProcessors()) {
      methods =
          methods.stream()
              .flatMap(
                  method -> {
                    TestInfo originalTest =
                        TestInfo.create(
                            method.getMethod(),
                            method.getName(),
                            ImmutableList.copyOf(method.getAnnotations()));
                    List<TestInfo> processedTests =
                        testMethodProcessor.processTest(
                            getTestClass().getJavaClass(), originalTest);

                    return processedTests.stream()
                        .map(
                            processedTest ->
                                new OverriddenFrameworkMethod(method.getMethod(), processedTest));
                  })
              .collect(toImmutableList());
    }
    return methods;
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
    Optional<Statement> statement = Optional.absent();
    for (TestMethodProcessor testMethodProcessor : getTestMethodProcessors()) {
      statement =
          testMethodProcessor.createStatement(
              getTestClass(), frameworkMethod, testObject, statement);
    }
    if (statement.isPresent()) {
      return statement.get();
    }
    return super.methodInvoker(frameworkMethod, testObject);
  }

  /** Modifies the statement with each {@link MethodRule} and {@link TestRule} */
  private Statement withRules(FrameworkMethod method, Object target, Statement statement) {
    ImmutableList<TestRule> testRules =
        Stream.of(
                getTestRulesForProcessors().stream(),
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
    Optional<Object> maybeTestInstance = Optional.absent();
    for (TestMethodProcessor testMethodProcessor : getTestMethodProcessors()) {
      maybeTestInstance = testMethodProcessor.createTest(getTestClass(), method, maybeTestInstance);
    }
    // If no processor created the test instance, fallback on the default implementation.
    Object testInstance =
        maybeTestInstance.isPresent() ? maybeTestInstance.get() : super.createTest();

    finalizeCreatedTestInstance(testInstance);

    return testInstance;
  }

  @Override
  protected final void validateZeroArgConstructor(List<Throwable> errorsReturned) {
    for (TestMethodProcessor testMethodProcessor : getTestMethodProcessors()) {
      if (testMethodProcessor.validateConstructor(getTestClass(), errorsReturned)
          == ValidationResult.HANDLED) {
        return;
      }
    }
    super.validateZeroArgConstructor(errorsReturned);
  }

  @Override
  protected final void validateTestMethods(List<Throwable> list) {
    List<FrameworkMethod> testMethods = getTestClass().getAnnotatedMethods(Test.class);
    for (FrameworkMethod testMethod : testMethods) {
      boolean isHandled = false;
      for (TestMethodProcessor testMethodProcessor : getTestMethodProcessors()) {
        if (testMethodProcessor.validateTestMethod(getTestClass(), testMethod, list)
            == ValidationResult.HANDLED) {
          isHandled = true;
          break;
        }
      }
      if (!isHandled) {
        testMethod.validatePublicVoidNoArg(false /* isStatic */, list);
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

  private synchronized List<TestMethodProcessor> getTestMethodProcessors() {
    if (testMethodProcessors == null) {
      testMethodProcessors = createTestMethodProcessorList();
    }
    return testMethodProcessors;
  }

  private synchronized ImmutableList<TestRule> getTestRulesForProcessors() {
    if (testRules == null) {
      testRules =
          testMethodProcessors.stream()
              .map(testMethodProcessor -> (TestRule) testMethodProcessor::processStatement)
              .collect(toImmutableList());
    }
    return testRules;
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
