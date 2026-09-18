/*
 * Copyright 2025 Google Inc.
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
 * A single {@link TestParameterValue}, paired with the index that it had in the list of values that
 * it was taken from.
 *
 * <p>Note that this class is written in Java (as opposed to Kotlin, where it is used most) because
 * it is referenced by the signature of a Java method, which javadoc can only resolve if it is
 * declared in a Java source file.
 */
final class IndexedTestParameterValue {

  private final TestParameterValue value;
  private final int indexInValueList;

  IndexedTestParameterValue(TestParameterValue value, int indexInValueList) {
    this.value = value;
    this.indexInValueList = indexInValueList;
  }

  TestParameterValue getValue() {
    return value;
  }

  int getIndexInValueList() {
    return indexInValueList;
  }
}
