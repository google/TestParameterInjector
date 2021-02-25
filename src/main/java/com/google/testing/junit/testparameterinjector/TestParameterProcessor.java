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

/**
 * Interface which allows {@link TestParameterAnnotation} annotations to run arbitrary code before
 * and after test execution.
 *
 * <p>When multiple TestParameterAnnotation processors exist for a single test, they are executed in
 * declaration order, starting with annotations defined at the class, field, method, and finally
 * parameter level.
 */
interface TestParameterProcessor {
  /** Executes code in the context of a running test statement before the statement starts. */
  void before(Object testParameterValue);

  /** Executes code in the context of a running test statement after the statement completes. */
  void after(Object testParameterValue);
}
