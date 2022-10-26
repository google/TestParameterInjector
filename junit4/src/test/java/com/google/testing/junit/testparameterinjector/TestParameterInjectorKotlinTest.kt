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
import com.google.testing.junit.testparameterinjector.SharedTestUtilitiesJUnit4.SuccessfulTestCaseBase
import com.google.testing.junit.testparameterinjector.TestParameter.TestParameterValuesProvider
import java.util.Arrays
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
        .put("testString[a]", "a")
        .put("testString[b]", "b")
        .put("testEnum_selection[RED]", "RED")
        .put("testEnum_selection[GREEN]", "GREEN")
        .put("testEnum_all[RED]", "RED")
        .put("testEnum_all[BLUE]", "BLUE")
        .put("testEnum_all[GREEN]", "GREEN")
        .put("testMixed[width=1,height=1.0]", "1:1.0")
        .put("testMixed[width=1,height=5.5]", "1:5.5")
        .put("testMixed[width=8,height=1.0]", "8:1.0")
        .put("testMixed[width=8,height=5.5]", "8:5.5")
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

    private class DoubleValueClassProvider : TestParameterValuesProvider {
      override fun provideValues(): List<DoubleValueClass> {
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

  // ********** Test infrastructure ********** //

  private val testClass: Class<*>

  constructor(@Suppress("UNUSED_PARAMETER") testName: String?, testClass: Class<*>) {
    this.testClass = testClass
  }

  @Test
  fun test_success() {
    SharedTestUtilitiesJUnit4.runTestsAndAssertNoFailures(
      object : PluggableTestRunner(testClass) {
        override fun createTestMethodProcessorList(): TestMethodProcessorList {
          return TestMethodProcessorList.createNewParameterizedProcessors()
        }
      }
    )
  }

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
  annotation class RunAsTest

  enum class Color {
    RED,
    BLUE,
    GREEN
  }
  @JvmInline value class ColorValueClass(val onlyValue: Color)
  @JvmInline value class StringValueClass(val onlyValue: String)
  @JvmInline value class DoubleValueClass(val onlyValue: Double)
}
