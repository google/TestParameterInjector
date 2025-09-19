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

package com.google.testing.junit.testparameterinjector.junit5;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import javax.annotation.Nullable;

/**
 * Wrapper class around a parameter value. Use this to give a value a name that is different from
 * its {@code toString()} method.
 */
public class TestParameterValue {
  private final @Nullable Object wrappedValue;
  private final Optional<String> customName;

  private TestParameterValue(@Nullable Object wrappedValue, Optional<String> customName) {
    this.wrappedValue = wrappedValue;
    this.customName = checkNotNull(customName);
  }

  /** Wraps the given value. */
  public static TestParameterValue wrap(@Nullable Object wrappedValue) {
    return new TestParameterValue(wrappedValue, /* customName= */ Optional.absent());
  }

  /** Wraps the given value unless it is already of this type. */
  static TestParameterValue maybeWrap(@Nullable Object value) {
    return (value instanceof TestParameterValue)
        ? (TestParameterValue) value
        : TestParameterValue.wrap(value);
  }

  /** Applies maybeWrap() to the given elements. */
  static ImmutableList<TestParameterValue> maybeWrapList(Iterable<Object> values) {
    return FluentIterable.from(values).transform(TestParameterValue::maybeWrap).toList();
  }

  /**
   * Returns a new {@link TestParameterValue} instance that stores the given name. The
   * TestParameterInjector framework will use this name instead of {@code wrappedValue.toString()}
   * when generating the test name.
   */
  public TestParameterValue withName(String name) {
    return new TestParameterValue(wrappedValue, Optional.of(name));
  }

  @Nullable
  Object getWrappedValue() {
    return wrappedValue;
  }

  Optional<String> getCustomName() {
    return customName;
  }

  @Override
  public String toString() {
    return customName.isPresent()
        ? String.format("%s (name = %s)", wrappedValue, customName.get())
        : String.valueOf(wrappedValue);
  }
}
