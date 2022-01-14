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
import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.Collection;

/**
 * Value class that captures the result of a validating a single constructor or test method.
 *
 * <p>If the validation is not validated by any processor, it will be validated using the default
 * validator. If a processor validates a constructor/test method, the remaining processors will
 * *not* be called.
 */
@AutoValue
abstract class ExecutableValidationResult {

  /** Returns true if the properties of the given constructor/test method were validated. */
  public abstract boolean wasValidated();

  /** Returns the validation errors, if any. */
  public abstract ImmutableList<Throwable> validationErrors();

  static ExecutableValidationResult notValidated() {
    return of(/* wasValidated= */ false, /* validationErrors= */ ImmutableList.of());
  }

  static ExecutableValidationResult validated(Collection<Throwable> errors) {
    return of(/* wasValidated= */ true, /* validationErrors= */ errors);
  }

  static ExecutableValidationResult validated(Throwable error) {
    return of(/* wasValidated= */ true, /* validationErrors= */ ImmutableList.of(error));
  }

  static ExecutableValidationResult valid() {
    return of(/* wasValidated= */ true, /* validationErrors= */ ImmutableList.of());
  }

  private static ExecutableValidationResult of(
      boolean wasValidated, Collection<Throwable> validationErrors) {
    checkArgument(wasValidated || validationErrors.isEmpty());
    return new AutoValue_ExecutableValidationResult(
        wasValidated, ImmutableList.copyOf(validationErrors));
  }

  void assertValid() {
    if (wasValidated() && !validationErrors().isEmpty()) {
      if (validationErrors().size() == 1) {
        throw new AssertionError(getOnlyElement(validationErrors()));
      } else {
        throw new AssertionError(String.format("Found validation errors: %s", validationErrors()));
      }
    }
  }
}
