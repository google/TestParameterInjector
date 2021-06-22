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
import static java.lang.Math.min;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;

/** A POJO containing information about a test (name and anotations). */
@AutoValue
abstract class TestInfo {

  /**
   * The maximum amount of characters that {@link #getName()} can have.
   *
   * <p>See b/168325767 for the reason behind this. tl;dr the name is put into a Unix file with max
   * 255 characters. The surrounding constant characters take up 31 characters. The max is reduced
   * by an additional 24 characters to account for future changes.
   */
  static final int MAX_TEST_NAME_LENGTH = 200;

  /** The maximum amount of characters that a single parameter can take up in {@link #getName()}. */
  static final int MAX_PARAMETER_NAME_LENGTH = 100;

  public abstract Method getMethod();

  public abstract String getName();

  abstract ImmutableList<TestInfoParameter> getParameters();

  public abstract ImmutableList<Annotation> getAnnotations();

  @Nullable
  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    for (Annotation annotation : getAnnotations()) {
      if (annotationClass.isInstance(annotation)) {
        return annotationClass.cast(annotation);
      }
    }
    return null;
  }

  TestInfo withExtraParameters(List<TestInfoParameter> parameters) {
    ImmutableList<TestInfoParameter> newParameters =
        ImmutableList.<TestInfoParameter>builder()
            .addAll(this.getParameters())
            .addAll(parameters)
            .build();
    return new AutoValue_TestInfo(
        getMethod(),
        TestInfo.getDefaultName(getMethod(), newParameters),
        newParameters,
        getAnnotations());
  }

  TestInfo withExtraAnnotation(Annotation annotation) {
    ImmutableList<Annotation> newAnnotations =
        ImmutableList.<Annotation>builder().addAll(this.getAnnotations()).add(annotation).build();
    return new AutoValue_TestInfo(getMethod(), getName(), getParameters(), newAnnotations);
  }

  @VisibleForTesting
  TestInfo withName(String otherName) {
    return new AutoValue_TestInfo(getMethod(), otherName, getParameters(), getAnnotations());
  }

  public static TestInfo legacyCreate(Method method, String name, List<Annotation> annotations) {
    return new AutoValue_TestInfo(
        method, name, /* parameters= */ ImmutableList.of(), ImmutableList.copyOf(annotations));
  }

  static TestInfo createWithoutParameters(Method method, List<Annotation> annotations) {
    return new AutoValue_TestInfo(
        method,
        getDefaultName(method, /* parameters= */ ImmutableList.of()),
        /* parameters= */ ImmutableList.of(),
        ImmutableList.copyOf(annotations));
  }

  static ImmutableList<TestInfo> shortenNamesIfNecessary(List<TestInfo> testInfos) {
    if (testInfos.stream()
        .anyMatch(
            info ->
                info.getName().length() > MAX_TEST_NAME_LENGTH
                    || info.getParameters().stream()
                        .anyMatch(param -> param.getName().length() > MAX_PARAMETER_NAME_LENGTH))) {
      int numberOfParameters = testInfos.get(0).getParameters().size();

      if (numberOfParameters == 0) {
        return ImmutableList.copyOf(testInfos);
      } else {
        Set<Integer> parameterIndicesThatNeedUpdate =
            IntStream.range(0, numberOfParameters)
                .filter(
                    parameterIndex ->
                        testInfos.stream()
                            .anyMatch(
                                info ->
                                    info.getParameters().get(parameterIndex).getName().length()
                                        > getMaxCharactersPerParameter(info, numberOfParameters)))
                .boxed()
                .collect(toSet());

        return testInfos.stream()
            .map(
                info ->
                    info.withName(
                        String.format(
                            "%s[%s]",
                            info.getMethod().getName(),
                            IntStream.range(0, numberOfParameters)
                                .mapToObj(
                                    parameterIndex ->
                                        parameterIndicesThatNeedUpdate.contains(parameterIndex)
                                            ? getShortenedName(
                                                info.getParameters().get(parameterIndex),
                                                getMaxCharactersPerParameter(
                                                    info, numberOfParameters))
                                            : info.getParameters().get(parameterIndex).getName())
                                .collect(joining(",")))))
            .collect(toImmutableList());
      }
    } else {
      return ImmutableList.copyOf(testInfos);
    }
  }

  private static int getMaxCharactersPerParameter(TestInfo testInfo, int numberOfParameters) {
    int maxLengthOfAllParameters =
        // Subtract 2 characters for square brackets
        MAX_TEST_NAME_LENGTH - testInfo.getMethod().getName().length() - 2;
    return min(
        // Subtract 4 characters to leave place for joining commas and the parameter index.
        maxLengthOfAllParameters / numberOfParameters - 4,
        // Subtract 3 characters to leave place for the parameter index
        MAX_PARAMETER_NAME_LENGTH - 3);
  }

  private static String getShortenedName(
      TestInfoParameter parameter, int maxCharactersPerParameter) {
    if (maxCharactersPerParameter < 4) {
      // Not enough characters for "..." suffix
      return String.valueOf(parameter.getIndexInValueSource() + 1);
    } else {
      String shortenedName =
          parameter.getName().length() > maxCharactersPerParameter
              ? parameter.getName().substring(0, maxCharactersPerParameter - 3) + "..."
              : parameter.getName();
      return String.format("%s.%s", parameter.getIndexInValueSource() + 1, shortenedName);
    }
  }

  private static String getDefaultName(Method testMethod, List<TestInfoParameter> parameters) {
    if (parameters.isEmpty()) {
      return testMethod.getName();
    } else {
      return String.format(
          "%s[%s]",
          testMethod.getName(),
          parameters.stream().map(TestInfoParameter::getName).collect(joining(",")));
    }
  }

  private static <E> Collector<E, ?, ImmutableList<E>> toImmutableList() {
    return Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf);
  }

  @AutoValue
  abstract static class TestInfoParameter {

    abstract String getName();

    @Nullable
    abstract Object getValue();

    /**
     * The index of this parameter value in the list of all values provided by the provider that
     * returned this value.
     */
    abstract int getIndexInValueSource();

    static TestInfoParameter create(String name, @Nullable Object value, int indexInValueSource) {
      checkArgument(indexInValueSource >= 0);
      return new AutoValue_TestInfo_TestInfoParameter(
          checkNotNull(name), value, indexInValueSource);
    }
  }
}
