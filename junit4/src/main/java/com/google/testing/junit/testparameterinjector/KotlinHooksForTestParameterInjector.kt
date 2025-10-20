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
object KotlinHooksForTestParameterInjector {

  @JvmStatic
  fun getParameterNames(method: java.lang.reflect.Method): Optional<ImmutableList<String>> {
    return try {
      Optional.of(ImmutableList.copyOf(methodToFunction(method).parameters.mapNotNull { it.name }))
    } catch (_: GetJavaExecutableFailureException) {
      // For some Android methods, kFunction.javaMethod fails because of a Kotlin-internal
      // consistency check. This is a workaround to avoid breaking these tests.
      Optional.absent()
    }
  }

  @JvmStatic
  fun getParameterNames(
    constructor: java.lang.reflect.Constructor<*>
  ): Optional<ImmutableList<String>> {
    return try {
      Optional.of(
        ImmutableList.copyOf(constructorToFunction(constructor).parameters.map { it.name })
      )
    } catch (_: GetJavaExecutableFailureException) {
      // For some Android methods, kFunction.javaMethod fails because of a Kotlin-internal
      // consistency check. This is a workaround to avoid breaking these tests.
      Optional.absent()
    }
  }

  @JvmStatic
  fun hasOptionalParameters(method: java.lang.reflect.Method): Boolean {
    return try {
      methodToFunction(method).parameters.any { it.kind == KParameter.Kind.VALUE && it.isOptional }
    } catch (_: GetJavaExecutableFailureException) {
      // For some Android methods, kFunction.javaMethod fails because of a Kotlin-internal
      // consistency check. This is a workaround to avoid breaking these tests.
      false
    }
  }

  @JvmStatic
  fun hasOptionalParameters(constructor: java.lang.reflect.Constructor<*>): Boolean {
    return try {
      constructorToFunction(constructor).parameters.any {
        it.kind == KParameter.Kind.VALUE && it.isOptional
      }
    } catch (_: GetJavaExecutableFailureException) {
      // For some Android methods, kFunction.javaMethod fails because of a Kotlin-internal
      // consistency check. This is a workaround to avoid breaking these tests.
      false
    }
  }

  @JvmStatic
  fun extractValuesForEachParameter(
    testInstance: Any,
    method: java.lang.reflect.Method,
    getExplicitValuesByIndex: (Int) -> Optional<ImmutableList<TestParameterValue>>,
    getImplicitValuesByIndex: (Int) -> ImmutableList<TestParameterValue>,
  ): ImmutableList<ImmutableList<TestParameterValue>> {
    val function = methodToFunction(method)
    val functionDescription = "${method.declaringClass.simpleName}.${function.name}"

    return extractValuesForEachParameterInternal(
      testInstance,
      function,
      functionDescription,
      javaParameterTypes = method.parameterTypes,
      getExplicitValuesByIndex,
      getImplicitValuesByIndex,
    )
  }

  @JvmStatic
  fun extractValuesForEachParameter(
    constructor: java.lang.reflect.Constructor<*>,
    getExplicitValuesByIndex: (Int) -> Optional<ImmutableList<TestParameterValue>>,
    getImplicitValuesByIndex: (Int) -> ImmutableList<TestParameterValue>,
  ): ImmutableList<ImmutableList<TestParameterValue>> {
    val function = constructorToFunction(constructor)
    val functionDescription = "${constructor.declaringClass.simpleName}.constructor"

    return extractValuesForEachParameterInternal(
      testInstance = null,
      function,
      functionDescription,
      javaParameterTypes = constructor.parameterTypes,
      getExplicitValuesByIndex,
      getImplicitValuesByIndex,
    )
  }

  private fun extractValuesForEachParameterInternal(
    testInstance: Any?,
    function: KFunction<*>,
    functionDescription: String,
    javaParameterTypes: Array<Class<*>>,
    getExplicitValuesByIndex: (Int) -> Optional<ImmutableList<TestParameterValue>>,
    getImplicitValuesByIndex: (Int) -> ImmutableList<TestParameterValue>,
  ): ImmutableList<ImmutableList<TestParameterValue>> {
    val parameters = function.parameters.filter { it.kind == KParameter.Kind.VALUE }

    // Sanity check
    require(parameters.size == javaParameterTypes.size) {
      ("$functionDescription: Number of parameters don't match: kotlinParameters=$parameters," +
        " javaParameterTypes=${javaParameterTypes.toList()}, function=$function")
    }

    val parameterValues: MutableMap<KParameter, ImmutableList<TestParameterValue>> = mutableMapOf()
    // Start with non-optional parameters
    for ((index, parameter) in parameters.withIndex()) {
      if (!parameter.isOptional) {
        parameterValues[parameter] =
          assertAtLeastOneValue(
            getExplicitValuesByIndex(index).or { getImplicitValuesByIndex(index) },
            functionDescription,
          )
      }
    }
    // Populate the optional parameter from first to last
    for ((index, parameter) in parameters.withIndex()) {
      if (parameter.isOptional) {
        require(!getExplicitValuesByIndex(index).isPresent) {
          "$functionDescription: @TestParameter annotation found on " +
            "${parameter.name} with specified value and a default value, which is not " +
            "allowed: parameter=$parameter"
        }
        parameterValues[parameter] =
          getValuesFromNextDefaultValue(
            testInstance,
            function,
            parameterValues,
            functionDescription,
          )
      }
    }

    return ImmutableList.copyOf(parameters.map(parameterValues::getValue))
  }

  private fun methodToFunction(method: java.lang.reflect.Method): KFunction<*> {
    val kClass = method.declaringClass.kotlin

    val candidates = mutableListOf<KFunction<*>>()
    var caughtError = false
    for (member in kClass.members) {
      if (member is KFunction<*>) {
        val javaMethodFromMember =
          try {
            member.javaMethod
          } catch (_: Error) {
            // For some Android methods, kFunction.javaMethod fails because of a Kotlin-internal
            // consistency check. If this happens, only throw a GetJavaExecutableFailureException if
            // the method we are looking for has this error.
            caughtError = true
            continue
          }
        if (javaMethodFromMember == method) {
          candidates.add(member)
        }
      }
    }
    if (candidates.isEmpty() && caughtError) {
      throw GetJavaExecutableFailureException()
    } else {
      return candidates.single()
    }
  }

  private fun constructorToFunction(constructor: java.lang.reflect.Constructor<*>): KFunction<*> {
    val kClass = constructor.declaringClass.kotlin

    val candidates = mutableListOf<KFunction<*>>()
    var caughtError: Boolean = false
    for (kotlinConstructor in kClass.constructors) {
      val javaConstructorFromKotlin =
        try {
          kotlinConstructor.javaConstructor
        } catch (e: Error) {
          // For some Android methods, kFunction.javaConstructor fails because of a Kotlin-internal
          // consistency check. If this happens, only throw a GetJavaExecutableFailureException if
          // the method we are looking for has this error.
          caughtError = true
          continue
        }
      if (javaConstructorFromKotlin == constructor) {
        candidates.add(kotlinConstructor)
      }
    }
    if (candidates.isEmpty() and caughtError) {
      throw GetJavaExecutableFailureException()
    } else {
      return candidates.single()
    }
  }

  private fun getValuesFromNextDefaultValue(
    testInstance: Any?,
    function: KFunction<*>,
    parameterValuesSoFar: Map<KParameter, ImmutableList<TestParameterValue>>,
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
          } + parameterValuesSoFar.mapValues { it.value[0].wrappedValue }
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

  private class GetJavaExecutableFailureException : RuntimeException()
}
