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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertThrows;

import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
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
public class TestParameterMethodProcessorTest {

  @Retention(RUNTIME)
  @interface RunAsTest {
    String failsWithMessage() default "";
  }

  public enum TestEnum {
    ONE,
    TWO,
    THREE,
  }

  @RunAsTest
  public static class StandardNonParameterizedTest extends SuccessfulTestCaseBase {

    @Test
    public void test() {
      storeTestParametersForThisTest("nothing");
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder().put("test", "nothing").build();
    }
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
  public static class MultipleAnnotatedFieldsAndParameters extends SuccessfulTestCaseBase {

    @TestParameter({"ONE"})
    TestEnum a;

    @TestParameter boolean b;
    private final TestEnum c;
    private final boolean d;

    public MultipleAnnotatedFieldsAndParameters(
        @TestParameter({"TWO"}) TestEnum c, @TestParameter boolean d) {
      this.c = c;
      this.d = d;
    }

    @Test
    public void test(@TestParameter({"THREE"}) TestEnum e, @TestParameter boolean f) {
      storeTestParametersForThisTest(a, b, c, d, e, f);
    }

    @Override
    ImmutableMap<String, String> expectedTestNameToStringifiedParameters() {
      return ImmutableMap.<String, String>builder()
          .put("test[ONE,b=false,TWO,d=false,THREE,f=false]", "ONE:false:TWO:false:THREE:false")
          .put("test[ONE,b=false,TWO,d=false,THREE,f=true]", "ONE:false:TWO:false:THREE:true")
          .put("test[ONE,b=false,TWO,d=true,THREE,f=false]", "ONE:false:TWO:true:THREE:false")
          .put("test[ONE,b=false,TWO,d=true,THREE,f=true]", "ONE:false:TWO:true:THREE:true")
          .put("test[ONE,b=true,TWO,d=false,THREE,f=false]", "ONE:true:TWO:false:THREE:false")
          .put("test[ONE,b=true,TWO,d=false,THREE,f=true]", "ONE:true:TWO:false:THREE:true")
          .put("test[ONE,b=true,TWO,d=true,THREE,f=false]", "ONE:true:TWO:true:THREE:false")
          .put("test[ONE,b=true,TWO,d=true,THREE,f=true]", "ONE:true:TWO:true:THREE:true")
          .build();
    }
  }

  @RunAsTest
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

  @RunAsTest
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

  public abstract static class BaseClassWithSingleTest extends SuccessfulTestCaseBase {
    @Test
    public void testInBase(@TestParameter boolean b) {
      storeTestParametersForThisTest(b);
    }
  }

  @RunAsTest
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

  @RunAsTest
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

  @RunAsTest
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
    public void withEnum(@TestParameter("TWO") TestEnum param1) {
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

  @RunAsTest(
      failsWithMessage =
          "Could not find a no-arg constructor for NonStaticProvider, probably because it is a"
              + " not-static inner class. You can fix this by making NonStaticProvider static")
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

  @RunAsTest(failsWithMessage = "parameter number 2 is not annotated with @TestParameter")
  public static class NotAllParametersAnnotated {
    @Test
    public void test1(@TestParameter boolean bool, boolean bool2) {}
  }

  @RunAsTest(failsWithMessage = "Method test() should be public")
  public static class ErrorNonPublicTestMethod {

    @Test
    void test(@TestParameter boolean b) {}
  }

  @RunAsTest(failsWithMessage = "Expected exactly one constructor, but got []")
  public static class ErrorPackagePrivateConstructor {
    ErrorPackagePrivateConstructor() {}

    @Test
    public void test1() {}
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return Arrays.stream(TestParameterMethodProcessorTest.class.getClasses())
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

  public TestParameterMethodProcessorTest(
      String name, Class<?> testClass, String failsWithMessage) {
    this.testClass = testClass;
    this.maybeFailureMessage =
        failsWithMessage.isEmpty() ? Optional.absent() : Optional.of(failsWithMessage);
  }

  @Test
  public void test_success() throws Exception {
    assume().that(maybeFailureMessage.isPresent()).isFalse();

    SharedTestUtilitiesJUnit4.runTestsAndAssertNoFailures(new PluggableTestRunner(testClass) {});
  }

  @Test
  public void test_failure() throws Exception {
    assume().that(maybeFailureMessage.isPresent()).isTrue();

    Throwable throwable =
        assertThrows(
            Throwable.class,
            () ->
                SharedTestUtilitiesJUnit4.runTestsAndAssertNoFailures(
                    new PluggableTestRunner(testClass) {}));

    assertWithMessage("Full stack trace: %s", Throwables.getStackTraceAsString(throwable))
        .that(throwable)
        .hasMessageThat()
        .contains(maybeFailureMessage.get());
  }

  private static ImmutableList<Class<? extends Annotation>> annotationTypes(
      Iterable<Annotation> annotations) {
    return FluentIterable.from(annotations).transform(Annotation::annotationType).toList();
  }
}
