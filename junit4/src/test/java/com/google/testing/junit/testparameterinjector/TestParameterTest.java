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
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.stream.Collectors.joining;

import com.google.common.base.CharMatcher;
import com.google.common.base.Throwables;
import com.google.testing.junit.testparameterinjector.TestParameter.TestParameterValuesProvider;
import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
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
  public static class AnnotatedField {
    private static List<TestEnum> testedParameters;

    @TestParameter TestEnum enumParameter;

    @BeforeClass
    public static void initializeStaticFields() {
      assertWithMessage("Expecting this class to be run only once").that(testedParameters).isNull();
      testedParameters = new ArrayList<>();
    }

    @Test
    public void test() {
      testedParameters.add(enumParameter);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters).containsExactly(TestEnum.ONE, TestEnum.TWO, TestEnum.THREE);
    }
  }

  @RunAsTest
  public static class AnnotatedConstructorParameter {
    private static List<String> testedParameters;

    private final TestEnum constructorEnum;

    public AnnotatedConstructorParameter(@TestParameter TestEnum constructorEnum) {
      this.constructorEnum = constructorEnum;
    }

    @TestParameter TestEnum fieldEnum;

    @BeforeClass
    public static void initializeStaticFields() {
      assertWithMessage("Expecting this class to be run only once").that(testedParameters).isNull();
      testedParameters = new ArrayList<>();
    }

    @Test
    public void test() {
      testedParameters.add(String.format("%s:%s", fieldEnum, constructorEnum));
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters)
          .containsExactly(
              "ONE:ONE",
              "ONE:TWO",
              "ONE:THREE",
              "TWO:ONE",
              "TWO:TWO",
              "TWO:THREE",
              "THREE:ONE",
              "THREE:TWO",
              "THREE:THREE");
    }
  }

  @RunAsTest
  public static class MultipleAnnotatedParameters {
    private static List<String> testedParameters;

    @BeforeClass
    public static void initializeStaticFields() {
      assertWithMessage("Expecting this class to be run only once").that(testedParameters).isNull();
      testedParameters = new ArrayList<>();
    }

    @Test
    public void test(
        @TestParameter TestEnum enumParameterA,
        @TestParameter({"TWO", "THREE"}) TestEnum enumParameterB,
        @TestParameter(exclude = {"TWO", "THREE"}) TestEnum enumParameterC,
        @TestParameter({"!!binary 'ZGF0YQ=='", "data2"}) byte[] bytes) {
      testedParameters.add(
          String.format("%s:%s:%s:%s", enumParameterA, enumParameterB, enumParameterC, new String(bytes)));
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters)
          .containsExactly(
              "ONE:TWO:ONE:data",
              "ONE:THREE:ONE:data",
              "TWO:TWO:ONE:data",
              "TWO:THREE:ONE:data",
              "THREE:TWO:ONE:data",
              "THREE:THREE:ONE:data",
              "ONE:TWO:ONE:data2",
              "ONE:THREE:ONE:data2",
              "TWO:TWO:ONE:data2",
              "TWO:THREE:ONE:data2",
              "THREE:TWO:ONE:data2",
              "THREE:THREE:ONE:data2");
    }
  }

  @RunAsTest
  public static class WithValuesProvider {
    private static List<Object> testedParameters;

    @BeforeClass
    public static void initializeStaticFields() {
      assertWithMessage("Expecting this class to be run only once").that(testedParameters).isNull();
      testedParameters = new ArrayList<>();
    }

    @Test
    public void stringTest(
        @TestParameter(valuesProvider = TestStringProvider.class) String string) {
      testedParameters.add(string);
    }

    @Test
    public void charMatcherTest(
        @TestParameter(valuesProvider = CharMatcherProvider.class) CharMatcher charMatcher) {
      testedParameters.add(charMatcher);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters)
          .containsExactly(
              "A", "B", null, CharMatcher.any(), CharMatcher.ascii(), CharMatcher.whitespace());
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
    List<Failure> failures =
        PluggableTestRunner.run(
            new PluggableTestRunner(testClass) {
              @Override
              protected TestMethodProcessorList createTestMethodProcessorList() {
                return TestMethodProcessorList.createNewParameterizedProcessors();
              }
            });

    assertNoFailures(failures);
  }

  private static void assertNoFailures(List<Failure> failures) {
    if (failures.size() == 1) {
      throw new AssertionError(getOnlyElement(failures).getException());
    } else if (failures.size() > 1) {
      throw new AssertionError(
          String.format(
              "Test failed unexpectedly:\n\n%s",
              failures.stream()
                  .map(
                      f ->
                          String.format(
                              "<<%s>> %s",
                              f.getDescription(),
                              Throwables.getStackTraceAsString(f.getException())))
                  .collect(joining("\n------------------------------------\n"))));
    }
  }
}
