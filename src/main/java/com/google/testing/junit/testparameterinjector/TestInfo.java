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

import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;
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

  public abstract String getName();

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

  private TestInfo withName(String otherName) {
    return TestInfo.create(getMethod(), otherName, getAnnotations());
  }

  public static TestInfo create(Method method, String name, List<Annotation> annotations) {
    return new AutoValue_TestInfo(method, name, ImmutableList.copyOf(annotations));
  }

  static ImmutableList<TestInfo> shortenNamesIfNecessary(
      List<TestInfo> testInfos, Function<TestInfo, String> shorterNameFunction) {
    if (testInfos.stream().anyMatch(i -> i.getName().length() > MAX_TEST_NAME_LENGTH)) {
      return ImmutableList.copyOf(
          testInfos.stream()
              .map(testInfo -> testInfo.withName(shorterNameFunction.apply(testInfo)))
              .collect(toList()));
    } else {
      return ImmutableList.copyOf(testInfos);
    }
  }
}
