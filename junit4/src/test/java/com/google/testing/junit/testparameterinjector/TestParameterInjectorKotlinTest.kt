/*
 * Copyright 2022 Google Inc.
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

package com.google.testing.junit.testparameterinjector

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import com.google.testing.junit.testparameterinjector.SharedTestUtilitiesJUnit4.SuccessfulTestCaseBase
import java.util.Arrays
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class TestParameterInjectorKotlinTest {

  // ********** Test cases ********** //
  // These test classes are all expected to run successfully

  @RunAsTest
  internal class TestParameter_MethodParam : SuccessfulTestCaseBase() {
    @Test
    fun testString(@TestParameter("a", "b") param: String) {
      storeTestParametersForThisTest(param)
    }

    @Test
    fun testEnum_selection(@TestParameter("RED", "GREEN") param: Color) {
      storeTestParametersForThisTest(param)
    }

    @Test
    fun testEnum_all(@TestParameter param: Color) {
      storeTestParametersForThisTest(param)
    }

    @Test
    fun testMultiple(
      @TestParameter("1", "8") width: Int,
      @TestParameter("1", "5.5") height: Double,
    ) {
      storeTestParametersForThisTest(width, height)
    }

    override fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String> {
      return ImmutableMap.builder<String, String>()
        .put("testString[a]", "a")
        .put("testString[b]", "b")
        .put("testEnum_selection[RED]", "RED")
        .put("testEnum_selection[GREEN]", "GREEN")
        .put("testEnum_all[RED]", "RED")
        .put("testEnum_all[BLUE]", "BLUE")
        .put("testEnum_all[GREEN]", "GREEN")
        .put("testMultiple[width=1,height=1.0]", "1:1.0")
        .put("testMultiple[width=1,height=5.5]", "1:5.5")
        .put("testMultiple[width=8,height=1.0]", "8:1.0")
        .put("testMultiple[width=8,height=5.5]", "8:5.5")
        .buildOrThrow()
    }
  }

  @RunAsTest
  internal class TestParameter_MethodParam_WithValueClasses : SuccessfulTestCaseBase() {
    @Test
    fun testString(@TestParameter("a", "b") param: StringValueClass) {
      storeTestParametersForThisTest(param.onlyValue)
    }

    @Test
    fun testEnum_selection(@TestParameter("RED", "GREEN") param: ColorValueClass) {
      storeTestParametersForThisTest(param.onlyValue)
    }

    @Test
    fun testEnum_all(@TestParameter param: ColorValueClass) {
      storeTestParametersForThisTest(param.onlyValue)
    }

    @Test
    fun testMixed(
      @TestParameter("1", "8") width: Int,
      @TestParameter("1", "5.5") height: DoubleValueClass,
    ) {
      storeTestParametersForThisTest(width, height.onlyValue)
    }

    override fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String> {
      return ImmutableMap.builder<String, String>()
        .put("testString-HMW45e8[a]", "a")
        .put("testString-HMW45e8[b]", "b")
        .put("testEnum_selection-fiSAjMM[RED]", "RED")
        .put("testEnum_selection-fiSAjMM[GREEN]", "GREEN")
        .put("testEnum_all-fiSAjMM[RED]", "RED")
        .put("testEnum_all-fiSAjMM[BLUE]", "BLUE")
        .put("testEnum_all-fiSAjMM[GREEN]", "GREEN")
        .put("testMixed-lvZ97mM[width=1,height=1.0]", "1:1.0")
        .put("testMixed-lvZ97mM[width=1,height=5.5]", "1:5.5")
        .put("testMixed-lvZ97mM[width=8,height=1.0]", "8:1.0")
        .put("testMixed-lvZ97mM[width=8,height=5.5]", "8:5.5")
        .buildOrThrow()
    }
  }

  @RunAsTest
  internal class TestParameter_Field : SuccessfulTestCaseBase() {
    @TestParameter("1", "2") var width: Int? = null

    @Test
    fun test() {
      storeTestParametersForThisTest(width)
    }

    override fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String> {
      return ImmutableMap.builder<String, String>()
        .put("test[width=1]", "1")
        .put("test[width=2]", "2")
        .buildOrThrow()
    }
  }

  @RunAsTest
  internal class TestParameter_Field_WithValueClass : SuccessfulTestCaseBase() {
    @TestParameter(valuesProvider = DoubleValueClassProvider::class)
    var width: DoubleValueClass? = null

    @Test
    fun test() {
      storeTestParametersForThisTest(width?.onlyValue)
    }

    override fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String> {
      return ImmutableMap.builder<String, String>()
        .put("test[DoubleValueClass(onlyValue=1.0)]", "1.0")
        .put("test[DoubleValueClass(onlyValue=2.5)]", "2.5")
        .buildOrThrow()
    }

    private class DoubleValueClassProvider : TestParameterValuesProvider() {
      override fun provideValues(context: Context): List<DoubleValueClass> {
        return ImmutableList.of(DoubleValueClass(1.0), DoubleValueClass(2.5))
      }
    }
  }

  @RunAsTest
  internal class TestParameter_ConstructorParam : SuccessfulTestCaseBase {
    val width: Int

    constructor(@TestParameter("1", "2") width: Int) {
      this.width = width
    }

    @Test
    fun test() {
      storeTestParametersForThisTest(width)
    }

    override fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String> {
      return ImmutableMap.builder<String, String>()
        .put("test[width=1]", "1")
        .put("test[width=2]", "2")
        .buildOrThrow()
    }
  }

  @RunAsTest
  internal class TestParameter_PrimaryConstructorParam(
    @TestParameter private val testBoolean: Boolean
  ) : SuccessfulTestCaseBase() {

    @Test
    fun testWithPrimaryConstructorParam() {
      storeTestParametersForThisTest(testBoolean)
    }

    override fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String> {
      return ImmutableMap.builder<String, String>()
        .put("testWithPrimaryConstructorParam[testBoolean=false]", "false")
        .put("testWithPrimaryConstructorParam[testBoolean=true]", "true")
        .buildOrThrow()
    }
  }

  @RunAsTest
  internal class TestParameter_PrimaryConstructorParamMixedWithField(
    @TestParameter private val testBoolean1: Boolean
  ) : SuccessfulTestCaseBase() {
    @TestParameter private var testBoolean2: Boolean = false
    @TestParameter private var testBoolean3: Boolean = false

    @Test
    fun testWithPrimaryConstructorParam() {
      storeTestParametersForThisTest(testBoolean1, testBoolean2)
    }

    override fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String> {
      return ImmutableMap.builder<String, String>()
        .put(
          "testWithPrimaryConstructorParam[testBoolean2=false,testBoolean3=false,testBoolean1=false]",
          "false:false",
        )
        .put(
          "testWithPrimaryConstructorParam[testBoolean2=false,testBoolean3=false,testBoolean1=true]",
          "true:false",
        )
        .put(
          "testWithPrimaryConstructorParam[testBoolean2=false,testBoolean3=true,testBoolean1=false]",
          "false:false",
        )
        .put(
          "testWithPrimaryConstructorParam[testBoolean2=false,testBoolean3=true,testBoolean1=true]",
          "true:false",
        )
        .put(
          "testWithPrimaryConstructorParam[testBoolean2=true,testBoolean3=false,testBoolean1=false]",
          "false:true",
        )
        .put(
          "testWithPrimaryConstructorParam[testBoolean2=true,testBoolean3=false,testBoolean1=true]",
          "true:true",
        )
        .put(
          "testWithPrimaryConstructorParam[testBoolean2=true,testBoolean3=true,testBoolean1=false]",
          "false:true",
        )
        .put(
          "testWithPrimaryConstructorParam[testBoolean2=true,testBoolean3=true,testBoolean1=true]",
          "true:true",
        )
        .buildOrThrow()
    }
  }

  @RunAsTest
  internal class TestParameter_WithDefaultValues_OnMethod() : SuccessfulTestCaseBase() {

    @Test
    fun test(
      @TestParameter width: Int = KotlinTestParameters.testValues(5, 6),
      @TestParameter("11") height: Int,
      @TestParameter
      middle: PointDataClass = KotlinTestParameters.testValuesIn(listOf(PointDataClass(1.0, 2.0))),
      @TestParameter
      hasDepth: Boolean =
        KotlinTestParameters.namedTestValues("hasDepth" to true, "noDepth" to false),
      @TestParameter("false") isCircular: Boolean,
    ) {
      storeTestParametersForThisTest(width, height, middle, hasDepth, isCircular)
    }

    override fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String> {
      return ImmutableMap.builder<String, String>()
        .put(
          "test[width=5,height=11,PointDataClass(x=1.0, y=2.0),hasDepth,isCircular=false]",
          "5:11:PointDataClass(x=1.0, y=2.0):true:false",
        )
        .put(
          "test[width=5,height=11,PointDataClass(x=1.0, y=2.0),noDepth,isCircular=false]",
          "5:11:PointDataClass(x=1.0, y=2.0):false:false",
        )
        .put(
          "test[width=6,height=11,PointDataClass(x=1.0, y=2.0),hasDepth,isCircular=false]",
          "6:11:PointDataClass(x=1.0, y=2.0):true:false",
        )
        .put(
          "test[width=6,height=11,PointDataClass(x=1.0, y=2.0),noDepth,isCircular=false]",
          "6:11:PointDataClass(x=1.0, y=2.0):false:false",
        )
        .buildOrThrow()
    }
  }

  @RunAsTest
  internal class TestParameter_WithDefaultValues_OnMethod_withParameterizedConstructor(
    @TestParameter("1", "2") private val width: Int
  ) : SuccessfulTestCaseBase() {

    @Test
    fun test(
      @TestParameter height: Int = KotlinTestParameters.testValues(11, 12),
      @TestParameter("false") isCircular: Boolean,
    ) {
      storeTestParametersForThisTest(width, height, isCircular)
    }

    override fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String> {
      return ImmutableMap.builder<String, String>()
        .put("test[width=1,height=11,isCircular=false]", "1:11:false")
        .put("test[width=1,height=12,isCircular=false]", "1:12:false")
        .put("test[width=2,height=11,isCircular=false]", "2:11:false")
        .put("test[width=2,height=12,isCircular=false]", "2:12:false")
        .buildOrThrow()
    }
  }

  @RunAsTest
  internal class TestParameters_MethodParam : SuccessfulTestCaseBase() {
    @TestParameters("{width: 3, height: 8}")
    @TestParameters("{width: 5, height: 2.5}")
    @Test
    fun test(width: Int, height: Double) {
      storeTestParametersForThisTest(width, height)
    }

    override fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String> {
      return ImmutableMap.builder<String, String>()
        .put("test[{width: 3, height: 8}]", "3:8.0")
        .put("test[{width: 5, height: 2.5}]", "5:2.5")
        .buildOrThrow()
    }
  }

  @RunAsTest
  internal class TestParameters_MethodParam_WithValueClasses : SuccessfulTestCaseBase() {
    @TestParameters("{width: 3, height: 8}")
    @TestParameters("{width: 5, height: 2.5}")
    @Test
    fun test(width: Int, height: DoubleValueClass) {
      storeTestParametersForThisTest(width, height.onlyValue)
    }

    override fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String> {
      return ImmutableMap.builder<String, String>()
        .put("test-lvZ97mM[{width: 3, height: 8}]", "3:8.0")
        .put("test-lvZ97mM[{width: 5, height: 2.5}]", "5:2.5")
        .buildOrThrow()
    }
  }

  @RunAsTest
  internal class TestParameters_ConstructorParam : SuccessfulTestCaseBase {
    val width: Int

    @TestParameters("{width: 1}")
    @TestParameters("{width: 2}")
    constructor(width: Int) {
      this.width = width
    }

    @Test
    fun test() {
      storeTestParametersForThisTest(width)
    }

    override fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String> {
      return ImmutableMap.builder<String, String>()
        .put("test[{width: 1}]", "1")
        .put("test[{width: 2}]", "2")
        .buildOrThrow()
    }
  }

  @RunAsTest(
    failsWithMessage =
      "TestParameter_WithDefaultValues_CombinedExplicitAndDefaultParameter.test:" +
        " @TestParameter annotation found on height with specified value and a default value, which" +
        " is not allowed"
  )
  internal class TestParameter_WithDefaultValues_CombinedExplicitAndDefaultParameter {
    @Test
    fun test(@TestParameter("11", "12") height: Int = KotlinTestParameters.testValues(11, 12)) {}
  }

  @RunAsTest(
    failsWithMessage =
      "TestParameter_WithDefaultValues_ParameterListIsEmpty.test: A default parameter value" +
        " returned an empty value list. This is not allowed, because it would cause the test" +
        " to be skipped."
  )
  internal class TestParameter_WithDefaultValues_ParameterListIsEmpty {
    @Test fun test(@TestParameter height: Int = KotlinTestParameters.testValuesIn(listOf())) {}
  }

  @RunAsTest(
    failsWithMessage =
      "TestParameter_WithDefaultValues_WithUnsupportedConstructor:" +
        " Expected each constructor parameter to be annotated with @TestParameter"
  )
  internal class TestParameter_WithDefaultValues_WithUnsupportedConstructor(
    private val width: Int
  ) {
    @Test fun test(@TestParameter height: Int = KotlinTestParameters.testValues(11, 12)) {}
  }

  // ********** Test infrastructure ********** //

  private val testClass: Class<*>

  constructor(@Suppress("UNUSED_PARAMETER") testName: String?, testClass: Class<*>) {
    this.testClass = testClass
  }

  @Test
  fun test_success() {
    assume().that(runAsTsetAnnotation().failsWithMessage).isEmpty()

    SharedTestUtilitiesJUnit4.runTestsAndAssertNoFailures(
      object : PluggableTestRunner(testClass) {}
    )
  }

  @Test
  fun test_failure() {
    assume().that(runAsTsetAnnotation().failsWithMessage).isNotEmpty()

    val throwable =
      assertThrows(Throwable::class.java) {
        SharedTestUtilitiesJUnit4.runTestsAndAssertNoFailures(
          object : PluggableTestRunner(testClass) {}
        )
      }

    assertThat(throwable).hasMessageThat().contains(runAsTsetAnnotation().failsWithMessage)
  }

  private fun runAsTsetAnnotation(): RunAsTest = testClass.getAnnotation(RunAsTest::class.java)!!

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun parameters(): Collection<Array<Any>> {
      return Arrays.stream(TestParameterInjectorKotlinTest::class.java.classes)
        .filter { cls: Class<*> -> cls.isAnnotationPresent(RunAsTest::class.java) }
        .map { cls: Class<*> -> arrayOf<Any>(cls.simpleName, cls) }
        .collect(ImmutableList.toImmutableList())
    }
  }

  @Target(CLASS) @Retention(RUNTIME) annotation class RunAsTest(val failsWithMessage: String = "")

  // ********** Test subtypes ********** //
  enum class Color {
    RED,
    BLUE,
    GREEN,
  }

  @JvmInline value class ColorValueClass(val onlyValue: Color)

  @JvmInline value class StringValueClass(val onlyValue: String)

  @JvmInline value class DoubleValueClass(val onlyValue: Double)

  data class PointDataClass(val x: Double, val y: Double)
}
