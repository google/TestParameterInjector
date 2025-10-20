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
import static com.google.common.truth.TruthJUnit.assume;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.SharedTestUtilitiesJUnit4.SuccessfulTestCaseBase;
import com.google.testing.junit.testparameterinjector.TestParameters.TestParametersValues;
import com.google.testing.junit.testparameterinjector.TestParametersValuesProvider.Context;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestParametersMethodProcessorTest {

  @Retention(RUNTIME)
  @interface RunAsTest {
    String failsWithMessage() default "";
  }

  public enum TestEnum {
    ONE,
    TWO,
    THREE;
  }

  private static final class TestEnumValuesProvider extends TestParametersValuesProvider {
    @Override
    public List<TestParametersValues> provideValues(Context context) {
      return ImmutableList.of(
          TestParametersValues.builder().name("one").addParameter("testEnum", TestEnum.ONE).build(),
          TestParametersValues.builder().addParameter("testEnum", TestEnum.TWO).build(),
          TestParametersValues.builder().name("null-case").addParameter("testEnum", null).build());
    }
  }

  @RunAsTest
  public static class SimpleMethodAnnotation extends SuccessfulTestCaseBase {

    @Test
    @TestParameters("{testEnum: ONE, testLong: 11, testBoolean: false, testString: ABC}")
    @TestParameters("{testEnum: TWO,\ntestLong: 22,\ntestBoolean: true,\r\n\r\n testString: 'DEF'}")
    @TestParameters("{testEnum: null, testLong: 33, testBoolean: false, testString: null}")
    public void test(TestEnum testEnum, long testLong, boolean testBoolean, String testString) {
      storeTestParametersForThisTest(testEnum, testLong, testBoolean, testString);
    }

    @Test
    @TestParameters({
      "{testEnum: ONE, testLong: 11, testBoolean: false, testString: ABC}",
      "{testEnum: TWO,\ntestLong: 22,\ntestBoolean: true,\r\n\r\n testString: 'DEF'}",
      "{testEnum: null, testLong: 33, testBoolean: false, testString: null}",
    })
    public void test_singleAnnotation(
        TestEnum testEnum, long testLong, boolean testBoolean, String testString) {
      storeTestParametersForThisTest(testEnum, testLong, testBoolean, testString);
    }

    @Test
    @TestParameters("{testString: ABC}")
    @TestParameters(
        "{testString: 'This is a very long string (240 characters) that would normally cause"
            + " Sponge+Tin to exceed the filename limit of 255 characters."
            + " ================================================================================="
            + "=============='}")
    public void test2_withLongNames(String testString) {
      storeTestParametersForThisTest(testString);
    }

    @Test
    @TestParameters(
        "{testEnums: [ONE, TWO, THREE], testLongs: [11, 4], testBooleans: [false, true],"
            + " testStrings: [ABC, '123']}")
    @TestParameters(
        "{testEnums: [TWO],\ntestLongs: [22],\ntestBooleans: [true],\r\n\r\n testStrings: ['DEF']}")
    @TestParameters("{testEnums: [], testLongs: [], testBooleans: [], testStrings: []}")
    public void test3_withRepeatedParams(
        List<TestEnum> testEnums,
        List<Long> testLongs,
        List<Boolean> testBooleans,
        List<String> testStrings) {
      storeTestParametersForThisTest(testEnums, testLongs, testBooleans, testStrings);
    }

    @Test
    @TestParameters(customName = "custom1", value = "{testEnum: ONE}")
    @TestParameters("{testEnum: TWO}")
    @TestParameters(customName = "custom3", value = "{testEnum: THREE}")
    public void test4_withCustomName(TestEnum testEnum) {
      storeTestParametersForThisTest(testEnum);
    }

    @Test
    @TestParameters("{testDuration: 0}")
    @TestParameters("{testDuration: 1d}")
    @TestParameters("{testDuration: -2h}")
    public void test5_withDuration(Duration testDuration) {
      storeTestParametersForThisTest(testDuration);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put(
              "test[{testEnum: ONE, testLong: 11, testBoolean: false, testString: ABC}]",
              "ONE:11:false:ABC")
          .put(
              "test[{testEnum: TWO, testLong: 22, testBoolean: true, testString: 'DEF'}]",
              "TWO:22:true:DEF")
          .put(
              "test[{testEnum: null, testLong: 33, testBoolean: false, testString: null}]",
              "null:33:false:null")
          .put(
              "test_singleAnnotation[{testEnum: ONE, testLong: 11, testBoolean: false, testString:"
                  + " ABC}]",
              "ONE:11:false:ABC")
          .put(
              "test_singleAnnotation[{testEnum: TWO, testLong: 22, testBoolean: true, testString:"
                  + " 'DEF'}]",
              "TWO:22:true:DEF")
          .put(
              "test_singleAnnotation[{testEnum: null, testLong: 33, testBoolean: false, testString:"
                  + " null}]",
              "null:33:false:null")
          .put("test2_withLongNames[1.{testString: ABC}]", "ABC")
          .put(
              "test2_withLongNames[2.{testString: 'This is a very long string (240 characters) that"
                  + " would normally cause Sponge+Tin to exceed the filename limit of 255"
                  + " characters. =============================...]",
              "This is a very long string (240 characters) that would normally cause Sponge+Tin to"
                  + " exceed the filename limit of 255 characters."
                  + " ===============================================================================================")
          .put(
              "test3_withRepeatedParams[{testEnums: [ONE, TWO, THREE], testLongs: [11, 4],"
                  + " testBooleans: [false, true], testStrings: [ABC, '123']}]",
              "[ONE, TWO, THREE]:[11, 4]:[false, true]:[ABC, 123]")
          .put(
              "test3_withRepeatedParams[{testEnums: [TWO], testLongs: [22], testBooleans: [true],"
                  + " testStrings: ['DEF']}]",
              "[TWO]:[22]:[true]:[DEF]")
          .put(
              "test3_withRepeatedParams[{testEnums: [], testLongs: [], testBooleans: [],"
                  + " testStrings: []}]",
              "[]:[]:[]:[]")
          .put("test4_withCustomName[custom1]", "ONE")
          .put("test4_withCustomName[{testEnum: TWO}]", "TWO")
          .put("test4_withCustomName[custom3]", "THREE")
          .put("test5_withDuration[{testDuration: 0}]", "PT0S")
          .put("test5_withDuration[{testDuration: 1d}]", "PT24H")
          .put("test5_withDuration[{testDuration: -2h}]", "PT-2H")
          .build();
    }
  }

  @RunAsTest
  public static class SimpleConstructorAnnotation extends SuccessfulTestCaseBase {

    private final TestEnum testEnum;
    private final long testLong;
    private final boolean testBoolean;
    private final String testString;

    @TestParameters({
      "{testEnum: ONE, testLong: 11, testBoolean: false, testString: ABC}",
      "{testEnum: TWO, testLong: 22, testBoolean: true, testString: DEF}",
      "{testEnum: null, testLong: 33, testBoolean: false, testString: null}",
    })
    public SimpleConstructorAnnotation(
        TestEnum testEnum, long testLong, boolean testBoolean, String testString) {
      this.testEnum = testEnum;
      this.testLong = testLong;
      this.testBoolean = testBoolean;
      this.testString = testString;
    }

    @Test
    public void test1() {
      storeTestParametersForThisTest(testEnum, testLong, testBoolean, testString);
    }

    @Test
    public void test2() {
      storeTestParametersForThisTest(testEnum, testLong, testBoolean, testString);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put(
              "test1[{testEnum: ONE, testLong: 11, testBoolean: false, testString: ABC}]",
              "ONE:11:false:ABC")
          .put(
              "test1[{testEnum: TWO, testLong: 22, testBoolean: true, testString: DEF}]",
              "TWO:22:true:DEF")
          .put(
              "test1[{testEnum: null, testLong: 33, testBoolean: false, testString: null}]",
              "null:33:false:null")
          .put(
              "test2[{testEnum: ONE, testLong: 11, testBoolean: false, testString: ABC}]",
              "ONE:11:false:ABC")
          .put(
              "test2[{testEnum: TWO, testLong: 22, testBoolean: true, testString: DEF}]",
              "TWO:22:true:DEF")
          .put(
              "test2[{testEnum: null, testLong: 33, testBoolean: false, testString: null}]",
              "null:33:false:null")
          .build();
    }
  }

  @RunAsTest
  public static class ConstructorAnnotationWithProvider extends SuccessfulTestCaseBase {

    private final TestEnum testEnum;

    @TestParameters(valuesProvider = TestEnumValuesProvider.class)
    public ConstructorAnnotationWithProvider(TestEnum testEnum) {
      this.testEnum = testEnum;
    }

    @Test
    public void test() {
      storeTestParametersForThisTest(testEnum);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test[one]", "ONE")
          .put("test[{TWO}]", "TWO")
          .put("test[null-case]", "null")
          .build();
    }
  }

  @RunAsTest
  public static class MethodAnnotationWithProvider extends SuccessfulTestCaseBase {

    @TestParameters(valuesProvider = CustomProvider.class)
    @Test
    public void test(int testInt, TestEnum testEnum) {
      storeTestParametersForThisTest(testInt, testEnum);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test[{testInt=5, ONE}]", "5:ONE")
          .put("test[{testInt=10, TWO}]", "10:TWO")
          .build();
    }

    private static final class CustomProvider extends TestParametersValuesProvider {
      @Override
      public List<TestParametersValues> provideValues(Context context) {
        return ImmutableList.of(
            TestParametersValues.builder()
                .addParameter("testInt", 5)
                .addParameter("testEnum", TestEnum.ONE)
                .build(),
            TestParametersValues.builder()
                .addParameter("testInt", 10)
                .addParameter("testEnum", TestEnum.TWO)
                .build());
      }
    }
  }

  @RunAsTest
  public static class ProviderWithContext extends SuccessfulTestCaseBase {

    @CustomAnnotation('A')
    @CustomRepeatableAnnotation('B')
    @TestParameters(valuesProvider = InjectContextProvider.class)
    public ProviderWithContext(Context context) {
      assertThat(context.testClass()).isEqualTo(ProviderWithContext.class);

      assertThat(annotationTypes(context.annotationsOnParameter()))
          .containsExactly(
              TestParameters.class, CustomAnnotation.class, CustomRepeatableAnnotation.class);

      assertThat(context.getOtherAnnotation(CustomAnnotation.class).value()).isEqualTo('A');

      assertThat(getOnlyElement(context.getOtherAnnotations(CustomAnnotation.class)).value())
          .isEqualTo('A');
      assertThat(
              getOnlyElement(context.getOtherAnnotations(CustomRepeatableAnnotation.class)).value())
          .isEqualTo('B');
    }

    @TestParameters(valuesProvider = InjectContextProvider.class)
    @Test
    public void testWithoutOtherAnnotations(Context context) {
      assertThat(context.testClass()).isEqualTo(ProviderWithContext.class);

      assertThat(annotationTypes(context.annotationsOnParameter()))
          .containsExactly(TestParameters.class, Test.class);

      assertThat(context.getOtherAnnotations(CustomAnnotation.class)).isEmpty();
      assertThat(context.getOtherAnnotations(CustomRepeatableAnnotation.class)).isEmpty();

      storeTestParametersForThisTest(context);
    }

    @TestParameters(valuesProvider = InjectContextProvider.class)
    @CustomAnnotation('C')
    @CustomRepeatableAnnotation('D')
    @CustomRepeatableAnnotation('E')
    @Test
    public void testWithOtherAnnotations(Context context) {
      assertThat(context.testClass()).isEqualTo(ProviderWithContext.class);

      assertThat(annotationTypes(context.annotationsOnParameter()))
          .containsExactly(
              TestParameters.class,
              Test.class,
              CustomAnnotation.class,
              CustomRepeatableAnnotation.CustomRepeatableAnnotationHolder.class);

      assertThat(context.getOtherAnnotation(CustomAnnotation.class).value()).isEqualTo('C');

      assertThat(getOnlyElement(context.getOtherAnnotations(CustomAnnotation.class)).value())
          .isEqualTo('C');
      assertThat(
              FluentIterable.from(context.getOtherAnnotations(CustomRepeatableAnnotation.class))
                  .transform(a -> a.value())
                  .toList())
          .containsExactly('D', 'E');

      storeTestParametersForThisTest(context);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put(
              "testWithoutOtherAnnotations[1.{context(annotationsOnParameter=[@TestParameters,@CustomAnnotation,@CustomRepe...,1.{context(annotationsOnParameter=[@TestParameters,@Test],testClass=ProviderWith...]",
              "context(annotationsOnParameter=[@TestParameters,@Test],testClass=ProviderWithContext)")
          .put(
              "testWithOtherAnnotations[1.{context(annotationsOnParameter=[@TestParameters,@CustomAnnotation,@CustomRepeat...,1.{context(annotationsOnParameter=[@TestParameters,@CustomAnnotation,@CustomRepeat...]",
              "context(annotationsOnParameter=[@TestParameters,@CustomAnnotation,@CustomRepeatableAnnotationHolder,@Test],testClass=ProviderWithContext)")
          .build();
    }

    private static final class InjectContextProvider extends TestParametersValuesProvider {
      @Override
      protected List<TestParametersValues> provideValues(Context context) {
        return newArrayList(
            TestParametersValues.builder().addParameter("context", context).build());
      }
    }

    @Retention(RUNTIME)
    @interface CustomAnnotation {
      char value();
    }

    @Retention(RUNTIME)
    @Repeatable(CustomRepeatableAnnotation.CustomRepeatableAnnotationHolder.class)
    @interface CustomRepeatableAnnotation {
      char value();

      @Retention(RUNTIME)
      @interface CustomRepeatableAnnotationHolder {
        CustomRepeatableAnnotation[] value();

        String test() default "TEST";
      }
    }
  }

  public abstract static class BaseClassWithMethodAnnotation extends SuccessfulTestCaseBase {

    @Test
    @TestParameters("{testEnum: ONE}")
    @TestParameters("{testEnum: TWO}")
    public void testInBase(TestEnum testEnum) {
      storeTestParametersForThisTest(testEnum);
    }
  }

  @RunAsTest
  public static class AnnotationInheritedFromBaseClass extends BaseClassWithMethodAnnotation {

    @Test
    @TestParameters({"{testEnum: TWO}", "{testEnum: THREE}"})
    public void testInChild(TestEnum testEnum) {
      storeTestParametersForThisTest(testEnum);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("testInChild[{testEnum: TWO}]", "TWO")
          .put("testInChild[{testEnum: THREE}]", "THREE")
          .put("testInBase[{testEnum: ONE}]", "ONE")
          .put("testInBase[{testEnum: TWO}]", "TWO")
          .build();
    }
  }

  @RunAsTest
  public static class MixedWithTestParameterMethodAnnotation extends SuccessfulTestCaseBase {

    private final TestEnum testEnumFromConstructor;

    @TestParameters("{testEnum: ONE}")
    @TestParameters("{testEnum: TWO}")
    public MixedWithTestParameterMethodAnnotation(TestEnum testEnum) {
      this.testEnumFromConstructor = testEnum;
    }

    @Test
    public void test1(@TestParameter TestEnum testEnum) {
      storeTestParametersForThisTest(testEnumFromConstructor, testEnum);
    }

    @Test
    @TestParameters("{testString: ABC}")
    @TestParameters("{testString: DEF}")
    public void test2(String testString) {
      storeTestParametersForThisTest(testEnumFromConstructor, testString);
    }

    @Test
    @TestParameters("{testString: ABC}")
    @TestParameters(
        "{testString: 'This is a very long string (240 characters) that would normally cause"
            + " Sponge+Tin to exceed the filename limit of 255 characters."
            + " ================================================================================="
            + "=============='}")
    public void test3_withLongNames(String testString) {
      storeTestParametersForThisTest(testEnumFromConstructor, testString);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test1[{testEnum: ONE},ONE]", "ONE:ONE")
          .put("test1[{testEnum: ONE},TWO]", "ONE:TWO")
          .put("test1[{testEnum: ONE},THREE]", "ONE:THREE")
          .put("test1[{testEnum: TWO},ONE]", "TWO:ONE")
          .put("test1[{testEnum: TWO},TWO]", "TWO:TWO")
          .put("test1[{testEnum: TWO},THREE]", "TWO:THREE")
          .put("test2[{testEnum: ONE},{testString: ABC}]", "ONE:ABC")
          .put("test2[{testEnum: ONE},{testString: DEF}]", "ONE:DEF")
          .put("test2[{testEnum: TWO},{testString: ABC}]", "TWO:ABC")
          .put("test2[{testEnum: TWO},{testString: DEF}]", "TWO:DEF")
          .put("test3_withLongNames[{testEnum: ONE},1.{testString: ABC}]", "ONE:ABC")
          .put(
              "test3_withLongNames[{testEnum: ONE},2.{testString: 'This is a very long string (240"
                  + " characters) that would normally caus...]",
              "ONE:This is a very long string (240 characters) that would normally cause Sponge+Tin"
                  + " to exceed the filename limit of 255 characters."
                  + " ==================================================================="
                  + "============================")
          .put("test3_withLongNames[{testEnum: TWO},1.{testString: ABC}]", "TWO:ABC")
          .put(
              "test3_withLongNames[{testEnum: TWO},2.{testString: 'This is a very long string (240"
                  + " characters) that would normally caus...]",
              "TWO:This is a very long string (240 characters) that would normally cause Sponge+Tin"
                  + " to exceed the filename limit of 255 characters."
                  + " ======================================================================"
                  + "=========================")
          .build();
    }
  }

  @RunAsTest
  public static class MixedWithTestParameterFieldAnnotation extends SuccessfulTestCaseBase {

    private final TestEnum testEnumB;

    @TestParameter TestEnum testEnumA;

    @TestParameters("{testEnumB: ONE}")
    @TestParameters("{testEnumB: TWO}")
    public MixedWithTestParameterFieldAnnotation(TestEnum testEnumB) {
      this.testEnumB = testEnumB;
    }

    @Test
    @TestParameters({"{testString: ABC}", "{testString: DEF}"})
    public void test1(String testString) {
      storeTestParametersForThisTest(testEnumA, testEnumB, testString);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test1[{testEnumB: ONE},{testString: ABC},ONE]", "ONE:ONE:ABC")
          .put("test1[{testEnumB: ONE},{testString: ABC},TWO]", "TWO:ONE:ABC")
          .put("test1[{testEnumB: ONE},{testString: ABC},THREE]", "THREE:ONE:ABC")
          .put("test1[{testEnumB: ONE},{testString: DEF},ONE]", "ONE:ONE:DEF")
          .put("test1[{testEnumB: ONE},{testString: DEF},TWO]", "TWO:ONE:DEF")
          .put("test1[{testEnumB: ONE},{testString: DEF},THREE]", "THREE:ONE:DEF")
          .put("test1[{testEnumB: TWO},{testString: ABC},ONE]", "ONE:TWO:ABC")
          .put("test1[{testEnumB: TWO},{testString: ABC},TWO]", "TWO:TWO:ABC")
          .put("test1[{testEnumB: TWO},{testString: ABC},THREE]", "THREE:TWO:ABC")
          .put("test1[{testEnumB: TWO},{testString: DEF},ONE]", "ONE:TWO:DEF")
          .put("test1[{testEnumB: TWO},{testString: DEF},TWO]", "TWO:TWO:DEF")
          .put("test1[{testEnumB: TWO},{testString: DEF},THREE]", "THREE:TWO:DEF")
          .build();
    }
  }

  @RunAsTest(
      failsWithMessage =
          "Either a value or a valuesProvider must be set in @TestParameters on test1()")
  public static class InvalidTestBecauseEmptyAnnotation {
    @Test
    @TestParameters
    public void test1() {}
  }

  @RunAsTest(
      failsWithMessage =
          "Either a value or a valuesProvider must be set in @TestParameters on"
              + " com.google.testing.junit.testparameterinjector.TestParametersMethodProcessorTest"
              + "$InvalidTestBecauseEmptyAnnotationOnConstructor()")
  public static class InvalidTestBecauseEmptyAnnotationOnConstructor {
    @TestParameters
    public InvalidTestBecauseEmptyAnnotationOnConstructor() {}

    @Test
    public void test1() {}
  }

  @RunAsTest(
      failsWithMessage =
          "It is not allowed to specify both value and valuesProvider in"
              + " @TestParameters(value=[{testEnum: ONE}], valuesProvider=TestEnumValuesProvider)"
              + " on test1()")
  public static class InvalidTestBecauseCombiningValueWithProvider {

    @Test
    @TestParameters(value = "{testEnum: ONE}", valuesProvider = TestEnumValuesProvider.class)
    public void test1(TestEnum testEnum) {}
  }

  @RunAsTest(
      failsWithMessage =
          "Either a value or a valuesProvider must be set in @TestParameters on test1()")
  public static class InvalidTestBecauseRepeatedAnnotationIsEmpty {
    @Test
    @TestParameters(value = "{testEnum: ONE}")
    @TestParameters
    public void test1(TestEnum testEnum) {}
  }

  @RunAsTest(
      failsWithMessage =
          "When specifying more than one @TestParameter for a method/constructor, each annotation"
              + " must have exactly one value. Instead, got 2 values on test1(): [{testEnum: TWO},"
              + " {testEnum: THREE}]")
  public static class InvalidTestBecauseRepeatedAnnotationHasMultipleValues {
    @Test
    @TestParameters(value = "{testEnum: ONE}")
    @TestParameters(value = {"{testEnum: TWO}", "{testEnum: THREE}"})
    public void test1(TestEnum testEnum) {}
  }

  @RunAsTest(
      failsWithMessage =
          "Setting a valuesProvider is not supported for methods/constructors with"
              + " multiple @TestParameters annotations on test1()")
  public static class InvalidTestBecauseRepeatedAnnotationHasProvider {
    @Test
    @TestParameters(valuesProvider = TestEnumValuesProvider.class)
    @TestParameters(valuesProvider = TestEnumValuesProvider.class)
    public void test1(TestEnum testEnum) {}
  }

  @RunAsTest(
      failsWithMessage =
          "Setting @TestParameters.customName is only allowed if there is exactly one YAML string"
              + " in @TestParameters.value (on test1())")
  public static class InvalidTestBecauseNamedAnnotationHasMultipleValues {
    @Test
    @TestParameters(
        customName = "custom",
        value = {"{testEnum: TWO}", "{testEnum: THREE}"})
    public void test1(TestEnum testEnum) {}
  }

  @RunAsTest(failsWithMessage = "Expected exactly one constructor, but got []")
  public static class InvalidTestBecausePackagePrivateConstructor {
    InvalidTestBecausePackagePrivateConstructor() {}

    @Test
    public void test1() {}
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return Arrays.stream(TestParametersMethodProcessorTest.class.getClasses())
        .filter(cls -> cls.isAnnotationPresent(RunAsTest.class))
        .map(
            cls ->
                new Object[] {
                  cls.getSimpleName(), cls, cls.getAnnotation(RunAsTest.class).failsWithMessage()
                })
        .collect(toImmutableList());
  }

  private final Class<?> testClass;
  private final Optional<String> maybeFailureMessage;

  public TestParametersMethodProcessorTest(
      String name, Class<?> testClass, String failsWithMessage) {
    this.testClass = testClass;
    this.maybeFailureMessage =
        failsWithMessage.isEmpty() ? Optional.absent() : Optional.of(failsWithMessage);
  }

  @Test
  public void test_success() throws Exception {
    assume().that(maybeFailureMessage.isPresent()).isFalse();

    SharedTestUtilitiesJUnit4.runTestsAndAssertNoFailures(newTestRunner());
  }

  @Test
  public void test_failure() throws Exception {
    assume().that(maybeFailureMessage.isPresent()).isTrue();

    Throwable throwable =
        assertThrows(
            Throwable.class,
            () -> SharedTestUtilitiesJUnit4.runTestsAndAssertNoFailures(newTestRunner()));

    assertThat(throwable).hasMessageThat().contains(maybeFailureMessage.get());
  }

  private PluggableTestRunner newTestRunner() throws Exception {
    return new PluggableTestRunner(testClass) {};
  }

  private static ImmutableList<Class<? extends Annotation>> annotationTypes(
      Iterable<Annotation> annotations) {
    return FluentIterable.from(annotations).transform(Annotation::annotationType).toList();
  }
}
