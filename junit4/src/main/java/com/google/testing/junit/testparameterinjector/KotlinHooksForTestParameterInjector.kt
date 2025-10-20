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

import com.google.common.annotations.GoogleInternal
import com.google.common.base.Optional
import com.google.common.collect.ImmutableList
import java.lang.reflect.InvocationTargetException
import kotlin.jvm.kotlin
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaMethod

/**
 * Helper functions for the TestParameterInjector implementation, providing functionality that can
 * only be implemented using Kotlin.
 */
@GoogleInternal // TODO: jnyman - Expose this to the open source version.
object KotlinHooksForTestParameterInjector {

  @JvmStatic
  fun hasOptionalParameters(method: java.lang.reflect.Method): Boolean {
    try {
      return methodToFunction(method).parameters.any {
        it.kind == KParameter.Kind.VALUE && it.isOptional
      }
    } catch (e: GetJavaMethodFailureException) {
      // For some Android methods, kFunction.javaMethod fails because of a Kotlin-internal
      // consistency check. This is a workaround to avoid breaking these tests.
      return false
    }
  }

  @JvmStatic
  fun extractValuesForEachParameter(
    testInstance: Any,
    method: java.lang.reflect.Method,
    getExplicitValuesByIndex: (Int) -> Optional<ImmutableList<TestParameterValue>>,
    getImplicitValuesByIndex: (Int) -> ImmutableList<TestParameterValue>,
  ): ImmutableList<ImmutableList<TestParameterValue>> {
    val kClass = method.declaringClass.kotlin
    val function = methodToFunction(method)
    val parameters = function.parameters.filter { it.kind == KParameter.Kind.VALUE }

    // Sanity check
    require(parameters.size == method.parameterTypes.size) {
      "parameters=$parameters, method=$method, function=$function"
    }

    val parameterValues: MutableMap<KParameter, ImmutableList<TestParameterValue>> = mutableMapOf()
    // Start with non-optional parameters
    for ((index, parameter) in parameters.withIndex()) {
      if (!parameter.isOptional) {
        parameterValues[parameter] =
          assertAtLeastOneValue(
            getExplicitValuesByIndex(index).or { getImplicitValuesByIndex(index) },
            kClass,
            function,
          )
      }
    }
    // Populate the optional parameter from first to last
    for ((index, parameter) in parameters.withIndex()) {
      if (parameter.isOptional) {
        require(!getExplicitValuesByIndex(index).isPresent) {
          "${kClass.simpleName}.${function.name}: @TestParameter annotation found on " +
            "${parameter.name} with specified value and a default value, which is not " +
            "allowed: parameter=$parameter"
        }
        parameterValues[parameter] =
          getValuesFromNextDefaultValue(testInstance, function, parameterValues, kClass)
      }
    }

    return ImmutableList.copyOf(parameters.map(parameterValues::getValue))
  }

  private fun methodToFunction(method: java.lang.reflect.Method): KFunction<*> {
    val kClass = method.declaringClass.kotlin

    val candidates = mutableListOf<KFunction<*>>()
    var caughtError: Boolean = false
    for (member in kClass.members) {
      if (member is KFunction<*>) {
        val javaMethodFromMember =
          try {
            member.javaMethod
          } catch (e: Error) {
            // For some Android methods, kFunction.javaMethod fails because of a Kotlin-internal
            // consistency check. If this happens, only throw a GetJavaMethodFailureException if the
            // method we are looking for has this error.
            caughtError = true
            continue
          }
        if (javaMethodFromMember == method) {
          candidates.add(member)
        }
      }
    }
    if (candidates.isEmpty() and caughtError) {
      throw GetJavaMethodFailureException()
    } else {
      return candidates.single()
    }
  }

  private fun getValuesFromNextDefaultValue(
    testInstance: Any,
    function: KFunction<*>,
    parameterValuesSoFar: Map<KParameter, ImmutableList<TestParameterValue>>,
    kClass: KClass<*>,
  ): ImmutableList<TestParameterValue> {
    try {
      val unused =
        function.callBy(
          mapOf(
            function.parameters.single { it.kind == KParameter.Kind.INSTANCE } to testInstance
          ) + parameterValuesSoFar.mapValues { it.value[0].wrappedValue }
        )
      throw RuntimeException(
        "${kClass.simpleName}.${function.name}: Expected all default parameter values to" +
          " be produced by a call to KotlinTestParameters.testValues()"
      )
    } catch (e: InvocationTargetException) {
      val cause = e.cause
      when (cause) {
        is KotlinTestParameters.KotlinDefaultParameterHolderException ->
          return assertAtLeastOneValue(
            ImmutableList.copyOf(cause.testParameterValues),
            kClass,
            function,
          )
        else ->
          throw IllegalStateException(
            "${kClass.simpleName}.${function.name}: Caught exception while getting the default" +
              " parameter values",
            e,
          )
      }
    }
  }

  private fun assertAtLeastOneValue(
    values: ImmutableList<TestParameterValue>,
    kClass: KClass<*>,
    function: KFunction<*>,
  ): ImmutableList<TestParameterValue> {
    require(values.isNotEmpty()) {
      "${kClass.simpleName}.${function.name}: A default parameter value returned an empty" +
        " value list. This is not allowed, because it would cause the test to be skipped."
    }
    return values
  }

  private class GetJavaMethodFailureException : RuntimeException()
}
