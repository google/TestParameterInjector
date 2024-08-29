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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;
import com.google.testing.junit.testparameterinjector.TestParameters.TestParametersValues;
import java.math.BigInteger;
import java.time.Duration;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class ParameterValueParsingTest {

  @Test
  public void parseEnum_success() throws Exception {
    Enum<?> result = ParameterValueParsing.parseEnum("BBB", TestEnum.class);

    assertThat(result).isEqualTo(TestEnum.BBB);
  }

  @Test
  @TestParameters({
    "{yamlString: '{a: b, c: 15}', valid: true}",
    "{yamlString: '{a: b c: 15',   valid: false}",
    "{yamlString: 'a: b c: 15',    valid: false}",
  })
  public void isValidYamlString_success(String yamlString, boolean valid) throws Exception {
    boolean result = ParameterValueParsing.isValidYamlString(yamlString);

    assertThat(result).isEqualTo(valid);
  }

  enum ParseYamlValueToJavaTypeCases {
    STRING_TO_STRING(
        /* yamlString= */ "abc", /* javaClass= */ String.class, /* expectedResult= */ "abc"),
    BOOLEAN_TO_STRING(
        /* yamlString= */ "true", /* javaClass= */ String.class, /* expectedResult= */ "true"),
    INT_TO_STRING(
        /* yamlString= */ "123", /* javaClass= */ String.class, /* expectedResult= */ "123"),
    LONG_TO_STRING(
        /* yamlString= */ "442147483648",
        /* javaClass= */ String.class,
        /* expectedResult= */ "442147483648"),
    BIG_INTEGER_TO_BIGINTEGER(
        /* yamlString= */ "1000000000000000000000000000",
        /* javaClass= */ BigInteger.class,
        /* expectedResult= */ new BigInteger("1000000000000000000000000000")),
    BIG_INTEGER_TO_UNSIGNED_LONG(
        /* yamlString= */ "18446744073709551615", // This is UnsignedLong.MAX_VALUE.
        /* javaClass= */ UnsignedLong.class,
        /* expectedResult= */ UnsignedLong.MAX_VALUE),
    LONG_TO_UNSIGNED_LONG(
        /* yamlString= */ "10000000000000",
        /* javaClass= */ UnsignedLong.class,
        /* expectedResult= */ UnsignedLong.fromLongBits(10000000000000L)),
    LONG_TO_BIG_INTEGER(
        /* yamlString= */ "10000000000000",
        /* javaClass= */ BigInteger.class,
        /* expectedResult= */ BigInteger.valueOf(10000000000000L)),
    INTEGER_TO_BIG_INTEGER(
        /* yamlString= */ "1000000",
        /* javaClass= */ BigInteger.class,
        /* expectedResult= */ BigInteger.valueOf(1000000)),
    INTEGER_TO_UNSIGNED_LONG(
        /* yamlString= */ "1000000",
        /* javaClass= */ UnsignedLong.class,
        /* expectedResult= */ UnsignedLong.fromLongBits(1000000)),
    DOUBLE_TO_STRING(
        /* yamlString= */ "1.23", /* javaClass= */ String.class, /* expectedResult= */ "1.23"),

    BOOLEAN_TO_BOOLEAN(
        /* yamlString= */ "true", /* javaClass= */ Boolean.class, /* expectedResult= */ true),

    INT_TO_INT(/* yamlString= */ "123", /* javaClass= */ int.class, /* expectedResult= */ 123),

    LONG_TO_LONG(
        /* yamlString= */ "442147483648",
        /* javaClass= */ long.class,
        /* expectedResult= */ 442147483648L),
    INT_TO_LONG(/* yamlString= */ "123", /* javaClass= */ Long.class, /* expectedResult= */ 123L),

    DOUBLE_TO_DOUBLE(
        /* yamlString= */ "1.23", /* javaClass= */ Double.class, /* expectedResult= */ 1.23),
    INT_TO_DOUBLE(
        /* yamlString= */ "123", /* javaClass= */ Double.class, /* expectedResult= */ 123.0),
    LONG_TO_DOUBLE(
        /* yamlString= */ "442147483648",
        /* javaClass= */ Double.class,
        /* expectedResult= */ 442147483648.0),
    NAN_TO_DOUBLE(
        /* yamlString= */ "NaN", /* javaClass= */ Double.class, /* expectedResult= */ Double.NaN),
    INFINITY_TO_DOUBLE(
        /* yamlString= */ "Infinity",
        /* javaClass= */ Double.class,
        /* expectedResult= */ Double.POSITIVE_INFINITY),
    POSITIVE_INFINITY_TO_DOUBLE(
        /* yamlString= */ "+Infinity",
        /* javaClass= */ Double.class,
        /* expectedResult= */ Double.POSITIVE_INFINITY),
    NEGATIVE_INFINITY_TO_DOUBLE(
        /* yamlString= */ "-Infinity",
        /* javaClass= */ Double.class,
        /* expectedResult= */ Double.NEGATIVE_INFINITY),

    DOUBLE_TO_FLOAT(
        /* yamlString= */ "1.23", /* javaClass= */ Float.class, /* expectedResult= */ 1.23F),
    INT_TO_FLOAT(/* yamlString= */ "123", /* javaClass= */ Float.class, /* expectedResult= */ 123F),

    STRING_TO_ENUM(
        /* yamlString= */ "AAA",
        /* javaClass= */ TestEnum.class,
        /* expectedResult= */ TestEnum.AAA),
    BOOLEAN_TO_ENUM_FALSE(
        /* yamlString= */ "NO", /* javaClass= */ TestEnum.class, /* expectedResult= */ TestEnum.NO),
    BOOLEAN_TO_ENUM_TRUE(
        /* yamlString= */ "TRUE",
        /* javaClass= */ TestEnum.class,
        /* expectedResult= */ TestEnum.TRUE),
    // This works because the YAML parser in between makes it impossible to differentiate. This test
    // case is not testing desired behavior, but rather double-checking that the YAML parsing step
    // actually happens and we are testing this edge case.
    BOOLEAN_TO_ENUM_TRUE_DIFFERENT_ALIAS(
        /* yamlString= */ "ON",
        /* javaClass= */ TestEnum.class,
        /* expectedResult= */ TestEnum.TRUE),

    STRING_TO_BYTES(
        /* yamlString= */ "data",
        /* javaClass= */ byte[].class,
        /* expectedResult= */ "data".getBytes()),

    BYTES_TO_BYTES(
        /* yamlString= */ "!!binary 'ZGF0YQ=='",
        /* javaClass= */ byte[].class,
        /* expectedResult= */ "data".getBytes()),

    STRING_TO_BYTESTRING(
        /* yamlString= */ "'data'",
        /* javaClass= */ ByteString.class,
        /* expectedResult= */ ByteString.copyFromUtf8("data")),

    BINARY_TO_BYTESTRING(
        /* yamlString= */ "!!binary 'ZGF0YQ=='",
        /* javaClass= */ ByteString.class,
        /* expectedResult= */ ByteString.copyFromUtf8("data"));

    final String yamlString;
    final Class<?> javaClass;
    final Object expectedResult;

    ParseYamlValueToJavaTypeCases(String yamlString, Class<?> javaClass, Object expectedResult) {
      this.yamlString = yamlString;
      this.javaClass = javaClass;
      this.expectedResult = expectedResult;
    }
  }

  @Test
  public void parseYamlStringToJavaType_success(
      @TestParameter ParseYamlValueToJavaTypeCases parseYamlValueToJavaTypeCases) throws Exception {
    Object result =
        ParameterValueParsing.parseYamlStringToJavaType(
            parseYamlValueToJavaTypeCases.yamlString, parseYamlValueToJavaTypeCases.javaClass);

    assertThat(result).isEqualTo(parseYamlValueToJavaTypeCases.expectedResult);
  }

  private static final class DurationSuccessTestCasesProvider extends TestParametersValuesProvider {
    @Override
    protected ImmutableList<TestParametersValues> provideValues(Context context) {
      return ImmutableList.of(
          // Simple
          testCase("7d", Duration.ofDays(7)),
          testCase("6h", Duration.ofHours(6)),
          testCase("5m", Duration.ofMinutes(5)),
          testCase("5min", Duration.ofMinutes(5)),
          testCase("4s", Duration.ofSeconds(4)),
          testCase("3.2s", Duration.ofMillis(3200)),
          testCase("0.2s", Duration.ofMillis(200)),
          testCase(".15s", Duration.ofMillis(150)),
          testCase("5.0s", Duration.ofSeconds(5)),
          testCase("1.0s", Duration.ofSeconds(1)),
          testCase("1.00s", Duration.ofSeconds(1)),
          testCase("1.004s", Duration.ofSeconds(1).plusMillis(4)),
          testCase("1.0040s", Duration.ofSeconds(1).plusMillis(4)),
          testCase("100.00100s", Duration.ofSeconds(100).plusMillis(1)),
          testCase("0.3333333333333333333h", Duration.ofMinutes(20)),
          testCase("1s3ms", Duration.ofSeconds(1).plusMillis(3)),
          testCase("1s34ms", Duration.ofSeconds(1).plusMillis(34)),
          testCase("1s345ms", Duration.ofSeconds(1).plusMillis(345)),
          testCase("345ms", Duration.ofMillis(345)),
          testCase(".9ms", Duration.ofNanos(900000)),
          testCase("5.s", Duration.ofSeconds(5)),
          testCase("+24h", Duration.ofHours(24)),
          testCase("0d", Duration.ZERO),
          testCase("-0d", Duration.ZERO),
          testCase("-1d", Duration.ofDays(-1)),
          testCase("1d", Duration.ofDays(1)),

          // Zero
          testCase("0", Duration.ZERO),
          testCase("-0", Duration.ZERO),
          testCase("+0", Duration.ZERO),

          // Multiple fields
          testCase("1h30m", Duration.ofMinutes(90)),
          testCase("1h30min", Duration.ofMinutes(90)),
          testCase("1d7m", Duration.ofDays(1).plusMinutes(7)),
          testCase("1m3.5s", Duration.ofMinutes(1).plusMillis(3500)),
          testCase("1m3s500ms", Duration.ofMinutes(1).plusMillis(3500)),
          testCase("5d4h3m2.1s", Duration.ofDays(5).plusHours(4).plusMinutes(3).plusMillis(2100)),
          testCase("3.5s250ms", Duration.ofMillis(3500 + 250)),
          testCase("1m2m3m", Duration.ofMinutes(6)),
          testCase("1m2h", Duration.ofHours(2).plusMinutes(1)),

          // Negative duration
          testCase("-.5h", Duration.ofMinutes(-30)),

          // Overflow
          testCase("106751d23h47m16s854ms775us807ns", Duration.ofNanos(Long.MAX_VALUE)),
          testCase("106751991167d7h12m55s807ms", Duration.ofMillis(Long.MAX_VALUE)),
          testCase("106751991167300d15h30m7s", Duration.ofSeconds(Long.MAX_VALUE)),
          testCase("106945d", Duration.ofDays(293 * 365)),

          // Underflow
          testCase("-106751d23h47m16s854ms775us808ns", Duration.ofNanos(Long.MIN_VALUE)),
          testCase("-106751991167d7h12m55s808ms", Duration.ofMillis(Long.MIN_VALUE)),
          testCase("-106751991167300d15h30m7s", Duration.ofSeconds(Long.MIN_VALUE + 1)),
          testCase("-106945d", Duration.ofDays(-293 * 365)),

          // Very large values
          testCase("9223372036854775807ns", Duration.ofNanos(Long.MAX_VALUE)),
          testCase("9223372036854775806ns", Duration.ofNanos(Long.MAX_VALUE - 1)),
          testCase("106751991167d7h12m55s807ms", Duration.ofMillis(Long.MAX_VALUE)),
          testCase("900000000000d", Duration.ofDays(900000000000L)),
          testCase("100000000000d100000000000d", Duration.ofDays(200000000000L)));
    }

    private static TestParametersValues testCase(String yamlString, Duration expectedResult) {
      return TestParametersValues.builder()
          .name(yamlString)
          .addParameter("yamlString", yamlString)
          .addParameter("expectedResult", expectedResult)
          .build();
    }
  }

  @Test
  @TestParameters(valuesProvider = DurationSuccessTestCasesProvider.class)
  public void parseYamlStringToJavaType_duration_success(String yamlString, Duration expectedResult)
      throws Exception {
    Object result = ParameterValueParsing.parseYamlStringToJavaType(yamlString, Duration.class);

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  public void parseYamlStringToJavaType_duration_fails(
      @TestParameter({
            // Wrong format
            "1m 3s", // spaces not allowed
            "0x123abc",
            "123x456",
            ".s",
            "d",
            "5dh",
            "1s500",
            "unparseable",
            "-",
            "+",
            "2",
            "-2",
            "+2",

            // Uppercase
            "1D",
            "1H",
            "1M",
            "1S",
            "1MS",
            "1Ms",
            "1mS",
            "1NS",
            "1Ns",
            "1nS",

            // Very large values
            Long.MAX_VALUE + "d",
            "10000000000000000000000000d"
          })
          String yamlString)
      throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> ParameterValueParsing.parseYamlStringToJavaType(yamlString, Duration.class));
  }

  @Test
  public void parseYamlStringToJavaType_booleanToEnum_ambiguousValues_fails(
      @TestParameter({"OFF", "YES", "false", "True"}) String yamlString) throws Exception {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ParameterValueParsing.parseYamlStringToJavaType(
                    yamlString, TestEnumWithAmbiguousValues.class));

    assertThat(exception)
        .hasCauseThat()
        .hasMessageThat()
        .contains(
            "It is likely that the YAML parser is 'wrongly' parsing one of these values as"
                + " boolean");
  }

  enum FormatTestNameStringTestCases {
    NULL_REFERENCE(/* value= */ null, /* expectedResult= */ "param=null"),
    BOOLEAN(/* value= */ false, /* expectedResult= */ "param=false"),
    INTEGER(/* value= */ 123, /* expectedResult= */ "param=123"),
    REGULAR_STRING(/* value= */ "abc", /* expectedResult= */ "abc"),
    EMPTY_STRING(/* value= */ "", /* expectedResult= */ "param="),
    NULL_STRING(/* value= */ "null", /* expectedResult= */ "param=null"),
    INTEGER_STRING(/* value= */ "123", /* expectedResult= */ "param=123"),
    ARRAY(/* value= */ new byte[] {2, 3, 4}, /* expectedResult= */ "[2, 3, 4]"),
    CHAR_MATCHER(/* value= */ CharMatcher.any(), /* expectedResult= */ "CharMatcher.any()");

    @Nullable final Object value;
    final String expectedResult;

    FormatTestNameStringTestCases(@Nullable Object value, String expectedResult) {
      this.value = value;
      this.expectedResult = expectedResult;
    }
  }

  @Test
  public void formatTestNameString_success(@TestParameter FormatTestNameStringTestCases testCase)
      throws Exception {
    String result =
        ParameterValueParsing.formatTestNameString(
            /* parameterName= */ Optional.of("param"), /* value= */ testCase.value);

    assertThat(result).isEqualTo(testCase.expectedResult);
  }

  private enum TestEnum {
    AAA,
    BBB,
    NO,
    TRUE;
  }

  private enum TestEnumWithAmbiguousValues {
    AAA,
    BBB,
    NO,
    OFF,
    YES,
    TRUE;
  }
}
