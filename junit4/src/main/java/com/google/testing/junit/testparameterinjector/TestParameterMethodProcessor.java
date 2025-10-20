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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.auto.value.AutoAnnotation;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;
import com.google.common.primitives.Primitives;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.testing.junit.testparameterinjector.TestInfo.TestInfoParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjectorUtils.JavaCompatibilityExecutable;
import com.google.testing.junit.testparameterinjector.TestParameterInjectorUtils.JavaCompatibilityParameter;
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider.Context;
import com.google.testing.junit.testparameterinjector.TestParameter.DefaultTestParameterValuesProvider;
import com.google.testing.junit.testparameterinjector.TestParameter.TestParameterValuesProvider;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * {@code TestMethodProcessor} implementation for supporting the {@link TestParameter} annotation.
 */
@SuppressWarnings("KotlinInternal")
class TestParameterMethodProcessor implements TestMethodProcessor {

  private final Cache<Method, List<List<TestParameterValueHolder>>> parameterValuesCache =
      CacheBuilder.newBuilder().maximumSize(1000).build();
  private final Cache<Class<?>, Object> arbitraryTestInstanceCache =
      CacheBuilder.newBuilder().maximumSize(1).build();

  /** Calculates the cartesian product of all relevant @TestParameter values. */
  @Override
  public List<TestInfo> calculateTestInfos(TestInfo originalTest) {
    List<List<TestParameterValueHolder>> parameterValuesForMethod =
        getParameterValuesForMethod(originalTest.getMethod(), originalTest.getTestClass());

    if (parameterValuesForMethod.isEmpty()) {
      // This test is not parameterized
      return ImmutableList.of(originalTest);
    }

    ImmutableList.Builder<TestInfo> testInfos = ImmutableList.builder();
    for (int parametersIndex = 0;
        parametersIndex < parameterValuesForMethod.size();
        ++parametersIndex) {
      List<TestParameterValueHolder> testParameterValues =
          parameterValuesForMethod.get(parametersIndex);
      testInfos.add(
          originalTest
              .withExtraParameters(
                  FluentIterable.from(testParameterValues)
                      .transform(
                          param ->
                              TestInfoParameter.create(
                                  param.toTestNameString(),
                                  param.unwrappedValue(),
                                  param.valueIndex()))
                      .toList())
              .withExtraAnnotation(
                  TestIndexHolderFactory.create(
                      /* methodIndex= */ strictIndexOf(
                          getMethodsIncludingParentsSorted(originalTest.getTestClass()),
                          originalTest.getMethod()),
                      parametersIndex,
                      originalTest.getTestClass().getName())));
    }

    return testInfos.build();
  }

  @Override
  public ExecutableValidationResult validateConstructor(Constructor<?> constructor) {
    return validateAnnotations(constructor.getParameterAnnotations(), "constructor");
  }

  @Override
  public ExecutableValidationResult validateTestMethod(Method testMethod, Class<?> testClass) {
    return validateAnnotations(testMethod.getParameterAnnotations(), testMethod.getName());
  }

  @Override
  public Optional<List<Object>> maybeGetConstructorParameters(
      Constructor<?> constructor, TestInfo testInfo) {
    if (isValidAndContainsRelevantAnnotations(constructor.getParameterAnnotations())) {
      TestIndexHolder testIndexHolder = testInfo.getAnnotation(TestIndexHolder.class);
      return Optional.of(
          FluentIterable.from(getParameterValuesForTest(testIndexHolder, testInfo.getTestClass()))
              .filter(p -> p.origin() == Origin.CONSTRUCTOR_PARAMETER)
              .transform(TestParameterValueHolder::unwrappedValue)
              .copyInto(new ArrayList<>()));
    } else {
      return Optional.absent();
    }
  }

  @Override
  public Optional<List<Object>> maybeGetTestMethodParameters(TestInfo testInfo) {
    Method testMethod = testInfo.getMethod();
    if (isValidAndContainsRelevantAnnotations(testMethod.getParameterAnnotations())) {
      TestIndexHolder testIndexHolder = testInfo.getAnnotation(TestIndexHolder.class);
      return Optional.of(
          FluentIterable.from(getParameterValuesForTest(testIndexHolder, testInfo.getTestClass()))
              .filter(p -> p.origin() == Origin.METHOD_PARAMETER)
              .transform(TestParameterValueHolder::unwrappedValue)
              .copyInto(new ArrayList<>()));
    } else {
      return Optional.absent();
    }
  }

  @Override
  public void postProcessTestInstance(Object testInstance, TestInfo testInfo) {
    TestIndexHolder testIndexHolder = testInfo.getAnnotation(TestIndexHolder.class);
    try {
      if (testIndexHolder != null) {
        List<TestParameterValueHolder> remainingTestParameterValuesForFieldInjection =
            FluentIterable.from(getParameterValuesForTest(testIndexHolder, testInfo.getTestClass()))
                .filter(p -> p.origin() == Origin.FIELD)
                .copyInto(new ArrayList<>());

        for (Field declaredField :
            FluentIterable.from(listWithParents(testInstance.getClass()))
                .transformAndConcat(c -> Arrays.asList(c.getDeclaredFields()))
                .toList()) {
          for (TestParameterValueHolder testParameterValue :
              remainingTestParameterValuesForFieldInjection) {
            if (declaredField.isAnnotationPresent(TestParameter.class)) {
              if (!declaredField.getName().equals(testParameterValue.paramName().get())) {
                // names don't match
                continue;
              }
              declaredField.setAccessible(true);
              declaredField.set(testInstance, testParameterValue.unwrappedValue());
              remainingTestParameterValuesForFieldInjection.remove(testParameterValue);
              break;
            }
          }
        }
      }
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static ExecutableValidationResult validateAnnotations(
      Annotation[][] annotations, String executableName) {
    if (containsRelevantAnnotation(annotations)) {
      int parameterIndex = 0;
      for (Annotation[] annotationsOnParameter : annotations) {
        boolean hasTestParameter =
            FluentIterable.from(annotationsOnParameter)
                .anyMatch(annotation -> annotation instanceof TestParameter);
        if (!hasTestParameter) {
          return ExecutableValidationResult.validated(
              new IllegalArgumentException(
                  String.format(
                      "%s has at least one parameter annotated with @TestParameter, but"
                          + " parameter number %d is not annotated with @TestParameter.",
                      executableName, parameterIndex + 1)));
        }
        parameterIndex++;
      }
      return ExecutableValidationResult.valid();
    } else {
      // This method has no relevant @TestParameter annotations, and therefore its parameters are
      // not handled by this processor.
      return ExecutableValidationResult.notValidated();
    }
  }

  private static Optional<ImmutableList<TestParameterValue>> getExplicitValuesFromAnnotation(
      AnnotationWithMetadata annotationWithMetadata) {
    TestParameter annotation = annotationWithMetadata.annotation();

    boolean valueIsSet = !valueIsEmpty(annotation);
    Class<? extends TestParameterValuesProvider> valuesProvider = valuesProvider(annotation);
    boolean valuesProviderIsSet = !valuesProvider.equals(DefaultTestParameterValuesProvider.class);
    checkState(
        !(valueIsSet && valuesProviderIsSet),
        "It is not allowed to specify both value and valuesProvider on annotation %s",
        annotation);

    if (valueIsSet) {
      if (isAndroidMarshmallow()) {
        System.err.println("Note from TestParameterInjector:");
        System.err.println(
            "Under Android 23, we have seen crashes on the operation we are about to perform.");
        System.err.println(
            "If you see a crash here, you may be able to work around it by changing your"
                + " @TestParameter annotation to specify a valuesProvider instead of a value"
                + " array.");
        System.err.println("For background, see bug 287424109.");
        System.err.println("We will now perform the operation...");
      }
      String[] value = annotation.value();
      if (isAndroidMarshmallow()) {
        System.err.println("The operation has succeeded.");
        System.err.println(
            "Any crash after now is unrelated to TestParameterInjector or is at least somewhat"
                + " different from the usual crash.");
      }
      return Optional.of(
          FluentIterable.from(value)
              .transform(
                  v ->
                      TestParameterValue.maybeWrap(
                          parseStringValue(v, annotationWithMetadata.paramClass())))
              .toList());
    } else if (valuesProviderIsSet) {
      return Optional.of(
          TestParameterValue.maybeWrapList(
              getValuesFromProvider(valuesProvider, annotationWithMetadata.context())));
    } else {
      return Optional.absent();
    }
  }

  /**
   * Returns the value of {@code annotation.valuesProvider()}, working around b/287424109.
   *
   * <p>Under Android API Level 23, we have seen calls to {@code valuesProvider()} sometimes crash
   * with a segmentation fault. The crashes appear and disappear with "unrelated" changes elswhere
   * in the binary. To work around them, we try to derive the value of {@code valuesProvider()} from
   * the {@code toString()} value of the annotation, at least when running under an Android
   * emulator. If we have any trouble, we fall back to calling {@code valuesProvider()}.
   */
  private static Class<? extends TestParameterValuesProvider> valuesProvider(
      TestParameter annotation) {
    if (!isAndroidMarshmallow()) {
      return annotation.valuesProvider();
    }

    String string = annotation.toString();
    // The format of toString() is unspecified.
    // But under API Level 23, the only format we've seen so far is something like this:
    // @com.google.testing.junit.testparameterinjector.TestParameter(value=[], valuesProvider=class
    // com.google.testing.junit.testparameterinjector.TestParameterValuesProvider$DefaultTestParameterValuesProvider)
    Matcher matcher = Pattern.compile("valuesProvider=class ([^),]*)").matcher(string);
    if (!matcher.find()) {
      // Maybe we're seeing a different format under a different version of Android?
      // Rather than give up, we fall back to at least trying to read the value normally.
      // Since the problem seems to be specific to API Level 23, we'd hope that this will succeed.
      return annotation.valuesProvider();
    }
    try {
      /*
       * TODO: b/287424109 - Should we pass a specific ClassLoader, like use getContextClassLoader
       * or the ClassLoader that loaded TestParameter or the class that contains the TestParameter
       * usage (which we'd need to plumb through to here)?
       */
      return Class.forName(matcher.group(1)).asSubclass(TestParameterValuesProvider.class);
    } catch (ClassNotFoundException e) {
      // Falling back is unlikely to help here, but we might as well try.
      // If we're lucky, it will somehow work, or at least the user will get a sensible failure.
      // If we're unlucky, it will trigger the segfault.
      return annotation.valuesProvider();
    }
  }

  /**
   * Checks whether {@code annotation.value()} is an empty array, working around b/287424109.
   *
   * <p>Compare {@link #valuesProvider}.
   */
  private static boolean valueIsEmpty(TestParameter annotation) {
    return isAndroidMarshmallow()
        ? annotation.toString().contains("TestParameter(value=[], valuesProvider=")
        : annotation.value().length == 0;
  }

  private static boolean isAndroidMarshmallow() {
    if (!System.getProperty("java.runtime.name", "").contains("Android")) {
      return false;
    }

    try {
      int version = (int) Class.forName("android.os.Build$VERSION").getField("SDK_INT").get(null);
      int marshmallow =
          (int) Class.forName("android.os.Build$VERSION_CODES").getField("M").get(null);
      return version == marshmallow;
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  /**
   * Returns the obvious values for the given parameter type (e.g. true and false for a boolean), or
   * throws an exception if the given type has no obvious values.
   */
  private static ImmutableList<TestParameterValue> getObviousValuesForParameterClass(
      Class<?> parameterClass) {
    if (Enum.class.isAssignableFrom(parameterClass)) {
      return TestParameterValue.maybeWrapList(
          Arrays.asList((Object[]) parameterClass.asSubclass(Enum.class).getEnumConstants()));
    } else if (Primitives.wrap(parameterClass).equals(Boolean.class)) {
      return TestParameterValue.maybeWrapList(Arrays.asList(false, true));
    } else {
      throw new IllegalStateException(
          String.format(
              "A @TestParameter without values can only be placed at an enum or a boolean, but"
                  + " was placed by a %s",
              parameterClass));
    }
  }

  private static Object parseStringValue(String value, Class<?> parameterClass) {
    if (parameterClass.equals(String.class)) {
      return value.equals("null") ? null : value;
    } else if (Enum.class.isAssignableFrom(parameterClass)) {
      return value.equals("null") ? null : ParameterValueParsing.parseEnum(value, parameterClass);
    } else {
      return ParameterValueParsing.parseYamlStringToJavaType(value, parameterClass);
    }
  }

  private static List<Object> getValuesFromProvider(
      Class<? extends TestParameterValuesProvider> valuesProvider,
      GenericParameterContext context) {
    try {
      Constructor<? extends TestParameterValuesProvider> constructor =
          valuesProvider.getDeclaredConstructor();
      constructor.setAccessible(true);
      TestParameterValuesProvider instance = constructor.newInstance();
      if (instance
          instanceof com.google.testing.junit.testparameterinjector.TestParameterValuesProvider) {
        return new ArrayList<>(
            ((com.google.testing.junit.testparameterinjector.TestParameterValuesProvider) instance)
                .provideValues(new Context(context)));
      } else {
        return new ArrayList<>(instance.provideValues());
      }
    } catch (NoSuchMethodException e) {
      if (!Modifier.isStatic(valuesProvider.getModifiers()) && valuesProvider.isMemberClass()) {
        throw new IllegalStateException(
            String.format(
                "Could not find a no-arg constructor for %s, probably because it is a not-static"
                    + " inner class. You can fix this by making %s static.",
                valuesProvider.getSimpleName(), valuesProvider.getSimpleName()),
            e);
      } else {
        throw new IllegalStateException(
            String.format(
                "Could not find a no-arg constructor for %s.", valuesProvider.getSimpleName()),
            e);
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    } catch (Exception e) {
      // Catch any unchecked exception that may come from `provideValues(Context)`
      if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      } else {
        throw new IllegalStateException(e);
      }
    }
  }

  private static boolean containsRelevantAnnotation(Annotation[][] annotations) {
    return FluentIterable.from(annotations)
        .transformAndConcat(Arrays::asList)
        .anyMatch(annotation -> annotation instanceof TestParameter);
  }

  private static boolean isValidAndContainsRelevantAnnotations(Annotation[][] annotationsList) {
    return annotationsList.length > 0
        && FluentIterable.from(annotationsList)
            .allMatch(
                annotations ->
                    FluentIterable.from(annotations)
                        .anyMatch(annotation -> annotation instanceof TestParameter));
  }

  private List<TestParameterValueHolder> getParameterValuesForTest(
      TestIndexHolder testIndexHolder, Class<?> testClass) {
    verify(
        testIndexHolder.testClassName().equals(testClass.getName()),
        "The class for which the given annotation was created (%s) is not the same as the test"
            + " class that this runner is handling (%s)",
        testIndexHolder.testClassName(),
        testClass.getName());
    Method testMethod =
        getMethodsIncludingParentsSorted(testClass).get(testIndexHolder.methodIndex());
    return getParameterValuesForMethod(testMethod, testClass)
        .get(testIndexHolder.parametersIndex());
  }

  private List<List<TestParameterValueHolder>> getParameterValuesForMethod(
      Method method, Class<?> testClass) {
    try {
      return parameterValuesCache.get(
          method,
          () -> {
            JavaCompatibilityExecutable constructorExecutable =
                JavaCompatibilityExecutable.create(
                    TestParameterInjectorUtils.getOnlyConstructor(testClass));
            JavaCompatibilityExecutable methodExecutable =
                JavaCompatibilityExecutable.create(method);
            return Lists.cartesianProduct(
                FluentIterable.from(ImmutableList.<ImmutableList<TestParameterValueHolder>>of())
                    .append(getFieldValueHolders(testClass))
                    .append(
                        calculateTestParameterValueList(
                            constructorExecutable,
                            getAnnotationWithMetadataListWithType(constructorExecutable, testClass),
                            Origin.CONSTRUCTOR_PARAMETER,
                            testClass))
                    .append(
                        calculateTestParameterValueList(
                            methodExecutable,
                            getAnnotationWithMetadataListWithType(methodExecutable, testClass),
                            Origin.METHOD_PARAMETER,
                            testClass))
                    .toList());
          });
    } catch (ExecutionException | UncheckedExecutionException e) {
      Throwables.throwIfUnchecked(e.getCause());
      throw new RuntimeException(e);
    }
  }

  private ImmutableList<ImmutableList<TestParameterValueHolder>> getFieldValueHolders(
      Class<?> testClass) {
    List<AnnotationWithMetadata> annotations =
        FluentIterable.from(listWithParents(testClass))
            .transformAndConcat(c -> Arrays.asList(c.getDeclaredFields()))
            .transformAndConcat(
                field ->
                    maybeGetTestParameter(field.getAnnotations())
                        .transform(
                            annotation ->
                                AnnotationWithMetadata.withMetadata(
                                    annotation,
                                    field.getType(),
                                    Optional.of(field.getName()),
                                    GenericParameterContext.create(field, testClass)))
                        .asSet())
            .toList();

    if (TestParameterInjectorUtils.isKotlinClass(testClass)) {
      annotations = applyKotlinDuplicateAnnotationWorkaround(annotations, testClass);
    }

    return calculateTestParameterValueList(annotations, Origin.FIELD);
  }

  /**
   * Hack to fix a 2025 Kotlin change that causes the field/parameter of a primary constructor to
   * get its annotation on both the field and the constructor parameter.
   *
   * <p>Example:
   *
   * {@snippet :
   *   @RunWith(TestParameterInjector::class)
   *   class ExampleTest(@TestParameter private val isEnabled: Boolean)
   * }
   *
   * In this heuristic, we look for fields with the same name and type as a constructor parameter.
   * If we find one, we assume it's the same parameter, and drop it.
   *
   * <p>For more info, see: https://github.com/google/TestParameterInjector/issues/49
   */
  private static List<AnnotationWithMetadata> applyKotlinDuplicateAnnotationWorkaround(
      List<AnnotationWithMetadata> fieldAnnotations, Class<?> testClass) {
    Constructor<?> constructor = TestParameterInjectorUtils.getOnlyConstructor(testClass);

    if (!isValidAndContainsRelevantAnnotations(constructor.getParameterAnnotations())) {
      // Return early if there are no @TestParameter annotations on the constructor parameters. The
      // remainder of this method can assume there are only @TestParameter-annotated parameters.
      return fieldAnnotations;
    }

    List<AnnotationWithMetadata> constructorAnnotations =
        getAnnotationWithMetadataListWithType(
            JavaCompatibilityExecutable.create(constructor), testClass);

    ImmutableList.Builder<AnnotationWithMetadata> resultBuilder = ImmutableList.builder();
    for (AnnotationWithMetadata fieldAnnotation : fieldAnnotations) {
      List<AnnotationWithMetadata> matchingConstructorAnnotations =
          FluentIterable.from(constructorAnnotations)
              .filter(
                  constructorAnnotation ->
                      fieldAnnotation.annotation().equals(constructorAnnotation.annotation())
                          && fieldAnnotation
                              .paramClass()
                              .equals(constructorAnnotation.paramClass()))
              .toList();

      if (matchingConstructorAnnotations.isEmpty()) {
        // Most common case: No suspect fields/parameters.
        resultBuilder.add(fieldAnnotation);
      } else {
        if (matchingConstructorAnnotations.get(0).paramName().isPresent()) {
          if (FluentIterable.from(matchingConstructorAnnotations)
              .filter(
                  constructorAnnotation ->
                      fieldAnnotation.paramName().equals(constructorAnnotation.paramName()))
              .isEmpty()) {
            // No suspect fields/parameters because their names don't match.
            resultBuilder.add(fieldAnnotation);
          } else {
            // Skip this field because it has the same name + type as a constructor
            // parameter. Therefore, it is almost certainly an artefact of the Kotlin-Java
            // translation of a field+parameter in the primary constructor..
          }
        } else {
          // This field has the same type as a constructor parameter, but this code was built
          // without parameter names, so it may or may not be the same parameter.
          throw new RuntimeException(
              String.format(
                  "%s: Found a Kotlin field (%s) and constructor parameter with the same type. This"
                      + " may be an artefact of the Kotlin-Java translation of a field+parameter in"
                      + " the primary constructor. However, TestParameterInjector needs to be built"
                      + " with access to parameter names to be able to confirm.\n"
                      + "\n"
                      + "To fix this error, either:\n"
                      + "  - Use `@param:TestParameter` instead of `@TestParameter` on the primary"
                      + " constructor parameter.\n"
                      + "  - Build this test with the `-parameters` compiler option. In  Maven, you"
                      + " do this by adding <javaParameters>true</javaParameters> to the"
                      + " kotlin-maven-plugin's configuration.",
                  testClass.getSimpleName(), fieldAnnotation.paramName().get()));
        }
      }
    }

    return resultBuilder.build();
  }

  private static ImmutableList<ImmutableList<TestParameterValueHolder>>
      calculateTestParameterValueList(
          List<AnnotationWithMetadata> annotationWithMetadatas, Origin origin) {
    return FluentIterable.from(annotationWithMetadatas)
        .transform(
            annotationWithMetadata ->
                toValueHolders(
                    annotationWithMetadata,
                    getExplicitValuesFromAnnotation(annotationWithMetadata)
                        .or(
                            () ->
                                getObviousValuesForParameterClass(
                                    annotationWithMetadata.paramClass())),
                    origin))
        .toList();
  }

  private ImmutableList<ImmutableList<TestParameterValueHolder>> calculateTestParameterValueList(
      JavaCompatibilityExecutable executable,
      List<AnnotationWithMetadata> annotationWithMetadatas,
      Origin origin,
      Class<?> testClass) {
    if (!isValidAndContainsRelevantAnnotations(executable.getParameterAnnotations())) {
      return ImmutableList.of();
    }

    if (TestParameterInjectorUtils.isKotlinClass(executable.getDeclaringClass())
        && KotlinHooksForTestParameterInjector.hasOptionalParameters(executable)) {

      Object arbitraryTestInstance = null;
      if (executable.isMethod()) {
        try {
          arbitraryTestInstance =
              arbitraryTestInstanceCache.get(
                  testClass, () -> createArbitraryTestInstance(testClass));
        } catch (ExecutionException | UncheckedExecutionException e) {
          Throwables.throwIfUnchecked(e.getCause());
          throw new RuntimeException(e);
        }
      }

      ImmutableList<ImmutableList<TestParameterValue>> valuesList =
          KotlinHooksForTestParameterInjector.extractValuesForEachParameter(
              arbitraryTestInstance,
              executable,
              /* getExplicitValuesByIndex= */ index ->
                  getExplicitValuesFromAnnotation(annotationWithMetadatas.get(index)),
              /* getImplicitValuesByIndex= */ index ->
                  getObviousValuesForParameterClass(
                      annotationWithMetadatas.get(index).paramClass()));
      return FluentIterable.from(
              ContiguousSet.create(
                  Range.closedOpen(0, annotationWithMetadatas.size()), DiscreteDomain.integers()))
          .transform(
              index ->
                  toValueHolders(annotationWithMetadatas.get(index), valuesList.get(index), origin))
          .toList();
    } else {
      return calculateTestParameterValueList(annotationWithMetadatas, origin);
    }
  }

  private Object createArbitraryTestInstance(Class<?> testClass) {
    Constructor<?> constructor = TestParameterInjectorUtils.getOnlyConstructor(testClass);
    JavaCompatibilityExecutable constructorExecutable =
        JavaCompatibilityExecutable.create(constructor);

    List<Object> constructorParameters;
    if (constructor.getParameterTypes().length == 0) {
      constructorParameters = ImmutableList.of();
    } else {
      checkState(
          isValidAndContainsRelevantAnnotations(constructor.getParameterAnnotations()),
          "%s: Expected each constructor parameter to be annotated with @TestParameter",
          testClass.getName());
      ImmutableList<ImmutableList<TestParameterValueHolder>> valueList =
          calculateTestParameterValueList(
              constructorExecutable,
              getAnnotationWithMetadataListWithType(constructorExecutable, testClass),
              Origin.CONSTRUCTOR_PARAMETER,
              testClass);
      constructorParameters =
          FluentIterable.from(valueList)
              .transform(valueHolders -> valueHolders.get(0).unwrappedValue())
              .toList();
    }
    try {
      return constructor.newInstance(constructorParameters.toArray());
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static ImmutableList<TestParameterValueHolder> toValueHolders(
      AnnotationWithMetadata annotationWithMetadata,
      List<TestParameterValue> allParameterValues,
      Origin origin) {
    checkState(
        !allParameterValues.isEmpty(),
        "The number of parameter values should not be 0"
            + ", otherwise the parameter would cause the test to be skipped.");
    return FluentIterable.from(
            ContiguousSet.create(
                Range.closedOpen(0, allParameterValues.size()), DiscreteDomain.integers()))
        .transform(
            valueIndex ->
                TestParameterValueHolder.create(
                    origin,
                    allParameterValues.get(valueIndex),
                    valueIndex,
                    annotationWithMetadata.paramName()))
        .toList();
  }

  private static ImmutableList<AnnotationWithMetadata> getAnnotationWithMetadataListWithType(
      JavaCompatibilityExecutable executable, Class<?> testClass) {
    if (!isValidAndContainsRelevantAnnotations(executable.getParameterAnnotations())) {
      return ImmutableList.of();
    }
    Optional<ImmutableList<String>> maybeNamesFromKotlin =
        TestParameterInjectorUtils.isKotlinClass(testClass)
            ? KotlinHooksForTestParameterInjector.getParameterNames(executable)
            : Optional.absent();

    return FluentIterable.from(executable.getParametersWithFallback(maybeNamesFromKotlin))
        .transform(parameter -> AnnotationWithMetadata.fromAnnotatedParameter(parameter, testClass))
        .toList();
  }

  private static Optional<TestParameter> maybeGetTestParameter(Annotation[] annotations) {
    return FluentIterable.from(annotations)
        .filter(annotation -> annotation.annotationType().equals(TestParameter.class))
        .transform(annotation -> (TestParameter) annotation)
        .first();
  }

  private <T> int strictIndexOf(List<T> haystack, T needle) {
    int index = haystack.indexOf(needle);
    checkArgument(index >= 0, "Could not find '%s' in %s", needle, haystack);
    return index;
  }

  private ImmutableList<Method> getMethodsIncludingParentsSorted(Class<?> clazz) {
    ImmutableList.Builder<Method> resultBuilder = ImmutableList.builder();
    while (clazz != null) {
      resultBuilder.add(clazz.getDeclaredMethods());
      clazz = clazz.getSuperclass();
    }
    // Because getDeclaredMethods()'s order is not specified, there is the theoretical possibility
    // that the order of methods is unstable. To partly fix this, we sort the result based on method
    // name. This is still not perfect because of method overloading, but that should be
    // sufficiently rare for test names.
    return ImmutableList.sortedCopyOf(
        Ordering.natural().onResultOf(Method::getName), resultBuilder.build());
  }

  private static ImmutableList<Class<?>> listWithParents(Class<?> clazz) {
    ImmutableList.Builder<Class<?>> resultBuilder = ImmutableList.builder();

    Class<?> currentClass = clazz;
    while (currentClass != null) {
      resultBuilder.add(currentClass);
      currentClass = currentClass.getSuperclass();
    }

    return resultBuilder.build();
  }

  /** The origin of an annotation type. */
  enum Origin {
    FIELD,
    METHOD_PARAMETER,
    CONSTRUCTOR_PARAMETER,
  }

  /** Class to hold a single parameter value and its metadata. */
  @AutoValue
  abstract static class TestParameterValueHolder implements Serializable {

    private static final long serialVersionUID = -2694196563712540762L;

    /** Origin of the {@link TestParameter} annotation. */
    abstract Origin origin();

    /** The value used for the test as defined by the @TestParameter annotation. */
    abstract TestParameterValue wrappedValue();

    /** The index of this value in the list of all possible values for this parameter. */
    abstract int valueIndex();

    /**
     * The name of the parameter or field that is being annotated. Can be absent if the annotation
     * is on a parameter and Java was not compiled with the -parameters flag.
     */
    abstract Optional<String> paramName();

    /**
     * Returns {@link #wrappedValue()} without the {@link TestParameterValue} wrapper if it exists.
     */
    @Nullable
    Object unwrappedValue() {
      return wrappedValue().getWrappedValue();
    }

    /**
     * Returns a String that represents this value and is fit for use in a test name (between
     * brackets).
     */
    String toTestNameString() {
      return ParameterValueParsing.formatTestNameString(paramName(), wrappedValue());
    }

    public static TestParameterValueHolder create(
        Origin origin,
        TestParameterValue wrappedValue,
        int valueIndex,
        Optional<String> paramName) {
      return new AutoValue_TestParameterMethodProcessor_TestParameterValueHolder(
          origin, wrappedValue, valueIndex, paramName);
    }
  }

  /** Class to hold an annotation type and metadata about the annotated parameter. */
  @AutoValue
  abstract static class AnnotationWithMetadata implements Serializable {

    /** The @TestParameter annotation instance. */
    abstract TestParameter annotation();

    /** The class of the parameter or field that is being annotated. */
    abstract Class<?> paramClass();

    /**
     * The name of the parameter or field that is being annotated. Can be absent if the annotation
     * is on a parameter and Java was not compiled with the -parameters flag.
     */
    abstract Optional<String> paramName();

    /** A value class that contains extra information about the context of this parameter. */
    abstract GenericParameterContext context();

    public static AnnotationWithMetadata withMetadata(
        TestParameter annotation,
        Class<?> paramClass,
        Optional<String> paramName,
        GenericParameterContext context) {
      return new AutoValue_TestParameterMethodProcessor_AnnotationWithMetadata(
          annotation, paramClass, paramName, context);
    }

    @SuppressWarnings("AndroidJdkLibsChecker")
    public static AnnotationWithMetadata fromAnnotatedParameter(
        JavaCompatibilityParameter parameter, Class<?> testClass) {
      TestParameter annotation = parameter.getAnnotation(TestParameter.class);
      checkNotNull(annotation, "Parameter %s is not annotated with @TestParameter", parameter);
      return AnnotationWithMetadata.withMetadata(
          annotation,
          parameter.getType(),
          parameter.maybeGetName(),
          GenericParameterContext.create(parameter, testClass));
    }

    // Prevent anyone relying on equals() and hashCode() so that it remains possible to add fields
    // to this class without breaking existing code.
    @Override
    public final boolean equals(Object other) {
      throw new UnsupportedOperationException("Equality is not supported");
    }

    @Override
    public final int hashCode() {
      throw new UnsupportedOperationException("hashCode() is not supported");
    }
  }

  /**
   * This mechanism is a workaround to be able to store the annotation values in the annotation list
   * of the {@link TestInfo}, since we cannot carry other information through the test runner.
   */
  @Retention(RUNTIME)
  @interface TestIndexHolder {

    /** The index of the test method in {@code getMethodsIncludingParentsSorted(testClass)} */
    int methodIndex();

    /**
     * The index of the set of parameters to run the test method with in the list produced by {@link
     * #getParameterValuesForMethod}.
     */
    int parametersIndex();

    /**
     * The full name of the test class. Only used for verifying that assumptions about the above
     * indices are valid.
     */
    String testClassName();
  }

  /** Factory for {@link TestIndexHolder}. */
  static class TestIndexHolderFactory {
    @AutoAnnotation
    static TestIndexHolder create(int methodIndex, int parametersIndex, String testClassName) {
      return new AutoAnnotation_TestParameterMethodProcessor_TestIndexHolderFactory_create(
          methodIndex, parametersIndex, testClassName);
    }

    private TestIndexHolderFactory() {}
  }
}
