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

import com.google.common.collect.ImmutableList;
import java.util.List;
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

  private static int ruleInvocationCount = 0;

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
  public static class PluggableTestRunnerTestClass {

    @Rule public TestAndMethodRule rule = new TestAndMethodRule();

    @Test
    public void test() {
      // no-op
    }
  }

  @Test
  public void ruleThatIsBothTestRuleAndMethodRuleIsInvokedOnceOnly() throws Exception {
    PluggableTestRunner.run(
        new PluggableTestRunner(PluggableTestRunnerTestClass.class) {
          @Override
          protected List<TestMethodProcessor> createTestMethodProcessorList() {
            return ImmutableList.of();
          }
        });

    assertThat(ruleInvocationCount).isEqualTo(1);
  }
}
