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

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Range;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;
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

  /**
   * The test class that is being run.
   *
   * <p>Note that this is not always the same as the class that declares {@link #getMethod()}
   * because test methods can be inherited.
   */
  public abstract Class<?> getTestClass();

  public final String getName() {
    if (getParameters().isEmpty()) {
      return getRealMethodName();
    } else {
      return String.format(
          "%s[%s]",
          getRealMethodName(),
          FluentIterable.from(getParameters())
              .transform(TestInfoParameter::getValueInTestName)
              .join(Joiner.on(",")));
    }
  }

  abstract ImmutableList<TestInfoParameter> getParameters();

  public abstract ImmutableList<Annotation> getAnnotations();

  @Nullable
  public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    for (Annotation annotation : getAnnotations()) {
      if (annotationClass.isInstance(annotation)) {
        return annotationClass.cast(annotation);
      }
    }
    return null;
  }

  final TestInfo withExtraParameters(List<TestInfoParameter> parameters) {
    return new AutoValue_TestInfo(
        getMethod(),
        getTestClass(),
        ImmutableList.<TestInfoParameter>builder()
            .addAll(this.getParameters())
            .addAll(parameters)
            .build(),
        getAnnotations());
  }

  final TestInfo withExtraAnnotation(Annotation annotation) {
    ImmutableList<Annotation> newAnnotations =
        ImmutableList.<Annotation>builder().addAll(this.getAnnotations()).add(annotation).build();
    return new AutoValue_TestInfo(getMethod(), getTestClass(), getParameters(), newAnnotations);
  }

  /**
   * Returns a new TestInfo instance with updated parameter names.
   *
   * @param parameterWithIndexToNewName A function of the parameter and its index in the {@link
   *     #getParameters()} list to the new name.
   */
  private TestInfo withUpdatedParameterNames(
      Java8BiFunction<TestInfoParameter, Integer, String> parameterWithIndexToNewName) {
    return new AutoValue_TestInfo(
        getMethod(),
        getTestClass(),
        FluentIterable.from(
                ContiguousSet.create(
                    Range.closedOpen(0, getParameters().size()), DiscreteDomain.integers()))
            .transform(
                parameterIndex -> {
                  TestInfoParameter parameter = getParameters().get(parameterIndex);
                  return parameter.withValueInTestName(
                      parameterWithIndexToNewName.apply(parameter, parameterIndex));
                })
            .toList(),
        getAnnotations());
  }

  private String getRealMethodName() {
    String candidate = getMethod().getName();
    if (candidate.contains("-")) {
      // Kotlin hack:
      // Method names can normally not contain the '-' character. However, when a Kotlin method gets
      // a @JvmInline value class as parameter, a method with a hash suffix will show up in the
      // TestParameterInjector's reflection results. These are of the form realMethodName-fiSAjMM().
      // The code below strips off this suffix.
      return Splitter.on('-').omitEmptyStrings().split(candidate).iterator().next();
    } else {
      return candidate;
    }
  }

  public static TestInfo legacyCreate(
      Method method, Class<?> testClass, String name, List<Annotation> annotations) {
    return new AutoValue_TestInfo(
        method, testClass, /* parameters= */ ImmutableList.of(), ImmutableList.copyOf(annotations));
  }

  static TestInfo createWithoutParameters(
      Method method, Class<?> testClass, List<Annotation> annotations) {
    return new AutoValue_TestInfo(
        method, testClass, /* parameters= */ ImmutableList.of(), ImmutableList.copyOf(annotations));
  }

  static ImmutableList<TestInfo> shortenNamesIfNecessary(List<TestInfo> testInfos) {
    if (FluentIterable.from(testInfos)
        .anyMatch(info -> info.getName().length() > MAX_TEST_NAME_LENGTH)) {
      int numberOfParameters = testInfos.get(0).getParameters().size();

      if (numberOfParameters == 0) {
        return ImmutableList.copyOf(testInfos);
      } else {
        Set<Integer> parameterIndicesThatNeedUpdate =
            FluentIterable.from(
                    ContiguousSet.create(
                        Range.closedOpen(0, numberOfParameters), DiscreteDomain.integers()))
                .filter(
                    parameterIndex ->
                        FluentIterable.from(testInfos)
                            .anyMatch(
                                info ->
                                    info.getParameters()
                                            .get(parameterIndex)
                                            .getValueInTestName()
                                            .length()
                                        > getMaxCharactersPerParameter(info, numberOfParameters)))
                .toSet();

        return FluentIterable.from(testInfos)
            .transform(
                info ->
                    info.withUpdatedParameterNames(
                        (parameter, parameterIndex) ->
                            parameterIndicesThatNeedUpdate.contains(parameterIndex)
                                ? getShortenedName(
                                    parameter,
                                    getMaxCharactersPerParameter(info, numberOfParameters))
                                : info.getParameters().get(parameterIndex).getValueInTestName()))
            .toList();
      }
    } else {
      return ImmutableList.copyOf(testInfos);
    }
  }

  private static int getMaxCharactersPerParameter(TestInfo testInfo, int numberOfParameters) {
    int maxLengthOfAllParameters =
        // Subtract 2 characters for square brackets
        MAX_TEST_NAME_LENGTH - testInfo.getRealMethodName().length() - 2;

    // Subtract 4 characters to leave place for joining commas and the parameter index.
    return maxLengthOfAllParameters / numberOfParameters - 4;
  }

  static ImmutableList<TestInfo> deduplicateTestNames(List<TestInfo> testInfos) {
    long uniqueTestNameCount =
        FluentIterable.from(testInfos).transform(TestInfo::getName).toSet().size();
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
          parameter.getValueInTestName().length() > maxCharactersPerParameter
              ? parameter.getValueInTestName().substring(0, maxCharactersPerParameter - 3) + "..."
              : parameter.getValueInTestName();
      return String.format("%s.%s", parameter.getIndexInValueSource() + 1, shortenedName);
    }
  }

  private static ImmutableList<TestInfo> maybeAddTypesIfDuplicate(List<TestInfo> testInfos) {
    Multimap<String, TestInfo> testNameToInfo =
        MultimapBuilder.linkedHashKeys().arrayListValues().build();
    for (TestInfo testInfo : testInfos) {
      testNameToInfo.put(testInfo.getName(), testInfo);
    }

    return FluentIterable.from(testNameToInfo.keySet())
        .transformAndConcat(
            testName -> {
              Collection<TestInfo> matchedInfos = testNameToInfo.get(testName);
              if (matchedInfos.size() == 1) {
                // There was only one method with this name, so no deduplication is necessary
                return matchedInfos;
              } else {
                // Found tests with duplicate test names
                int numParameters = matchedInfos.iterator().next().getParameters().size();
                Set<Integer> indicesThatShouldGetSuffix =
                    // Find parameter indices for which a suffix would allow the reader to
                    // differentiate
                    FluentIterable.from(
                            ContiguousSet.create(
                                Range.closedOpen(0, numParameters), DiscreteDomain.integers()))
                        .filter(
                            parameterIndex ->
                                FluentIterable.from(matchedInfos)
                                        .transform(
                                            info ->
                                                getTypeSuffix(
                                                    info.getParameters()
                                                        .get(parameterIndex)
                                                        .getValue()))
                                        .toSet()
                                        .size()
                                    > 1)
                        .toSet();

                return FluentIterable.from(matchedInfos)
                    .transform(
                        testInfo ->
                            testInfo.withUpdatedParameterNames(
                                (parameter, parameterIndex) ->
                                    indicesThatShouldGetSuffix.contains(parameterIndex)
                                        ? parameter.getValueInTestName()
                                            + getTypeSuffix(parameter.getValue())
                                        : parameter.getValueInTestName()));
              }
            })
        .toList();
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
    long uniqueTestNameCount =
        FluentIterable.from(testInfos).transform(TestInfo::getName).toSet().size();
    if (testInfos.size() == uniqueTestNameCount) {
      return ImmutableList.copyOf(testInfos);
    } else {
      // There are still duplicates, even after adding type suffixes. As a last resort: add a
      // counter to all parameters to guarantee that each case is unique.
      return FluentIterable.from(testInfos)
          .transform(
              testInfo ->
                  testInfo.withUpdatedParameterNames(
                      (parameter, parameterIndex) ->
                          String.format(
                              "%s.%s",
                              parameter.getIndexInValueSource() + 1,
                              parameter.getValueInTestName())))
          .toList();
    }
  }

  @AutoValue
  abstract static class TestInfoParameter {

    abstract String getValueInTestName();

    @Nullable
    abstract Object getValue();

    /**
     * The index of this parameter value in the list of all values provided by the provider that
     * returned this value.
     */
    abstract int getIndexInValueSource();

    final TestInfoParameter withValueInTestName(String newValueInTestName) {
      return create(newValueInTestName, getValue(), getIndexInValueSource());
    }

    static TestInfoParameter create(
        String valueInTestName, @Nullable Object value, int indexInValueSource) {
      checkArgument(indexInValueSource >= 0);
      return new AutoValue_TestInfo_TestInfoParameter(
          checkNotNull(valueInTestName), value, indexInValueSource);
    }
  }

  /** Copy of Java8's java.util.BiFunction which is not available in older versions of the JDK */
  interface Java8BiFunction<I, J, K> {
    K apply(I a, J b);
  }
}
