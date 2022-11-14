/*
 * Copyright 2022 Google Inc.
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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import java.lang.reflect.InvocationTargetException;

/**
 * Utility methods to interact with com.google.protobuf.ByteString via reflection.
 *
 * <p>This is a hack to avoid the open source project to depend on protobuf-lite/javalite, which is
 * causing conflicts for users (see https://github.com/google/TestParameterInjector/issues/24).
 */
final class ByteStringReflection {

  static final Optional<Class<?>> MAYBE_BYTE_STRING_CLASS = maybeGetByteStringClass();

  /** Equivalent of {@code object instanceof ByteString} */
  static boolean isInstanceOfByteString(Object object) {
    if (MAYBE_BYTE_STRING_CLASS.isPresent()) {
      return MAYBE_BYTE_STRING_CLASS.get().isInstance(object);
    } else {
      return false;
    }
  }

  /** Eqvuivalent of {@code ((ByteString) byteString).toByteArray()} */
  static byte[] byteStringToByteArray(Object byteString) {
    return (byte[])
        invokeByteStringMethod("toByteArray", /* obj= */ byteString, /* args= */ ImmutableMap.of());
  }

  /**
   * Eqvuivalent of {@code ByteString.copyFromUtf8(text)}.
   *
   * <p>Encodes {@code text} into a sequence of UTF-8 bytes and returns the result as a {@code
   * ByteString}.
   */
  static Object copyFromUtf8(String text) {
    return invokeByteStringMethod(
        "copyFromUtf8", /* obj= */ null, /* args= */ ImmutableMap.of(String.class, text));
  }

  /**
   * Eqvuivalent of {@code ByteString.copyFrom(bytes)}.
   *
   * <p>Copies the given bytes into a {@code ByteString}.
   */
  static Object copyFrom(byte[] bytes) {
    return invokeByteStringMethod(
        "copyFrom", /* obj= */ null, /* args= */ ImmutableMap.of(byte[].class, bytes));
  }

  @SuppressWarnings("UseMultiCatch")
  private static Object invokeByteStringMethod(
      String methodName, Object obj, ImmutableMap<Class<?>, ?> args) {
    try {
      return MAYBE_BYTE_STRING_CLASS
          .get()
          .getMethod(methodName, args.keySet().toArray(new Class<?>[0]))
          .invoke(obj, args.values().toArray());
      /*
       * Do not merge the 3 catch blocks below. javac would infer a type of
       * ReflectiveOperationException, which Animal Sniffer would reject. (Old versions of
       * Android don't *seem* to mind, but there might be edge cases of which we're unaware.)
       */
    } catch (IllegalAccessException e) {
      throw new LinkageError(String.format("Accessing %s()", methodName), e);
    } catch (InvocationTargetException e) {
      throw new LinkageError(String.format("Calling %s()", methodName), e);
    } catch (NoSuchMethodException e) {
      throw new LinkageError(String.format("Calling %s()", methodName), e);
    }
  }

  private static Optional<Class<?>> maybeGetByteStringClass() {
    try {
      return Optional.of(Class.forName("com.google.protobuf.ByteString"));
    } catch (ClassNotFoundException | LinkageError unused) {
      return Optional.absent();
    }
  }

  private ByteStringReflection() {} // Inhibit instantiation
}
