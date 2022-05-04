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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.SharedTestUtilitiesJUnit4.SuccessfulTestCaseBase;
import com.google.testing.junit.testparameterinjector.TestParameter.TestParameterValuesProvider;
import java.lang.annotation.Retention;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Test class to test the @TestParameter's value provider. */
@RunWith(Parameterized.class)
public class TestParameterTest {

  @Retention(RUNTIME)
  @interface RunAsTest {}

  public enum TestEnum {
    ONE,
    TWO,
    THREE,
  }

  @RunAsTest
  public static class AnnotatedField extends SuccessfulTestCaseBase {

    @TestParameter TestEnum enumParameter;

    @Test
    public void test() {
      storeTestParametersForThisTest(enumParameter);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test[ONE]", "ONE")
          .put("test[TWO]", "TWO")
          .put("test[THREE]", "THREE")
          .build();
    }
  }

  @RunAsTest
  public static class AnnotatedConstructorParameter extends SuccessfulTestCaseBase {

    private final TestEnum constructorEnum;

    @TestParameter TestEnum fieldEnum;

    public AnnotatedConstructorParameter(@TestParameter TestEnum constructorEnum) {
      this.constructorEnum = constructorEnum;
    }

    @Test
    public void test() {
      storeTestParametersForThisTest(fieldEnum, constructorEnum);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test[ONE,ONE]", "ONE:ONE")
          .put("test[ONE,TWO]", "ONE:TWO")
          .put("test[ONE,THREE]", "ONE:THREE")
          .put("test[TWO,ONE]", "TWO:ONE")
          .put("test[TWO,TWO]", "TWO:TWO")
          .put("test[TWO,THREE]", "TWO:THREE")
          .put("test[THREE,ONE]", "THREE:ONE")
          .put("test[THREE,TWO]", "THREE:TWO")
          .put("test[THREE,THREE]", "THREE:THREE")
          .build();
    }
  }

  @RunAsTest
  public static class MultipleAnnotatedParameters extends SuccessfulTestCaseBase {

    @Test
    public void test(
        @TestParameter TestEnum enumParameterA,
        @TestParameter({"TWO", "THREE"}) TestEnum enumParameterB,
        @TestParameter({"!!binary 'ZGF0YQ=='", "data2"}) byte[] bytes) {
      storeTestParametersForThisTest(enumParameterA, enumParameterB, new String(bytes));
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test[ONE,TWO,[100, 97, 116, 97]]", "ONE:TWO:data")
          .put("test[ONE,TWO,[100, 97, 116, 97, 50]]", "ONE:TWO:data2")
          .put("test[ONE,THREE,[100, 97, 116, 97]]", "ONE:THREE:data")
          .put("test[ONE,THREE,[100, 97, 116, 97, 50]]", "ONE:THREE:data2")
          .put("test[TWO,TWO,[100, 97, 116, 97]]", "TWO:TWO:data")
          .put("test[TWO,TWO,[100, 97, 116, 97, 50]]", "TWO:TWO:data2")
          .put("test[TWO,THREE,[100, 97, 116, 97]]", "TWO:THREE:data")
          .put("test[TWO,THREE,[100, 97, 116, 97, 50]]", "TWO:THREE:data2")
          .put("test[THREE,TWO,[100, 97, 116, 97]]", "THREE:TWO:data")
          .put("test[THREE,TWO,[100, 97, 116, 97, 50]]", "THREE:TWO:data2")
          .put("test[THREE,THREE,[100, 97, 116, 97]]", "THREE:THREE:data")
          .put("test[THREE,THREE,[100, 97, 116, 97, 50]]", "THREE:THREE:data2")
          .build();
    }
  }

  @RunAsTest
  public static class WithValuesProvider extends SuccessfulTestCaseBase {

    @Test
    public void stringTest(
        @TestParameter(valuesProvider = TestStringProvider.class) String string) {
      storeTestParametersForThisTest(string);
    }

    @Test
    public void charMatcherTest(
        @TestParameter(valuesProvider = CharMatcherProvider.class) CharMatcher charMatcher) {
      storeTestParametersForThisTest(charMatcher);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("stringTest[A]", "A")
          .put("stringTest[B]", "B")
          .put("stringTest[null]", "null")
          .put("charMatcherTest[CharMatcher.any()]", "CharMatcher.any()")
          .put("charMatcherTest[CharMatcher.ascii()]", "CharMatcher.ascii()")
          .put("charMatcherTest[CharMatcher.whitespace()]", "CharMatcher.whitespace()")
          .build();
    }

    private static final class TestStringProvider implements TestParameterValuesProvider {
      @Override
      public List<String> provideValues() {
        return newArrayList("A", "B", null);
      }
    }

    private static final class CharMatcherProvider implements TestParameterValuesProvider {
      @Override
      public List<CharMatcher> provideValues() {
        return newArrayList(CharMatcher.any(), CharMatcher.ascii(), CharMatcher.whitespace());
      }
    }
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return Arrays.stream(TestParameterTest.class.getClasses())
        .filter(cls -> cls.isAnnotationPresent(RunAsTest.class))
        .map(cls -> new Object[] {cls.getSimpleName(), cls})
        .collect(toImmutableList());
  }

  private final Class<?> testClass;

  public TestParameterTest(String name, Class<?> testClass) {
    this.testClass = testClass;
  }

  @Test
  public void test() throws Exception {
    SharedTestUtilitiesJUnit4.runTestsAndAssertNoFailures(
        new PluggableTestRunner(testClass) {
          @Override
          protected TestMethodProcessorList createTestMethodProcessorList() {
            return TestMethodProcessorList.createNewParameterizedProcessors();
          }
        });
  }
}
