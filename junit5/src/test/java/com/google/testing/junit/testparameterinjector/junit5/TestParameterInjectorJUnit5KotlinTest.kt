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

package com.google.testing.junit.testparameterinjector.junit5

import com.google.common.base.Throwables
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.Arrays
import java.util.stream.Stream
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.TestExecutionResult.Status
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory

/** Tests the full feature set of TestParameterInjector with JUnit5 (Jupiter) in Kotlin. */
class TestParameterInjectorJUnit5KotlinTest {

  abstract class SuccessfulTestCaseBase {
    private lateinit var testName: String

    @BeforeEach
    private fun storeTestName(testInfo: TestInfo) {
      testName = testInfo.displayName
    }

    protected fun storeTestParametersForThisTest(vararg params: Any?) {
      if (testNameToStringifiedParameters == null) {
        testNameToStringifiedParameters = mutableMapOf()
        // Copying this into a static field because @AfterAll methods have to be static
        expectedTestNameToStringifiedParameters = expectedTestNameToStringifiedParameters()
      }
      check(!testNameToStringifiedParameters!!.containsKey(testName)) {
        "Parameters for the test with name '$testName' are already stored." +
          " This might mean that there are duplicate test names"
      }
      testNameToStringifiedParameters!![testName] = params.map { it.toString() }.joinToString(":")
    }

    abstract fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String>

    companion object {
      private var testNameToStringifiedParameters: MutableMap<String, String>? = null
      private var expectedTestNameToStringifiedParameters: ImmutableMap<String, String>? = null

      @BeforeAll
      @JvmStatic
      internal fun checkStaticFieldAreNull() {
        check(testNameToStringifiedParameters == null)
        check(expectedTestNameToStringifiedParameters == null)
      }

      @AfterAll
      @JvmStatic
      internal fun completedAllTests() {
        try {
          assertWithMessage(toCopyPastableJavaString(testNameToStringifiedParameters!!))
            .that(testNameToStringifiedParameters)
            .isEqualTo(expectedTestNameToStringifiedParameters)
        } finally {
          testNameToStringifiedParameters = null
          expectedTestNameToStringifiedParameters = null
        }
      }
    }
  }

  // ********** Test cases from TestParameterInjectorKotlinTest ********** //
  @RunAsTest
  class SimpleCases_WithoutExplicitConstructor : SuccessfulTestCaseBase() {

    @Test
    fun withoutCustomAnnotation() {
      storeTestParametersForThisTest()
    }

    @TestParameterInjectorTest
    fun withoutParameters() {
      storeTestParametersForThisTest()
    }

    @TestParameterInjectorTest
    @TestParameters("{name: 1, number: 3.3}")
    @TestParameters("{name: abc, number: 5}")
    fun withParameters_success(name: String, number: Double) {
      storeTestParametersForThisTest(name, number)
    }

    @TestParameterInjectorTest
    fun withParameter_success(
      @TestParameter(value = ["2", "xyz"]) name: String,
      @TestParameter bool: Boolean,
    ) {
      storeTestParametersForThisTest(name, bool)
    }

    override fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String> {
      return ImmutableMap.builder<String, String>()
        .put("withoutCustomAnnotation()", "")
        .put("withoutParameters", "")
        .put("withParameters_success[{name: 1, number: 3.3}]", "1:3.3")
        .put("withParameters_success[{name: abc, number: 5}]", "abc:5.0")
        .put("withParameter_success[name=2,bool=false]", "2:false")
        .put("withParameter_success[name=2,bool=true]", "2:true")
        .put("withParameter_success[xyz,bool=false]", "xyz:false")
        .put("withParameter_success[xyz,bool=true]", "xyz:true")
        .build()
    }
  }

  @RunAsTest
  internal class TestParameter_MethodParam : SuccessfulTestCaseBase() {
    @TestParameterInjectorTest
    fun testString(@TestParameter("a", "b") param: String) {
      storeTestParametersForThisTest(param)
    }

    @TestParameterInjectorTest
    fun testEnum_selection(@TestParameter("RED", "GREEN") param: Color) {
      storeTestParametersForThisTest(param)
    }

    @TestParameterInjectorTest
    fun testEnum_all(@TestParameter param: Color) {
      storeTestParametersForThisTest(param)
    }

    @TestParameterInjectorTest
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
    @TestParameterInjectorTest
    fun testString(@TestParameter("a", "b") param: StringValueClass) {
      storeTestParametersForThisTest(param.onlyValue)
    }

    @TestParameterInjectorTest
    fun testEnum_selection(@TestParameter("RED", "GREEN") param: ColorValueClass) {
      storeTestParametersForThisTest(param.onlyValue)
    }

    @TestParameterInjectorTest
    fun testEnum_all(@TestParameter param: ColorValueClass) {
      storeTestParametersForThisTest(param.onlyValue)
    }

    @TestParameterInjectorTest
    fun testMixed(
      @TestParameter("1", "8") width: Int,
      @TestParameter("1", "5.5") height: DoubleValueClass,
    ) {
      storeTestParametersForThisTest(width, height.onlyValue)
    }

    override fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String> {
      return ImmutableMap.builder<String, String>()
        .put("testString-Ebx4h0g[a]", "a")
        .put("testString-Ebx4h0g[b]", "b")
        .put("testMixed-idXy_f8[width=1,height=1.0]", "1:1.0")
        .put("testMixed-idXy_f8[width=1,height=5.5]", "1:5.5")
        .put("testMixed-idXy_f8[width=8,height=1.0]", "8:1.0")
        .put("testMixed-idXy_f8[width=8,height=5.5]", "8:5.5")
        .put("testEnum_all-XIxCwdc[RED]", "RED")
        .put("testEnum_all-XIxCwdc[BLUE]", "BLUE")
        .put("testEnum_all-XIxCwdc[GREEN]", "GREEN")
        .put("testEnum_selection-XIxCwdc[RED]", "RED")
        .put("testEnum_selection-XIxCwdc[GREEN]", "GREEN")
        .buildOrThrow()
    }
  }

  @RunAsTest
  internal class TestParameter_Field : SuccessfulTestCaseBase() {
    @TestParameter("1", "2") var width: Int? = null

    @TestParameterInjectorTest
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

    @TestParameterInjectorTest
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

    @TestParameterInjectorTest
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

    @TestParameterInjectorTest
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

    @TestParameterInjectorTest
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

    @TestParameterInjectorTest
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

    @TestParameterInjectorTest
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
    @TestParameterInjectorTest
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
    @TestParameterInjectorTest
    fun test(width: Int, height: DoubleValueClass) {
      storeTestParametersForThisTest(width, height.onlyValue)
    }

    override fun expectedTestNameToStringifiedParameters(): ImmutableMap<String, String> {
      return ImmutableMap.builder<String, String>()
        .put("test-idXy_f8[{width: 3, height: 8}]", "3:8.0")
        .put("test-idXy_f8[{width: 5, height: 2.5}]", "5:2.5")
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

    @TestParameterInjectorTest
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
      "TestParameter_WithDefaultValues_CombinedExplicitAndDefaultParameter.test():" +
        " @TestParameter annotation found on height with specified value and a default value, which" +
        " is not allowed"
  )
  internal class TestParameter_WithDefaultValues_CombinedExplicitAndDefaultParameter {
    @TestParameterInjectorTest
    fun test(@TestParameter("11", "12") height: Int = KotlinTestParameters.testValues(11, 12)) {}
  }

  @RunAsTest(
    failsWithMessage =
      "TestParameter_WithDefaultValues_ParameterListIsEmpty.test(): A default parameter value" +
        " returned an empty value list. This is not allowed, because it would cause the test" +
        " to be skipped."
  )
  internal class TestParameter_WithDefaultValues_ParameterListIsEmpty {
    @TestParameterInjectorTest
    fun test(@TestParameter height: Int = KotlinTestParameters.testValuesIn(listOf())) {}
  }

  @RunAsTest(
    failsWithMessage =
      "TestParameter_WithDefaultValues_NotViaTestValues.test():" +
        " Expected all default parameter values to be produced by a call to" +
        " KotlinTestParameters.testValues()"
  )
  internal class TestParameter_WithDefaultValues_NotViaTestValues {
    @TestParameterInjectorTest fun test(@TestParameter height: Int = 12) {}
  }

  // ********** Test infrastructure from TestParameterInjectorJUnit5Test ********** //

  @ParameterizedTest(name = "{0}")
  @MethodSource("provideTestClassesThatExpectSuccess")
  fun runTest_success(testClass: Class<*>) {
    val listener = FailureListener()
    val request =
      LauncherDiscoveryRequestBuilder.request()
        .selectors(DiscoverySelectors.selectClass(testClass))
        .build()
    val launcher = LauncherFactory.create()
    launcher.registerTestExecutionListeners(listener)
    launcher.execute(request)

    assertNoFailures(listener.failures)
  }

  @ParameterizedTest(name = "{0} fails with '{1}'")
  @MethodSource("provideTestClassesThatExpectFailure")
  fun runTest_failure(testClass: Class<*>, failureMessage: String) {
    val listener = FailureListener()
    val request =
      LauncherDiscoveryRequestBuilder.request()
        .selectors(DiscoverySelectors.selectClass(testClass))
        .build()
    val launcher = LauncherFactory.create()
    launcher.registerTestExecutionListeners(listener)
    launcher.execute(request)

    assertThat(listener.failures).hasSize(1)
    assertThat(listener.failures.single()).contains(failureMessage)
  }

  class FailureListener : TestExecutionListener {
    val failures = mutableListOf<String>()

    override fun executionFinished(
      testIdentifier: TestIdentifier,
      testExecutionResult: TestExecutionResult,
    ) {
      if (testExecutionResult.status != Status.SUCCESSFUL) {
        failures.add(
          "${testIdentifier.displayName} --> ${testExecutionResult.throwable.map { Throwables.getStackTraceAsString(it) }.orElseGet { testExecutionResult.toString() }}"
        )
      }
    }
  }

  @Target(CLASS)
  @Retention(RUNTIME)
  internal annotation class RunAsTest(val failsWithMessage: String = "")

  // ********** Test subtypes from TestParameterInjectorKotlinTest ********** //
  enum class Color {
    RED,
    BLUE,
    GREEN,
  }

  @JvmInline value class ColorValueClass(val onlyValue: Color)

  @JvmInline value class StringValueClass(val onlyValue: String)

  @JvmInline value class DoubleValueClass(val onlyValue: Double)

  data class PointDataClass(val x: Double, val y: Double)

  companion object {
    @JvmStatic
    fun provideTestClassesThatExpectSuccess(): Stream<Class<*>> {
      return Arrays.stream(TestParameterInjectorJUnit5KotlinTest::class.java.declaredClasses)
        .filter { cls ->
          cls.isAnnotationPresent(RunAsTest::class.java) &&
            cls.getAnnotation(RunAsTest::class.java).failsWithMessage.isEmpty()
        }
    }

    @JvmStatic
    fun provideTestClassesThatExpectFailure(): Stream<Arguments> {
      return Arrays.stream(TestParameterInjectorJUnit5KotlinTest::class.java.declaredClasses)
        .filter { cls ->
          cls.isAnnotationPresent(RunAsTest::class.java) &&
            cls.getAnnotation(RunAsTest::class.java).failsWithMessage.isNotEmpty()
        }
        .map { cls -> Arguments.of(cls, cls.getAnnotation(RunAsTest::class.java).failsWithMessage) }
    }

    private fun assertNoFailures(failures: List<String>) {
      if (failures.size == 1) {
        throw AssertionError(failures.single())
      } else if (failures.size > 1) {
        throw AssertionError(
          "Test failed unexpectedly:\n\n${failures.joinToString("\n------------------------------------\n")}"
        )
      }
    }

    private fun toCopyPastableJavaString(map: Map<String, String>): String {
      val resultBuilder = StringBuilder()
      resultBuilder.append("\n----------------------\n")
      resultBuilder.append("ImmutableMap.<String, String>builder()\n")
      map.forEach { (key, value) -> resultBuilder.append("    .put(\"$key\", \"$value\")\n") }
      resultBuilder.append("    .build()\n")
      resultBuilder.append("----------------------\n")
      return resultBuilder.toString()
    }
  }
}
