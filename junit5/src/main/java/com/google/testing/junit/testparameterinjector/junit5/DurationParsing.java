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

package com.google.testing.junit.testparameterinjector.junit5;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper for parsing {@link Duration} parameter values without eagerly class-loading {@link
 * Duration}.
 *
 * <p>{@link Duration} was introduced in Android API 26, so referencing it in {@link
 * ParameterValueParsing}'s static initializer or non-duration code paths throws {@link
 * NoClassDefFoundError} on older Android devices when core library desugaring is not enabled.
 */
final class DurationParsing {

  static final Optional<Class<?>> MAYBE_DURATION_CLASS = maybeGetDurationClass();

  static Duration parseDuration(String value) {
    return DurationParser.parseDuration(value);
  }

  private static Optional<Class<?>> maybeGetDurationClass() {
    try {
      return Optional.of(DurationHolder.getDurationClass());
    } catch (LinkageError unused) {
      return Optional.absent();
    }
  }

  /**
   * Isolates the {@code Duration.class} literal from {@link DurationParsing} and {@link
   * DurationParser}.
   *
   * <p>Using {@code Duration.class} directly (rather than {@code
   * Class.forName("java.time.Duration")}) preserves support for Android core library desugaring,
   * which rewrites {@link Duration} references to {@code j$.time.Duration}. Keeping it separate
   * from {@link DurationParser} ensures that checking availability does not initialize {@link
   * DurationParser}'s static fields unless a duration is actually parsed.
   */
  private static final class DurationHolder {
    private static Class<?> getDurationClass() {
      return Duration.class;
    }

    private DurationHolder() {}
  }

  /**
   * Holds the {@link Duration} parsing logic and static lookup table so they are only loaded and
   * initialized when a {@link Duration} parameter is actually parsed.
   */
  private static final class DurationParser {

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
          // TODO: b/142748138 - Consider using saturated duration math here
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

    private DurationParser() {}
  }

  private DurationParsing() {} // Inhibit instantiation
}
