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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.SharedTestUtilitiesJUnit4.SuccessfulTestCaseBase;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

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
  public static class SingleAnnotationClass extends SuccessfulTestCaseBase {

    @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
    TestEnum enumParameter;

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

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class MultipleAllEnumValuesAnnotationClass extends SuccessfulTestCaseBase {

    @TestParameter({"ONE", "THREE"})
    TestEnum enumParameter1;

    @TestParameter TestEnum2 enumParameter2;

    @Test
    public void test(@TestParameter TestEnum2 enumParameter3) {
      storeTestParametersForThisTest(enumParameter1, enumParameter2, enumParameter3);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test[ONE,A,A]", "ONE:A:A")
          .put("test[ONE,A,B]", "ONE:A:B")
          .put("test[ONE,B,A]", "ONE:B:A")
          .put("test[ONE,B,B]", "ONE:B:B")
          .put("test[THREE,A,A]", "THREE:A:A")
          .put("test[THREE,A,B]", "THREE:A:B")
          .put("test[THREE,B,A]", "THREE:B:A")
          .put("test[THREE,B,B]", "THREE:B:B")
          .build();
    }

    enum TestEnum2 {
      A,
      B;
    }
  }

  @ClassTestResult(Result.SUCCESS_FOR_ALL_PLACEMENTS_ONLY)
  public static class SingleParameterAnnotationClass extends SuccessfulTestCaseBase {

    @Test
    @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
    public void test(TestEnum enumParameter) {
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

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class SingleAnnotatedParameterAnnotationClass extends SuccessfulTestCaseBase {

    @Test
    public void test(
        @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE}) TestEnum enumParameter) {
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

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class AnnotatedSuperclassParameter extends SuccessfulTestCaseBase {

    @Test
    public void test(
        @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE}) Object enumParameter) {
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

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class DuplicatedAnnotatedParameterAnnotationClass extends SuccessfulTestCaseBase {

    @Test
    public void test(
        @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE}) TestEnum enumParameter,
        @EnumParameter({TestEnum.FOUR, TestEnum.FIVE}) TestEnum enumParameter2) {
      storeTestParametersForThisTest(enumParameter, enumParameter2);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test[ONE,FOUR]", "ONE:FOUR")
          .put("test[ONE,FIVE]", "ONE:FIVE")
          .put("test[TWO,FOUR]", "TWO:FOUR")
          .put("test[TWO,FIVE]", "TWO:FIVE")
          .put("test[THREE,FOUR]", "THREE:FOUR")
          .put("test[THREE,FIVE]", "THREE:FIVE")
          .build();
    }
  }

  @ClassTestResult(Result.FAILURE)
  public static class SingleAnnotatedParameterAnnotationClassWithMissingValue {

    @Test
    public void test(@EnumParameter TestEnum enumParameter) {}
  }

  @ClassTestResult(Result.SUCCESS_FOR_ALL_PLACEMENTS_ONLY)
  public static class MultipleAnnotationTestClass extends SuccessfulTestCaseBase {

    @EnumParameter({TestEnum.ONE, TestEnum.TWO})
    TestEnum enumParameter;

    @Test
    @EnumParameter({TestEnum.THREE})
    public void parameterized() {
      storeTestParametersForThisTest(enumParameter);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder().put("parameterized[THREE]", "THREE").build();
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class TooLongTestNamesShortened extends SuccessfulTestCaseBase {

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
      storeTestParametersForThisTest(testString);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test1[1.ABC]", "ABC")
          .put(
              "test1[2.This is a very long string (240 characters) that would normally cause"
                  + " Sponge+Tin to exceed the filename limit of 255 characters."
                  + " =========================================================...]",
              "This is a very long string (240 characters) that would normally cause Sponge+Tin to"
                  + " exceed the filename limit of 255 characters."
                  + " ============================================================================"
                  + "==================================")
          .build();
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class DuplicateTestNames extends SuccessfulTestCaseBase {

    @Test
    public void test1(@TestParameter({"ABC", "ABC"}) String testString) {
      storeTestParametersForThisTest(testString);
    }

    private static final class Test2Provider extends TestParameterValuesProvider {
      @Override
      public List<Object> provideValues(TestParameterValuesProvider.Context context) {
        return newArrayList(123, "123", "null", null);
      }
    }

    @Test
    public void test2(@TestParameter(valuesProvider = Test2Provider.class) Object testObject) {
      storeTestParametersForThisTest(testObject);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test1[1.ABC]", "ABC")
          .put("test1[2.ABC]", "ABC")
          .put("test2[testObject=123 (Integer)]", "123")
          .put("test2[testObject=123 (String)]", "123")
          .put("test2[testObject=null (String)]", "null")
          .put("test2[testObject=null (null reference)]", "null")
          .build();
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class DuplicateFieldAnnotationTestClass extends SuccessfulTestCaseBase {

    @TestParameter({"foo", "bar"})
    String stringParameter;

    @TestParameter({"baz", "qux"})
    String stringParameter2;

    @Test
    public void test() {
      storeTestParametersForThisTest(stringParameter, stringParameter2);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test[foo,baz]", "foo:baz")
          .put("test[foo,qux]", "foo:qux")
          .put("test[bar,baz]", "bar:baz")
          .put("test[bar,qux]", "bar:qux")
          .build();
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class DuplicateIdenticalFieldAnnotationTestClass extends SuccessfulTestCaseBase {

    @TestParameter({"foo", "bar"})
    String stringParameter;

    @TestParameter({"foo", "bar"})
    String stringParameter2;

    @Test
    public void test() {
      storeTestParametersForThisTest(stringParameter, stringParameter2);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test[foo,foo]", "foo:foo")
          .put("test[foo,bar]", "foo:bar")
          .put("test[bar,foo]", "bar:foo")
          .put("test[bar,bar]", "bar:bar")
          .build();
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
  public static class SingleAnnotationTestClassWithAnnotation extends SuccessfulTestCaseBase {

    @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
    TestEnum enumParameter;

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

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class MultipleAnnotationTestClassWithAnnotation extends SuccessfulTestCaseBase {

    @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
    TestEnum enumParameter;

    @Test
    public void parameterized(@TestParameter({"foo", "bar"}) String stringParameter) {
      storeTestParametersForThisTest(enumParameter, stringParameter);
    }

    @Test
    public void nonParameterized() {
      storeTestParametersForThisTest(enumParameter);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("parameterized[ONE,foo]", "ONE:foo")
          .put("parameterized[ONE,bar]", "ONE:bar")
          .put("parameterized[TWO,foo]", "TWO:foo")
          .put("parameterized[TWO,bar]", "TWO:bar")
          .put("parameterized[THREE,foo]", "THREE:foo")
          .put("parameterized[THREE,bar]", "THREE:bar")
          .put("nonParameterized[ONE]", "ONE")
          .put("nonParameterized[TWO]", "TWO")
          .put("nonParameterized[THREE]", "THREE")
          .build();
    }
  }

  public abstract static class BaseClassWithSingleTest extends SuccessfulTestCaseBase {
    @Test
    public void testInBase(@TestParameter boolean b) {
      storeTestParametersForThisTest(b);
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class SimpleTestInheritedFromBaseClass extends BaseClassWithSingleTest {
    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("testInBase[b=false]", "false")
          .put("testInBase[b=true]", "true")
          .build();
    }
  }

  public abstract static class BaseClassWithAnnotations extends SuccessfulTestCaseBase {

    @TestParameter boolean boolInBase;

    @Test
    public void testInBase(@TestParameter({"ONE", "TWO"}) TestEnum enumInBase) {
      storeTestParametersForThisTest(boolInBase, enumInBase);
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
      storeTestParametersForThisTest(boolInBase, boolInChild, enumInChild);
    }

    @Override
    public void abstractTestInBase() {
      storeTestParametersForThisTest(boolInBase, boolInChild);
    }

    @Override
    public void overridableTestInBase() {
      storeTestParametersForThisTest(boolInBase, boolInChild);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("testInChild[boolInChild=false,boolInBase=false,TWO]", "false:false:TWO")
          .put("testInChild[boolInChild=false,boolInBase=false,THREE]", "false:false:THREE")
          .put("testInChild[boolInChild=false,boolInBase=true,TWO]", "true:false:TWO")
          .put("testInChild[boolInChild=false,boolInBase=true,THREE]", "true:false:THREE")
          .put("testInChild[boolInChild=true,boolInBase=false,TWO]", "false:true:TWO")
          .put("testInChild[boolInChild=true,boolInBase=false,THREE]", "false:true:THREE")
          .put("testInChild[boolInChild=true,boolInBase=true,TWO]", "true:true:TWO")
          .put("testInChild[boolInChild=true,boolInBase=true,THREE]", "true:true:THREE")
          .put("abstractTestInBase[boolInChild=false,boolInBase=false]", "false:false")
          .put("abstractTestInBase[boolInChild=false,boolInBase=true]", "true:false")
          .put("abstractTestInBase[boolInChild=true,boolInBase=false]", "false:true")
          .put("abstractTestInBase[boolInChild=true,boolInBase=true]", "true:true")
          .put("overridableTestInBase[boolInChild=false,boolInBase=false]", "false:false")
          .put("overridableTestInBase[boolInChild=false,boolInBase=true]", "true:false")
          .put("overridableTestInBase[boolInChild=true,boolInBase=false]", "false:true")
          .put("overridableTestInBase[boolInChild=true,boolInBase=true]", "true:true")
          .put("testInBase[boolInChild=false,boolInBase=false,ONE]", "false:ONE")
          .put("testInBase[boolInChild=false,boolInBase=false,TWO]", "false:TWO")
          .put("testInBase[boolInChild=false,boolInBase=true,ONE]", "true:ONE")
          .put("testInBase[boolInChild=false,boolInBase=true,TWO]", "true:TWO")
          .put("testInBase[boolInChild=true,boolInBase=false,ONE]", "false:ONE")
          .put("testInBase[boolInChild=true,boolInBase=false,TWO]", "false:TWO")
          .put("testInBase[boolInChild=true,boolInBase=true,ONE]", "true:ONE")
          .put("testInBase[boolInChild=true,boolInBase=true,TWO]", "true:TWO")
          .build();
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
  public static class MethodEvaluatorClass extends SuccessfulTestCaseBase {

    @Test
    public void test(
        @EnumEvaluatorParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE}) TestEnum value) {
      storeTestParametersForThisTest(value);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test[ONE]", "ONE")
          .put("test[TWO]", "TWO")
          .build();
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class FieldEvaluatorClass extends SuccessfulTestCaseBase {

    @EnumEvaluatorParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
    TestEnum value;

    @Test
    public void test() {
      storeTestParametersForThisTest(value);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test[ONE]", "ONE")
          .put("test[TWO]", "TWO")
          .build();
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class ConstructorClass extends SuccessfulTestCaseBase {

    final TestEnum enumParameter;

    public ConstructorClass(
        @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE}) TestEnum enumParameter) {
      this.enumParameter = enumParameter;
    }

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

  @ClassTestResult(Result.SUCCESS_FOR_ALL_PLACEMENTS_ONLY)
  public static class MethodFieldOverrideClass extends SuccessfulTestCaseBase {

    @EnumParameter({TestEnum.ONE, TestEnum.TWO})
    TestEnum enumParameter;

    @Test
    @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
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

  @ClassTestResult(Result.SUCCESS_FOR_ALL_PLACEMENTS_ONLY)
  public static class ErrorDuplicatedConstructorMethodAnnotation extends SuccessfulTestCaseBase {

    final TestEnum enumParameter;

    @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
    public ErrorDuplicatedConstructorMethodAnnotation(TestEnum enumParameter) {
      this.enumParameter = enumParameter;
    }

    @Test
    @EnumParameter({TestEnum.ONE, TestEnum.TWO})
    public void test(TestEnum otherParameter) {
      storeTestParametersForThisTest(enumParameter, otherParameter);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test[ONE,ONE]", "ONE:ONE")
          .put("test[ONE,TWO]", "ONE:TWO")
          .put("test[TWO,ONE]", "TWO:ONE")
          .put("test[TWO,TWO]", "TWO:TWO")
          .put("test[THREE,ONE]", "THREE:ONE")
          .put("test[THREE,TWO]", "THREE:TWO")
          .build();
    }
  }

  @ClassTestResult(Result.FAILURE)
  @EnumParameter({TestEnum.ONE, TestEnum.TWO, TestEnum.THREE})
  public static class ErrorDuplicatedClassFieldAnnotation {

    @EnumParameter({TestEnum.ONE, TestEnum.TWO})
    TestEnum enumParameter;

    @Test
    public void test() {}
  }

  @ClassTestResult(Result.FAILURE)
  public static class ErrorNonStaticProviderClass {

    @Test
    public void test(@TestParameter(valuesProvider = NonStaticProvider.class) int i) {}

    @SuppressWarnings("ClassCanBeStatic")
    class NonStaticProvider extends TestParameterValuesProvider {
      @Override
      public List<?> provideValues(TestParameterValuesProvider.Context context) {
        return ImmutableList.of();
      }
    }
  }

  @ClassTestResult(Result.FAILURE)
  public static class ErrorNonPublicTestMethod {

    @Test
    void test(@TestParameter boolean b) {}
  }

  @ClassTestResult(Result.FAILURE)
  public static class ErrorPackagePrivateConstructor {
    ErrorPackagePrivateConstructor() {}

    @Test
    public void test1() {}
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
  public static class IndependentAnnotation extends SuccessfulTestCaseBase {

    @EnumAParameter EnumA enumA;
    @EnumBParameter EnumB enumB;
    @EnumCParameter EnumC enumC;

    @Test
    public void test() {
      storeTestParametersForThisTest(enumA, enumB, enumC);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test[A1,B1,C1]", "A1:B1:C1")
          .put("test[A2,B2,C2]", "A2:B2:C2")
          .put("test[A2,B2,C3]", "A2:B2:C3")
          .build();
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class TestNamesTest extends SuccessfulTestCaseBase {

    @TestParameter("8")
    long fieldParam;

    @Test
    public void withPrimitives(
        @TestParameter("true") boolean param1, @TestParameter("2") int param2) {
      storeTestParametersForThisTest(fieldParam, param1, param2);
    }

    @Test
    public void withString(@TestParameter("AAA") String param1) {
      storeTestParametersForThisTest(fieldParam, param1);
    }

    @Test
    public void withEnum(@EnumParameter(TestEnum.TWO) TestEnum param1) {
      storeTestParametersForThisTest(fieldParam, param1);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("withString[fieldParam=8,AAA]", "8:AAA")
          .put("withEnum[fieldParam=8,TWO]", "8:TWO")
          .put("withPrimitives[fieldParam=8,param1=true,param2=2]", "8:true:2")
          .build();
    }
  }

  @ClassTestResult(Result.SUCCESS_ALWAYS)
  public static class MethodNameContainsOrderedParameterNames extends SuccessfulTestCaseBase {

    @Test
    public void pretest(@TestParameter({"a", "b"}) String foo) {
      storeTestParametersForThisTest(foo);
    }

    @Test
    public void test(
        @EnumParameter({TestEnum.ONE, TestEnum.TWO}) TestEnum e, @TestParameter({"c"}) String foo) {
      storeTestParametersForThisTest(e, foo);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("pretest[a]", "a")
          .put("pretest[b]", "b")
          .put("test[ONE,c]", "ONE:c")
          .put("test[TWO,c]", "TWO:c")
          .build();
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
        SharedTestUtilitiesJUnit4.runTestsAndAssertNoFailures(
            newTestRunner(/* supportLegacyFeatures= */ false));
        break;

      case SUCCESS_FOR_ALL_PLACEMENTS_ONLY:
        assertThrows(
            Exception.class,
            () ->
                SharedTestUtilitiesJUnit4.runTestsAndGetFailures(
                    newTestRunner(/* supportLegacyFeatures= */ false)));
        break;

      case FAILURE:
        assertThrows(
            Exception.class,
            () ->
                SharedTestUtilitiesJUnit4.runTestsAndGetFailures(
                    newTestRunner(/* supportLegacyFeatures= */ false)));
        break;
    }
  }

  private PluggableTestRunner newTestRunner(boolean supportLegacyFeatures) throws Exception {
    return new PluggableTestRunner(testClass) {};
  }
}
