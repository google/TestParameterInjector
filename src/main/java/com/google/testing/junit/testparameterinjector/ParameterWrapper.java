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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import javax.annotation.Nullable;

abstract class ParameterWrapper {
  @SuppressWarnings("AndroidJdkLibsChecker") // j.l.r.Parameter is not available on old Android SDKs.
  static ParameterWrapper[] get(Constructor<?> constructor) {
    try {
      return Java8.create(constructor.getParameters());
    } catch (NoSuchMethodError ignored) {
      return Legacy.create(constructor);
    }
  }

  @SuppressWarnings("AndroidJdkLibsChecker") // j.l.r.Parameter is not available on old Android SDKs.
  static ParameterWrapper[] get(Method method) {
    try {
      return Java8.create(method.getParameters());
    } catch (NoSuchMethodError ignored) {
      return Legacy.create(method);
    }
  }

  abstract <T extends Annotation> @Nullable T getAnnotation(Class<T> annotationType);

  abstract Class<?> getType();

  abstract String getName();

  @SuppressWarnings("AndroidJdkLibsChecker") // j.l.r.Parameter is not available on old Android SDKs.
  private static final class Java8 extends ParameterWrapper {
    static ParameterWrapper[] create(Parameter[] parameters) {
      ParameterWrapper[] array = new ParameterWrapper[parameters.length];
      for (int i = 0; i < parameters.length; i++) {
        array[i] = new Java8(parameters[i]);
      }
      return array;
    }

    private final Parameter parameter;

    private Java8(Parameter parameter) {
      this.parameter = parameter;
    }

    @Override
    <T extends Annotation> @Nullable T getAnnotation(Class<T> annotationType) {
      return parameter.getAnnotation(annotationType);
    }

    @Override
    Class<?> getType() {
      return parameter.getType();
    }

    @Override
    String getName() {
      return parameter.getName();
    }
  }

  private static final class Legacy extends ParameterWrapper {
    static ParameterWrapper[] create(Constructor<?> constructor) {
      return create(constructor.getParameterAnnotations(), constructor.getParameterTypes());
    }

    static ParameterWrapper[] create(Method method) {
      return create(method.getParameterAnnotations(), method.getParameterTypes());
    }

    private static ParameterWrapper[] create(Annotation[][] annotations, Class<?>[] types) {
      assert annotations.length == types.length;
      ParameterWrapper[] array = new ParameterWrapper[annotations.length];
      for (int i = 0; i < annotations.length; i++) {
        // Per j.l.r.Parameter.getName(), "argN" is the synthetic name format used when a class
        //  file does not actually contain parameter name information.
        array[i] = new Legacy(annotations[i], types[i], "arg" + i);
      }
      return array;
    }

    private final Annotation[] annotations;
    private final Class<?> type;
    private final String name;

    private Legacy(Annotation[] annotations, Class<?> type, String name) {
      this.annotations = annotations;
      this.type = type;
      this.name = name;
    }

    @Override
    <T extends Annotation> @Nullable T getAnnotation(Class<T> annotationType) {
      for (Annotation annotation : annotations) {
        if (annotation.annotationType().equals(annotationType)) {
          return annotationType.cast(annotation);
        }
      }
      return null;
    }

    @Override
    Class<?> getType() {
      return type;
    }

    @Override
    String getName() {
      return name;
    }
  }
}
