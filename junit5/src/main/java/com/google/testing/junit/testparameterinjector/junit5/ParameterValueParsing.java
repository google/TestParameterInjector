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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.primitives.Primitives;
import com.google.common.primitives.UnsignedLong;
import com.google.common.reflect.TypeToken;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** A helper class for parsing parameter values from strings. */
final class ParameterValueParsing {

  @SuppressWarnings("unchecked")
  static <E extends Enum<E>> Enum<?> parseEnum(String str, Class<?> enumType) {
    try {
      return Enum.valueOf((Class<E>) enumType, str);
    } catch (IllegalArgumentException e) {
      // The given name was not a valid enum value. However, the enum might have an alias to one of
      // its values defined as static field. This happens for example (via code generation) in the
      // case of Protocol Buffer aliases (see the allow_alias option).
      Optional<Enum<?>> enumValue = maybeGetStaticConstant(enumType, str);
      if (enumValue.isPresent()) {
        return enumValue.get();
      } else {
        throw e;
      }
    }
  }

  private static Optional<Enum<?>> maybeGetStaticConstant(Class<?> enumType, String fieldName) {
    verify(enumType.isEnum(), "Given type %s is not a enum.", enumType.getSimpleName());
    try {
      Field field = enumType.getField(fieldName);
      Object valueCandidate = field.get(null);
      checkArgument(
          enumType.isInstance(valueCandidate),
          "The field %s.%s exists, but is not of expected type %s.",
          enumType.getSimpleName(),
          fieldName,
          enumType.getSimpleName());
      return Optional.of((Enum<?>) valueCandidate);
    } catch (SecurityException | ReflectiveOperationException e) {
      return Optional.absent();
    }
  }

  static boolean isValidYamlString(String yamlString) {
    try {
      new Yaml(new SafeConstructor(new LoaderOptions())).load(yamlString);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  static Object parseYamlStringToJavaType(String yamlString, Class<?> javaType) {
    return parseYamlObjectToJavaType(parseYamlStringToObject(yamlString), TypeToken.of(javaType));
  }

  static Object parseYamlStringToObject(String yamlString) {
    return new Yaml(new SafeConstructor(new LoaderOptions())).load(yamlString);
  }

  private static UnsignedLong parseYamlSignedLongToUnsignedLong(long number) {
    checkState(number >= 0, "%s should be greater than or equal to zero", number);
    return UnsignedLong.fromLongBits(number);
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
        .ifJavaType(UnsignedLong.class)
        .supportParsedType(Long.class, self -> parseYamlSignedLongToUnsignedLong(self.longValue()))
        .supportParsedType(
            Integer.class, self -> parseYamlSignedLongToUnsignedLong(self.longValue()))
        // UnsignedLong::valueOf(BigInteger) will validate that BigInteger is in the valid range and
        // throws otherwise.
        .supportParsedType(BigInteger.class, UnsignedLong::valueOf);

    yamlValueTransformer
        .ifJavaType(BigInteger.class)
        .supportParsedType(Long.class, self -> BigInteger.valueOf(self.longValue()))
        .supportParsedType(Integer.class, self -> BigInteger.valueOf(self.longValue()))
        .supportParsedType(BigInteger.class, self -> self);

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
            Boolean.class,
            bool ->
                ParameterValueParsing.parseEnumIfUnambiguousYamlBoolean(
                    bool, javaType.getRawType()))
        .supportParsedType(
            String.class, str -> ParameterValueParsing.parseEnum(str, javaType.getRawType()));

    yamlValueTransformer
        .ifJavaType(byte[].class)
        .supportParsedType(byte[].class, self -> self)
        // Uses String based charset because StandardCharsets was not introduced until later
        // versions of Android
        // See https://developer.android.com/reference/java/nio/charset/StandardCharsets.
        .supportParsedType(String.class, s -> s.getBytes(Charset.forName("UTF-8")));

    if (ByteStringReflection.MAYBE_BYTE_STRING_CLASS.isPresent()) {
      yamlValueTransformer
          .ifJavaType((Class<Object>) ByteStringReflection.MAYBE_BYTE_STRING_CLASS.get())
          .supportParsedType(String.class, ByteStringReflection::copyFromUtf8)
          .supportParsedType(byte[].class, ByteStringReflection::copyFrom);
    }

    yamlValueTransformer
        .ifJavaType(Duration.class)
        .supportParsedType(String.class, ParameterValueParsing::parseDuration)
        // Support the special case where the YAML string is "0"
        .supportParsedType(Integer.class, i -> parseDuration(String.valueOf(i)));

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

  private static Enum<?> parseEnumIfUnambiguousYamlBoolean(boolean yamlValue, Class<?> enumType) {
    Set<String> negativeYamlStrings =
        ImmutableSet.of("false", "False", "FALSE", "n", "N", "no", "No", "NO", "off", "Off", "OFF");
    Set<String> positiveYamlStrings =
        ImmutableSet.of("on", "On", "ON", "true", "True", "TRUE", "y", "Y", "yes", "Yes", "YES");

    // This is the list of YAML strings that a user could have used to define this boolean. Since
    // the user probably didn't intend a boolean but an enum (since we're expecting an enum), one of
    // these strings may (unambiguously) match one of the enum values.
    Set<String> yamlStringCandidates = yamlValue ? positiveYamlStrings : negativeYamlStrings;

    Set<Enum<?>> matches = new HashSet<>();
    for (Object enumValueObject : enumType.getEnumConstants()) {
      Enum<?> enumValue = (Enum<?>) enumValueObject;
      if (yamlStringCandidates.contains(enumValue.name())) {
        matches.add(enumValue);
      }
    }

    checkArgument(
        !matches.isEmpty(),
        "Cannot cast a boolean (%s) to an enum of type %s.",
        yamlValue,
        enumType.getSimpleName());
    checkArgument(
        matches.size() == 1,
        "Cannot cast a boolean (%s) to an enum of type %s. It is likely that the YAML parser is"
            + " 'wrongly' parsing one of these values as boolean: %s. You can solve this by putting"
            + " quotes around the YAML value, forcing the YAML parser to parse a String, which can"
            + " then be converted to the enum.",
        yamlValue,
        enumType.getSimpleName(),
        matches);
    return getOnlyElement(matches);
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

    <JavaT> SupportedJavaType<JavaT> ifJavaType(Predicate<Class<?>> supportedJavaType) {
      return new SupportedJavaType<>(supportedJavaType);
    }

    <JavaT> SupportedJavaType<JavaT> ifJavaType(Class<JavaT> supportedJavaType) {
      return new SupportedJavaType<>(Primitives.wrap(supportedJavaType)::isAssignableFrom);
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

      private final Predicate<Class<?>> supportedJavaType;

      private SupportedJavaType(Predicate<Class<?>> supportedJavaType) {
        this.supportedJavaType = supportedJavaType;
      }

      @SuppressWarnings("unchecked")
      @CanIgnoreReturnValue
      <ParsedYamlT> SupportedJavaType<JavaT> supportParsedType(
          Class<ParsedYamlT> parsedYamlType, Function<ParsedYamlT, JavaT> transformation) {
        if (supportedJavaType.test(Primitives.wrap(javaType))) {
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

  static String formatTestNameString(Optional<String> parameterName, @Nullable Object value) {
    Object unwrappedValue;
    Optional<String> customName;

    if (value instanceof TestParameterValue) {
      TestParameterValue tpValue = (TestParameterValue) value;
      unwrappedValue = tpValue.getWrappedValue();
      customName = tpValue.getCustomName();
    } else {
      unwrappedValue = value;
      customName = Optional.absent();
    }

    String result = customName.or(() -> valueAsString(unwrappedValue));
    if (parameterName.isPresent() && !customName.isPresent()) {
      if (unwrappedValue == null
          ||
          // Primitives are often ambiguous
          Primitives.unwrap(unwrappedValue.getClass()).isPrimitive()
          // Ambiguous String cases
          || unwrappedValue.equals("null")
          || (unwrappedValue instanceof CharSequence
              && CharMatcher.anyOf("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
                  .matchesNoneOf((CharSequence) unwrappedValue))) {
        // Prefix the parameter value with its field name. This is to avoid test names
        // such as myMethod_success[true,false,2]. Instead, it'll be
        // myMethod_success[dryRun=true,experimentFlag=false,retries=2].
        result = String.format("%s=%s", parameterName.get(), valueAsString(unwrappedValue));
      }
    }
    return result.trim().replaceAll("\\s+", " ");
  }

  private static String valueAsString(Object value) {
    if (value != null && value.getClass().isArray()) {
      StringBuilder resultBuider = new StringBuilder();
      resultBuider.append("[");
      for (int i = 0; i < Array.getLength(value); i++) {
        if (i > 0) {
          resultBuider.append(", ");
        }
        resultBuider.append(Array.get(value, i));
      }
      resultBuider.append("]");
      return resultBuider.toString();
    } else if (ByteStringReflection.isInstanceOfByteString(value)) {
      return Arrays.toString(ByteStringReflection.byteStringToByteArray(value));
    } else if (value instanceof Enum<?>) {
      // Sometimes, enums have custom toString() methods. They are probably adding extra information
      // (such as with protobuf enums on Android), but for a test name, the string should be as
      // short as possible
      return ((Enum<?>) value).name();
    } else {
      return String.valueOf(value);
    }
  }

  // ********** Duration parsing ********** //

  private static final ImmutableMap<String, Duration> ABBREVIATION_TO_DURATION =
      new ImmutableMap.Builder<String, Duration>()
          .put("d", Duration.ofDays(1))
          .put("h", Duration.ofHours(1))
          .put("m", Duration.ofMinutes(1))
          .put("min", Duration.ofMinutes(1))
          .put("s", Duration.ofSeconds(1))
          .put("ms", Duration.ofMillis(1))
          .put("us", Duration.ofNanos(1000))
          .put("ns", Duration.ofNanos(1))
          .buildOrThrow();
  private static final Pattern UNIT_PATTERN =
      Pattern.compile("(?x) ([0-9]+)? (\\.[0-9]*)? (d|h|min|ms?|s|us|ns)");
  private static final CharMatcher ASCII_DIGIT = CharMatcher.inRange('0', '9');

  private static Duration parseDuration(String value) {
    checkArgument(value != null, "input value cannot be null");
    checkArgument(!value.isEmpty(), "input value cannot be empty");
    checkArgument(!value.equals("-"), "input value cannot be '-'");
    checkArgument(!value.equals("+"), "input value cannot be '+'");

    value = CharMatcher.whitespace().trimFrom(value);

    if (Objects.equals(value, "0")) {
      return Duration.ZERO;
    }

    Duration duration = Duration.ZERO;
    boolean negative = value.startsWith("-");
    boolean explicitlyPositive = value.startsWith("+");
    int index = negative || explicitlyPositive ? 1 : 0;
    Matcher matcher = UNIT_PATTERN.matcher(value);
    while (matcher.find(index) && matcher.start() == index) {
      // Prevent strings like ".s" or "d" by requiring at least one digit.
      checkArgument(ASCII_DIGIT.matchesAnyOf(matcher.group(0)));
      try {
        String unit = matcher.group(3);

        long whole = Long.parseLong(firstNonNull(matcher.group(1), "0"));
        Duration singleUnit = ABBREVIATION_TO_DURATION.get(unit);
        checkArgument(singleUnit != null, "invalid unit (%s)", unit);
        // TODO(b/142748138): Consider using saturated duration math here
        duration = duration.plus(singleUnit.multipliedBy(whole));

        long nanosPerUnit = singleUnit.toNanos();
        double frac = Double.parseDouble("0" + firstNonNull(matcher.group(2), ""));
        duration = duration.plus(Duration.ofNanos((long) (nanosPerUnit * frac)));
      } catch (ArithmeticException e) {
        throw new IllegalArgumentException(e);
      }
      index = matcher.end();
    }
    if (index < value.length()) {
      throw new IllegalArgumentException("Could not parse entire duration: " + value);
    }
    if (negative) {
      duration = duration.negated();
    }
    return duration;
  }

  private ParameterValueParsing() {}
}
