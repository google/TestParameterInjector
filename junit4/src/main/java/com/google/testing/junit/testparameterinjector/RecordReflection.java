/*
 * Copyright 2026 Google Inc.
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

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Utility methods to interact with Java records via reflection.
 *
 * <p>Records were introduced in Java 16, but this library also supports older Java versions (and
 * Android versions). This is why the record-specific reflection API (e.g. {@code Class.isRecord()})
 * is itself called via reflection.
 */
final class RecordReflection {

  private static final Optional<Method> MAYBE_IS_RECORD_METHOD = maybeGetClassMethod("isRecord");
  private static final Optional<Method> MAYBE_GET_RECORD_COMPONENTS_METHOD =
      maybeGetClassMethod("getRecordComponents");

  /**
   * Equivalent of {@code clazz.isRecord()}, except that this returns false if the runtime doesn't
   * support records.
   */
  static boolean isRecord(Class<?> clazz) {
    if (!MAYBE_IS_RECORD_METHOD.isPresent() || !MAYBE_GET_RECORD_COMPONENTS_METHOD.isPresent()) {
      return false;
    }
    return (Boolean) invokeMethod(MAYBE_IS_RECORD_METHOD.get(), /* obj= */ clazz);
  }

  /** Equivalent of {@code recordClass.getRecordComponents()}. */
  static ImmutableList<RecordComponent> getRecordComponents(Class<?> recordClass) {
    checkArgument(isRecord(recordClass), "%s is not a record", recordClass);

    Object[] components =
        (Object[]) invokeMethod(MAYBE_GET_RECORD_COMPONENTS_METHOD.get(), /* obj= */ recordClass);
    ImmutableList.Builder<RecordComponent> result = ImmutableList.builder();
    for (Object component : components) {
      result.add(
          RecordComponent.create(
              (String) invokeNoArgMethod(component, "getName"),
              (Class<?>) invokeNoArgMethod(component, "getType"),
              (Type) invokeNoArgMethod(component, "getGenericType")));
    }
    return result.build();
  }

  /**
   * Creates a new instance of the given record class by calling its canonical constructor.
   *
   * @param componentValues the values of all record components, in declaration order
   * @throws IllegalArgumentException if the canonical constructor threw an exception (e.g. because
   *     it validates its arguments)
   */
  static Object newInstance(Class<?> recordClass, Object... componentValues) {
    ImmutableList<RecordComponent> components = getRecordComponents(recordClass);
    Class<?>[] componentTypes = new Class<?>[components.size()];
    for (int i = 0; i < components.size(); i++) {
      componentTypes[i] = components.get(i).getType();
    }

    Constructor<?> canonicalConstructor;
    try {
      canonicalConstructor = recordClass.getDeclaredConstructor(componentTypes);
    } catch (NoSuchMethodException e) {
      // This should never happen because every record has a canonical constructor
      throw new LinkageError(
          String.format("Could not find the canonical constructor of %s", recordClass), e);
    }
    // The canonical constructor has the same visibility as the record, which may be private
    canonicalConstructor.setAccessible(true);

    try {
      return canonicalConstructor.newInstance(componentValues);
      /*
       * Do not merge the catch blocks below. javac would infer a type of
       * ReflectiveOperationException, which Animal Sniffer would reject. See ByteStringReflection.
       */
    } catch (InvocationTargetException e) {
      throw new IllegalArgumentException(
          String.format(
              "The canonical constructor of record %s threw an exception for the component values"
                  + " %s",
              recordClass.getSimpleName(), Arrays.toString(componentValues)),
          e.getCause());
    } catch (InstantiationException e) {
      throw new LinkageError(String.format("Instantiating %s", recordClass), e);
    } catch (IllegalAccessException e) {
      throw new LinkageError(
          String.format("Accessing the canonical constructor of %s", recordClass), e);
    }
  }

  /**
   * Equivalent of {@code java.lang.reflect.RecordComponent}, limited to what this library needs.
   */
  @AutoValue
  abstract static class RecordComponent {

    /** Equivalent of {@code RecordComponent.getName()}. */
    abstract String getName();

    /** Equivalent of {@code RecordComponent.getType()}. */
    abstract Class<?> getType();

    /** Equivalent of {@code RecordComponent.getGenericType()}. */
    abstract Type getGenericType();

    private static RecordComponent create(String name, Class<?> type, Type genericType) {
      return new AutoValue_RecordReflection_RecordComponent(name, type, genericType);
    }
  }

  private static Optional<Method> maybeGetClassMethod(String methodName) {
    try {
      return Optional.of(Class.class.getMethod(methodName));
    } catch (NoSuchMethodException unused) {
      // This runtime doesn't support records
      return Optional.absent();
    }
  }

  private static Object invokeNoArgMethod(Object obj, String methodName) {
    try {
      return invokeMethod(obj.getClass().getMethod(methodName), obj);
    } catch (NoSuchMethodException e) {
      throw new LinkageError(String.format("Calling %s()", methodName), e);
    }
  }

  private static Object invokeMethod(Method method, Object obj) {
    try {
      return method.invoke(obj);
      /*
       * Do not merge the catch blocks below. javac would infer a type of
       * ReflectiveOperationException, which Animal Sniffer would reject. See ByteStringReflection.
       */
    } catch (IllegalAccessException e) {
      throw new LinkageError(String.format("Accessing %s()", method.getName()), e);
    } catch (InvocationTargetException e) {
      throw new LinkageError(String.format("Calling %s()", method.getName()), e);
    }
  }

  private RecordReflection() {} // Inhibit instantiation
}
