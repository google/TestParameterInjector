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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.auto.value.AutoAnnotation;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.testing.junit.testparameterinjector.TestInfo.TestInfoParameter;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/**
 * {@code TestMethodProcessor} implementation for supporting parameterized tests annotated with
 * {@link TestParameterAnnotation}.
 *
 * @see TestParameterAnnotation
 */
final class TestParameterAnnotationMethodProcessor implements TestMethodProcessor {

  /**
   * Class to hold an annotation type and origin and one of the values as returned by the {@code
   * value()} method.
   */
  @AutoValue
  abstract static class TestParameterValueHolder implements Serializable {

    private static final long serialVersionUID = -6491624726743872379L;

    /**
     * Annotation type and origin of the annotation annotated with {@link TestParameterAnnotation}.
     */
    abstract AnnotationTypeOrigin annotationTypeOrigin();

    /**
     * The value used for the test as returned by the @TestParameterAnnotation annotated
     * annotation's {@code value()} method (e.g. 'true' or 'false' in the case of a Boolean
     * parameter).
     */
    abstract TestParameterValue wrappedValue();

    /** The index of this value in {@link #specifiedValues()}. */
    abstract int valueIndex();

    /**
     * The list of values specified by the @TestParameterAnnotation annotated annotation's {@code
     * value()} method (e.g. {true, false} in the case of a boolean parameter).
     */
    @SuppressWarnings("AutoValueImmutableFields") // intentional to allow null values
    abstract List<Object> specifiedValues();

    /**
     * The name of the parameter or field that is being annotated. In case the annotation is
     * annotating a method, constructor or class, {@code paramName} is an absent optional.
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

    public static ImmutableList<TestParameterValueHolder> create(
        AnnotationWithMetadata annotationWithMetadata, Origin origin) {
      List<TestParameterValue> specifiedValues =
          getParametersAnnotationValues(annotationWithMetadata);
      checkState(
          !specifiedValues.isEmpty(),
          "The number of parameter values should not be 0"
              + ", otherwise the parameter would cause the test to be skipped.");
      return FluentIterable.from(
              ContiguousSet.create(
                  Range.closedOpen(0, specifiedValues.size()), DiscreteDomain.integers()))
          .transform(
              valueIndex ->
                  (TestParameterValueHolder)
                      new AutoValue_TestParameterAnnotationMethodProcessor_TestParameterValueHolder(
                          AnnotationTypeOrigin.create(
                              annotationWithMetadata.annotation().annotationType(), origin),
                          specifiedValues.get(valueIndex),
                          valueIndex,
                          newArrayList(
                              FluentIterable.from(specifiedValues)
                                  .transform(TestParameterValue::getWrappedValue)),
                          annotationWithMetadata.paramName()))
          .toList();
    }
  }

  /**
   * Returns a {@link TestParameterValues} for retrieving the {@link TestParameterAnnotation}
   * annotation values for a the {@code testInfo}.
   */
  public static TestParameterValues getTestParameterValues(TestInfo testInfo) {
    TestIndexHolder testIndexHolder = testInfo.getAnnotation(TestIndexHolder.class);
    if (testIndexHolder == null) {
      return annotationType -> Optional.absent();
    } else {
      return annotationType ->
          FluentIterable.from(
                  new TestParameterAnnotationMethodProcessor(
                          /* onlyForFieldsAndParameters= */ false)
                      .getParameterValuesForTest(testIndexHolder, testInfo.getTestClass()))
              .filter(
                  testParameterValue ->
                      testParameterValue
                          .annotationTypeOrigin()
                          .annotationType()
                          .equals(annotationType))
              .transform(TestParameterValueHolder::unwrappedValue)
              .first();
    }
  }

  /**
   * Returns a {@link TestParameterAnnotation} value for the current test as specified by {@code
   * testInfo}, or {@link Optional#absent()} if the {@code annotationType} is not found.
   */
  public static Optional<Object> getTestParameterValue(
      TestInfo testInfo, Class<? extends Annotation> annotationType) {
    return getTestParameterValues(testInfo).getValue(annotationType);
  }

  private static ImmutableList<TestParameterValue> getParametersAnnotationValues(
      AnnotationWithMetadata annotationWithMetadata) {
    Annotation annotation = annotationWithMetadata.annotation();
    TestParameterAnnotation testParameter =
        annotation.annotationType().getAnnotation(TestParameterAnnotation.class);
    Class<? extends TestParameterValueProvider> valueProvider = testParameter.valueProvider();
    try {
      return FluentIterable.from(
              valueProvider
                  .getConstructor()
                  .newInstance()
                  .provideValues(
                      annotation,
                      annotationWithMetadata.paramClass(),
                      annotationWithMetadata.context()))
          .transform(
              value ->
                  (value instanceof TestParameterValue)
                      ? (TestParameterValue) value
                      : TestParameterValue.wrap(value))
          .toList();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(
          "Unexpected exception while invoking value provider " + valueProvider, e);
    }
  }

  /** The origin of an annotation type. */
  enum Origin {
    CLASS,
    FIELD,
    METHOD,
    METHOD_PARAMETER,
    CONSTRUCTOR,
    CONSTRUCTOR_PARAMETER,
  }

  /** Class to hold an annotation type and the element where it was declared. */
  @AutoValue
  abstract static class AnnotationTypeOrigin implements Serializable {

    private static final long serialVersionUID = 4909750539931241385L;

    /** Annotation type of the @TestParameterAnnotation annotated annotation. */
    abstract Class<? extends Annotation> annotationType();

    /** Where the annotation was declared. */
    abstract Origin origin();

    public static AnnotationTypeOrigin create(
        Class<? extends Annotation> annotationType, Origin origin) {
      return new AutoValue_TestParameterAnnotationMethodProcessor_AnnotationTypeOrigin(
          annotationType, origin);
    }

    @Override
    public final String toString() {
      return annotationType().getSimpleName() + ":" + origin();
    }
  }

  /** Class to hold an annotation type and metadata about the annotated parameter. */
  @AutoValue
  abstract static class AnnotationWithMetadata implements Serializable {

    /**
     * The annotation whose interface is itself annotated by the @TestParameterAnnotation
     * annotation.
     */
    abstract Annotation annotation();

    /**
     * The class of the parameter or field that is being annotated. In case the annotation is
     * annotating a method, constructor or class, {@code paramClass} is an absent optional.
     */
    abstract Optional<Class<?>> paramClass();

    /**
     * The name of the parameter or field that is being annotated. In case the annotation is
     * annotating a method, constructor or class, {@code paramName} is an absent optional.
     */
    abstract Optional<String> paramName();

    /**
     * A value class that contains extra information about the context of this parameter.
     *
     * <p>In case the annotation is annotating a method, constructor or class (deprecated
     * functionality), the annotations in the context will be empty.
     */
    abstract GenericParameterContext context();

    public static AnnotationWithMetadata withMetadata(
        Annotation annotation,
        Class<?> paramClass,
        String paramName,
        GenericParameterContext context) {
      return new AutoValue_TestParameterAnnotationMethodProcessor_AnnotationWithMetadata(
          annotation, Optional.of(paramClass), Optional.of(paramName), context);
    }

    public static AnnotationWithMetadata withMetadata(
        Annotation annotation, Class<?> paramClass, GenericParameterContext context) {
      return new AutoValue_TestParameterAnnotationMethodProcessor_AnnotationWithMetadata(
          annotation, Optional.of(paramClass), Optional.absent(), context);
    }

    public static AnnotationWithMetadata withoutMetadata(
        Annotation annotation, GenericParameterContext context) {
      return new AutoValue_TestParameterAnnotationMethodProcessor_AnnotationWithMetadata(
          annotation,
          /* paramClass= */ Optional.absent(),
          /* paramName= */ Optional.absent(),
          context);
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

  private final boolean onlyForFieldsAndParameters;
  private final LoadingCache<Class<?>, ImmutableList<AnnotationTypeOrigin>>
      annotationTypeOriginsCache =
          CacheBuilder.newBuilder()
              .maximumSize(1000)
              .build(CacheLoader.from(this::calculateAnnotationTypeOrigins));
  private final Cache<Method, List<List<TestParameterValueHolder>>> parameterValuesCache =
      CacheBuilder.newBuilder().maximumSize(1000).build();

  private TestParameterAnnotationMethodProcessor(boolean onlyForFieldsAndParameters) {
    this.onlyForFieldsAndParameters = onlyForFieldsAndParameters;
  }

  /**
   * Constructs a new {@link TestMethodProcessor} that handles {@link
   * TestParameterAnnotation}-annotated annotations that are placed anywhere:
   *
   * <ul>
   *   <li>At a method / constructor parameter
   *   <li>At a field
   *   <li>At a method / constructor on the class
   *   <li>At the test class
   * </ul>
   */
  static TestMethodProcessor forAllAnnotationPlacements() {
    return new TestParameterAnnotationMethodProcessor(/* onlyForFieldsAndParameters= */ false);
  }

  /**
   * Constructs a new {@link TestMethodProcessor} that handles {@link
   * TestParameterAnnotation}-annotated annotations that are placed at fields or parameters.
   *
   * <p>Note that this excludes class and method-level annotations, as is the default (using the
   * constructor).
   */
  static TestMethodProcessor onlyForFieldsAndParameters() {
    return new TestParameterAnnotationMethodProcessor(/* onlyForFieldsAndParameters= */ true);
  }

  private ImmutableList<AnnotationTypeOrigin> calculateAnnotationTypeOrigins(Class<?> testClass) {
    // Collect all annotations used in declared fields and methods that have themselves a
    // @TestParameterAnnotation annotation.
    List<AnnotationTypeOrigin> fieldAnnotations =
        extractTestParameterAnnotations(
            FluentIterable.from(listWithParents(testClass))
                .transformAndConcat(c -> Arrays.asList(c.getDeclaredFields()))
                .transformAndConcat(field -> Arrays.asList(field.getAnnotations()))
                .toList(),
            Origin.FIELD);
    List<AnnotationTypeOrigin> methodAnnotations =
        extractTestParameterAnnotations(
            FluentIterable.from(testClass.getMethods())
                .transformAndConcat(method -> Arrays.asList(method.getAnnotations()))
                .toList(),
            Origin.METHOD);
    List<AnnotationTypeOrigin> parameterAnnotations =
        extractTestParameterAnnotations(
            FluentIterable.from(listWithParents(testClass))
                .transformAndConcat(c -> Arrays.asList(c.getDeclaredMethods()))
                .transformAndConcat(method -> Arrays.asList(method.getParameterAnnotations()))
                .transformAndConcat(Arrays::asList)
                .toList(),
            Origin.METHOD_PARAMETER);
    List<AnnotationTypeOrigin> classAnnotations =
        extractTestParameterAnnotations(Arrays.asList(testClass.getAnnotations()), Origin.CLASS);
    List<AnnotationTypeOrigin> constructorAnnotations =
        extractTestParameterAnnotations(
            FluentIterable.from(testClass.getDeclaredConstructors())
                .transformAndConcat(constructor -> Arrays.asList(constructor.getAnnotations()))
                .toList(),
            Origin.CONSTRUCTOR);
    List<AnnotationTypeOrigin> constructorParameterAnnotations =
        extractTestParameterAnnotations(
            FluentIterable.from(testClass.getDeclaredConstructors())
                .transformAndConcat(
                    constructor ->
                        FluentIterable.from(Arrays.asList(constructor.getParameterAnnotations()))
                            .transformAndConcat(Arrays::asList))
                .toList(),
            Origin.CONSTRUCTOR_PARAMETER);

    checkDuplicatedClassAndFieldAnnotations(
        constructorAnnotations, classAnnotations, fieldAnnotations);

    checkDuplicatedFieldsAnnotations(methodAnnotations, fieldAnnotations);

    checkState(
        FluentIterable.from(constructorAnnotations).toSet().size() == constructorAnnotations.size(),
        "Annotations should not be duplicated on the constructor.");

    checkState(
        FluentIterable.from(classAnnotations).toSet().size() == classAnnotations.size(),
        "Annotations should not be duplicated on the class.");

    if (onlyForFieldsAndParameters) {
      checkState(
          methodAnnotations.isEmpty(),
          "This test runner (constructed by the testparameterinjector package) was configured"
              + " to disallow method-level annotations that could be field/parameter"
              + " annotations, but found %s",
          methodAnnotations);
      checkState(
          classAnnotations.isEmpty(),
          "This test runner (constructed by the testparameterinjector package) was configured"
              + " to disallow class-level annotations that could be field/parameter annotations,"
              + " but found %s",
          classAnnotations);
      checkState(
          constructorAnnotations.isEmpty(),
          "This test runner (constructed by the testparameterinjector package) was configured"
              + " to disallow constructor-level annotations that could be field/parameter"
              + " annotations, but found %s",
          constructorAnnotations);
    }

    // The order matters, since it will determine which annotation processor is
    // called first.
    return FluentIterable.from(classAnnotations)
        .append(fieldAnnotations)
        .append(constructorAnnotations)
        .append(constructorParameterAnnotations)
        .append(methodAnnotations)
        .append(parameterAnnotations)
        .toSet()
        .asList();
  }

  private ImmutableList<AnnotationTypeOrigin> getAnnotationTypeOrigins(
      Class<?> testClass, Origin firstOrigin, Origin... otherOrigins) {
    Set<Origin> originsToFilterBy =
        ImmutableSet.<Origin>builder().add(firstOrigin).add(otherOrigins).build();
    try {
      return FluentIterable.from(annotationTypeOriginsCache.getUnchecked(testClass))
          .filter(annotationTypeOrigin -> originsToFilterBy.contains(annotationTypeOrigin.origin()))
          .toList();
    } catch (UncheckedExecutionException e) {
      Throwables.throwIfInstanceOf(e.getCause(), IllegalStateException.class);
      throw e;
    }
  }

  private void checkDuplicatedFieldsAnnotations(
      List<AnnotationTypeOrigin> methodAnnotations, List<AnnotationTypeOrigin> fieldAnnotations) {
    // If an annotation is duplicated on two fields, then it becomes specific, and cannot be
    // overridden by a method.
    if (FluentIterable.from(fieldAnnotations).toSet().size() != fieldAnnotations.size()) {
      List<Class<? extends Annotation>> methodOrFieldAnnotations =
          new ArrayList<>(
              FluentIterable.from(methodAnnotations)
                  .append(new HashSet<>(fieldAnnotations))
                  .transform(AnnotationTypeOrigin::annotationType)
                  .toList());

      checkState(
          FluentIterable.from(methodOrFieldAnnotations).toSet().size()
              == methodOrFieldAnnotations.size(),
          "Annotations should not be duplicated on a method and field"
              + " if they are present on multiple fields");
    }
  }

  private void checkDuplicatedClassAndFieldAnnotations(
      List<AnnotationTypeOrigin> constructorAnnotations,
      List<AnnotationTypeOrigin> classAnnotations,
      List<AnnotationTypeOrigin> fieldAnnotations) {
    ImmutableSet<? extends Class<? extends Annotation>> classAnnotationTypes =
        FluentIterable.from(classAnnotations)
            .transform(AnnotationTypeOrigin::annotationType)
            .toSet();

    ImmutableSet<? extends Class<? extends Annotation>> uniqueFieldAnnotations =
        FluentIterable.from(fieldAnnotations)
            .transform(AnnotationTypeOrigin::annotationType)
            .toSet();
    ImmutableSet<? extends Class<? extends Annotation>> uniqueConstructorAnnotations =
        FluentIterable.from(constructorAnnotations)
            .transform(AnnotationTypeOrigin::annotationType)
            .toSet();

    checkState(
        Collections.disjoint(classAnnotationTypes, uniqueFieldAnnotations),
        "Annotations should not be duplicated on a class and field");

    checkState(
        Collections.disjoint(classAnnotationTypes, uniqueConstructorAnnotations),
        "Annotations should not be duplicated on a class and constructor");

    checkState(
        Collections.disjoint(uniqueConstructorAnnotations, uniqueFieldAnnotations),
        "Annotations should not be duplicated on a field and constructor");
  }

  private List<AnnotationTypeOrigin> extractTestParameterAnnotations(
      List<Annotation> annotations, Origin origin) {
    return new ArrayList<>(
        FluentIterable.from(annotations)
            .transform(Annotation::annotationType)
            .filter(
                annotationType -> annotationType.isAnnotationPresent(TestParameterAnnotation.class))
            .transform(annotationType -> AnnotationTypeOrigin.create(annotationType, origin))
            .toList());
  }

  @Override
  public ExecutableValidationResult validateConstructor(Constructor<?> constructor) {
    Class<?>[] parameterTypes = constructor.getParameterTypes();
    if (parameterTypes.length == 0) {
      return ExecutableValidationResult.notValidated();
    }
    // The constructor has parameters, they must be injected by a TestParameterAnnotation
    // annotation.
    Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
    Class<?> testClass = constructor.getDeclaringClass();
    return ExecutableValidationResult.validated(
        validateMethodOrConstructorParameters(
            removeOverrides(
                getAnnotationTypeOrigins(
                    testClass, Origin.CLASS, Origin.CONSTRUCTOR, Origin.CONSTRUCTOR_PARAMETER),
                testClass),
            testClass,
            constructor,
            parameterTypes,
            parameterAnnotations));
  }

  @Override
  public ExecutableValidationResult validateTestMethod(Method testMethod, Class<?> testClass) {
    Class<?>[] methodParameterTypes = testMethod.getParameterTypes();
    if (methodParameterTypes.length == 0) {
      return ExecutableValidationResult.notValidated();
    } else {
      // The method has parameters, they must be injected by a TestParameterAnnotation annotation.
      return ExecutableValidationResult.validated(
          validateMethodOrConstructorParameters(
              getAnnotationTypeOrigins(
                  testClass, Origin.CLASS, Origin.METHOD, Origin.METHOD_PARAMETER),
              testClass,
              testMethod,
              methodParameterTypes,
              testMethod.getParameterAnnotations()));
    }
  }

  private List<Throwable> validateMethodOrConstructorParameters(
      List<AnnotationTypeOrigin> annotationTypeOrigins,
      Class<?> testClass,
      AnnotatedElement methodOrConstructor,
      Class<?>[] parameterTypes,
      Annotation[][] parametersAnnotations) {
    List<Throwable> errors = new ArrayList<>();

    for (int parameterIndex = 0; parameterIndex < parameterTypes.length; parameterIndex++) {
      Class<?> parameterType = parameterTypes[parameterIndex];
      Annotation[] parameterAnnotations = parametersAnnotations[parameterIndex];
      boolean matchingTestParameterAnnotationFound = false;
      // First, handle the case where the method parameter specifies the test parameter explicitly,
      // e.g. {@code public void test(@ColorParameter({...}) Color c)}.
      for (AnnotationTypeOrigin testParameterAnnotationType : annotationTypeOrigins) {
        for (Annotation parameterAnnotation : parameterAnnotations) {
          if (parameterAnnotation
              .annotationType()
              .equals(testParameterAnnotationType.annotationType())) {
            // Verify that the type is assignable with the return type of the 'value' method.
            Class<?> valueMethodReturnType =
                getValueMethodReturnType(
                    testParameterAnnotationType.annotationType(),
                    /* paramClass= */ Optional.of(parameterType));
            if (!parameterType.isAssignableFrom(valueMethodReturnType)) {
              errors.add(
                  new IllegalStateException(
                      String.format(
                          "Parameter of type %s annotated with %s does not match"
                              + " expected type %s in method/constructor %s",
                          parameterType.getName(),
                          testParameterAnnotationType.annotationType().getName(),
                          valueMethodReturnType.getName(),
                          methodOrConstructor)));
            } else {
              matchingTestParameterAnnotationFound = true;
            }
          }
        }
      }
      // Second, handle the case where the method parameter does not specify the test parameter,
      // and instead relies on the type matching, e.g. {@code public void test(Color c)}.
      if (!matchingTestParameterAnnotationFound) {
        ImmutableList<? extends Class<? extends Annotation>> testParameterAnnotationTypes =
            getTestParameterAnnotations(
                // Do not include METHOD_PARAMETER or CONSTRUCTOR_PARAMETER since they have already
                // been evaluated.
                filterAnnotationTypeOriginsByOrigin(
                    annotationTypeOrigins, Origin.CLASS, Origin.CONSTRUCTOR, Origin.METHOD),
                testClass,
                methodOrConstructor);
        // If no annotation is present, simply compare the type.
        for (Class<? extends Annotation> testParameterAnnotationType :
            testParameterAnnotationTypes) {
          if (parameterType.isAssignableFrom(
              getValueMethodReturnType(
                  testParameterAnnotationType, /* paramClass= */ Optional.absent()))) {
            if (matchingTestParameterAnnotationFound) {
              errors.add(
                  new IllegalStateException(
                      String.format(
                          "Ambiguous method/constructor parameter type, matching multiple"
                              + " annotations for parameter of type %s in method %s",
                          parameterType.getName(), methodOrConstructor)));
            }
            matchingTestParameterAnnotationFound = true;
          }
        }
      }
      if (!matchingTestParameterAnnotationFound) {
        errors.add(
            new IllegalStateException(
                String.format(
                    "No matching test parameter annotation found"
                        + " for parameter of type %s in method/constructor %s",
                    parameterType.getName(), methodOrConstructor)));
      }
    }
    return errors;
  }

  @Override
  public Optional<List<Object>> maybeGetConstructorParameters(
      Constructor<?> constructor, TestInfo testInfo) {
    if (testInfo.getAnnotation(TestIndexHolder.class) == null
        // Explicitly skip @TestParameters annotated methods to ensure compatibility.
        //
        // Reason (see b/175678220): @TestIndexHolder will even be present when the only (supported)
        // parameterization is at the field level (e.g. @TestParameter private TestEnum enum;).
        // Without the @TestParameters check below, this class would try to find parameters for
        // these methods. When there are no method parameters, this is a no-op, but when the method
        // is annotated with @TestParameters, this throws an exception (because there are method
        // parameters that this processor has no values for - they are provided by the
        // @TestParameters processor).
        || constructor.isAnnotationPresent(TestParameters.class)) {
      return Optional.absent();
    } else {
      TestIndexHolder testIndexHolder = testInfo.getAnnotation(TestIndexHolder.class);
      List<TestParameterValueHolder> testParameterValues =
          getParameterValuesForTest(testIndexHolder, testInfo.getTestClass());

      Class<?>[] parameterTypes = constructor.getParameterTypes();
      Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
      List<Object> parameterValues = new ArrayList<>(/* initialCapacity= */ parameterTypes.length);
      List<Class<? extends Annotation>> processedAnnotationTypes = new ArrayList<>();
      List<TestParameterValueHolder> parameterValuesForConstructor =
          filterByOrigin(
              testParameterValues, Origin.CLASS, Origin.CONSTRUCTOR, Origin.CONSTRUCTOR_PARAMETER);
      for (int i = 0; i < parameterTypes.length; i++) {
        // Initialize each parameter value from the corresponding TestParameterAnnotation value.
        parameterValues.add(
            getParameterValue(
                parameterValuesForConstructor,
                parameterTypes[i],
                parameterAnnotations[i],
                processedAnnotationTypes));
      }
      return Optional.of(parameterValues);
    }
  }

  @Override
  public Optional<List<Object>> maybeGetTestMethodParameters(TestInfo testInfo) {
    Method testMethod = testInfo.getMethod();
    if (testInfo.getAnnotation(TestIndexHolder.class) == null
        // Explicitly skip @TestParameters annotated methods to ensure compatibility.
        //
        // Reason (see b/175678220): @TestIndexHolder will even be present when the only (supported)
        // parameterization is at the field level (e.g. @TestParameter private TestEnum enum;).
        // Without the @TestParameters check below, this class would try to find parameters for
        // these methods. When there are no method parameters, this is a no-op, but when the method
        // is annotated with @TestParameters, this throws an exception (because there are method
        // parameters that this processor has no values for - they are provided by the
        // @TestParameters processor).
        || testMethod.isAnnotationPresent(TestParameters.class)) {
      return Optional.absent();
    } else {
      TestIndexHolder testIndexHolder = testInfo.getAnnotation(TestIndexHolder.class);
      checkState(testIndexHolder != null);
      List<TestParameterValueHolder> testParameterValues =
          filterByOrigin(
              getParameterValuesForTest(testIndexHolder, testInfo.getTestClass()),
              Origin.CLASS,
              Origin.METHOD,
              Origin.METHOD_PARAMETER);

      Class<?>[] parameterTypes = testMethod.getParameterTypes();
      Annotation[][] parametersAnnotations = testMethod.getParameterAnnotations();
      ArrayList<Object> parameterValues =
          new ArrayList<>(/* initialCapacity= */ parameterTypes.length);

      List<Class<? extends Annotation>> processedAnnotationTypes = new ArrayList<>();
      for (int i = 0; i < parameterTypes.length; i++) {
        parameterValues.add(
            getParameterValue(
                testParameterValues,
                parameterTypes[i],
                parametersAnnotations[i],
                processedAnnotationTypes));
      }

      return Optional.of(parameterValues);
    }
  }

  /**
   * Returns the {@link TestInfo}, one for each result of the cartesian product of each test
   * parameter values.
   *
   * <p>For example, given the annotation {@code @ColorParameter({BLUE, WHITE, RED})} on a method,
   * it method will return the TestParameterValues: "(@ColorParameter, BLUE), (@ColorParameter,
   * WHITE), (@ColorParameter, RED)}).
   *
   * <p>For multiple annotations (say, {@code @TestParameter("foo", "bar")} and
   * {@code @ColorParameter({BLUE, WHITE})}), it will generate the following result:
   *
   * <ul>
   *   <li>("foo", BLUE)
   *   <li>("foo", WHITE)
   *   <li>("bar", BLUE)
   *   <li>("bar", WHITE)
   *   <li>
   * </ul>
   *
   * corresponding to the cartesian product of both annotations.
   */
  @Override
  public List<TestInfo> calculateTestInfos(TestInfo originalTest) {
    List<List<TestParameterValueHolder>> parameterValuesForMethod =
        getParameterValuesForMethod(originalTest.getMethod(), originalTest.getTestClass());

    if (parameterValuesForMethod.equals(ImmutableList.of(ImmutableList.of()))) {
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

  private List<List<TestParameterValueHolder>> getParameterValuesForMethod(
      Method method, Class<?> testClass) {
    try {
      return parameterValuesCache.get(
          method,
          () -> {
            List<List<TestParameterValueHolder>> testParameterValuesList =
                getAnnotationValuesForUsedAnnotationTypes(method, testClass);

            return FluentIterable.from(Lists.cartesianProduct(testParameterValuesList))
                .filter(
                    // Skip tests based on the annotations' {@link Validator#shouldSkip} return
                    // value.
                    testParameterValues ->
                        FluentIterable.from(testParameterValues)
                            .filter(
                                testParameterValue ->
                                    callShouldSkip(
                                        testParameterValue.annotationTypeOrigin().annotationType(),
                                        testParameterValues))
                            .isEmpty())
                .toList();
          });
    } catch (ExecutionException | UncheckedExecutionException e) {
      Throwables.throwIfUnchecked(e.getCause());
      throw new RuntimeException(e);
    }
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

  /**
   * Returns the list of annotation index for all annotations defined in a given test method and its
   * class.
   */
  private ImmutableList<List<TestParameterValueHolder>> getAnnotationValuesForUsedAnnotationTypes(
      Method method, Class<?> testClass) {
    ImmutableList<AnnotationTypeOrigin> annotationTypes =
        FluentIterable.from(getAnnotationTypeOrigins(testClass, Origin.CLASS))
            .append(getAnnotationTypeOrigins(testClass, Origin.FIELD))
            .append(getAnnotationTypeOrigins(testClass, Origin.CONSTRUCTOR))
            .append(getAnnotationTypeOrigins(testClass, Origin.CONSTRUCTOR_PARAMETER))
            .append(getAnnotationTypeOrigins(testClass, Origin.METHOD))
            .append(
                ImmutableList.sortedCopyOf(
                    annotationComparator(method.getParameterAnnotations()),
                    getAnnotationTypeOrigins(testClass, Origin.METHOD_PARAMETER)))
            .toList();

    return FluentIterable.from(removeOverrides(annotationTypes, testClass, method))
        .transform(
            annotationTypeOrigin ->
                getAnnotationFromParametersOrTestOrClass(annotationTypeOrigin, method, testClass))
        .filter(l -> !l.isEmpty())
        .transformAndConcat(i -> i)
        .toList();
  }

  private Comparator<AnnotationTypeOrigin> annotationComparator(
      Annotation[][] parameterAnnotations) {
    ImmutableList<String> annotationOrdering =
        FluentIterable.from(parameterAnnotations)
            .transformAndConcat(Arrays::asList)
            .transform(Annotation::annotationType)
            .transform(Class::getName)
            .toList();
    return (annotationTypeOrigin, t1) ->
        Integer.compare(
            annotationOrdering.indexOf(annotationTypeOrigin.annotationType().getName()),
            annotationOrdering.indexOf(t1.annotationType().getName()));
  }

  /**
   * Returns a list of {@link AnnotationTypeOrigin} where the overridden annotation are removed for
   * the current {@code originalTest} and {@code testClass}.
   *
   * <p>Specifically, annotation defined on CLASS and FIELD elements will be removed if they are
   * also defined on the method, method parameter, constructor, or constructor parameters.
   */
  private List<AnnotationTypeOrigin> removeOverrides(
      List<AnnotationTypeOrigin> annotationTypeOrigins, Class<?> testClass, Method method) {
    return removeOverrides(
        new ArrayList<>(
            FluentIterable.from(annotationTypeOrigins)
                .filter(
                    annotationTypeOrigin -> {
                      switch (annotationTypeOrigin.origin()) {
                        case FIELD: // Fall through.
                        case CLASS:
                          return getAnnotationListWithType(
                                  method.getAnnotations(), annotationTypeOrigin.annotationType())
                              .isEmpty();
                        default:
                          return true;
                      }
                    })
                .toList()),
        testClass);
  }

  /**
   * @see #removeOverrides(List, Class)
   */
  private List<AnnotationTypeOrigin> removeOverrides(
      List<AnnotationTypeOrigin> annotationTypeOrigins, Class<?> testClass) {
    return new ArrayList<>(
        FluentIterable.from(annotationTypeOrigins)
            .filter(
                annotationTypeOrigin -> {
                  switch (annotationTypeOrigin.origin()) {
                    case FIELD: // Fall through.
                    case CLASS:
                      return getAnnotationListWithType(
                              TestParameterInjectorUtils.getOnlyConstructor(testClass)
                                  .getAnnotations(),
                              annotationTypeOrigin.annotationType())
                          .isEmpty();
                    default:
                      return true;
                  }
                })
            .toList());
  }

  /**
   * Returns the given annotations defined either on the method parameters, method or the test
   * class.
   *
   * <p>The annotation from the parameters takes precedence over the same annotation defined on the
   * method, and the one defined on the method takes precedence over the same annotation defined on
   * the class.
   */
  private ImmutableList<List<TestParameterValueHolder>> getAnnotationFromParametersOrTestOrClass(
      AnnotationTypeOrigin annotationTypeOrigin, Method method, Class<?> testClass) {
    Origin origin = annotationTypeOrigin.origin();
    Class<? extends Annotation> annotationType = annotationTypeOrigin.annotationType();
    if (origin == Origin.CONSTRUCTOR_PARAMETER) {
      Constructor<?> constructor = TestParameterInjectorUtils.getOnlyConstructor(testClass);
      List<AnnotationWithMetadata> annotations =
          getAnnotationWithMetadataListWithType(constructor, annotationType, testClass);

      if (!annotations.isEmpty()) {
        return toTestParameterValueList(annotations, origin);
      }
    } else if (origin == Origin.CONSTRUCTOR) {
      Annotation annotation =
          TestParameterInjectorUtils.getOnlyConstructor(testClass).getAnnotation(annotationType);
      if (annotation != null) {
        return ImmutableList.of(
            TestParameterValueHolder.create(
                AnnotationWithMetadata.withoutMetadata(
                    annotation,
                    GenericParameterContext.createWithoutParameterAnnotations(testClass)),
                origin));
      }

    } else if (origin == Origin.METHOD_PARAMETER) {
      List<AnnotationWithMetadata> annotations =
          getAnnotationWithMetadataListWithType(method, annotationType, testClass);
      if (!annotations.isEmpty()) {
        return toTestParameterValueList(annotations, origin);
      }
    } else if (origin == Origin.METHOD) {
      if (method.isAnnotationPresent(annotationType)) {
        return ImmutableList.of(
            TestParameterValueHolder.create(
                AnnotationWithMetadata.withoutMetadata(
                    method.getAnnotation(annotationType),
                    GenericParameterContext.createWithoutParameterAnnotations(testClass)),
                origin));
      }
    } else if (origin == Origin.FIELD) {
      List<AnnotationWithMetadata> annotations =
          new ArrayList<>(
              FluentIterable.from(listWithParents(testClass))
                  .transformAndConcat(c -> Arrays.asList(c.getDeclaredFields()))
                  .transformAndConcat(
                      field ->
                          FluentIterable.from(
                                  getAnnotationListWithType(field.getAnnotations(), annotationType))
                              .transform(
                                  annotation ->
                                      AnnotationWithMetadata.withMetadata(
                                          annotation,
                                          field.getType(),
                                          field.getName(),
                                          GenericParameterContext.create(field, testClass))))
                  .toList());
      if (!annotations.isEmpty()) {
        return toTestParameterValueList(annotations, origin);
      }
    } else if (origin == Origin.CLASS) {
      Annotation annotation = testClass.getAnnotation(annotationType);
      if (annotation != null) {
        return ImmutableList.of(
            TestParameterValueHolder.create(
                AnnotationWithMetadata.withoutMetadata(
                    annotation,
                    GenericParameterContext.createWithoutParameterAnnotations(testClass)),
                origin));
      }
    }
    return ImmutableList.of();
  }

  private static ImmutableList<List<TestParameterValueHolder>> toTestParameterValueList(
      List<AnnotationWithMetadata> annotationWithMetadatas, Origin origin) {
    return FluentIterable.from(annotationWithMetadatas)
        .transform(
            annotationWithMetadata ->
                (List<TestParameterValueHolder>)
                    new ArrayList<>(
                        TestParameterValueHolder.create(annotationWithMetadata, origin)))
        .toList();
  }

  private static ImmutableList<AnnotationWithMetadata> getAnnotationWithMetadataListWithType(
      Method callable, Class<? extends Annotation> annotationType, Class<?> testClass) {
    try {
      return getAnnotationWithMetadataListWithType(
          callable.getParameters(), annotationType, testClass);
    } catch (NoSuchMethodError ignored) {
      return getAnnotationWithMetadataListWithType(
          callable.getParameterTypes(),
          callable.getParameterAnnotations(),
          annotationType,
          testClass);
    }
  }

  private static ImmutableList<AnnotationWithMetadata> getAnnotationWithMetadataListWithType(
      Constructor<?> callable, Class<? extends Annotation> annotationType, Class<?> testClass) {
    try {
      return getAnnotationWithMetadataListWithType(
          callable.getParameters(), annotationType, testClass);
    } catch (NoSuchMethodError ignored) {
      return getAnnotationWithMetadataListWithType(
          callable.getParameterTypes(),
          callable.getParameterAnnotations(),
          annotationType,
          testClass);
    }
  }

  // Parameter is not available on old Android SDKs, and isn't desugared. That's why this method
  // has a fallback that takes the parameter types and annotations (without the parameter names,
  // which are optional anyway).
  @SuppressWarnings("AndroidJdkLibsChecker")
  private static ImmutableList<AnnotationWithMetadata> getAnnotationWithMetadataListWithType(
      Parameter[] parameters, Class<? extends Annotation> annotationType, Class<?> testClass) {
    return FluentIterable.from(parameters)
        .transform(
            parameter -> {
              Annotation annotation = parameter.getAnnotation(annotationType);
              return annotation == null
                  ? null
                  : parameter.isNamePresent()
                      ? AnnotationWithMetadata.withMetadata(
                          annotation,
                          parameter.getType(),
                          parameter.getName(),
                          GenericParameterContext.create(parameter, testClass))
                      : AnnotationWithMetadata.withMetadata(
                          annotation,
                          parameter.getType(),
                          GenericParameterContext.create(parameter, testClass));
            })
        .filter(Objects::nonNull)
        .toList();
  }

  private static ImmutableList<AnnotationWithMetadata> getAnnotationWithMetadataListWithType(
      Class<?>[] parameterTypes,
      Annotation[][] annotations,
      Class<? extends Annotation> annotationType,
      Class<?> testClass) {
    checkArgument(parameterTypes.length == annotations.length);

    ImmutableList.Builder<AnnotationWithMetadata> resultBuilder = ImmutableList.builder();
    for (int i = 0; i < annotations.length; i++) {
      for (Annotation annotation : annotations[i]) {
        if (annotation.annotationType().equals(annotationType)) {
          resultBuilder.add(
              AnnotationWithMetadata.withMetadata(
                  annotation,
                  parameterTypes[i],
                  GenericParameterContext.createWithRepeatableAnnotationsFallback(
                      annotations[i], testClass)));
        }
      }
    }
    return resultBuilder.build();
  }

  private ImmutableList<Annotation> getAnnotationListWithType(
      Annotation[] annotations, Class<? extends Annotation> annotationType) {
    return FluentIterable.from(annotations)
        .filter(annotation -> annotation.annotationType().equals(annotationType))
        .toList();
  }

  @Override
  public void postProcessTestInstance(Object testInstance, TestInfo testInfo) {
    TestIndexHolder testIndexHolder = testInfo.getAnnotation(TestIndexHolder.class);
    try {
      if (testIndexHolder != null) {
        List<TestParameterValueHolder> testParameterValues =
            getParameterValuesForTest(testIndexHolder, testInfo.getTestClass());

        // Do not include {@link Origin#METHOD_PARAMETER} nor {@link Origin#CONSTRUCTOR_PARAMETER}
        // annotations.
        List<TestParameterValueHolder> testParameterValuesForFieldInjection =
            filterByOrigin(testParameterValues, Origin.CLASS, Origin.FIELD, Origin.METHOD);
        // The annotationType corresponding to the annotationIndex, e.g. ColorParameter.class
        // in the example above.
        List<TestParameterValueHolder> remainingTestParameterValuesForFieldInjection =
            new ArrayList<>(testParameterValuesForFieldInjection);
        for (Field declaredField :
            FluentIterable.from(listWithParents(testInstance.getClass()))
                .transformAndConcat(c -> Arrays.asList(c.getDeclaredFields()))
                .toList()) {
          for (TestParameterValueHolder testParameterValue :
              remainingTestParameterValuesForFieldInjection) {
            if (declaredField.isAnnotationPresent(
                testParameterValue.annotationTypeOrigin().annotationType())) {
              if (testParameterValue.paramName().isPresent()
                  && !declaredField.getName().equals(testParameterValue.paramName().get())) {
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

  /**
   * Returns an {@link TestParameterValueHolder} list that contains only the values originating from
   * one of the {@code origins}.
   */
  private static ImmutableList<TestParameterValueHolder> filterByOrigin(
      List<TestParameterValueHolder> testParameterValues, Origin... origins) {
    Set<Origin> originsToFilterBy = ImmutableSet.copyOf(origins);
    return FluentIterable.from(testParameterValues)
        .filter(
            testParameterValue ->
                originsToFilterBy.contains(testParameterValue.annotationTypeOrigin().origin()))
        .toList();
  }

  /**
   * Returns an {@link AnnotationTypeOrigin} list that contains only the values originating from one
   * of the {@code origins}.
   */
  private static ImmutableList<AnnotationTypeOrigin> filterAnnotationTypeOriginsByOrigin(
      List<AnnotationTypeOrigin> annotationTypeOrigins, Origin... origins) {
    List<Origin> originList = Arrays.asList(origins);
    return FluentIterable.from(annotationTypeOrigins)
        .filter(annotationTypeOrigin -> originList.contains(annotationTypeOrigin.origin()))
        .toList();
  }

  /** Returns a {@link TestParameterAnnotation}'s value for a method or constructor parameter. */
  private Object getParameterValue(
      List<TestParameterValueHolder> testParameterValues,
      Class<?> methodParameterType,
      Annotation[] parameterAnnotations,
      List<Class<? extends Annotation>> processedAnnotationTypes) {
    List<Class<? extends Annotation>> iteratedAnnotationTypes = new ArrayList<>();
    for (TestParameterValueHolder testParameterValue : testParameterValues) {
      // The annotationType corresponding to the annotationIndex, e.g. ColorParameter.class
      // in the example above.
      for (Annotation parameterAnnotation : parameterAnnotations) {
        Class<? extends Annotation> annotationType =
            testParameterValue.annotationTypeOrigin().annotationType();
        if (parameterAnnotation.annotationType().equals(annotationType)) {
          // If multiple annotations exist, ensure that the proper one is selected.
          // For instance, for:
          // <code>
          //    test(@FooParameter(1,2) Foo foo, @FooParameter(3,4) Foo bar) {}
          // </code>
          // Verifies that the correct @FooParameter annotation value will be assigned to the
          // corresponding variable.
          if (Collections.frequency(processedAnnotationTypes, annotationType)
              == Collections.frequency(iteratedAnnotationTypes, annotationType)) {
            processedAnnotationTypes.add(annotationType);
            return testParameterValue.unwrappedValue();
          }
          iteratedAnnotationTypes.add(annotationType);
        }
      }
    }
    // If no annotation matches, use the method parameter type.
    for (TestParameterValueHolder testParameterValue : testParameterValues) {
      // The annotationType corresponding to the annotationIndex, e.g. ColorParameter.class
      // in the example above.
      if (methodParameterType.isAssignableFrom(
          getValueMethodReturnType(
              testParameterValue.annotationTypeOrigin().annotationType(),
              /* paramClass= */ Optional.absent()))) {
        return testParameterValue.unwrappedValue();
      }
    }
    throw new IllegalStateException(
        "The method parameter should have matched a TestParameterAnnotation");
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
      return new AutoAnnotation_TestParameterAnnotationMethodProcessor_TestIndexHolderFactory_create(
          methodIndex, parametersIndex, testClassName);
    }

    private TestIndexHolderFactory() {}
  }

  /**
   * Returns whether the test should be skipped according to the {@code annotationType}'s {@link
   * TestParameterValidator} and the current list of {@link TestParameterValueHolder}.
   */
  private static boolean callShouldSkip(
      Class<? extends Annotation> annotationType,
      List<TestParameterValueHolder> testParameterValues) {
    TestParameterAnnotation annotation =
        annotationType.getAnnotation(TestParameterAnnotation.class);
    Class<? extends TestParameterValidator> validator = annotation.validator();
    try {
      return validator
          .getConstructor()
          .newInstance()
          .shouldSkip(new ValidatorContext(testParameterValues));
    } catch (Exception e) {
      throw new RuntimeException("Unexpected exception while invoking validator " + validator, e);
    }
  }

  private static class ValidatorContext implements TestParameterValidator.Context {

    private final List<TestParameterValueHolder> testParameterValues;
    private final Set<Object> valueList;

    public ValidatorContext(List<TestParameterValueHolder> testParameterValues) {
      this.testParameterValues = testParameterValues;
      this.valueList =
          FluentIterable.from(testParameterValues)
              .transform(TestParameterValueHolder::unwrappedValue)
              .filter(Objects::nonNull)
              .toSet();
    }

    @Override
    public boolean has(Class<? extends Annotation> testParameter, Object value) {
      return getValue(testParameter).transform(value::equals).or(false);
    }

    @Override
    public <T extends Enum<T>, U extends Enum<U>> boolean has(T value1, U value2) {
      return valueList.contains(value1) && valueList.contains(value2);
    }

    @Override
    public Optional<Object> getValue(Class<? extends Annotation> testParameter) {
      return getParameter(testParameter).transform(TestParameterValueHolder::unwrappedValue);
    }

    @Override
    public List<Object> getSpecifiedValues(Class<? extends Annotation> testParameter) {
      return getParameter(testParameter)
          .transform(TestParameterValueHolder::specifiedValues)
          .or(ImmutableList.of());
    }

    private Optional<TestParameterValueHolder> getParameter(
        Class<? extends Annotation> testParameter) {
      return FluentIterable.from(testParameterValues)
          .firstMatch(value -> value.annotationTypeOrigin().annotationType().equals(testParameter));
    }
  }

  /**
   * Returns the class of the list elements returned by {@code provideValues()}.
   *
   * @param annotationType The type of the annotation that was encountered in the test class. The
   *     definition of this annotation is itself annotated with the {@link TestParameterAnnotation}
   *     annotation.
   * @param paramClass The class of the parameter or field that is being annotated. In case the
   *     annotation is annotating a method, constructor or class, {@code paramClass} is an absent
   *     optional.
   */
  private static Class<?> getValueMethodReturnType(
      Class<? extends Annotation> annotationType, Optional<Class<?>> paramClass) {
    TestParameterAnnotation testParameter =
        annotationType.getAnnotation(TestParameterAnnotation.class);
    Class<? extends TestParameterValueProvider> valueProvider = testParameter.valueProvider();
    try {
      return valueProvider.getConstructor().newInstance().getValueType(annotationType, paramClass);
    } catch (Exception e) {
      throw new RuntimeException(
          "Unexpected exception while invoking value provider " + valueProvider, e);
    }
  }

  /** Returns the TestParameterAnnotation annotation types defined for a method or constructor. */
  private ImmutableList<? extends Class<? extends Annotation>> getTestParameterAnnotations(
      List<AnnotationTypeOrigin> annotationTypeOrigins,
      final Class<?> testClass,
      AnnotatedElement methodOrConstructor) {
    return FluentIterable.from(annotationTypeOrigins)
        .transform(AnnotationTypeOrigin::annotationType)
        .filter(
            annotationType ->
                testClass.isAnnotationPresent(annotationType)
                    || methodOrConstructor.isAnnotationPresent(annotationType))
        .toList();
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
}
