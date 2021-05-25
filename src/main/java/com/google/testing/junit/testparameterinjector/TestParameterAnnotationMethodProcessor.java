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
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;

import com.google.auto.value.AutoAnnotation;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.primitives.Primitives;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

/**
 * {@code TestMethodProcessor} implementation for supporting parameterized tests annotated with
 * {@link TestParameterAnnotation}.
 *
 * @see TestParameterAnnotation
 */
class TestParameterAnnotationMethodProcessor implements TestMethodProcessor {

  /**
   * Class to hold an annotation type and origin and one of the values as returned by the {@code
   * value()} method.
   */
  @AutoValue
  abstract static class TestParameterValue implements Serializable {

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
    @Nullable
    abstract Object value();

    /**
     * The list of values specified by the @TestParameterAnnotation annotated annotation's {@code
     * value()} method (e.g. {true, false} in the case of a boolean parameter).
     */
    @SuppressWarnings("AutoValueImmutableFields") // intentional to allow null values
    abstract List<Object> specifiedValues();

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

    public static ImmutableList<TestParameterValue> create(
        AnnotationWithMetadata annotationWithMetadata, Origin origin) {
      List<Object> specifiedValues = getParametersAnnotationValues(annotationWithMetadata);
      checkState(
          !specifiedValues.isEmpty(),
          "The number of parameter values should not be 0"
              + ", otherwise the parameter would cause the test to be skipped.");
      return specifiedValues.stream()
          .map(
              value ->
                  new AutoValue_TestParameterAnnotationMethodProcessor_TestParameterValue(
                      AnnotationTypeOrigin.create(
                          annotationWithMetadata.annotation().annotationType(), origin),
                      value,
                      new ArrayList<>(specifiedValues),
                      annotationWithMetadata.paramClass(),
                      annotationWithMetadata.paramName()))
          .collect(toImmutableList());
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
          Optional.fromNullable(
              new TestParameterAnnotationMethodProcessor(
                      new TestClass(testInfo.getMethod().getDeclaringClass()),
                      /* onlyForFieldsAndParameters= */ false)
                  .getParameterValuesForTest(testIndexHolder).stream()
                      .filter(matches(annotationType))
                      .map(TestParameterValue::value)
                      .findFirst()
                      .orElse(null));
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

  private static List<Object> getParametersAnnotationValues(
      AnnotationWithMetadata annotationWithMetadata) {
    Annotation annotation = annotationWithMetadata.annotation();
    TestParameterAnnotation testParameter =
        annotation.annotationType().getAnnotation(TestParameterAnnotation.class);
    Class<? extends TestParameterValueProvider> valueProvider = testParameter.valueProvider();
    try {
      return valueProvider
          .getConstructor()
          .newInstance()
          .provideValues(
              annotation,
              java.util.Optional.ofNullable(annotationWithMetadata.paramClass().orNull()));
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(
          "Unexpected exception while invoking value provider " + valueProvider, e);
    }
  }

  private static Predicate<TestParameterValue> matches(Class<? extends Annotation> annotationType) {
    return testParameterValue ->
        testParameterValue.annotationTypeOrigin().annotationType().equals(annotationType);
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

    public static AnnotationWithMetadata withMetadata(
        Annotation annotation, Class<?> paramClass, String paramName) {
      return new AutoValue_TestParameterAnnotationMethodProcessor_AnnotationWithMetadata(
          annotation, Optional.of(paramClass), Optional.of(paramName));
    }

    public static AnnotationWithMetadata withMetadata(Annotation annotation, Class<?> paramClass) {
      return new AutoValue_TestParameterAnnotationMethodProcessor_AnnotationWithMetadata(
          annotation, Optional.of(paramClass), Optional.absent());
    }

    public static AnnotationWithMetadata withoutMetadata(Annotation annotation) {
      return new AutoValue_TestParameterAnnotationMethodProcessor_AnnotationWithMetadata(
          annotation, Optional.absent(), Optional.absent());
    }
  }

  private final TestClass testClass;
  private final boolean onlyForFieldsAndParameters;
  private volatile ImmutableList<AnnotationTypeOrigin> cachedAnnotationTypeOrigins;
  private final Cache<Method, List<List<TestParameterValue>>> parameterValuesCache =
      CacheBuilder.newBuilder().maximumSize(1000).build();

  private TestParameterAnnotationMethodProcessor(
      TestClass testClass, boolean onlyForFieldsAndParameters) {
    this.testClass = testClass;
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
  static TestMethodProcessor forAllAnnotationPlacements(TestClass testClass) {
    return new TestParameterAnnotationMethodProcessor(
        testClass, /* onlyForFieldsAndParameters= */ false);
  }

  /**
   * Constructs a new {@link TestMethodProcessor} that handles {@link
   * TestParameterAnnotation}-annotated annotations that are placed at fields or parameters.
   *
   * <p>Note that this excludes class and method-level annotations, as is the default (using the
   * constructor).
   */
  static TestMethodProcessor onlyForFieldsAndParameters(TestClass testClass) {
    return new TestParameterAnnotationMethodProcessor(
        testClass, /* onlyForFieldsAndParameters= */ true);
  }

  private ImmutableList<AnnotationTypeOrigin> getAnnotationTypeOrigins(
      Origin firstOrigin, Origin... otherOrigins) {
    if (cachedAnnotationTypeOrigins == null) {
      // Collect all annotations used in declared fields and methods that have themselves a
      // @TestParameterAnnotation annotation.
      List<AnnotationTypeOrigin> fieldAnnotations =
          extractTestParameterAnnotations(
              streamWithParents(testClass.getJavaClass())
                  .flatMap(c -> stream(c.getDeclaredFields()))
                  .flatMap(field -> stream(field.getAnnotations())),
              Origin.FIELD);
      List<AnnotationTypeOrigin> methodAnnotations =
          extractTestParameterAnnotations(
              stream(testClass.getJavaClass().getMethods())
                  .flatMap(method -> stream(method.getAnnotations())),
              Origin.METHOD);
      List<AnnotationTypeOrigin> parameterAnnotations =
          extractTestParameterAnnotations(
              stream(testClass.getJavaClass().getMethods())
                  .flatMap(method -> stream(method.getParameterAnnotations()).flatMap(Stream::of)),
              Origin.METHOD_PARAMETER);
      List<AnnotationTypeOrigin> classAnnotations =
          extractTestParameterAnnotations(
              stream(testClass.getJavaClass().getAnnotations()), Origin.CLASS);
      List<AnnotationTypeOrigin> constructorAnnotations =
          extractTestParameterAnnotations(
              stream(testClass.getJavaClass().getConstructors())
                  .flatMap(constructor -> stream(constructor.getAnnotations())),
              Origin.CONSTRUCTOR);
      List<AnnotationTypeOrigin> constructorParameterAnnotations =
          extractTestParameterAnnotations(
              stream(testClass.getJavaClass().getConstructors())
                  .flatMap(
                      constructor ->
                          stream(constructor.getParameterAnnotations()).flatMap(Stream::of)),
              Origin.CONSTRUCTOR_PARAMETER);

      checkDuplicatedClassAndFieldAnnotations(
          constructorAnnotations, classAnnotations, fieldAnnotations);

      checkDuplicatedFieldsAnnotations(methodAnnotations, fieldAnnotations);

      checkState(
          constructorAnnotations.stream().distinct().count() == constructorAnnotations.size(),
          "Annotations should not be duplicated on the constructor.");

      checkState(
          classAnnotations.stream().distinct().count() == classAnnotations.size(),
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

      cachedAnnotationTypeOrigins =
          Stream.of(
                  // The order matters, since it will determine which annotation processor is
                  // called first.
                  classAnnotations.stream(),
                  fieldAnnotations.stream(),
                  constructorAnnotations.stream(),
                  constructorParameterAnnotations.stream(),
                  methodAnnotations.stream(),
                  parameterAnnotations.stream())
              .flatMap(x -> x)
              .distinct()
              .collect(toImmutableList());
    }

    Set<Origin> originsToFilterBy =
        ImmutableSet.<Origin>builder().add(firstOrigin).add(otherOrigins).build();
    return cachedAnnotationTypeOrigins.stream()
        .filter(annotationTypeOrigin -> originsToFilterBy.contains(annotationTypeOrigin.origin()))
        .collect(toImmutableList());
  }

  private void checkDuplicatedFieldsAnnotations(
      List<AnnotationTypeOrigin> methodAnnotations, List<AnnotationTypeOrigin> fieldAnnotations) {
    // If an annotation is duplicated on two fields, then it becomes specific, and cannot be
    // overridden by a method.
    if (fieldAnnotations.stream().distinct().count() != fieldAnnotations.size()) {
      List<Class<? extends Annotation>> methodOrFieldAnnotations =
          Stream.concat(methodAnnotations.stream(), fieldAnnotations.stream().distinct())
              .map(AnnotationTypeOrigin::annotationType)
              .collect(toCollection(ArrayList::new));

      checkState(
          methodOrFieldAnnotations.stream().distinct().count() == methodOrFieldAnnotations.size(),
          "Annotations should not be duplicated on a method and field"
              + " if they are present on multiple fields");
    }
  }

  private void checkDuplicatedClassAndFieldAnnotations(
      List<AnnotationTypeOrigin> constructorAnnotations,
      List<AnnotationTypeOrigin> classAnnotations,
      List<AnnotationTypeOrigin> fieldAnnotations) {
    ImmutableSet<? extends Class<? extends Annotation>> classAnnotationTypes =
        classAnnotations.stream()
            .map(AnnotationTypeOrigin::annotationType)
            .collect(toImmutableSet());

    ImmutableSet<Class<? extends Annotation>> uniqueFieldAnnotations =
        fieldAnnotations.stream()
            .map(AnnotationTypeOrigin::annotationType)
            .collect(toImmutableSet());
    ImmutableSet<Class<? extends Annotation>> uniqueConstructorAnnotations =
        constructorAnnotations.stream()
            .map(AnnotationTypeOrigin::annotationType)
            .collect(toImmutableSet());

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

  /** Returns a list of annotation types that are a {@link TestParameterAnnotation}. */
  private List<AnnotationTypeOrigin> extractTestParameterAnnotations(
      Stream<Annotation> annotations, Origin origin) {
    return annotations
        .map(Annotation::annotationType)
        .filter(annotationType -> annotationType.isAnnotationPresent(TestParameterAnnotation.class))
        .map(annotationType -> AnnotationTypeOrigin.create(annotationType, origin))
        .collect(toCollection(ArrayList::new));
  }

  @Override
  public ValidationResult validateConstructor(TestClass testClass, List<Throwable> errorsReturned) {
    if (testClass.getJavaClass().getConstructors().length != 1) {
      errorsReturned.add(
          new IllegalStateException("Test class should have exactly one public constructor"));
      return ValidationResult.HANDLED;
    }
    Constructor<?> constructor = testClass.getOnlyConstructor();
    Class<?>[] parameterTypes = constructor.getParameterTypes();
    if (parameterTypes.length == 0) {
      return ValidationResult.NOT_HANDLED;
    }
    // The constructor has parameters, they must be injected by a TestParameterAnnotation
    // annotation.
    Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
    validateMethodOrConstructorParameters(
        removeOverrides(
            getAnnotationTypeOrigins(
                Origin.CLASS, Origin.CONSTRUCTOR, Origin.CONSTRUCTOR_PARAMETER),
            testClass.getJavaClass()),
        testClass,
        errorsReturned,
        constructor,
        parameterTypes,
        parameterAnnotations);

    return ValidationResult.HANDLED;
  }

  @Override
  public ValidationResult validateTestMethod(
      TestClass testClass, FrameworkMethod testMethod, List<Throwable> errorsReturned) {
    Class<?>[] methodParameterTypes = testMethod.getMethod().getParameterTypes();
    if (methodParameterTypes.length == 0) {
      return ValidationResult.NOT_HANDLED;
    } else {
      Method method = testMethod.getMethod();
      // The method has parameters, they must be injected by a TestParameterAnnotation annotation.
      testMethod.validatePublicVoid(false /* isStatic */, errorsReturned);
      Annotation[][] parametersAnnotations = method.getParameterAnnotations();
      validateMethodOrConstructorParameters(
          getAnnotationTypeOrigins(Origin.CLASS, Origin.METHOD, Origin.METHOD_PARAMETER),
          testClass,
          errorsReturned,
          method,
          methodParameterTypes,
          parametersAnnotations);
      return ValidationResult.HANDLED;
    }
  }

  private void validateMethodOrConstructorParameters(
      List<AnnotationTypeOrigin> annotationTypeOrigins,
      TestClass testClass,
      List<Throwable> errors,
      AnnotatedElement methodOrConstructor,
      Class<?>[] parameterTypes,
      Annotation[][] parametersAnnotations) {
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
                    /* paramClass = */ Optional.of(parameterType));
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
        List<Class<? extends Annotation>> testParameterAnnotationTypes =
            getTestParameterAnnotations(
                // Do not include METHOD_PARAMETER or CONSTRUCTOR_PARAMETER since they have already
                // been evaluated.
                filterAnnotationTypeOriginsByOrigin(
                    annotationTypeOrigins, Origin.CLASS, Origin.CONSTRUCTOR, Origin.METHOD),
                testClass.getJavaClass(),
                methodOrConstructor);
        // If no annotation is present, simply compare the type.
        for (Class<? extends Annotation> testParameterAnnotationType :
            testParameterAnnotationTypes) {
          if (parameterType.isAssignableFrom(
              getValueMethodReturnType(
                  testParameterAnnotationType, /* paramClass = */ Optional.absent()))) {
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
  }

  @Override
  public Optional<Statement> createStatement(
      TestClass testClass,
      FrameworkMethod frameworkMethod,
      Object testObject,
      Optional<Statement> statement) {
    if (frameworkMethod.getAnnotation(TestIndexHolder.class) == null
        // Explicitly skip @TestParameters annotated methods to ensure compatibility.
        //
        // Reason (see b/175678220): @TestIndexHolder will even be present when the only (supported)
        // parameterization is at the field level (e.g. @TestParameter private TestEnum enum;).
        // Without the @TestParameters check below, InvokeParameterizedMethod would be invoked for
        // these methods. When there are no method parameters, this is a no-op, but when the method
        // is annotated with @TestParameters, this throws an exception (because there are method
        // parameters that this processor has no values for - they are provided by the
        // @TestParameters processor).
        || frameworkMethod.getAnnotation(TestParameters.class) != null) {
      return statement;
    } else {
      return Optional.of(new InvokeParameterizedMethod(frameworkMethod, testObject));
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
  public List<TestInfo> processTest(Class<?> testClass, TestInfo originalTest) {
    List<List<TestParameterValue>> parameterValuesForMethod =
        getParameterValuesForMethod(originalTest.getMethod());

    if (parameterValuesForMethod.equals(ImmutableList.of(ImmutableList.of()))) {
      // This test is not parameterized
      return ImmutableList.of(originalTest);
    }

    ImmutableList.Builder<TestInfo> testInfos = ImmutableList.builder();
    for (int parametersIndex = 0;
        parametersIndex < parameterValuesForMethod.size();
        ++parametersIndex) {
      List<TestParameterValue> testParameterValues = parameterValuesForMethod.get(parametersIndex);
      String testNameSuffix = getTestNameSuffix(testParameterValues);
      testInfos.add(
          TestInfo.create(
              originalTest.getMethod(),
              appendParametersToTestName(originalTest.getName(), testNameSuffix),
              ImmutableList.<Annotation>builder()
                  .addAll(originalTest.getAnnotations())
                  .add(
                      TestIndexHolderFactory.create(
                          /* methodIndex= */ strictIndexOf(
                              getMethodsIncludingParents(testClass), originalTest.getMethod()),
                          parametersIndex,
                          testClass.getName()))
                  .build()));
    }

    return TestInfo.shortenNamesIfNecessary(
        testInfos.build(),
        testInfo ->
            appendParametersToTestName(
                originalTest.getName(),
                String.valueOf(
                    testInfo.getAnnotation(TestIndexHolder.class).parametersIndex() + 1)));
  }

  /**
   * Returns the suffix of the test given the {@code testParameterValues} that will be appended to
   * the test name inside bracket, e.g. "testname[suffix]".
   */
  private static String getTestNameSuffix(List<TestParameterValue> testParameterValues) {
    return testParameterValues.stream()
        .map(
            value -> {
              Class<? extends Annotation> annotationType =
                  value.annotationTypeOrigin().annotationType();
              String namePattern =
                  annotationType.getAnnotation(TestParameterAnnotation.class).name();
              if (value.paramName().isPresent()
                  && value.paramClass().isPresent()
                  && namePattern.equals("{0}")
                  && Primitives.unwrap(value.paramClass().get()).isPrimitive()) {
                // If no custom name pattern was set and this parameter is a primitive (e.g.
                // boolean
                // or integer), prefix the parameter value with its field name. This is to avoid
                // test names such as myMethod_success[true,false,2]. Instead, it'll be
                // myMethod_success[dryRun=true,experimentFlag=false,retries=2].
                return String.format("%s=%s", value.paramName().get(), value.value());
              } else {
                return MessageFormat.format(namePattern, value.value());
              }
            })
        .map(string -> string.trim().replaceAll("\\s+", " "))
        .collect(joining(","));
  }

  /**
   * Appends the given suffix to the given test name in brackets. If the original test name already
   * has brackets, the suffix is inserted in the existing brackets instead.
   */
  private static String appendParametersToTestName(String originalTestName, String testNameSuffix) {
    if (originalTestName.endsWith("]")) {
      return String.format(
          "%s,%s]", originalTestName.substring(0, originalTestName.length() - 1), testNameSuffix);
    } else {
      return String.format("%s[%s]", originalTestName, testNameSuffix);
    }
  }

  private List<List<TestParameterValue>> getParameterValuesForMethod(Method method) {
    try {
      return parameterValuesCache.get(
          method,
          () -> {
            List<List<TestParameterValue>> testParameterValuesList =
                getAnnotationValuesForUsedAnnotationTypes(testClass.getJavaClass(), method);

            return Lists.cartesianProduct(testParameterValuesList).stream()
                .filter(
                    // Skip tests based on the annotations' {@link Validator#shouldSkip} return
                    // value.
                    testParameterValues ->
                        testParameterValues.stream()
                            .noneMatch(
                                testParameterValue ->
                                    callShouldSkip(
                                        testParameterValue.annotationTypeOrigin().annotationType(),
                                        testParameterValues)))
                .collect(toImmutableList());
          });
    } catch (ExecutionException | UncheckedExecutionException e) {
      Throwables.throwIfUnchecked(e.getCause());
      throw new RuntimeException(e);
    }
  }

  private List<TestParameterValue> getParameterValuesForTest(TestIndexHolder testIndexHolder) {
    verify(
        testIndexHolder.testClassName().equals(testClass.getName()),
        "The class for which the given annotation was created (%s) is not the same as the test"
            + " class that this runner is handling (%s)",
        testIndexHolder.testClassName(),
        testClass.getName());
    Method testMethod =
        getMethodsIncludingParents(testClass.getJavaClass()).get(testIndexHolder.methodIndex());
    return getParameterValuesForMethod(testMethod).get(testIndexHolder.parametersIndex());
  }

  /**
   * Returns the list of annotation index for all annotations defined in a given test method and its
   * class.
   */
  private ImmutableList<List<TestParameterValue>> getAnnotationValuesForUsedAnnotationTypes(
      Class<?> testClass, Method method) {
    ImmutableList<AnnotationTypeOrigin> annotationTypes =
        Stream.of(
                getAnnotationTypeOrigins(Origin.CLASS).stream(),
                getAnnotationTypeOrigins(Origin.FIELD).stream(),
                getAnnotationTypeOrigins(Origin.CONSTRUCTOR).stream(),
                getAnnotationTypeOrigins(Origin.CONSTRUCTOR_PARAMETER).stream(),
                getAnnotationTypeOrigins(Origin.METHOD).stream(),
                getAnnotationTypeOrigins(Origin.METHOD_PARAMETER).stream()
                    .sorted(annotationComparator(method.getParameterAnnotations())))
            .flatMap(x -> x)
            .collect(toImmutableList());

    return removeOverrides(annotationTypes, testClass, method).stream()
        .map(
            annotationTypeOrigin ->
                getAnnotationFromParametersOrTestOrClass(annotationTypeOrigin, method, testClass))
        .filter(l -> !l.isEmpty())
        .flatMap(List::stream)
        .collect(toImmutableList());
  }

  private Comparator<AnnotationTypeOrigin> annotationComparator(
      Annotation[][] parameterAnnotations) {
    ImmutableList<String> annotationOrdering =
        stream(parameterAnnotations)
            .flatMap(Arrays::stream)
            .map(Annotation::annotationType)
            .map(Class::getName)
            .collect(toImmutableList());
    return Comparator.comparingInt(o -> annotationOrdering.indexOf(o.annotationType().getName()));
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
        annotationTypeOrigins.stream()
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
            .collect(toCollection(ArrayList::new)),
        testClass);
  }

  /** @see #removeOverrides(List, Class) */
  private List<AnnotationTypeOrigin> removeOverrides(
      List<AnnotationTypeOrigin> annotationTypeOrigins, Class<?> testClass) {
    return annotationTypeOrigins.stream()
        .filter(
            annotationTypeOrigin -> {
              switch (annotationTypeOrigin.origin()) {
                case FIELD: // Fall through.
                case CLASS:
                  return getAnnotationListWithType(
                              getOnlyConstructor(testClass).getAnnotations(),
                              annotationTypeOrigin.annotationType())
                          .isEmpty()
                      && getAnnotationListWithType(
                              getOnlyConstructor(testClass).getParameterAnnotations(),
                              annotationTypeOrigin.annotationType())
                          .isEmpty();
                default:
                  return true;
              }
            })
        .collect(toCollection(ArrayList::new));
  }

  /**
   * Returns the given annotations defined either on the method parameters, method or the test
   * class.
   *
   * <p>The annotation from the parameters takes precedence over the same annotation defined on the
   * method, and the one defined on the method takes precedence over the same annotation defined on
   * the class.
   */
  private ImmutableList<List<TestParameterValue>> getAnnotationFromParametersOrTestOrClass(
      AnnotationTypeOrigin annotationTypeOrigin, Method method, Class<?> testClass) {
    Origin origin = annotationTypeOrigin.origin();
    Class<? extends Annotation> annotationType = annotationTypeOrigin.annotationType();
    if (origin == Origin.CONSTRUCTOR_PARAMETER) {
      Constructor<?> constructor = getOnlyConstructor(testClass);
      List<AnnotationWithMetadata> annotations =
          getAnnotationWithMetadataListWithType(constructor, annotationType);

      if (!annotations.isEmpty()) {
        return toTestParameterValueList(annotations, origin);
      }
    } else if (origin == Origin.CONSTRUCTOR) {
      Annotation annotation = getOnlyConstructor(testClass).getAnnotation(annotationType);
      if (annotation != null) {
        return ImmutableList.of(
            TestParameterValue.create(AnnotationWithMetadata.withoutMetadata(annotation), origin));
      }

    } else if (origin == Origin.METHOD_PARAMETER) {
      List<AnnotationWithMetadata> annotations =
          getAnnotationWithMetadataListWithType(method, annotationType);
      if (!annotations.isEmpty()) {
        return toTestParameterValueList(annotations, origin);
      }
    } else if (origin == Origin.METHOD) {
      if (method.isAnnotationPresent(annotationType)) {
        return ImmutableList.of(
            TestParameterValue.create(
                AnnotationWithMetadata.withoutMetadata(method.getAnnotation(annotationType)),
                origin));
      }
    } else if (origin == Origin.FIELD) {
      List<AnnotationWithMetadata> annotations =
          streamWithParents(testClass)
              .flatMap(c -> stream(c.getDeclaredFields()))
              .flatMap(
                  field ->
                      getAnnotationListWithType(field.getAnnotations(), annotationType).stream()
                          .map(
                              annotation ->
                                  AnnotationWithMetadata.withMetadata(
                                      annotation, field.getType(), field.getName())))
              .collect(toCollection(ArrayList::new));
      if (!annotations.isEmpty()) {
        return toTestParameterValueList(annotations, origin);
      }
    } else if (origin == Origin.CLASS) {
      Annotation annotation = testClass.getAnnotation(annotationType);
      if (annotation != null) {
        return ImmutableList.of(
            TestParameterValue.create(AnnotationWithMetadata.withoutMetadata(annotation), origin));
      }
    }
    return ImmutableList.of();
  }

  private static ImmutableList<List<TestParameterValue>> toTestParameterValueList(
      List<AnnotationWithMetadata> annotationWithMetadatas, Origin origin) {
    return annotationWithMetadatas.stream()
        .map(annotationWithMetadata -> TestParameterValue.create(annotationWithMetadata, origin))
        .collect(toImmutableList());
  }

  private static ImmutableList<AnnotationWithMetadata> getAnnotationWithMetadataListWithType(
      Method callable, Class<? extends Annotation> annotationType) {
    try {
      return getAnnotationWithMetadataListWithType(callable.getParameters(), annotationType);
    } catch (NoSuchMethodError ignored) {
      return getAnnotationWithMetadataListWithType(
          callable.getParameterTypes(), callable.getParameterAnnotations(), annotationType);
    }
  }

  private static ImmutableList<AnnotationWithMetadata> getAnnotationWithMetadataListWithType(
      Constructor<?> callable, Class<? extends Annotation> annotationType) {
    try {
      return getAnnotationWithMetadataListWithType(callable.getParameters(), annotationType);
    } catch (NoSuchMethodError ignored) {
      return getAnnotationWithMetadataListWithType(
          callable.getParameterTypes(), callable.getParameterAnnotations(), annotationType);
    }
  }

  // Parameter is not available on old Android SDKs, and isn't desugared. That's why this method
  // has a fallback that takes the parameter types and annotations (without the parameter names,
  // which are optional anyway).
  @SuppressWarnings("AndroidJdkLibsChecker")
  private static ImmutableList<AnnotationWithMetadata> getAnnotationWithMetadataListWithType(
      Parameter[] parameters, Class<? extends Annotation> annotationType) {
    return stream(parameters)
        .map(
            parameter -> {
              Annotation annotation = parameter.getAnnotation(annotationType);
              return annotation == null
                  ? null
                  : parameter.isNamePresent()
                      ? AnnotationWithMetadata.withMetadata(
                          annotation, parameter.getType(), parameter.getName())
                      : AnnotationWithMetadata.withMetadata(annotation, parameter.getType());
            })
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }

  private static ImmutableList<AnnotationWithMetadata> getAnnotationWithMetadataListWithType(
      Class<?>[] parameterTypes,
      Annotation[][] annotations,
      Class<? extends Annotation> annotationType) {
    checkArgument(parameterTypes.length == annotations.length);

    ImmutableList.Builder<AnnotationWithMetadata> resultBuilder = ImmutableList.builder();
    for (int i = 0; i < annotations.length; i++) {
      for (Annotation annotation : annotations[i]) {
        if (annotation.annotationType().equals(annotationType)) {
          resultBuilder.add(AnnotationWithMetadata.withMetadata(annotation, parameterTypes[i]));
        }
      }
    }
    return resultBuilder.build();
  }

  private ImmutableList<Annotation> getAnnotationListWithType(
      Annotation[][] parameterAnnotations, Class<? extends Annotation> annotationType) {
    return stream(parameterAnnotations)
        .flatMap(Stream::of)
        .filter(annotation -> annotation.annotationType().equals(annotationType))
        .collect(toImmutableList());
  }

  private ImmutableList<Annotation> getAnnotationListWithType(
      Annotation[] annotations, Class<? extends Annotation> annotationType) {
    return stream(annotations)
        .filter(annotation -> annotation.annotationType().equals(annotationType))
        .collect(toImmutableList());
  }

  private static Constructor<?> getOnlyConstructor(Class<?> testClass) {
    Constructor<?>[] constructors = testClass.getConstructors();
    checkState(
        constructors.length == 1,
        "a single public constructor is required for class %s",
        testClass);
    return constructors[0];
  }

  @Override
  public Optional<Object> createTest(
      TestClass testClass, FrameworkMethod method, Optional<Object> test) {
    TestIndexHolder testIndexHolder = method.getAnnotation(TestIndexHolder.class);
    if (testIndexHolder == null) {
      return test;
    }
    try {
      List<TestParameterValue> testParameterValues = getParameterValuesForTest(testIndexHolder);

      Object testObject;
      if (test.isPresent()) {
        testObject = test.get();
      } else {
        Constructor<?> constructor = testClass.getOnlyConstructor();
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        if (parameterTypes.length == 0) {
          testObject = constructor.newInstance();
        } else {
          // The constructor has parameters, they must be injected by a TestParameterAnnotation
          // annotation.
          Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
          Object[] arguments = new Object[parameterTypes.length];
          List<Class<? extends Annotation>> processedAnnotationTypes = new ArrayList<>();
          List<TestParameterValue> parameterValuesForConstructor =
              filterByOrigin(
                  testParameterValues,
                  Origin.CLASS,
                  Origin.CONSTRUCTOR,
                  Origin.CONSTRUCTOR_PARAMETER);
          for (int i = 0; i < arguments.length; i++) {
            // Initialize each parameter value from the corresponding TestParameterAnnotation value.
            arguments[i] =
                getParameterValue(
                    parameterValuesForConstructor,
                    parameterTypes[i],
                    parameterAnnotations[i],
                    processedAnnotationTypes);
          }
          testObject = constructor.newInstance(arguments);
        }
      }
      // Do not include {@link Origin#METHOD_PARAMETER} nor {@link Origin#CONSTRUCTOR_PARAMETER}
      // annotations.
      List<TestParameterValue> testParameterValuesForFieldInjection =
          filterByOrigin(testParameterValues, Origin.CLASS, Origin.FIELD, Origin.METHOD);
      // The annotationType corresponding to the annotationIndex, e.g ColorParameter.class
      // in the example above.
      List<TestParameterValue> remainingTestParameterValuesForFieldInjection =
          new ArrayList<>(testParameterValuesForFieldInjection);
      for (Field declaredField :
          streamWithParents(testObject.getClass())
              .flatMap(c -> stream(c.getDeclaredFields()))
              .collect(toImmutableList())) {
        for (TestParameterValue testParameterValue :
            remainingTestParameterValuesForFieldInjection) {
          if (declaredField.isAnnotationPresent(
              testParameterValue.annotationTypeOrigin().annotationType())) {
            declaredField.setAccessible(true);
            declaredField.set(testObject, testParameterValue.value());
            remainingTestParameterValuesForFieldInjection.remove(testParameterValue);
            break;
          }
        }
      }
      return Optional.of(testObject);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns an {@link TestParameterValue} list that contains only the values originating from one
   * of the {@code origins}.
   */
  private static ImmutableList<TestParameterValue> filterByOrigin(
      List<TestParameterValue> testParameterValues, Origin... origins) {
    Set<Origin> originsToFilterBy = ImmutableSet.copyOf(origins);
    return testParameterValues.stream()
        .filter(
            testParameterValue ->
                originsToFilterBy.contains(testParameterValue.annotationTypeOrigin().origin()))
        .collect(toImmutableList());
  }

  /**
   * Returns an {@link AnnotationTypeOrigin} list that contains only the values originating from one
   * of the {@code origins}.
   */
  private static ImmutableList<AnnotationTypeOrigin> filterAnnotationTypeOriginsByOrigin(
      List<AnnotationTypeOrigin> annotationTypeOrigins, Origin... origins) {
    List<Origin> originList = Arrays.asList(origins);
    return annotationTypeOrigins.stream()
        .filter(annotationTypeOrigin -> originList.contains(annotationTypeOrigin.origin()))
        .collect(toImmutableList());
  }

  @Override
  public Statement processStatement(Statement originalStatement, Description finalTestDescription) {
    TestIndexHolder testIndexHolder = finalTestDescription.getAnnotation(TestIndexHolder.class);
    if (testIndexHolder == null) {
      return originalStatement;
    }
    List<TestParameterValue> testParameterValues = getParameterValuesForTest(testIndexHolder);

    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        for (TestParameterValue testParameterValue : testParameterValues) {
          callBefore(
              testParameterValue.annotationTypeOrigin().annotationType(),
              testParameterValue.value());
        }
        try {
          originalStatement.evaluate();
        } finally {
          // In reverse order.
          for (TestParameterValue testParameterValue : Lists.reverse(testParameterValues)) {
            callAfter(
                testParameterValue.annotationTypeOrigin().annotationType(),
                testParameterValue.value());
          }
        }
      }
    };
  }

  /**
   * Class to invoke the test method if it has parameters, and they need to be injected from the
   * TestParameterAnnotation values.
   */
  private class InvokeParameterizedMethod extends Statement {

    private final FrameworkMethod frameworkMethod;
    private final Object testObject;
    private final List<TestParameterValue> testParameterValues;

    public InvokeParameterizedMethod(FrameworkMethod frameworkMethod, Object testObject) {
      this.frameworkMethod = frameworkMethod;
      this.testObject = testObject;
      TestIndexHolder testIndexHolder = frameworkMethod.getAnnotation(TestIndexHolder.class);
      checkState(testIndexHolder != null);
      testParameterValues =
          filterByOrigin(
              getParameterValuesForTest(testIndexHolder),
              Origin.CLASS,
              Origin.METHOD,
              Origin.METHOD_PARAMETER);
    }

    @Override
    public void evaluate() throws Throwable {
      Class<?>[] parameterTypes = frameworkMethod.getMethod().getParameterTypes();
      Annotation[][] parametersAnnotations = frameworkMethod.getMethod().getParameterAnnotations();
      Object[] parameterValues = new Object[parameterTypes.length];

      List<Class<? extends Annotation>> processedAnnotationTypes = new ArrayList<>();
      // Initialize each parameter value from the corresponding TestParameterAnnotation value.
      for (int i = 0; i < parameterTypes.length; i++) {
        parameterValues[i] =
            getParameterValue(
                testParameterValues,
                parameterTypes[i],
                parametersAnnotations[i],
                processedAnnotationTypes);
      }
      frameworkMethod.invokeExplosively(testObject, parameterValues);
    }
  }

  /** Returns a {@link TestParameterAnnotation}'s value for a method or constructor parameter. */
  private Object getParameterValue(
      List<TestParameterValue> testParameterValues,
      Class<?> methodParameterType,
      Annotation[] parameterAnnotations,
      List<Class<? extends Annotation>> processedAnnotationTypes) {
    List<Class<? extends Annotation>> iteratedAnnotationTypes = new ArrayList<>();
    for (TestParameterValue testParameterValue : testParameterValues) {
      // The annotationType corresponding to the annotationIndex, e.g ColorParameter.class
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
            return testParameterValue.value();
          }
          iteratedAnnotationTypes.add(annotationType);
        }
      }
    }
    // If no annotation matches, use the method parameter type.
    for (TestParameterValue testParameterValue : testParameterValues) {
      // The annotationType corresponding to the annotationIndex, e.g ColorParameter.class
      // in the example above.
      if (methodParameterType.isAssignableFrom(
          getValueMethodReturnType(
              testParameterValue.annotationTypeOrigin().annotationType(),
              /* paramClass = */ Optional.absent()))) {
        return testParameterValue.value();
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

    /** The index of the test method in {@code getMethodsIncludingParents(testClass)} */
    int methodIndex();

    /**
     * The index of the set of parameters to run the test method with in the list produced by {@link
     * #getParameterValuesForMethod(Method)}.
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

  /** Invokes the {@link TestParameterProcessor#before} method of an annotation. */
  private static void callBefore(
      Class<? extends Annotation> annotationType, Object annotationValue) {
    TestParameterAnnotation annotation =
        annotationType.getAnnotation(TestParameterAnnotation.class);
    Class<? extends TestParameterProcessor> processor = annotation.processor();
    try {
      processor.getConstructor().newInstance().before(annotationValue);
    } catch (Exception e) {
      throw new RuntimeException("Unexpected exception while invoking processor " + processor, e);
    }
  }

  /** Invokes the {@link TestParameterProcessor#after} method of an annotation. */
  private static void callAfter(
      Class<? extends Annotation> annotationType, Object annotationValue) {
    TestParameterAnnotation annotation =
        annotationType.getAnnotation(TestParameterAnnotation.class);
    Class<? extends TestParameterProcessor> processor = annotation.processor();
    try {
      processor.getConstructor().newInstance().after(annotationValue);
    } catch (Exception e) {
      throw new RuntimeException("Unexpected exception while invoking processor " + processor, e);
    }
  }

  /**
   * Returns whether the test should be skipped according to the {@code annotationType}'s {@link
   * TestParameterValidator} and the current list of {@link TestParameterValue}.
   */
  private static boolean callShouldSkip(
      Class<? extends Annotation> annotationType, List<TestParameterValue> testParameterValues) {
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

    private final List<TestParameterValue> testParameterValues;
    private final Set<Object> valueList;

    public ValidatorContext(List<TestParameterValue> testParameterValues) {
      this.testParameterValues = testParameterValues;
      this.valueList = testParameterValues.stream().map(TestParameterValue::value).collect(toSet());
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
      return getParameter(testParameter).transform(TestParameterValue::value);
    }

    @Override
    public List<Object> getSpecifiedValues(Class<? extends Annotation> testParameter) {
      return getParameter(testParameter)
          .transform(TestParameterValue::specifiedValues)
          .or(ImmutableList.of());
    }

    private Optional<TestParameterValue> getParameter(Class<? extends Annotation> testParameter) {
      return Optional.fromNullable(
          testParameterValues.stream()
              .filter(value -> value.annotationTypeOrigin().annotationType().equals(testParameter))
              .findAny()
              .orElse(null));
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
      return valueProvider
          .getConstructor()
          .newInstance()
          .getValueType(annotationType, java.util.Optional.ofNullable(paramClass.orNull()));
    } catch (Exception e) {
      throw new RuntimeException(
          "Unexpected exception while invoking value provider " + valueProvider, e);
    }
  }

  /** Returns the TestParameterAnnotation annotation types defined for a method or constructor. */
  private ImmutableList<Class<? extends Annotation>> getTestParameterAnnotations(
      List<AnnotationTypeOrigin> annotationTypeOrigins,
      final Class<?> testClass,
      AnnotatedElement methodOrConstructor) {
    return annotationTypeOrigins.stream()
        .map(AnnotationTypeOrigin::annotationType)
        .filter(
            annotationType ->
                testClass.isAnnotationPresent(annotationType)
                    || methodOrConstructor.isAnnotationPresent(annotationType))
        .collect(toImmutableList());
  }

  private <T> int strictIndexOf(List<T> haystack, T needle) {
    int index = haystack.indexOf(needle);
    checkArgument(index >= 0, "Could not find '%s' in %s", needle, haystack);
    return index;
  }

  private ImmutableList<Method> getMethodsIncludingParents(Class<?> clazz) {
    ImmutableList.Builder<Method> resultBuilder = ImmutableList.builder();
    while (clazz != null) {
      resultBuilder.add(clazz.getMethods());
      clazz = clazz.getSuperclass();
    }
    return resultBuilder.build();
  }

  private static Stream<Class<?>> streamWithParents(Class<?> clazz) {
    Stream.Builder<Class<?>> resultBuilder = Stream.builder();

    Class<?> currentClass = clazz;
    while (currentClass != null) {
      resultBuilder.add(currentClass);
      currentClass = currentClass.getSuperclass();
    }

    return resultBuilder.build();
  }

  // Immutable collectors are re-implemented here because they are missing from the Android
  // collection library.
  private static <E> Collector<E, ?, ImmutableList<E>> toImmutableList() {
    return Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf);
  }

  private static <E> Collector<E, ?, ImmutableSet<E>> toImmutableSet() {
    return Collectors.collectingAndThen(Collectors.toList(), ImmutableSet::copyOf);
  }
}
