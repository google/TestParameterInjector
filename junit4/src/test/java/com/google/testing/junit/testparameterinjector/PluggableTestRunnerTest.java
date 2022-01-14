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

import static com.google.common.truth.Truth.assertThat;
import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableList;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

@RunWith(JUnit4.class)
public class PluggableTestRunnerTest {
  @Retention(RetentionPolicy.RUNTIME)
  private static @interface CustomTest {}

  private static int ruleInvocationCount = 0;
  private static int testMethodInvocationCount = 0;

  public static class TestAndMethodRule implements MethodRule, TestRule {

    @Override
    public Statement apply(Statement base, Description description) {
      ruleInvocationCount++;
      return base;
    }

    @Override
    public Statement apply(Statement base, FrameworkMethod method, Object target) {
      ruleInvocationCount++;
      return base;
    }
  }

  @RunWith(PluggableTestRunner.class)
  public static class TestAndMethodRuleTestClass {

    @Rule public TestAndMethodRule rule = new TestAndMethodRule();

    @Test
    public void test() {
      // no-op
    }
  }

  @Test
  public void ruleThatIsBothTestRuleAndMethodRuleIsInvokedOnceOnly() throws Exception {
    PluggableTestRunner.run(
        new PluggableTestRunner(TestAndMethodRuleTestClass.class) {
          @Override
          protected TestMethodProcessorList createTestMethodProcessorList() {
            return TestMethodProcessorList.empty();
          }
        });

    assertThat(ruleInvocationCount).isEqualTo(1);
  }

  @RunWith(PluggableTestRunner.class)
  public static class CustomTestAnnotationTestClass {
    @SuppressWarnings("JUnit4TestNotRun")
    @CustomTest
    public void customTestAnnotatedTest() {
      testMethodInvocationCount++;
    }

    @Test
    public void testAnnotatedTest() {
      testMethodInvocationCount++;
    }
  }

  @Test
  public void testMarkedWithCustomClassIsInvoked() throws Exception {
    testMethodInvocationCount = 0;
    PluggableTestRunner.run(
        new PluggableTestRunner(CustomTestAnnotationTestClass.class) {
          @Override
          protected TestMethodProcessorList createTestMethodProcessorList() {
            return TestMethodProcessorList.empty();
          }

          @Override
          protected ImmutableList<Class<? extends Annotation>> getSupportedTestAnnotations() {
            return ImmutableList.of(Test.class, CustomTest.class);
          }
        });

    assertThat(testMethodInvocationCount).isEqualTo(2);
  }

  private static final List<String> testOrder = new ArrayList<>();

  @RunWith(PluggableTestRunner.class)
  public static class SortedPluggableTestRunnerTestClass {
    @Test
    public void a() {
      testOrder.add("a");
    }

    @Test
    public void b() {
      testOrder.add("b");
    }

    @Test
    public void c() {
      testOrder.add("c");
    }
  }

  @Test
  public void testsAreSortedCorrectly() throws Exception {
    testOrder.clear();
    PluggableTestRunner.run(
        new PluggableTestRunner(SortedPluggableTestRunnerTestClass.class) {
          @Override
          protected TestMethodProcessorList createTestMethodProcessorList() {
            return TestMethodProcessorList.empty();
          }

          @Override
          protected Stream<FrameworkMethod> sortTestMethods(Stream<FrameworkMethod> methods) {
            return methods.sorted(comparing(FrameworkMethod::getName).reversed());
          }
        });
    assertThat(testOrder).containsExactly("c", "b", "a");
  }
}
