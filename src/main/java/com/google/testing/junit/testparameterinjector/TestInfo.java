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
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
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

  public abstract Method getMethod();

  public String getName() {
    if (getParameters().isEmpty()) {
      return getMethod().getName();
    } else {
      return String.format(
          "%s[%s]",
          getMethod().getName(),
          getParameters().stream().map(TestInfoParameter::getName).collect(joining(",")));
    }
  }

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
    return new AutoValue_TestInfo(
        getMethod(),
        ImmutableList.<TestInfoParameter>builder()
            .addAll(this.getParameters())
            .addAll(parameters)
            .build(),
        getAnnotations());
  }

  TestInfo withExtraAnnotation(Annotation annotation) {
    ImmutableList<Annotation> newAnnotations =
        ImmutableList.<Annotation>builder().addAll(this.getAnnotations()).add(annotation).build();
    return new AutoValue_TestInfo(getMethod(), getParameters(), newAnnotations);
  }

  /**
   * Returns a new TestInfo instance with updated parameter names.
   *
   * @param parameterWithIndexToNewName A function of the parameter and its index in the {@link
   *     #getParameters()} list to the new name.
   */
  private TestInfo withUpdatedParameterNames(
      BiFunction<TestInfoParameter, Integer, String> parameterWithIndexToNewName) {
    return new AutoValue_TestInfo(
        getMethod(),
        IntStream.range(0, getParameters().size())
            .mapToObj(
                parameterIndex -> {
                  TestInfoParameter parameter = getParameters().get(parameterIndex);
                  return parameter.withName(
                      parameterWithIndexToNewName.apply(parameter, parameterIndex));
                })
            .collect(toImmutableList()),
        getAnnotations());
  }

  public static TestInfo legacyCreate(Method method, String name, List<Annotation> annotations) {
    return new AutoValue_TestInfo(
        method, /* parameters= */ ImmutableList.of(), ImmutableList.copyOf(annotations));
  }

  static TestInfo createWithoutParameters(Method method, List<Annotation> annotations) {
    return new AutoValue_TestInfo(
        method, /* parameters= */ ImmutableList.of(), ImmutableList.copyOf(annotations));
  }

  static ImmutableList<TestInfo> shortenNamesIfNecessary(List<TestInfo> testInfos) {
    if (testInfos.stream().anyMatch(info -> info.getName().length() > MAX_TEST_NAME_LENGTH)) {
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
                    info.withUpdatedParameterNames(
                        (parameter, parameterIndex) ->
                            parameterIndicesThatNeedUpdate.contains(parameterIndex)
                                ? getShortenedName(
                                    parameter,
                                    getMaxCharactersPerParameter(info, numberOfParameters))
                                : info.getParameters().get(parameterIndex).getName()))
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

    // Subtract 4 characters to leave place for joining commas and the parameter index.
    return maxLengthOfAllParameters / numberOfParameters - 4;
  }

  static ImmutableList<TestInfo> deduplicateTestNames(List<TestInfo> testInfos) {
    long uniqueTestNameCount = testInfos.stream().map(TestInfo::getName).distinct().count();
    if (testInfos.size() == uniqueTestNameCount) {
      // Return early if there are no duplicates
      return ImmutableList.copyOf(testInfos);
    } else {
      return deduplicateWithNumberPrefixes(maybeAddTypesIfDuplicate(testInfos));
    }
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

  private static ImmutableList<TestInfo> maybeAddTypesIfDuplicate(List<TestInfo> testInfos) {
    Multimap<String, TestInfo> testNameToInfo =
        MultimapBuilder.linkedHashKeys().arrayListValues().build();
    for (TestInfo testInfo : testInfos) {
      testNameToInfo.put(testInfo.getName(), testInfo);
    }

    return testNameToInfo.keySet().stream()
        .flatMap(
            testName -> {
              Collection<TestInfo> matchedInfos = testNameToInfo.get(testName);
              if (matchedInfos.size() == 1) {
                // There was only one method with this name, so no deduplication is necessary
                return matchedInfos.stream();
              } else {
                // Found tests with duplicate test names
                int numParameters = matchedInfos.iterator().next().getParameters().size();
                Set<Integer> indicesThatShouldGetSuffix =
                    // Find parameter indices for which a suffix would allow the reader to
                    // differentiate
                    IntStream.range(0, numParameters)
                        .filter(
                            parameterIndex ->
                                matchedInfos.stream()
                                        .map(
                                            info ->
                                                getTypeSuffix(
                                                    info.getParameters()
                                                        .get(parameterIndex)
                                                        .getValue()))
                                        .distinct()
                                        .count()
                                    > 1)
                        .boxed()
                        .collect(toSet());

                return matchedInfos.stream()
                    .map(
                        testInfo ->
                            testInfo.withUpdatedParameterNames(
                                (parameter, parameterIndex) ->
                                    indicesThatShouldGetSuffix.contains(parameterIndex)
                                        ? parameter.getName() + getTypeSuffix(parameter.getValue())
                                        : parameter.getName()));
              }
            })
        .collect(toImmutableList());
  }

  private static String getTypeSuffix(@Nullable Object value) {
    if (value == null) {
      return " (null reference)";
    } else {
      return String.format(" (%s)", value.getClass().getSimpleName());
    }
  }

  private static ImmutableList<TestInfo> deduplicateWithNumberPrefixes(
      ImmutableList<TestInfo> testInfos) {
    long uniqueTestNameCount = testInfos.stream().map(TestInfo::getName).distinct().count();
    if (testInfos.size() == uniqueTestNameCount) {
      return ImmutableList.copyOf(testInfos);
    } else {
      // There are still duplicates, even after adding type suffixes. As a last resort: add a
      // counter to all parameters to guarantee that each case is unique.
      return testInfos.stream()
          .map(
              testInfo ->
                  testInfo.withUpdatedParameterNames(
                      (parameter, parameterIndex) ->
                          String.format(
                              "%s.%s", parameter.getIndexInValueSource() + 1, parameter.getName())))
          .collect(toImmutableList());
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

    TestInfoParameter withName(String newName) {
      return create(newName, getValue(), getIndexInValueSource());
    }

    static TestInfoParameter create(String name, @Nullable Object value, int indexInValueSource) {
      checkArgument(indexInValueSource >= 0);
      return new AutoValue_TestInfo_TestInfoParameter(
          checkNotNull(name), value, indexInValueSource);
    }
  }
}
