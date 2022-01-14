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

package com.google.testing.junit.testparameterinjector.junit5_otherpackage;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

import com.google.common.base.CharMatcher;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.junit5.TestParameter;
import com.google.testing.junit.testparameterinjector.junit5.TestParameter.TestParameterValuesProvider;
import com.google.testing.junit.testparameterinjector.junit5.TestParameterInjectorTest;
import com.google.testing.junit.testparameterinjector.junit5.TestParameters;
import com.google.testing.junit.testparameterinjector.junit5.TestParameters.TestParametersValues;
import com.google.testing.junit.testparameterinjector.junit5.TestParameters.TestParametersValuesProvider;
import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestExecutionResult.Status;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/** Tests the full feature set of TestParameterInjector with JUnit5 (Jupiter). */
class TestParameterInjectorJUnit5Test {

  abstract static class SuccessfulTestCaseBase {
    private static Map<String, String> testNameToStringifiedParameters;
    private static ImmutableMap<String, String> expectedTestNameToStringifiedParameters;
    private String testName;

    @BeforeAll
    private static void checkStaticFieldAreNull() {
      checkState(testNameToStringifiedParameters == null);
      checkState(expectedTestNameToStringifiedParameters == null);
    }

    @BeforeEach
    private void storeTestName(org.junit.jupiter.api.TestInfo testInfo) {
      testName = testInfo.getDisplayName();
    }

    final void storeTestParametersForThisTest(Object... params) {
      if (testNameToStringifiedParameters == null) {
        testNameToStringifiedParameters = new LinkedHashMap<>();
        // Copying this into a static field because @AfterAll methods have to be static
        expectedTestNameToStringifiedParameters = expectedTestNameToStringifiedParameters();
      }
      checkState(
          !testNameToStringifiedParameters.containsKey(testName),
          "Parameters for the test with name '%s' are already stored. This might mean that there"
              + " are duplicate test names",
          testName);
      testNameToStringifiedParameters.put(
          testName, stream(params).map(String::valueOf).collect(joining(":")));
    }

    abstract ImmutableMap<String, String> expectedTestNameToStringifiedParameters();

    @AfterAll
    private static void completedAllTests() {
      try {
        assertWithMessage(toCopyPastableJavaString(testNameToStringifiedParameters))
            .that(testNameToStringifiedParameters)
            .isEqualTo(expectedTestNameToStringifiedParameters);
      } finally {
        testNameToStringifiedParameters = null;
        expectedTestNameToStringifiedParameters = null;
      }
    }
  }

  @RunAsTest
  static class SimpleCases_WithoutExplicitConstructor extends SuccessfulTestCaseBase {
    @Test
    void withoutCustomAnnotation() {
      storeTestParametersForThisTest();
    }

    @TestParameterInjectorTest
    void withoutParameters() {
      storeTestParametersForThisTest();
    }

    @TestParameterInjectorTest
    @TestParameters("{name: 1, number: 3.3}")
    @TestParameters("{name: abc, number: 5}")
    void withParameters_success(String name, double number) {
      storeTestParametersForThisTest(name, number);
    }

    @TestParameterInjectorTest
    void withParameter_success(
        @TestParameter({"2", "xyz"}) String name, @TestParameter boolean bool) {
      storeTestParametersForThisTest(name, bool);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("withoutCustomAnnotation()", "")
          .put("withoutParameters", "")
          .put("withParameters_success[{name: 1, number: 3.3}]", "1:3.3")
          .put("withParameters_success[{name: abc, number: 5}]", "abc:5.0")
          .put("withParameter_success[2,bool=false]", "2:false")
          .put("withParameter_success[2,bool=true]", "2:true")
          .put("withParameter_success[xyz,bool=false]", "xyz:false")
          .put("withParameter_success[xyz,bool=true]", "xyz:true")
          .build();
    }
  }

  @RunAsTest
  static class SimpleCases_WithParameterizedConstructor_TestParameter
      extends SuccessfulTestCaseBase {
    private final boolean constr;

    @TestParameter({"AAA", "BBB"})
    private String field;

    SimpleCases_WithParameterizedConstructor_TestParameter(@TestParameter boolean constr) {
      this.constr = constr;
    }

    @TestParameterInjectorTest
    void withoutParameters() {
      storeTestParametersForThisTest(constr, field);
    }

    @TestParameterInjectorTest
    @TestParameters("{name: 1}")
    @TestParameters("{name: abc}")
    void withParameters_success(String name) {
      storeTestParametersForThisTest(constr, field, name);
    }

    @TestParameterInjectorTest
    void withParameter_success(@TestParameter({"2", "xyz"}) String name) {
      storeTestParametersForThisTest(constr, field, name);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("withParameters_success[{name: 1},AAA,constr=false]", "false:AAA:1")
          .put("withParameters_success[{name: 1},AAA,constr=true]", "true:AAA:1")
          .put("withParameters_success[{name: 1},BBB,constr=false]", "false:BBB:1")
          .put("withParameters_success[{name: 1},BBB,constr=true]", "true:BBB:1")
          .put("withParameters_success[{name: abc},AAA,constr=false]", "false:AAA:abc")
          .put("withParameters_success[{name: abc},AAA,constr=true]", "true:AAA:abc")
          .put("withParameters_success[{name: abc},BBB,constr=false]", "false:BBB:abc")
          .put("withParameters_success[{name: abc},BBB,constr=true]", "true:BBB:abc")
          .put("withParameter_success[AAA,constr=false,2]", "false:AAA:2")
          .put("withParameter_success[AAA,constr=false,xyz]", "false:AAA:xyz")
          .put("withParameter_success[AAA,constr=true,2]", "true:AAA:2")
          .put("withParameter_success[AAA,constr=true,xyz]", "true:AAA:xyz")
          .put("withParameter_success[BBB,constr=false,2]", "false:BBB:2")
          .put("withParameter_success[BBB,constr=false,xyz]", "false:BBB:xyz")
          .put("withParameter_success[BBB,constr=true,2]", "true:BBB:2")
          .put("withParameter_success[BBB,constr=true,xyz]", "true:BBB:xyz")
          .put("withoutParameters[AAA,constr=false]", "false:AAA")
          .put("withoutParameters[AAA,constr=true]", "true:AAA")
          .put("withoutParameters[BBB,constr=false]", "false:BBB")
          .put("withoutParameters[BBB,constr=true]", "true:BBB")
          .build();
    }
  }

  @RunAsTest
  static class SimpleCases_WithParameterizedConstructor_TestParameters
      extends SuccessfulTestCaseBase {
    private final boolean constr;

    @TestParameter({"AAA", "BBB"})
    private String field;

    @TestParameters("{constr: true}")
    @TestParameters("{constr: false}")
    SimpleCases_WithParameterizedConstructor_TestParameters(boolean constr) {
      this.constr = constr;
    }

    @TestParameterInjectorTest
    void withoutParameters() {
      storeTestParametersForThisTest(constr, field);
    }

    @TestParameterInjectorTest
    @TestParameters("{name: 1}")
    @TestParameters("{name: abc}")
    void withParameters_success(String name) {
      storeTestParametersForThisTest(constr, field, name);
    }

    @TestParameterInjectorTest
    void withParameter_success(@TestParameter({"2", "xyz"}) String name) {
      storeTestParametersForThisTest(constr, field, name);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("withParameters_success[{constr: true},{name: 1},AAA]", "true:AAA:1")
          .put("withParameters_success[{constr: true},{name: 1},BBB]", "true:BBB:1")
          .put("withParameters_success[{constr: true},{name: abc},AAA]", "true:AAA:abc")
          .put("withParameters_success[{constr: true},{name: abc},BBB]", "true:BBB:abc")
          .put("withParameters_success[{constr: false},{name: 1},AAA]", "false:AAA:1")
          .put("withParameters_success[{constr: false},{name: 1},BBB]", "false:BBB:1")
          .put("withParameters_success[{constr: false},{name: abc},AAA]", "false:AAA:abc")
          .put("withParameters_success[{constr: false},{name: abc},BBB]", "false:BBB:abc")
          .put("withParameter_success[{constr: true},AAA,2]", "true:AAA:2")
          .put("withParameter_success[{constr: true},AAA,xyz]", "true:AAA:xyz")
          .put("withParameter_success[{constr: true},BBB,2]", "true:BBB:2")
          .put("withParameter_success[{constr: true},BBB,xyz]", "true:BBB:xyz")
          .put("withParameter_success[{constr: false},AAA,2]", "false:AAA:2")
          .put("withParameter_success[{constr: false},AAA,xyz]", "false:AAA:xyz")
          .put("withParameter_success[{constr: false},BBB,2]", "false:BBB:2")
          .put("withParameter_success[{constr: false},BBB,xyz]", "false:BBB:xyz")
          .put("withoutParameters[{constr: true},AAA]", "true:AAA")
          .put("withoutParameters[{constr: true},BBB]", "true:BBB")
          .put("withoutParameters[{constr: false},AAA]", "false:AAA")
          .put("withoutParameters[{constr: false},BBB]", "false:BBB")
          .build();
    }
  }

  @RunAsTest
  public static class AdvancedCases_WithValuesProvider extends SuccessfulTestCaseBase {
    private final TestEnum testEnum;

    @TestParameters(valuesProvider = TestEnumValuesProvider.class)
    public AdvancedCases_WithValuesProvider(TestEnum testEnum) {
      this.testEnum = testEnum;
    }

    @TestParameterInjectorTest
    void test1() {
      storeTestParametersForThisTest(testEnum);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test1[one]", "ONE")
          .put("test1[two]", "TWO")
          .put("test1[null-case]", "null")
          .build();
    }
  }

  @RunAsTest
  public static class AdvancedCases_WithValueProvider extends SuccessfulTestCaseBase {
    @TestParameterInjectorTest
    void stringTest(@TestParameter(valuesProvider = TestStringProvider.class) String string) {
      storeTestParametersForThisTest(string);
    }

    @TestParameterInjectorTest
    void charMatcherTest(
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

  public abstract static class BaseClassWithTestParametersMethod extends SuccessfulTestCaseBase {
    @TestParameterInjectorTest
    @TestParameters("{testEnum: ONE}")
    @TestParameters("{testEnum: TWO}")
    void testInBase(TestEnum testEnum) {
      storeTestParametersForThisTest(testEnum);
    }
  }

  @RunAsTest
  public static class AdvancedCases_WithBaseClass_TestParametersMethodInBase
      extends BaseClassWithTestParametersMethod {
    @TestParameterInjectorTest
    @TestParameters({"{testEnum: TWO}", "{testEnum: THREE}"})
    void testInChild(TestEnum testEnum) {
      storeTestParametersForThisTest(testEnum);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("testInBase[{testEnum: ONE}]", "ONE")
          .put("testInBase[{testEnum: TWO}]", "TWO")
          .put("testInChild[{testEnum: TWO}]", "TWO")
          .put("testInChild[{testEnum: THREE}]", "THREE")
          .build();
    }
  }

  public abstract static class BaseClassWithTestParameterMethod extends SuccessfulTestCaseBase {
    @TestParameterInjectorTest
    void testInBase(@TestParameter({"ONE", "TWO"}) TestEnum testEnum) {
      storeTestParametersForThisTest(testEnum);
    }
  }

  @RunAsTest
  public static class AdvancedCases_WithBaseClass_TestParameterMethodInBase
      extends BaseClassWithTestParameterMethod {
    @TestParameterInjectorTest
    @TestParameters({"{testEnum: TWO}", "{testEnum: THREE}"})
    void testInChild(TestEnum testEnum) {
      storeTestParametersForThisTest(testEnum);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("testInBase[ONE]", "ONE")
          .put("testInBase[TWO]", "TWO")
          .put("testInChild[{testEnum: TWO}]", "TWO")
          .put("testInChild[{testEnum: THREE}]", "THREE")
          .build();
    }
  }

  public abstract static class BaseClassWithTestParameterField extends SuccessfulTestCaseBase {
    @TestParameter TestEnum fieldInBase;
  }

  @RunAsTest
  public static class AdvancedCases_WithBaseClass_TestParameterFieldInBase
      extends BaseClassWithTestParameterField {
    @TestParameterInjectorTest
    void testInChild() {
      storeTestParametersForThisTest(fieldInBase);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("testInChild[ONE]", "ONE")
          .put("testInChild[TWO]", "TWO")
          .put("testInChild[THREE]", "THREE")
          .build();
    }
  }

  @RunAsTest(
      failsWithMessage =
          "Either a value or a valuesProvider must be set in @TestParameters on test1()")
  public static class InvalidTest_TestParameters_EmptyAnnotation {
    @TestParameterInjectorTest
    @TestParameters
    void test1() {}
  }

  @RunAsTest(
      failsWithMessage = "Either a value or a valuesProvider must be set in @TestParameters on ")
  public static class InvalidTest_TestParameters_EmptyAnnotationOnConstructor {
    @TestParameters
    public InvalidTest_TestParameters_EmptyAnnotationOnConstructor() {}

    @TestParameterInjectorTest
    void test1() {}
  }

  @RunAsTest(
      failsWithMessage =
          "It is not allowed to specify both value and valuesProvider in"
              + " @TestParameters(value=[{testEnum: ONE}], valuesProvider=TestEnumValuesProvider)"
              + " on test1()")
  public static class InvalidTest_TestParameters_CombiningValueWithProvider {
    @TestParameterInjectorTest
    @TestParameters(value = "{testEnum: ONE}", valuesProvider = TestEnumValuesProvider.class)
    void test1(TestEnum testEnum) {}
  }

  @RunAsTest(
      failsWithMessage =
          "Either a value or a valuesProvider must be set in @TestParameters on test1()")
  public static class InvalidTest_TestParameters_RepeatedAnnotationIsEmpty {
    @TestParameterInjectorTest
    @TestParameters(value = "{testEnum: ONE}")
    @TestParameters
    void test1(TestEnum testEnum) {}
  }

  @RunAsTest(
      failsWithMessage =
          "When specifying more than one @TestParameter for a method/constructor, each annotation"
              + " must have exactly one value. Instead, got 2 values on test1(): [{testEnum: TWO},"
              + " {testEnum: THREE}]")
  public static class InvalidTest_TestParameters_RepeatedAnnotationHasMultipleValues {
    @TestParameterInjectorTest
    @TestParameters(value = "{testEnum: ONE}")
    @TestParameters(value = {"{testEnum: TWO}", "{testEnum: THREE}"})
    void test1(TestEnum testEnum) {}
  }

  @RunAsTest(
      failsWithMessage =
          "Setting a valuesProvider is not supported for methods/constructors with"
              + " multiple @TestParameters annotations on test1()")
  public static class InvalidTest_TestParameters_RepeatedAnnotationHasProvider {
    @TestParameterInjectorTest
    @TestParameters(valuesProvider = TestEnumValuesProvider.class)
    @TestParameters(valuesProvider = TestEnumValuesProvider.class)
    void test1(TestEnum testEnum) {}
  }

  @RunAsTest(
      failsWithMessage =
          "Setting @TestParameters.customName is only allowed if there is exactly one YAML string"
              + " in @TestParameters.value (on test1())")
  public static class InvalidTest_TestParameters_NamedAnnotationHasMultipleValues {
    @TestParameterInjectorTest
    @TestParameters(
        customName = "custom",
        value = {"{testEnum: TWO}", "{testEnum: THREE}"})
    void test1(TestEnum testEnum) {}
  }

  @RunAsTest(
      failsWithMessage =
          "Could not find a no-arg constructor for NonStaticProvider, probably because it is a"
              + " not-static inner class. You can fix this by making NonStaticProvider static.")
  public static class InvalidTest_TestParameter_NonStaticProviderClass {
    @TestParameterInjectorTest
    void test(@TestParameter(valuesProvider = NonStaticProvider.class) int i) {}

    @SuppressWarnings("ClassCanBeStatic")
    class NonStaticProvider implements TestParameterValuesProvider {
      @Override
      public List<?> provideValues() {
        return ImmutableList.of();
      }
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("provideTestClassesThatExpectSuccess")
  void runTest_success(Class<?> testClass) {
    FailureListener listener = new FailureListener();
    LauncherDiscoveryRequest request =
        LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(testClass))
            .build();
    Launcher launcher = LauncherFactory.create();
    launcher.registerTestExecutionListeners(listener);
    launcher.execute(request);

    assertNoFailures(listener.failures);
  }

  @ParameterizedTest(name = "{0} fails with '{1}'")
  @MethodSource("provideTestClassesThatExpectFailure")
  void runTest_failure(Class<?> testClass, String failureMessage) {
    FailureListener listener = new FailureListener();
    LauncherDiscoveryRequest request =
        LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(testClass))
            .build();
    Launcher launcher = LauncherFactory.create();
    TestPlan testPlan = launcher.discover(request);
    launcher.registerTestExecutionListeners(listener);
    launcher.execute(request);

    assertThat(listener.failures).hasSize(1);
    assertThat(getOnlyElement(listener.failures)).contains(failureMessage);
  }

  private static Stream<Class<?>> provideTestClassesThatExpectSuccess() {
    return stream(TestParameterInjectorJUnit5Test.class.getDeclaredClasses())
        .filter(
            cls ->
                cls.isAnnotationPresent(RunAsTest.class)
                    && cls.getAnnotation(RunAsTest.class).failsWithMessage().isEmpty());
  }

  private static Stream<Arguments> provideTestClassesThatExpectFailure() {
    return stream(TestParameterInjectorJUnit5Test.class.getDeclaredClasses())
        .filter(
            cls ->
                cls.isAnnotationPresent(RunAsTest.class)
                    && !cls.getAnnotation(RunAsTest.class).failsWithMessage().isEmpty())
        .map(cls -> Arguments.of(cls, cls.getAnnotation(RunAsTest.class).failsWithMessage()));
  }

  private static void assertNoFailures(List<String> failures) {
    if (failures.size() == 1) {
      throw new AssertionError(getOnlyElement(failures));
    } else if (failures.size() > 1) {
      throw new AssertionError(
          String.format(
              "Test failed unexpectedly:\n\n%s",
              failures.stream().collect(joining("\n------------------------------------\n"))));
    }
  }

  private static String toCopyPastableJavaString(Map<String, String> map) {
    StringBuilder resultBuilder = new StringBuilder();
    resultBuilder.append("\n----------------------\n");
    resultBuilder.append("ImmutableMap.<String, String>builder()\n");
    map.forEach(
        (key, value) ->
            resultBuilder.append(String.format("    .put(\"%s\", \"%s\")\n", key, value)));
    resultBuilder.append("    .build()\n");
    resultBuilder.append("----------------------\n");
    return resultBuilder.toString();
  }

  class FailureListener implements TestExecutionListener {
    final List<String> failures = new ArrayList<>();

    @Override
    public void executionFinished(
        TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
      if (testExecutionResult.getStatus() != Status.SUCCESSFUL) {
        failures.add(
            String.format(
                "%s --> %s",
                testIdentifier.getDisplayName(),
                testExecutionResult.getThrowable().isPresent()
                    ? Throwables.getStackTraceAsString(testExecutionResult.getThrowable().get())
                    : testExecutionResult));
      }
    }
  }

  @Retention(RUNTIME)
  @interface RunAsTest {
    String failsWithMessage() default "";
  }

  public enum TestEnum {
    ONE,
    TWO,
    THREE;
  }

  private static final class TestEnumValuesProvider implements TestParametersValuesProvider {
    @Override
    public List<TestParametersValues> provideValues() {
      return ImmutableList.of(
          TestParametersValues.builder().name("one").addParameter("testEnum", TestEnum.ONE).build(),
          TestParametersValues.builder().name("two").addParameter("testEnum", TestEnum.TWO).build(),
          TestParametersValues.builder().name("null-case").addParameter("testEnum", null).build());
    }
  }
}
