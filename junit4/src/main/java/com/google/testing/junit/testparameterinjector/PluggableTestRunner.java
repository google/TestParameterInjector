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

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.internal.runners.model.ReflectiveCallable;
import org.junit.internal.runners.statements.Fail;
import org.junit.internal.runners.statements.FailOnTimeout;
import org.junit.rules.MethodRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.MemberValueConsumer;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

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
  protected ImmutableList<FrameworkMethod> sortTestMethods(ImmutableList<FrameworkMethod> methods) {
    if (!shouldSortTestMethodsDeterministically()) {
      return methods;
    }
    return FluentIterable.from(methods)
        .toSortedList(
            (o1, o2) ->
                ComparisonChain.start()
                    .compare(o1.getName().hashCode(), o2.getName().hashCode())
                    .compare(o1.getName(), o2.getName())
                    .result());
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
   * {@link TestRule}s that will be executed before the ones defined in the test class. This is
   * meant to be overridden by subclasses.
   */
  protected List<TestRule> getExtraTestRules() {
    return ImmutableList.of();
  }

  @Override
  protected final ImmutableList<FrameworkMethod> computeTestMethods() {
    return sortTestMethods(
        FluentIterable.from(getSupportedTestAnnotations())
            .transformAndConcat(annotation -> getTestClass().getAnnotatedMethods(annotation))
            .transformAndConcat(this::processMethod)
            .toList());
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
    return FluentIterable.from(
            getTestMethodProcessors()
                .calculateTestInfos(initialMethod.getMethod(), getTestClass().getJavaClass()))
        .transform(
            testInfo ->
                (FrameworkMethod) new OverriddenFrameworkMethod(testInfo.getMethod(), testInfo))
        .toList();
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
    statement = withPotentialTimeoutInternal(method, testObject, statement);
    statement = withBefores(method, testObject, statement);
    statement = withAfters(method, testObject, statement);
    statement = withRules(method, testObject, statement);
    return statement;
  }

  // Note: This does the same as BlockJUnit4ClassRunner.withPotentialTimeout(), which is deprecated
  // and will soon be private.
  private Statement withPotentialTimeoutInternal(
      FrameworkMethod method, Object test, Statement next) {
    Test testAnnotation = method.getAnnotation(Test.class);
    if (testAnnotation == null) {
      return next;
    } else if (testAnnotation.timeout() <= 0) {
      return next;
    } else {
      return FailOnTimeout.builder()
          .withTimeout(testAnnotation.timeout(), TimeUnit.MILLISECONDS)
          .build(next);
    }
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
    Description testDescription = describeChild(method);
    TestClass testClass = getTestClass();

    LinkedListMultimap<Integer, Object> orderToRulesMultimap = LinkedListMultimap.create();
    MemberValueConsumer<Object> collector =
        (frameworkMember, rule) -> {
          Rule ruleAnnotation = frameworkMember.getAnnotation(Rule.class);
          int order = ruleAnnotation == null ? Rule.DEFAULT_ORDER : ruleAnnotation.order();
          if (orderToRulesMultimap.containsValue(rule)
              && rule instanceof MethodRule
              && rule instanceof TestRule) {
            // This rule was already added because it is both a MethodRule and a TestRule.
            // For legacy reasons, we need to put the new rule at the end of the list.
            orderToRulesMultimap.remove(order, rule);
          }
          orderToRulesMultimap.put(order, rule);
        };

    testClass.collectAnnotatedMethodValues(target, Rule.class, MethodRule.class, collector::accept);
    testClass.collectAnnotatedFieldValues(target, Rule.class, MethodRule.class, collector::accept);
    testClass.collectAnnotatedMethodValues(target, Rule.class, TestRule.class, collector::accept);
    testClass.collectAnnotatedFieldValues(target, Rule.class, TestRule.class, collector::accept);

    ArrayList<Integer> keys = new ArrayList<>(orderToRulesMultimap.keySet());
    Collections.sort(keys);
    ImmutableList<Object> orderedRules =
        FluentIterable.from(keys)
            .transformAndConcat(
                // Execute the rules in the reverse order of when the fields occurred. This may look
                // counter-intuitive, but that is what the default JUnit4 runner does, and there is
                // no reason to deviate from that here.
                key -> Lists.reverse(orderToRulesMultimap.get(key)))
            .toList();

    // Note: The perceived order* is the reverse of the order in which the code below applies the
    // rules to the statements because each subsequent rule wraps the previous statement.
    //
    // [*] The rule implementation can add its logic both before or after the base statement, so the
    // order depends on the rule implementation. If all rules put their logic before the base
    // statement, the order matches that of `orderedRules`.

    for (Object rule : Lists.reverse(orderedRules)) {
      if (rule instanceof TestRule) {
        statement = ((TestRule) rule).apply(statement, testDescription);
      } else if (rule instanceof MethodRule) {
        statement = ((MethodRule) rule).apply(statement, method, target);
      } else {
        throw new AssertionError(rule);
      }
    }

    // Apply extra rules
    for (TestRule testRule : getExtraTestRules()) {
      statement = testRule.apply(statement, testDescription);
    }
    statement = new ContextMethodRule().apply(statement, method, target);

    return statement;
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
        FluentIterable.from(getSupportedTestAnnotations())
            .transformAndConcat(annotation -> getTestClass().getAnnotatedMethods(annotation))
            .toList();
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
              FluentIterable.from(errors)
                  .transform(Throwables::getStackTraceAsString)
                  .join(Joiner.on("\n\n\n  - "))));
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
}
