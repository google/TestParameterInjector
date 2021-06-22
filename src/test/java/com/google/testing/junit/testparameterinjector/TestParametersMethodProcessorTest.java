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
import static com.google.common.truth.Truth.assertThat;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameters.TestParametersValues;
import com.google.testing.junit.testparameterinjector.TestParameters.TestParametersValuesProvider;
import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

@RunWith(Parameterized.class)
public class TestParametersMethodProcessorTest {

  @Retention(RUNTIME)
  @interface RunAsTest {}

  public enum TestEnum {
    ONE,
    TWO,
    THREE;
  }

  @RunAsTest
  public static class SimpleMethodAnnotation {
    @Rule public TestName testName = new TestName();

    private static Map<String, String> testNameToStringifiedParametersMap;

    @BeforeClass
    public static void resetStaticState() {
      testNameToStringifiedParametersMap = new LinkedHashMap<>();
    }

    @Test
    @TestParameters({
      "{testEnum: ONE, testLong: 11, testBoolean: false, testString: ABC}",
      "{testEnum: TWO,\ntestLong: 22,\ntestBoolean: true,\r\n\r\n testString: 'DEF'}",
      "{testEnum: null, testLong: 33, testBoolean: false, testString: null}",
    })
    public void test(TestEnum testEnum, long testLong, boolean testBoolean, String testString) {
      testNameToStringifiedParametersMap.put(
          testName.getMethodName(),
          String.format("%s,%s,%s,%s", testEnum, testLong, testBoolean, testString));
    }

    @Test
    @TestParameters({
      "{testString: ABC}",
      "{testString: 'This is a very long string (240 characters) that would normally cause"
          + " Sponge+Tin to exceed the filename limit of 255 characters."
          + " ================================================================================="
          + "=============='}"
    })
    public void test2_withLongNames(String testString) {
      testNameToStringifiedParametersMap.put(testName.getMethodName(), testString);
    }

    @Test
    @TestParameters({
      "{testEnums: [ONE, TWO, THREE], testLongs: [11, 4], testBooleans: [false, true],"
          + " testStrings: [ABC, '123']}",
      "{testEnums: [TWO],\ntestLongs: [22],\ntestBooleans: [true],\r\n\r\n testStrings: ['DEF']}",
      "{testEnums: [], testLongs: [], testBooleans: [], testStrings: []}",
    })
    public void test3_withRepeatedParams(
        List<TestEnum> testEnums,
        List<Long> testLongs,
        List<Boolean> testBooleans,
        List<String> testStrings) {
      testNameToStringifiedParametersMap.put(
          testName.getMethodName(),
          String.format("%s,%s,%s,%s", testEnums, testLongs, testBooleans, testStrings));
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testNameToStringifiedParametersMap)
          .containsExactly(
              "test[{testEnum: ONE, testLong: 11, testBoolean: false, testString: ABC}]",
              "ONE,11,false,ABC",
              "test[{testEnum: TWO, testLong: 22, testBoolean: true, testString: 'DEF'}]",
              "TWO,22,true,DEF",
              "test[{testEnum: null, testLong: 33, testBoolean: false, testString: null}]",
              "null,33,false,null",
              "test2_withLongNames[1.{testString: ABC}]",
              "ABC",
              "test2_withLongNames[2.{testString: 'This is a very long string (240 characters)"
                  + " that would normally cause Sponge+Tin...]",
              "This is a very long string (240 characters) that would normally cause Sponge+Tin to"
                  + " exceed the filename limit of 255 characters."
                  + " ================================================================================="
                  + "==============",
              "test3_withRepeatedParams[1.{testEnums: [ONE, TWO, THREE], testLongs: [11, 4],"
                  + " testBooleans: [false, true], testStrings: [...]",
              "[ONE, TWO, THREE],[11, 4],[false, true],[ABC, 123]",
              "test3_withRepeatedParams[2.{testEnums: [TWO], testLongs: [22], testBooleans: [true],"
                  + " testStrings: ['DEF']}]",
              "[TWO],[22],[true],[DEF]",
              "test3_withRepeatedParams[3.{testEnums: [], testLongs: [], testBooleans: [],"
                  + " testStrings: []}]",
              "[],[],[],[]");
    }
  }

  @RunAsTest
  public static class SimpleConstructorAnnotation {

    @Rule public TestName testName = new TestName();

    private static Map<String, String> testNameToStringifiedParametersMap;

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

    @BeforeClass
    public static void resetStaticState() {
      testNameToStringifiedParametersMap = new LinkedHashMap<>();
    }

    @Test
    public void test1() {
      testNameToStringifiedParametersMap.put(
          testName.getMethodName(),
          String.format("%s,%s,%s,%s", testEnum, testLong, testBoolean, testString));
    }

    @Test
    public void test2() {
      testNameToStringifiedParametersMap.put(
          testName.getMethodName(),
          String.format("%s,%s,%s,%s", testEnum, testLong, testBoolean, testString));
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testNameToStringifiedParametersMap)
          .containsExactly(
              "test1[{testEnum: ONE, testLong: 11, testBoolean: false, testString: ABC}]",
              "ONE,11,false,ABC",
              "test1[{testEnum: TWO, testLong: 22, testBoolean: true, testString: DEF}]",
              "TWO,22,true,DEF",
              "test1[{testEnum: null, testLong: 33, testBoolean: false, testString: null}]",
              "null,33,false,null",
              "test2[{testEnum: ONE, testLong: 11, testBoolean: false, testString: ABC}]",
              "ONE,11,false,ABC",
              "test2[{testEnum: TWO, testLong: 22, testBoolean: true, testString: DEF}]",
              "TWO,22,true,DEF",
              "test2[{testEnum: null, testLong: 33, testBoolean: false, testString: null}]",
              "null,33,false,null");
    }
  }

  @RunAsTest
  public static class ConstructorAnnotationWithProvider {

    @Rule public TestName testName = new TestName();

    private static Map<String, TestEnum> testNameToParameterMap;

    private final TestEnum testEnum;

    @TestParameters(valuesProvider = TestEnumValuesProvider.class)
    public ConstructorAnnotationWithProvider(TestEnum testEnum) {
      this.testEnum = testEnum;
    }

    @BeforeClass
    public static void resetStaticState() {
      testNameToParameterMap = new LinkedHashMap<>();
    }

    @Test
    public void test1() {
      testNameToParameterMap.put(testName.getMethodName(), testEnum);
    }

    @Test
    public void test2() {
      testNameToParameterMap.put(testName.getMethodName(), testEnum);
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(testNameToParameterMap)
          .containsExactly(
              "test1[one]", TestEnum.ONE,
              "test1[two]", TestEnum.TWO,
              "test1[null-case]", null,
              "test2[one]", TestEnum.ONE,
              "test2[two]", TestEnum.TWO,
              "test2[null-case]", null);
    }

    private static final class TestEnumValuesProvider implements TestParametersValuesProvider {
      @Override
      public List<TestParametersValues> provideValues() {
        return ImmutableList.of(
            TestParametersValues.builder()
                .name("one")
                .addParameter("testEnum", TestEnum.ONE)
                .build(),
            TestParametersValues.builder()
                .name("two")
                .addParameter("testEnum", TestEnum.TWO)
                .build(),
            TestParametersValues.builder()
                .name("null-case")
                .addParameter("testEnum", null)
                .build());
      }
    }
  }

  public abstract static class BaseClassWithMethodAnnotation {
    @Rule public TestName testName = new TestName();

    static List<String> allTestNames;

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
    @TestParameters({"{testEnum: ONE}", "{testEnum: TWO}"})
    public void testInBase(TestEnum testEnum) {
      allTestNames.add(testName.getMethodName());
    }
  }

  @RunAsTest
  public static class AnnotationInheritedFromBaseClass extends BaseClassWithMethodAnnotation {

    @Test
    @TestParameters({"{testEnum: TWO}", "{testEnum: THREE}"})
    public void testInChild(TestEnum testEnum) {
      allTestNames.add(testName.getMethodName());
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(allTestNames)
          .containsExactly(
              "testInBase[{testEnum: ONE}]",
              "testInBase[{testEnum: TWO}]",
              "testInChild[{testEnum: TWO}]",
              "testInChild[{testEnum: THREE}]");
    }
  }

  @RunAsTest
  public static class MixedWithTestParameterMethodAnnotation {
    @Rule public TestName testName = new TestName();

    private static List<String> allTestNames;
    private static List<String> testNamesThatInvokedBefore;
    private static List<String> testNamesThatInvokedAfter;

    @TestParameters({"{testEnum: ONE}", "{testEnum: TWO}"})
    public MixedWithTestParameterMethodAnnotation(TestEnum testEnum) {}

    @BeforeClass
    public static void resetStaticState() {
      allTestNames = new ArrayList<>();
      testNamesThatInvokedBefore = new ArrayList<>();
      testNamesThatInvokedAfter = new ArrayList<>();
    }

    @Before
    public void setUp() {
      assertThat(allTestNames).doesNotContain(testName.getMethodName());
      testNamesThatInvokedBefore.add(testName.getMethodName());
    }

    @After
    public void tearDown() {
      assertThat(allTestNames).contains(testName.getMethodName());
      testNamesThatInvokedAfter.add(testName.getMethodName());
    }

    @Test
    public void test1(@TestParameter TestEnum testEnum) {
      assertThat(testNamesThatInvokedBefore).contains(testName.getMethodName());
      allTestNames.add(testName.getMethodName());
    }

    @Test
    @TestParameters({"{testString: ABC}", "{testString: DEF}"})
    public void test2(String testString) {
      allTestNames.add(testName.getMethodName());
    }

    @Test
    @TestParameters({
      "{testString: ABC}",
      "{testString: 'This is a very long string (240 characters) that would normally cause"
          + " Sponge+Tin to exceed the filename limit of 255 characters."
          + " ================================================================================="
          + "=============='}"
    })
    public void test3_withLongNames(String testString) {
      allTestNames.add(testName.getMethodName());
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(allTestNames)
          .containsExactly(
              "test1[{testEnum: ONE},ONE]",
              "test1[{testEnum: ONE},TWO]",
              "test1[{testEnum: ONE},THREE]",
              "test1[{testEnum: TWO},ONE]",
              "test1[{testEnum: TWO},TWO]",
              "test1[{testEnum: TWO},THREE]",
              "test2[{testEnum: ONE},{testString: ABC}]",
              "test2[{testEnum: ONE},{testString: DEF}]",
              "test2[{testEnum: TWO},{testString: ABC}]",
              "test2[{testEnum: TWO},{testString: DEF}]",
              "test3_withLongNames[{testEnum: ONE},1.{testString: ABC}]",
              "test3_withLongNames[{testEnum: ONE},2.{testString: 'This is a very long string"
                  + " (240 characters) that would normally caus...]",
              "test3_withLongNames[{testEnum: TWO},1.{testString: ABC}]",
              "test3_withLongNames[{testEnum: TWO},2.{testString: 'This is a very long string"
                  + " (240 characters) that would normally caus...]");

      assertThat(testNamesThatInvokedBefore).containsExactlyElementsIn(allTestNames).inOrder();
      assertThat(testNamesThatInvokedAfter).containsExactlyElementsIn(allTestNames).inOrder();
    }
  }

  @RunAsTest
  public static class MixedWithTestParameterFieldAnnotation {
    @Rule public TestName testName = new TestName();

    private static List<String> allTestNames;

    @TestParameter TestEnum testEnumA;

    @TestParameters({"{testEnumB: ONE}", "{testEnumB: TWO}"})
    public MixedWithTestParameterFieldAnnotation(TestEnum testEnumB) {}

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
    @TestParameters({"{testString: ABC}", "{testString: DEF}"})
    public void test1(String testString) {
      allTestNames.add(testName.getMethodName());
    }

    @AfterClass
    public static void completedAllParameterizedTests() {
      assertThat(allTestNames)
          .containsExactly(
              "test1[{testEnumB: ONE},{testString: ABC},ONE]",
              "test1[{testEnumB: ONE},{testString: ABC},TWO]",
              "test1[{testEnumB: ONE},{testString: ABC},THREE]",
              "test1[{testEnumB: ONE},{testString: DEF},ONE]",
              "test1[{testEnumB: ONE},{testString: DEF},TWO]",
              "test1[{testEnumB: ONE},{testString: DEF},THREE]",
              "test1[{testEnumB: TWO},{testString: ABC},ONE]",
              "test1[{testEnumB: TWO},{testString: ABC},TWO]",
              "test1[{testEnumB: TWO},{testString: ABC},THREE]",
              "test1[{testEnumB: TWO},{testString: DEF},ONE]",
              "test1[{testEnumB: TWO},{testString: DEF},TWO]",
              "test1[{testEnumB: TWO},{testString: DEF},THREE]");
    }
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return Arrays.stream(TestParametersMethodProcessorTest.class.getClasses())
        .filter(cls -> cls.isAnnotationPresent(RunAsTest.class))
        .map(cls -> new Object[] {cls.getSimpleName(), cls})
        .collect(toImmutableList());
  }

  private final Class<?> testClass;

  public TestParametersMethodProcessorTest(String name, Class<?> testClass) {
    this.testClass = testClass;
  }

  @Test
  public void test() throws Exception {
    List<Failure> failures = PluggableTestRunner.run(newTestRunner());
    assertThat(failures).isEmpty();
  }

  private PluggableTestRunner newTestRunner() throws Exception {
    return new PluggableTestRunner(testClass) {
      @Override
      protected List<TestMethodProcessor> createTestMethodProcessorList() {
        return TestMethodProcessors.createNewParameterizedProcessorsWithLegacyFeatures(
            getTestClass());
      }
    };
  }
}
