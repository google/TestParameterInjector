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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.min;

import java.lang.annotation.Annotation;
import java.util.Comparator;
import java.util.List;

/**
 * Default base class for {@link TestParameterValidator}, simplifying how validators can exclude
 * variable independent test parameters annotations.
 */
abstract class BaseTestParameterValidator implements TestParameterValidator {

  @Override
  public boolean shouldSkip(Context context) {
    for (List<Class<? extends Annotation>> parameters : getIndependentParameters(context)) {
      checkArgument(!parameters.isEmpty());
      // For independent test parameters, the only allowed tests will be those that use the same
      // Nth specified parameter, except for parameter values that have less specified values than
      // others.

      // For example, if parameter A has values a1 and a2, parameter B has values b1 and b2, and
      // parameter C has values c1, c2 and c3, given that A, B and C are independent, the only
      // tests that will not be skipped will be {(a1, b1, c1), (a2, b2, c2), (a2, b2, c3)},
      // instead of 12 tests that would constitute their cartesian product.

      // First, find the largest specified value count (parameter C in the example above),
      // so that we can easily determine which parameter value should be used for validating the
      // other parameters (e.g. should this test be for (a1, b1, c1), (a2, b2, c2), or
      // (a2, b2, c3). The test parameter 'C' will be the 'leadingParameter'.
      Class<? extends Annotation> leadingParameter =
          parameters.stream()
              .max(Comparator.comparing(parameter -> context.getSpecifiedValues(parameter).size()))
              .get();
      // Second, determine which index is the current value in the specified value list of
      // the leading parameter.  In the example above, the index of the current value 'c2' of the
      // leading parameter 'C' would be '1', given the specified values (c1, c2, c3).
      int leadingParameterValueIndex =
          getValueIndex(context, leadingParameter, context.getValue(leadingParameter).get());
      checkState(leadingParameterValueIndex >= 0);
      // Each independent test parameter should be the same index, or the last available index.
      // For example, if the parameter is A, and the leading parameter (C) index is 2, the A's index
      // should be 1, since a2 is the only available value.
      for (Class<? extends Annotation> parameter : parameters) {
        List<Object> specifiedValues = context.getSpecifiedValues(parameter);
        int valueIndex = specifiedValues.indexOf(context.getValue(parameter).get());
        int requiredValueIndex = min(leadingParameterValueIndex, specifiedValues.size() - 1);
        if (valueIndex != requiredValueIndex) {
          return true;
        }
      }
    }
    return false;
  }

  private int getValueIndex(Context context, Class<? extends Annotation> annotation, Object value) {
    return context.getSpecifiedValues(annotation).indexOf(value);
  }

  /**
   * Returns a list of TestParameterAnnotation annotated annotation types that are mutually
   * independent, and therefore the combinations of their values do not need to be tested.
   */
  protected abstract List<List<Class<? extends Annotation>>> getIndependentParameters(
      Context context);
}
