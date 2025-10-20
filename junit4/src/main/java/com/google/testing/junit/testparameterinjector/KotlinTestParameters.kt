/*
 * Copyright 2025 Google Inc.
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

/**
 * Functions for producing test parameter values using Kotlin's default parameter values.
 *
 * Example:
 * ```
 * @Test
 * fun test(
 *   @TestParameter height: Int = testValues(11, 12),
 *   @TestParameter width: Int = testValuesIn(listOf(5, 6)),
 *   @TestParameter price: Double = namedTestValues("free" to 0.0, "expensive" to 100.0),
 *   // The combination with standard parameters (without default values) is also supported:
 *   @TestParameter inStock: Boolean, // {true, false}
 *   @TestParameter productCategory: ProductCategoryEnum, // all enum values are produced
 * ) { ... }
 * ```
 */
// TODO: jnyman - Expose this to the open source version.
internal object KotlinTestParameters {

  /** Specifies the values to be used for a parameter. */
  @JvmName("-testValues") // Invalid JVM name to effectively prohibit Java use
  fun <T> testValues(firstValue: T, vararg otherValues: T): T {
    return testValuesIn(listOf(firstValue, *otherValues))
  }

  /** Specifies the values to be used for a parameter. The given list may not be empty. */
  @JvmName("-testValuesIn") // Invalid JVM name to effectively prohibit Java use
  fun <T> testValuesIn(values: Iterable<T>): T {
    throw KotlinDefaultParameterHolderException(values.map { TestParameterValue.wrap(it) })
  }

  /**
   * Specifies the values to be used for a parameter keyed by name. The parameter names will be used
   * in the test name.
   *
   * Example:
   * ```
   * @Test
   * fun test(
   *   @TestParameter price: Double = namedTestValues("free" to 0.0, "expensive" to 100.0),
   * ) { ... }
   * ```
   *
   * will produce the following test names:
   * - `test[free]`
   * - `test[expensive]`
   */
  @JvmName("-namedTestValues") // Invalid JVM name to effectively prohibit Java use
  fun <T> namedTestValues(firstValue: Pair<String, T>, vararg otherValues: Pair<String, T>): T {
    throw KotlinDefaultParameterHolderException(
      (listOf(firstValue, *otherValues)).map {
        TestParameterValue.wrap(it.second).withName(it.first)
      }
    )
  }

  /**
   * Specifies the values to be used for a parameter keyed by name. The parameter names will be used
   * in the test name.
   */
  @JvmName("-namedTestValuesIn") // Invalid JVM name to effectively prohibit Java use
  fun <T> namedTestValuesIn(values: Map<String, T>): T {
    throw KotlinDefaultParameterHolderException(
      values.entries.map { TestParameterValue.wrap(it.value).withName(it.key) }
    )
  }

  internal class KotlinDefaultParameterHolderException(
    val testParameterValues: List<TestParameterValue>
  ) :
    RuntimeException(
      "KotlinTestParameters.testValues() or KotlinTestParameters.namedTestValues() were used " +
        "outside of their intended use case."
    )
}
