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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.TypeToken;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.ByteString;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** A helper class for parsing parameter values from strings. */
final class ParameterValueParsing {

  @SuppressWarnings("unchecked")
  static <E extends Enum<E>> Enum<?> parseEnum(String str, Class<?> enumType) {
    return Enum.valueOf((Class<E>) enumType, str);
  }

  static boolean isValidYamlString(String yamlString) {
    try {
      new Yaml(new SafeConstructor()).load(yamlString);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  static Object parseYamlStringToJavaType(String yamlString, Class<?> javaType) {
    return parseYamlObjectToJavaType(parseYamlStringToObject(yamlString), TypeToken.of(javaType));
  }

  static Object parseYamlStringToObject(String yamlString) {
    return new Yaml(new SafeConstructor()).load(yamlString);
  }

  @SuppressWarnings({"unchecked"})
  static Object parseYamlObjectToJavaType(Object parsedYaml, TypeToken<?> javaType) {
    // Pass along null so we don't have to worry about it below
    if (parsedYaml == null) {
      return null;
    }

    YamlValueTransformer yamlValueTransformer =
        new YamlValueTransformer(parsedYaml, javaType.getRawType());

    yamlValueTransformer
        .ifJavaType(String.class)
        .supportParsedType(String.class, self -> self)
        // Also support other primitives because it's easy to accidentally write e.g. a number when
        // a string was intended in YAML
        .supportParsedType(Boolean.class, Object::toString)
        .supportParsedType(Integer.class, Object::toString)
        .supportParsedType(Long.class, Object::toString)
        .supportParsedType(Double.class, Object::toString);

    yamlValueTransformer.ifJavaType(Boolean.class).supportParsedType(Boolean.class, self -> self);

    yamlValueTransformer.ifJavaType(Integer.class).supportParsedType(Integer.class, self -> self);

    yamlValueTransformer
        .ifJavaType(Long.class)
        .supportParsedType(Long.class, self -> self)
        .supportParsedType(Integer.class, Integer::longValue);

    yamlValueTransformer
        .ifJavaType(Float.class)
        .supportParsedType(Float.class, self -> self)
        .supportParsedType(Double.class, Double::floatValue)
        .supportParsedType(Integer.class, Integer::floatValue)
        .supportParsedType(String.class, Float::valueOf);

    yamlValueTransformer
        .ifJavaType(Double.class)
        .supportParsedType(Double.class, self -> self)
        .supportParsedType(Integer.class, Integer::doubleValue)
        .supportParsedType(Long.class, Long::doubleValue)
        .supportParsedType(String.class, Double::valueOf);

    yamlValueTransformer
        .ifJavaType(Enum.class)
        .supportParsedType(
            String.class, str -> ParameterValueParsing.parseEnum(str, javaType.getRawType()));

    yamlValueTransformer
        .ifJavaType(byte[].class)
        .supportParsedType(byte[].class, self -> self)
        // Uses String based charset because StandardCharsets was not introduced until later
        // versions of Android
        // See https://developer.android.com/reference/java/nio/charset/StandardCharsets.
        .supportParsedType(String.class, s -> s.getBytes(Charset.forName("UTF-8")));

    yamlValueTransformer
        .ifJavaType(ByteString.class)
        .supportParsedType(String.class, ByteString::copyFromUtf8)
        .supportParsedType(byte[].class, ByteString::copyFrom);

    // Added mainly for protocol buffer parsing
    yamlValueTransformer
        .ifJavaType(List.class)
        .supportParsedType(
            List.class,
            list ->
                Lists.transform(
                    list,
                    e ->
                        parseYamlObjectToJavaType(
                            e, getGenericParameterType(javaType, /* parameterIndex= */ 0))));
    yamlValueTransformer
        .ifJavaType(Map.class)
        .supportParsedType(Map.class, map -> parseYamlMapToJavaMap(map, javaType));

    return yamlValueTransformer.transformedJavaValue();
  }

  private static Map<?, ?> parseYamlMapToJavaMap(Map<?, ?> map, TypeToken<?> javaType) {
    Map<Object, Object> returnedMap = new LinkedHashMap<>();
    for (Entry<?, ?> entry : map.entrySet()) {
      returnedMap.put(
          parseYamlObjectToJavaType(
              entry.getKey(), getGenericParameterType(javaType, /* parameterIndex= */ 0)),
          parseYamlObjectToJavaType(
              entry.getValue(), getGenericParameterType(javaType, /* parameterIndex= */ 1)));
    }
    return returnedMap;
  }

  private static TypeToken<?> getGenericParameterType(TypeToken<?> typeToken, int parameterIndex) {
    checkArgument(
        typeToken.getType() instanceof ParameterizedType,
        "Could not parse the generic parameter of type %s",
        typeToken);

    ParameterizedType parameterizedType = (ParameterizedType) typeToken.getType();
    return TypeToken.of(parameterizedType.getActualTypeArguments()[parameterIndex]);
  }

  private static final class YamlValueTransformer {
    private final Object parsedYaml;
    private final Class<?> javaType;
    @Nullable private Object transformedJavaValue;

    YamlValueTransformer(Object parsedYaml, Class<?> javaType) {
      this.parsedYaml = parsedYaml;
      this.javaType = javaType;
    }

    <JavaT> SupportedJavaType<JavaT> ifJavaType(Class<JavaT> supportedJavaType) {
      return new SupportedJavaType<>(supportedJavaType);
    }

    Object transformedJavaValue() {
      checkArgument(
          transformedJavaValue != null,
          "Could not map YAML value %s (class = %s) to java class %s",
          parsedYaml,
          parsedYaml.getClass(),
          javaType);
      return transformedJavaValue;
    }

    final class SupportedJavaType<JavaT> {

      private final Class<JavaT> supportedJavaType;

      private SupportedJavaType(Class<JavaT> supportedJavaType) {
        this.supportedJavaType = supportedJavaType;
      }

      @SuppressWarnings("unchecked")
      @CanIgnoreReturnValue
      <ParsedYamlT> SupportedJavaType<JavaT> supportParsedType(
          Class<ParsedYamlT> parsedYamlType, Function<ParsedYamlT, JavaT> transformation) {
        if (Primitives.wrap(supportedJavaType).isAssignableFrom(Primitives.wrap(javaType))) {
          if (Primitives.wrap(parsedYamlType).isInstance(parsedYaml)) {
            checkState(
                transformedJavaValue == null,
                "This case is already handled. This is a bug in"
                    + " testparameterinjector.TestParametersMethodProcessor.");
            try {
              transformedJavaValue = checkNotNull(transformation.apply((ParsedYamlT) parsedYaml));
            } catch (Exception e) {
              throw new IllegalArgumentException(
                  String.format(
                      "Could not map YAML value %s (class = %s) to java class %s",
                      parsedYaml, parsedYaml.getClass(), javaType),
                  e);
            }
          }
        }

        return this;
      }
    }
  }

  private ParameterValueParsing() {}
}
