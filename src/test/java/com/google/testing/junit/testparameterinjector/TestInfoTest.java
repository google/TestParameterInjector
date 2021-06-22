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
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.IterableSubject;
import com.google.testing.junit.testparameterinjector.TestInfo.TestInfoParameter;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestInfoTest {

  @Test
  public void shortenNamesIfNecessary_emptyTestInfos() throws Exception {
    ImmutableList<TestInfo> result = TestInfo.shortenNamesIfNecessary(ImmutableList.of());

    assertThat(result).isEmpty();
  }

  @Test
  public void shortenNamesIfNecessary_noParameters() throws Exception {
    ImmutableList<TestInfo> givenTestInfos = ImmutableList.of(fakeTestInfo());

    ImmutableList<TestInfo> result = TestInfo.shortenNamesIfNecessary(givenTestInfos);

    assertThat(result).containsExactlyElementsIn(givenTestInfos);
  }

  @Test
  public void shortenNamesIfNecessary_veryLongTestMethodName_noParameters() throws Exception {
    ImmutableList<TestInfo> givenTestInfos =
        ImmutableList.of(
            TestInfo.createWithoutParameters(
                getClass()
                    .getMethod(
                        "unusedMethodThatHasAVeryLongNameForTest000000000000000000000000000000000"
                            + "000000000000000000000000000000000000000000000000000000000000000000"
                            + "000000000000000000000000000000000000000000000000000000000000000000"
                            + "000000000000000000000000"),
                /* annotations= */ ImmutableList.of()));

    ImmutableList<TestInfo> result = TestInfo.shortenNamesIfNecessary(givenTestInfos);

    assertThat(result).containsExactlyElementsIn(givenTestInfos);
  }

  @Test
  public void shortenNamesIfNecessary_noShorteningNeeded() throws Exception {
    ImmutableList<TestInfo> givenTestInfos =
        ImmutableList.of(
            fakeTestInfo(
                TestInfoParameter.create(
                    /* name= */ "short", /* value= */ 1, /* indexInValueSource= */ 1),
                TestInfoParameter.create(
                    /* name= */ "shorter", /* value= */ null, /* indexInValueSource= */ 3)),
            fakeTestInfo(
                TestInfoParameter.create(
                    /* name= */ "short", /* value= */ 1, /* indexInValueSource= */ 1),
                TestInfoParameter.create(
                    /* name= */ "shortest", /* value= */ 20, /* indexInValueSource= */ 0)));

    ImmutableList<TestInfo> result = TestInfo.shortenNamesIfNecessary(givenTestInfos);

    assertThat(result).containsExactlyElementsIn(givenTestInfos);
  }

  @Test
  public void shortenNamesIfNecessary_singleParameterTooLong_twoParameters() throws Exception {
    ImmutableList<TestInfo> result =
        TestInfo.shortenNamesIfNecessary(
            ImmutableList.of(
                fakeTestInfo(
                    TestInfoParameter.create(
                        /* name= */ "short", /* value= */ 1, /* indexInValueSource= */ 0),
                    TestInfoParameter.create(
                        /* name= */ "shorter", /* value= */ null, /* indexInValueSource= */ 0)),
                fakeTestInfo(
                    TestInfoParameter.create(
                        /* name= */ "short", /* value= */ 1, /* indexInValueSource= */ 0),
                    TestInfoParameter.create(
                        /* name= */ "very long parameter name for test"
                            + " 00000000000000000000000000000000000000000000000000000000"
                            + "000000000000000000000000000000000000000000000000000000000"
                            + "0000000000000000000000000000000000000000000000",
                        /* value= */ 20,
                        /* indexInValueSource= */ 1))));

    assertThatTestNamesOf(result)
        .containsExactly(
            "toLowerCase[short,1.shorter]",
            "toLowerCase[short,2.very long parameter name for test "
                + "0000000000000000000000000000000000000000000000000000...]");
  }

  @Test
  public void shortenNamesIfNecessary_singleParameterTooLong_onlyParameter() throws Exception {
    ImmutableList<TestInfo> result =
        TestInfo.shortenNamesIfNecessary(
            ImmutableList.of(
                fakeTestInfo(
                    TestInfoParameter.create(
                        /* name= */ "shorter", /* value= */ null, /* indexInValueSource= */ 0)),
                fakeTestInfo(
                    TestInfoParameter.create(
                        /* name= */ "very long parameter name for test"
                            + " 00000000000000000000000000000000000000000000000000000000"
                            + "000000000000000000000000000000000000000000000000000000000"
                            + "0000000000000000000000000000000000000000000000",
                        /* value= */ 20,
                        /* indexInValueSource= */ 1))));

    assertThatTestNamesOf(result)
        .containsExactly(
            "toLowerCase[1.shorter]",
            "toLowerCase[2.very long parameter name for test"
                + " 000000000000000000000000000000000000000000000000000000000000...]");
  }

  @Test
  public void shortenNamesIfNecessary_tooManyParameters() throws Exception {
    TestInfo testInfoWithManyParams =
        fakeTestInfo(
            IntStream.range(0, 50)
                .mapToObj(
                    i ->
                        TestInfoParameter.create(
                            /* name= */ "short", /* value= */ i, /* indexInValueSource= */ i))
                .toArray(TestInfoParameter[]::new));

    ImmutableList<TestInfo> result =
        TestInfo.shortenNamesIfNecessary(ImmutableList.of(testInfoWithManyParams));

    assertThatTestNamesOf(result)
        .containsExactly(
            "toLowerCase[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,"
                + "27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50]");
  }

  private static TestInfo fakeTestInfo(TestInfoParameter... parameters)
      throws NoSuchMethodException {
    return TestInfo.createWithoutParameters(
            String.class.getMethod("toLowerCase"), /* annotations= */ ImmutableList.of())
        .withExtraParameters(ImmutableList.copyOf(parameters));
  }

  private static IterableSubject assertThatTestNamesOf(List<TestInfo> result) {
    return assertThat(result.stream().map(TestInfo::getName).collect(toList()));
  }

  public void
      unusedMethodThatHasAVeryLongNameForTest000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000() {}
}
