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

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
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

  private static ArrayList<String> ruleInvocations;
  private static int testMethodInvocationCount;
  private static List<String> testOrder;

  @Before
  public void setUp() {
    ruleInvocations = new ArrayList<>();
    testMethodInvocationCount = 0;
    testOrder = new ArrayList<>();
  }

  @Retention(RetentionPolicy.RUNTIME)
  private @interface CustomTest {}

  static class TestAndMethodRule implements MethodRule, TestRule {
    private final String name;

    TestAndMethodRule() {
      this("DEFAULT_NAME");
    }

    TestAndMethodRule(String name) {
      this.name = name;
    }

    @Override
    public Statement apply(Statement base, Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          ruleInvocations.add(name);
          base.evaluate();
        }
      };
    }

    @Override
    public Statement apply(Statement base, FrameworkMethod method, Object target) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          ruleInvocations.add(name);
          base.evaluate();
        }
      };
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
    SharedTestUtilitiesJUnit4.runTestsAndAssertNoFailures(
        new PluggableTestRunner(TestAndMethodRuleTestClass.class) {
          @Override
          protected TestMethodProcessorList createTestMethodProcessorList() {
            return TestMethodProcessorList.empty();
          }
        });

    assertThat(ruleInvocations).hasSize(1);
  }

  @RunWith(PluggableTestRunner.class)
  public static class RuleOrderingTestClassWithExplicitOrder {

    @Rule(order = 3)
    public TestAndMethodRule ruleA = new TestAndMethodRule("A");

    @Rule(order = 1)
    public TestAndMethodRule ruleB = new TestAndMethodRule("B");

    @Rule(order = 2)
    public TestAndMethodRule ruleC = new TestAndMethodRule("C");

    @Test
    public void test() {
      // no-op
    }
  }

  @Test
  public void rulesAreSortedCorrectly_withExplicitOrder() throws Exception {
    SharedTestUtilitiesJUnit4.runTestsAndAssertNoFailures(
        new PluggableTestRunner(RuleOrderingTestClassWithExplicitOrder.class) {
          @Override
          protected TestMethodProcessorList createTestMethodProcessorList() {
            return TestMethodProcessorList.empty();
          }
        });

    assertThat(ruleInvocations).containsExactly("B", "C", "A").inOrder();
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
    SharedTestUtilitiesJUnit4.runTestsAndAssertNoFailures(
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
    SharedTestUtilitiesJUnit4.runTestsAndAssertNoFailures(
        new PluggableTestRunner(SortedPluggableTestRunnerTestClass.class) {
          @Override
          protected TestMethodProcessorList createTestMethodProcessorList() {
            return TestMethodProcessorList.empty();
          }

          @Override
          protected ImmutableList<FrameworkMethod> sortTestMethods(
              ImmutableList<FrameworkMethod> methods) {
            return FluentIterable.from(methods)
                .toSortedList((o1, o2) -> o2.getName().compareTo(o1.getName())); // reversed
          }
        });
    assertThat(testOrder).containsExactly("c", "b", "a");
  }
}
