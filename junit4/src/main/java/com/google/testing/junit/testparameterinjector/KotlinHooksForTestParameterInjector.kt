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

import com.google.common.base.Optional
import com.google.common.collect.ImmutableList
import com.google.testing.junit.testparameterinjector.TestParameterInjectorUtils.JavaCompatibilityExecutable
import java.lang.reflect.InvocationTargetException
import kotlin.jvm.kotlin
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod

/**
 * Helper functions for the TestParameterInjector implementation, providing functionality that can
 * only be implemented using Kotlin.
 */
// Only marking as internal for the open source version because the Google version is built in
// separate build targets.
internal object KotlinHooksForTestParameterInjector {

  @JvmStatic
  fun getParameterNames(executable: JavaCompatibilityExecutable): Optional<ImmutableList<String>> {
    return try {
      Optional.of(
        ImmutableList.copyOf(executableToFunction(executable).parameters.mapNotNull { it.name })
      )
    } catch (_: GetJavaExecutableFailureException) {
      // For some Android methods, kFunction.javaMethod fails because of a Kotlin-internal
      // consistency check. This is a workaround to avoid breaking these tests.
      Optional.absent()
    }
  }

  @JvmStatic
  fun hasOptionalParameters(executable: JavaCompatibilityExecutable): Boolean {
    return try {
      executableToFunction(executable).parameters.any {
        it.kind == KParameter.Kind.VALUE && it.isOptional
      }
    } catch (_: GetJavaExecutableFailureException) {
      // For some Android methods, kFunction.javaMethod fails because of a Kotlin-internal
      // consistency check. This is a workaround to avoid breaking these tests.
      false
    }
  }

  /**
   * Returns all combinations of test parameter values for the given executable.
   *
   * Every element of the returned list contains exactly one value for each parameter of
   * [executable], in declaration order. The default value of a parameter is evaluated once for
   * every combination of the parameters that precede it, which is what allows a default value to
   * depend on those earlier parameters.
   */
  @JvmStatic
  fun extractValueCombinations(
    testInstance: Any?,
    executable: JavaCompatibilityExecutable,
    getExplicitValuesByIndex: (Int) -> Optional<ImmutableList<TestParameterValue>>,
    getImplicitValuesByIndex: (Int) -> ImmutableList<TestParameterValue>,
  ): ImmutableList<ImmutableList<IndexedTestParameterValue>> {
    val function = executableToFunction(executable)
    val functionDescription = executable.humanReadableNameSummary
    val parameters = function.parameters.filter { it.kind == KParameter.Kind.VALUE }

    // Sanity check
    require(parameters.size == executable.parameterTypes.size) {
      ("$functionDescription: Number of parameters don't match: kotlinParameters=$parameters," +
        " javaParameterTypes=${executable.parameterTypes.toList()}, function=$function")
    }

    for ((index, parameter) in parameters.withIndex()) {
      require(!parameter.isOptional || !getExplicitValuesByIndex(index).isPresent) {
        "$functionDescription: @TestParameter annotation found on " +
          "${parameter.name} with specified value and a default value, which is not " +
          "allowed: parameter=$parameter"
      }
    }

    // The values of the parameters without a default value. These never depend on other parameters.
    val valuesOfRequiredParameters: Map<KParameter, ImmutableList<TestParameterValue>> =
      parameters
        .withIndex()
        .filter { (_, parameter) -> !parameter.isOptional }
        .associate { (index, parameter) ->
          parameter to
            assertAtLeastOneValue(
              getExplicitValuesByIndex(index).or { getImplicitValuesByIndex(index) },
              functionDescription,
            )
        }

    var combinations: List<Map<KParameter, IndexedTestParameterValue>> = listOf(emptyMap())
    for (parameter in parameters) {
      combinations = combinations.flatMap { combination ->
        val values: List<TestParameterValue> =
          if (parameter.isOptional) {
            getValuesFromDefaultValue(
              testInstance,
              function,
              valuesOfRequiredParameters,
              combination,
              functionDescription,
            )
          } else {
            valuesOfRequiredParameters.getValue(parameter)
          }
        values.mapIndexed { valueIndex, value ->
          combination + (parameter to IndexedTestParameterValue(value, valueIndex))
        }
      }
    }

    return ImmutableList.copyOf(
      combinations.map { combination ->
        ImmutableList.copyOf(parameters.map(combination::getValue))
      }
    )
  }

  private fun executableToFunction(executable: JavaCompatibilityExecutable): KFunction<*> {
    val kClass = executable.declaringClass.kotlin

    val candidates: Collection<*> =
      when (executable.javaReflectVersion) {
        is java.lang.reflect.Method -> kClass.members
        is java.lang.reflect.Constructor<*> -> kClass.constructors
        else -> throw IllegalArgumentException("Unsupported executable type: $executable")
      }
    val matches = mutableListOf<KFunction<*>>()
    var caughtError = false
    for (candidate in candidates) {
      if (candidate is KFunction<*>) {
        val candidateJavaExecutable: Any?
        try {
          // Using if statements instead of when because Kotlin's compiler can optimize the Method
          // and Constructor types into an Executable, which fails on older Android SDKs.
          if (executable.javaReflectVersion is java.lang.reflect.Method) {
            candidateJavaExecutable = candidate.javaMethod
          } else if (executable.javaReflectVersion is java.lang.reflect.Constructor<*>) {
            candidateJavaExecutable = candidate.javaConstructor
          } else {
            throw IllegalArgumentException("Unsupported executable type: $executable")
          }
        } catch (e: java.lang.LinkageError) {
          // Rethrow NoClassDefFoundError and similar linkage errors. We don't want to
          // swallow these.
          throw e
        } catch (_: Error) {
          // For some Android methods, kFunction.javaMethod fails because of a Kotlin-internal
          // consistency check. If this happens, only throw a GetJavaExecutableFailureException if
          // the method we are looking for has this error.
          caughtError = true
          continue
        }
        if (candidateJavaExecutable == executable.javaReflectVersion) {
          matches.add(candidate)
        }
      }
    }
    if (matches.isEmpty() && caughtError) {
      throw GetJavaExecutableFailureException()
    } else {
      return matches.single()
    }
  }

  /**
   * Returns the values produced by the default value expression of the first parameter that is
   * missing from [resolvedValues].
   *
   * @param valuesOfRequiredParameters the values of all parameters without a default value.
   *   [KFunction.callBy] requires a value for each of those, including the ones that come after the
   *   default value being evaluated. Those later values can never be referenced by the default
   *   value expression (Kotlin only allows references to preceding parameters), so an arbitrary
   *   value is passed for them.
   * @param resolvedValues the values of all parameters that precede the one being evaluated.
   */
  private fun getValuesFromDefaultValue(
    testInstance: Any?,
    function: KFunction<*>,
    valuesOfRequiredParameters: Map<KParameter, ImmutableList<TestParameterValue>>,
    resolvedValues: Map<KParameter, IndexedTestParameterValue>,
    functionDescription: String,
  ): ImmutableList<TestParameterValue> {
    try {
      val unused =
        function.callBy(
          if (testInstance == null) {
            mapOf()
          } else {
            mapOf(
              function.parameters.single { it.kind == KParameter.Kind.INSTANCE } to testInstance
            )
          } +
            valuesOfRequiredParameters.mapValues { it.value[0].wrappedValue } +
            resolvedValues.mapValues { it.value.value.wrappedValue }
        )
      throw RuntimeException(
        "$functionDescription: Expected all default parameter values to" +
          " be produced by a call to KotlinTestParameters.testValues()"
      )
    } catch (e: InvocationTargetException) {
      val cause = e.cause
      when (cause) {
        is KotlinTestParameters.KotlinDefaultParameterHolderException ->
          return assertAtLeastOneValue(
            ImmutableList.copyOf(cause.testParameterValues),
            functionDescription,
          )
        else ->
          throw IllegalStateException(
            "$functionDescription: Caught exception while getting the default" +
              " parameter values",
            e,
          )
      }
    }
  }

  private fun assertAtLeastOneValue(
    values: ImmutableList<TestParameterValue>,
    functionDescription: String,
  ): ImmutableList<TestParameterValue> {
    require(values.isNotEmpty()) {
      "$functionDescription: A default parameter value returned an empty" +
        " value list. This is not allowed, because it would cause the test to be skipped."
    }
    return values
  }

  /**
   * A single [TestParameterValue], paired with the index that it had in the list of values that it
   * was taken from.
   */
  class IndexedTestParameterValue
  internal constructor(val value: TestParameterValue, val indexInValueList: Int)

  private class GetJavaExecutableFailureException : RuntimeException()
}
