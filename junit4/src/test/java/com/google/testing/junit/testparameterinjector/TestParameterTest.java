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
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.SharedTestUtilitiesJUnit4.SuccessfulTestCaseBase;
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider.Context;
import java.lang.annotation.Annotation;
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

    private final int number1;

    @TestParameter(valuesProvider = TestNumberProvider.class)
    private int number2;

    public WithValuesProvider(
        @TestParameter(valuesProvider = TestNumberProvider.class) int number1) {
      this.number1 = number1;
    }

    @Test
    public void stringTest(
        @TestParameter(valuesProvider = TestStringProvider.class) String stringParam) {
      storeTestParametersForThisTest(number1, number2, stringParam);
    }

    @Test
    public void charMatcherTest(
        @TestParameter(valuesProvider = CharMatcherProvider.class) CharMatcher charMatcher) {
      storeTestParametersForThisTest(number1, number2, charMatcher);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("stringTest[one,one,A]", "1:1:A")
          .put("stringTest[one,one,B]", "1:1:B")
          .put("stringTest[one,one,stringParam=null]", "1:1:null")
          .put("stringTest[one,one,nothing]", "1:1:null")
          .put("stringTest[one,one,wizard]", "1:1:harry")
          .put("stringTest[one,number1=2,A]", "2:1:A")
          .put("stringTest[one,number1=2,B]", "2:1:B")
          .put("stringTest[one,number1=2,stringParam=null]", "2:1:null")
          .put("stringTest[one,number1=2,nothing]", "2:1:null")
          .put("stringTest[one,number1=2,wizard]", "2:1:harry")
          .put("stringTest[number2=2,one,A]", "1:2:A")
          .put("stringTest[number2=2,one,B]", "1:2:B")
          .put("stringTest[number2=2,one,stringParam=null]", "1:2:null")
          .put("stringTest[number2=2,one,nothing]", "1:2:null")
          .put("stringTest[number2=2,one,wizard]", "1:2:harry")
          .put("stringTest[number2=2,number1=2,A]", "2:2:A")
          .put("stringTest[number2=2,number1=2,B]", "2:2:B")
          .put("stringTest[number2=2,number1=2,stringParam=null]", "2:2:null")
          .put("stringTest[number2=2,number1=2,nothing]", "2:2:null")
          .put("stringTest[number2=2,number1=2,wizard]", "2:2:harry")
          .put("charMatcherTest[one,one,CharMatcher.any()]", "1:1:CharMatcher.any()")
          .put("charMatcherTest[one,one,CharMatcher.ascii()]", "1:1:CharMatcher.ascii()")
          .put("charMatcherTest[one,one,CharMatcher.whitespace()]", "1:1:CharMatcher.whitespace()")
          .put("charMatcherTest[one,number1=2,CharMatcher.any()]", "2:1:CharMatcher.any()")
          .put("charMatcherTest[one,number1=2,CharMatcher.ascii()]", "2:1:CharMatcher.ascii()")
          .put(
              "charMatcherTest[one,number1=2,CharMatcher.whitespace()]",
              "2:1:CharMatcher.whitespace()")
          .put("charMatcherTest[number2=2,one,CharMatcher.any()]", "1:2:CharMatcher.any()")
          .put("charMatcherTest[number2=2,one,CharMatcher.ascii()]", "1:2:CharMatcher.ascii()")
          .put(
              "charMatcherTest[number2=2,one,CharMatcher.whitespace()]",
              "1:2:CharMatcher.whitespace()")
          .put("charMatcherTest[number2=2,number1=2,CharMatcher.any()]", "2:2:CharMatcher.any()")
          .put(
              "charMatcherTest[number2=2,number1=2,CharMatcher.ascii()]", "2:2:CharMatcher.ascii()")
          .put(
              "charMatcherTest[number2=2,number1=2,CharMatcher.whitespace()]",
              "2:2:CharMatcher.whitespace()")
          .build();
    }

    private static final class TestNumberProvider extends TestParameterValuesProvider {
      @Override
      public List<?> provideValues(Context context) {
        return newArrayList(value(1).withName("one"), 2);
      }
    }

    private static final class TestStringProvider extends TestParameterValuesProvider {
      @Override
      public List<?> provideValues(Context context) {
        return newArrayList(
            "A", "B", null, value(null).withName("nothing"), value("harry").withName("wizard"));
      }
    }

    private static final class CharMatcherProvider extends TestParameterValuesProvider {
      @Override
      public List<CharMatcher> provideValues(Context context) {
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

  private static ImmutableList<Class<? extends Annotation>> annotationTypes(
      Iterable<Annotation> annotations) {
    return FluentIterable.from(annotations).transform(Annotation::annotationType).toList();
  }
}
