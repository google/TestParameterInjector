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

import com.google.common.base.Optional;
import java.lang.annotation.Annotation;

/** Interface to retrieve the {@link TestParameterAnnotation} values for a test. */
/* copybara:strip_begin(advanced usage) */ public /* copybara:strip_end */
interface TestParameterValues {
  /**
   * Returns a {@link TestParameterAnnotation} value for the current test as specified by {@code
   * testInfo}, or {@link Optional#absent()} if the {@code annotationType} is not found.
   */
  Optional<Object> getValue(Class<? extends Annotation> annotationType);
}
