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
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameter.TestParameterValuesProvider;
import com.google.testing.junit.testparameterinjector.TestParameterAnnotationMethodProcessorTest.ErrorNonStaticProviderClass.NonStaticProvider;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.model.TestClass;

/**
 * Test class to test the PluggableTestRunner test harness works with {@link
 * TestParameterAnnotation}s.
 */
@RunWith(Parameterized.class)
public class TestParameterAnnotationMethodProcessorTest {

  @Retention(RUNTIME)
  @interface ClassTestResult {
    Result value();
  }

  enum Result {
    /**
     * A successful test run is expected in both for
     * TestParameterAnnotationMethodProcessor#forAllAnnotationPlacements and
     * TestParameterAnnotationMethodProcessor#onlyForFieldsAndParameters.
     */
    SUCCESS_ALWAYS,
    SUCCESS_FOR_ALL_PLACEMENTS_ONLY,
    FAILURE,
  }

  public enum TestEnum {
    UNDEFINED,
    ONE,
    TWO,
    THREE,
    FOUR,
    FIVE
  }

  @Retention(RUNTIME)
  @TestParameterAnnotation
  public @interface EnumParameter {
    TestEnum[] value() default {};
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class SingleAnnotationClass {

    private static List<TestEnum> testedParameters;

    @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
    TestEnum enumParameter;

    @BeforeClass
    public static void resetStaticState() {
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

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class MultipleAllEnumValuesAnnotationClass {

    private static List<String> testedParameters;

    @TestParameter TestEnum enumParameter1;

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @Test
    public void test(@TestParameter TestEnum enumParameter2) {
      testedParameters.add(enumParameter1 + ":" + enumParameter2);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters).hasSize(TestEnum.values().length * TestEnum.values().length);
    }
  }

  @ClassTestResult(Result.SUCCESS_FOR_ALL_PLACEMENTS_ONLY)
  public static class SingleParameterAnnotationClass {

    private static List<TestEnum> testedParameters;

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @Test
    @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
    public void test(TestEnum enumParameter) {
      testedParameters.add(enumParameter);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters).containsExactly(TestEnum.ONE, TestEnum.TWO, TestEnum.THREE);
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class SingleAnnotatedParameterAnnotationClass {

    private static List<TestEnum> testedParameters;

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @Test
    public void test(
        @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE}) TestEnum enumParameter) {
      testedParameters.add(enumParameter);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters).containsExactly(TestEnum.ONE, TestEnum.TWO, TestEnum.THREE);
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class AnnotatedSuperclassParameter {

    private static List<Object> testedParameters;

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @Test
    public void test(
        @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE}) Object enumParameter) {
      testedParameters.add(enumParameter);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters).containsExactly(TestEnum.ONE, TestEnum.TWO, TestEnum.THREE);
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class DuplicatedAnnotatedParameterAnnotationClass {

    private static List<ImmutableList<TestEnum>> testedParameters;

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @Test
    public void test(
        @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE}) TestEnum enumParameter,
        @EnumParameter({TestEnum.FOUR, TestEnum.FIVE}) TestEnum enumParameter2) {
      testedParameters.add(ImmutableList.of(enumParameter, enumParameter2));
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters)
          .containsExactly(
              ImmutableList.of(TestEnum.ONE, TestEnum.FOUR),
              ImmutableList.of(TestEnum.ONE, TestEnum.FIVE),
              ImmutableList.of(TestEnum.TWO, TestEnum.FOUR),
              ImmutableList.of(TestEnum.TWO, TestEnum.FIVE),
              ImmutableList.of(TestEnum.THREE, TestEnum.FOUR),
              ImmutableList.of(TestEnum.THREE, TestEnum.FIVE));
    }
  }

  @ClassTestResult(Result.FAILURE)
  public static class SingleAnnotatedParameterAnnotationClassWithMissingValue {

    private static List<TestEnum> testedParameters;

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @Test
    public void test(@EnumParameter TestEnum enumParameter) {
      testedParameters.add(enumParameter);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters).containsExactly(TestEnum.ONE, TestEnum.TWO, TestEnum.THREE);
    }
  }

  @ClassTestResult(Result.SUCCESS_FOR_ALL_PLACEMENTS_ONLY)
  public static class MultipleAnnotationTestClass {

    private static List<TestEnum> testedParameters;

    @EnumParameter({TestEnum.ONE, TestEnum.TWO})
    TestEnum enumParameter;

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @Test
    @EnumParameter({TestEnum.THREE})
    public void parameterized() {
      testedParameters.add(enumParameter);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters).containsExactly(TestEnum.THREE);
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class TooLongTestNamesShortened {

    @Rule public TestName testName = new TestName();

    private static List<String> allTestNames;

    @BeforeClass
    public static void resetStaticState() {
      allTestNames = new ArrayList<>();
    }

    @Test
    public void test1(
        @TestParameter({
              "ABC",
              "This is a very long string (240 characters) that would normally cause Sponge+Tin to"
                  + " exceed the filename limit of 255 characters."
                  + " ==========================================================================="
                  + "==================================="
            })
            String testString) {
      allTestNames.add(testName.getMethodName());
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(allTestNames)
          .containsExactly(
              "test1[1.ABC]",
              "test1[2.This is a very long string (240 characters) that would normally cause"
                  + " Sponge+Tin to exceed the filename limit of 255 characters."
                  + " =========================================================...]");
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class DuplicateTestNames {

    @Rule public TestName testName = new TestName();

    private static List<String> allTestNames;
    private static List<Object> allTestParameterValues;

    @BeforeClass
    public static void resetStaticState() {
      allTestNames = new ArrayList<>();
      allTestParameterValues = new ArrayList<>();
    }

    @Test
    public void test1(@TestParameter({"ABC", "ABC"}) String testString) {
      allTestNames.add(testName.getMethodName());
      allTestParameterValues.add(testString);
    }

    private static final class Test2Provider implements TestParameterValuesProvider {
      @Override
      public List<Object> provideValues() {
        return newArrayList(123, "123", "null", null);
      }
    }

    @Test
    public void test2(@TestParameter(valuesProvider = Test2Provider.class) Object testObject) {
      allTestNames.add(testName.getMethodName());
      allTestParameterValues.add(testObject);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(allTestNames)
          .containsExactly(
              "test1[1.ABC]",
              "test1[2.ABC]",
              "test2[123 (Integer)]",
              "test2[123 (String)]",
              "test2[null (String)]",
              "test2[null (null reference)]");
      assertThat(allTestParameterValues).containsExactly("ABC", "ABC", 123, "123", "null", null);
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class DuplicateFieldAnnotationTestClass {

    private static List<String> testedParameters;

    @TestParameter({"foo", "bar"})
    String stringParameter;

    @TestParameter({"baz", "qux"})
    String stringParameter2;

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @Test
    public void test() {
      testedParameters.add(stringParameter + ":" + stringParameter2);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters).containsExactly("foo:baz", "foo:qux", "bar:baz", "bar:qux");
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class DuplicateIdenticalFieldAnnotationTestClass {

    private static List<String> testedParameters;

    @TestParameter({"foo", "bar"})
    String stringParameter;

    @TestParameter({"foo", "bar"})
    String stringParameter2;

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @Test
    public void test() {
      testedParameters.add(stringParameter + ":" + stringParameter2);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters).containsExactly("foo:foo", "foo:bar", "bar:foo", "bar:bar");
    }
  }

  @ClassTestResult(Result.FAILURE)
  public static class ErrorDuplicateFieldAnnotationTestClass {

    @EnumParameter({TestEnum.ONE, TestEnum.TWO})
    TestEnum parameter1;

    @EnumParameter({TestEnum.THREE, TestEnum.FOUR})
    TestEnum parameter2;

    @Test
    @EnumParameter(TestEnum.FIVE)
    public void test() {}
  }

  @ClassTestResult(Result.FAILURE)
  public static class ErrorDuplicateFieldAndClassAnnotationTestClass {

    @EnumParameter({TestEnum.ONE, TestEnum.TWO})
    TestEnum parameter;

    @EnumParameter(TestEnum.FIVE)
    public ErrorDuplicateFieldAndClassAnnotationTestClass() {}

    @Test
    public void test() {}
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class SingleAnnotationTestClassWithAnnotation {

    private static List<TestEnum> testedParameters;

    @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
    TestEnum enumParameter;

    @BeforeClass
    public static void resetStaticState() {
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

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class MultipleAnnotationTestClassWithAnnotation {

    private static List<String> testedParameters;

    @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
    TestEnum enumParameter;

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @Test
    public void parameterized(@TestParameter({"foo", "bar"}) String stringParameter) {
      testedParameters.add(stringParameter + ":" + enumParameter);
    }

    @Test
    public void nonParameterized() {
      testedParameters.add("none:" + enumParameter);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters)
          .containsExactly(
              "none:ONE",
              "none:TWO",
              "none:THREE",
              "foo:ONE",
              "foo:TWO",
              "foo:THREE",
              "bar:ONE",
              "bar:TWO",
              "bar:THREE");
    }
  }

  public abstract static class BaseClassWithSingleTest {
    @Rule public TestName testName = new TestName();

    static List<String> allTestNames;

    @BeforeClass
    public static void resetStaticState() {
      allTestNames = new ArrayList<>();
    }

    @Test
    public void testInBase(@TestParameter boolean b) {
      allTestNames.add(testName.getMethodName());
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(allTestNames).containsExactly("testInBase[b=true]", "testInBase[b=false]");
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class SimpleTestInheritedFromBaseClass extends BaseClassWithSingleTest {}

  public abstract static class BaseClassWithAnnotations {
    @Rule public TestName testName = new TestName();

    static List<String> allTestNames;

    @TestParameter boolean boolInBase;

    @BeforeClass
    public static void resetStaticState() {
      allTestNames = new ArrayList<>();
    }

    @Before
    public void setUp() {
      assertThat(allTestNames).doesNotContain(testName.getMethodName());
    }

    @After
    public void tearDown() {
      assertThat(allTestNames).contains(testName.getMethodName());
    }

    @Test
    public void testInBase(@TestParameter({"ONE", "TWO"}) TestEnum enumInBase) {
      allTestNames.add(testName.getMethodName());
    }

    @Test
    public abstract void abstractTestInBase();

    @Test
    public void overridableTestInBase() {
      throw new UnsupportedOperationException("Expected the base class to override this");
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class AnnotationInheritedFromBaseClass extends BaseClassWithAnnotations {

    @TestParameter boolean boolInChild;

    @Test
    public void testInChild(@TestParameter({"TWO", "THREE"}) TestEnum enumInChild) {
      allTestNames.add(testName.getMethodName());
    }

    @Override
    public void abstractTestInBase() {
      allTestNames.add(testName.getMethodName());
    }

    @Override
    public void overridableTestInBase() {
      allTestNames.add(testName.getMethodName());
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(allTestNames)
          .containsExactly(
              "testInBase[boolInChild=false,boolInBase=false,ONE]",
              "testInBase[boolInChild=false,boolInBase=false,TWO]",
              "testInBase[boolInChild=false,boolInBase=true,ONE]",
              "testInBase[boolInChild=false,boolInBase=true,TWO]",
              "testInBase[boolInChild=true,boolInBase=false,ONE]",
              "testInBase[boolInChild=true,boolInBase=false,TWO]",
              "testInBase[boolInChild=true,boolInBase=true,ONE]",
              "testInBase[boolInChild=true,boolInBase=true,TWO]",
              "testInChild[boolInChild=false,boolInBase=false,TWO]",
              "testInChild[boolInChild=false,boolInBase=false,THREE]",
              "testInChild[boolInChild=false,boolInBase=true,TWO]",
              "testInChild[boolInChild=false,boolInBase=true,THREE]",
              "testInChild[boolInChild=true,boolInBase=false,TWO]",
              "testInChild[boolInChild=true,boolInBase=false,THREE]",
              "testInChild[boolInChild=true,boolInBase=true,TWO]",
              "testInChild[boolInChild=true,boolInBase=true,THREE]",
              "abstractTestInBase[boolInChild=false,boolInBase=false]",
              "abstractTestInBase[boolInChild=false,boolInBase=true]",
              "abstractTestInBase[boolInChild=true,boolInBase=false]",
              "abstractTestInBase[boolInChild=true,boolInBase=true]",
              "overridableTestInBase[boolInChild=false,boolInBase=false]",
              "overridableTestInBase[boolInChild=false,boolInBase=true]",
              "overridableTestInBase[boolInChild=true,boolInBase=false]",
              "overridableTestInBase[boolInChild=true,boolInBase=true]");
    }
  }

  @Retention(RUNTIME)
  @TestParameterAnnotation(validator = TestEnumValidator.class)
  public @interface EnumEvaluatorParameter {
    TestEnum[] value() default {};
  }

  public static class TestEnumValidator implements TestParameterValidator {

    @Override
    public boolean shouldSkip(Context context) {
      return context.has(EnumEvaluatorParameter.class, TestEnum.THREE);
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class MethodEvaluatorClass {

    private static List<TestEnum> testedParameters;

    @Test
    public void test(
        @EnumEvaluatorParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE}) TestEnum value) {
      if (value == TestEnum.THREE) {
        fail();
      } else {
        testedParameters.add(value);
      }
    }

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters).containsExactly(TestEnum.ONE, TestEnum.TWO);
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class FieldEvaluatorClass {

    private static List<TestEnum> testedParameters;

    @EnumEvaluatorParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
    TestEnum value;

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @Test
    public void test() {
      if (value == TestEnum.THREE) {
        fail();
      } else {
        testedParameters.add(value);
      }
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters).containsExactly(TestEnum.ONE, TestEnum.TWO);
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class ConstructorClass {

    private static List<TestEnum> testedParameters;
    final TestEnum enumParameter;

    public ConstructorClass(
        @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE}) TestEnum enumParameter) {
      this.enumParameter = enumParameter;
    }

    @BeforeClass
    public static void resetStaticState() {
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

  @ClassTestResult(Result.SUCCESS_FOR_ALL_PLACEMENTS_ONLY)
  public static class MethodFieldOverrideClass {

    private static List<TestEnum> testedParameters;

    @EnumParameter({TestEnum.ONE, TestEnum.TWO})
    TestEnum enumParameter;

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @Test
    @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
    public void test() {
      testedParameters.add(enumParameter);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters).containsExactly(TestEnum.ONE, TestEnum.TWO, TestEnum.THREE);
    }
  }

  @ClassTestResult(Result.SUCCESS_FOR_ALL_PLACEMENTS_ONLY)
  public static class ErrorDuplicatedConstructorMethodAnnotation {

    private static List<String> testedParameters;
    final TestEnum enumParameter;

    @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
    public ErrorDuplicatedConstructorMethodAnnotation(TestEnum enumParameter) {
      this.enumParameter = enumParameter;
    }

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @Test
    @EnumParameter({TestEnum.ONE, TestEnum.TWO})
    public void test(TestEnum otherParameter) {
      testedParameters.add(enumParameter + ":" + otherParameter);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters)
          .containsExactly("ONE:ONE", "ONE:TWO", "TWO:ONE", "TWO:TWO", "THREE:ONE", "THREE:TWO");
    }
  }

  @ClassTestResult(Result.FAILURE)
  @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
  public static class ErrorDuplicatedClassFieldAnnotation {

    private static List<TestEnum> testedParameters;

    @EnumParameter({TestEnum.ONE, TestEnum.TWO})
    TestEnum enumParameter;

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @Test
    public void test() {
      testedParameters.add(enumParameter);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testedParameters).containsExactly(TestEnum.ONE, TestEnum.TWO);
    }
  }

  @ClassTestResult(Result.FAILURE)
  public static class ErrorNonStaticProviderClass {

    @Test
    public void test(@TestParameter(valuesProvider = NonStaticProvider.class) int i) {}

    @SuppressWarnings("ClassCanBeStatic")
    class NonStaticProvider implements TestParameterValuesProvider {
      @Override
      public List<?> provideValues() {
        return ImmutableList.of();
      }
    }
  }

  @ClassTestResult(Result.FAILURE)
  public static class ErrorNonPublicTestMethod {

    @Test
    void test(@TestParameter boolean b) {}
  }

  public enum EnumA {
    A1,
    A2
  }

  public enum EnumB {
    B1,
    B2
  }

  public enum EnumC {
    C1,
    C2,
    C3
  }

  @Retention(RUNTIME)
  @TestParameterAnnotation(validator = TestBaseValidatorValidator.class)
  public @interface EnumAParameter {
    EnumA[] value() default {EnumA.A1, EnumA.A2};
  }

  @Retention(RUNTIME)
  @TestParameterAnnotation(validator = TestBaseValidatorValidator.class)
  public @interface EnumBParameter {
    EnumB[] value() default {EnumB.B1, EnumB.B2};
  }

  @Retention(RUNTIME)
  @TestParameterAnnotation(validator = TestBaseValidatorValidator.class)
  public @interface EnumCParameter {
    EnumC[] value() default {EnumC.C1, EnumC.C2, EnumC.C3};
  }

  public static class TestBaseValidatorValidator extends BaseTestParameterValidator {

    @Override
    protected List<List<Class<? extends Annotation>>> getIndependentParameters(Context context) {
      return ImmutableList.of(
          ImmutableList.of(EnumAParameter.class, EnumBParameter.class, EnumCParameter.class));
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class IndependentAnnotation {

    @EnumAParameter EnumA enumA;
    @EnumBParameter EnumB enumB;
    @EnumCParameter EnumC enumC;

    private static List<List<Object>> testedParameters;

    @BeforeClass
    public static void resetStaticState() {
      testedParameters = new ArrayList<>();
    }

    @Test
    public void test() {
      testedParameters.add(ImmutableList.of(enumA, enumB, enumC));
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      // Only 3 tests should have been sufficient to cover all cases.
      assertThat(testedParameters).hasSize(3);
      assertAllEnumsAreIncluded(EnumA.values());
      assertAllEnumsAreIncluded(EnumB.values());
      assertAllEnumsAreIncluded(EnumC.values());
    }

    private static <T extends Enum<T>> void assertAllEnumsAreIncluded(Enum<T>[] values) {
      Set<Enum<T>> enumSet = new HashSet<>(Arrays.asList(values));
      for (List<Object> enumList : testedParameters) {
        enumSet.removeAll(enumList);
      }
      assertThat(enumSet).isEmpty();
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class TestNamesTest {

    @Rule public TestName name = new TestName();

    @TestParameter("8")
    long fieldParam;

    @Test
    public void withPrimitives(
        @TestParameter("true") boolean param1, @TestParameter("2") int param2) {
      assertThat(name.getMethodName())
          .isEqualTo("withPrimitives[fieldParam=8,param1=true,param2=2]");
    }

    @Test
    public void withString(@TestParameter("AAA") String param1) {
      assertThat(name.getMethodName()).isEqualTo("withString[fieldParam=8,AAA]");
    }

    @Test
    public void withEnum(@EnumParameter(TestEnum.TWO) TestEnum param1) {
      assertThat(name.getMethodName()).isEqualTo("withEnum[fieldParam=8,TWO]");
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class MethodNameContainsOrderedParameterNames {

    @Rule public TestName name = new TestName();

    @Test
    public void pretest(@TestParameter({"a", "b"}) String foo) {}

    @Test
    public void test(
        @EnumParameter({TestEnum.ONE, TestEnum.TWO}) TestEnum e, @TestParameter({"c"}) String foo) {
      assertThat(name.getMethodName()).isEqualTo("test[" + e.name() + "," + foo + "]");
    }
  }

  @Parameters(name = "{0}:{2}")
  public static Collection<Object[]> parameters() {
    return Arrays.stream(TestParameterAnnotationMethodProcessorTest.class.getClasses())
        .filter(cls -> cls.isAnnotationPresent(ClassTestResult.class))
        .map(
            cls ->
                new Object[] {
                  cls.getSimpleName(), cls, cls.getAnnotation(ClassTestResult.class).value()
                })
        .collect(toImmutableList());
  }

  private final Class<?> testClass;
  private final Result result;

  public TestParameterAnnotationMethodProcessorTest(
      String name, Class<?> testClass, Result result) {
    this.testClass = testClass;
    this.result = result;
  }

  @Test
  public void test() throws Exception {
    switch (result) {
      case SUCCESS_ALWAYS:
        assertNoFailures(
            PluggableTestRunner.run(
                newTestRunnerWithParameterizedSupport(
                    testClass -> TestMethodProcessorList.createNewParameterizedProcessors())));
        break;

      case SUCCESS_FOR_ALL_PLACEMENTS_ONLY:
        assertThrows(
            RuntimeException.class,
            () ->
                PluggableTestRunner.run(
                    newTestRunnerWithParameterizedSupport(
                        testClass -> TestMethodProcessorList.createNewParameterizedProcessors())));
        break;

      case FAILURE:
        assertThrows(
            RuntimeException.class,
            () ->
                PluggableTestRunner.run(
                    newTestRunnerWithParameterizedSupport(
                        testClass -> TestMethodProcessorList.createNewParameterizedProcessors())));
        break;
    }
  }

  private PluggableTestRunner newTestRunnerWithParameterizedSupport(
      Function<TestClass, TestMethodProcessorList> processorListGenerator) throws Exception {
    return new PluggableTestRunner(testClass) {
      @Override
      protected TestMethodProcessorList createTestMethodProcessorList() {
        return processorListGenerator.apply(getTestClass());
      }
    };
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
