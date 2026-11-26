/*
 * Copyright (c) 2023, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.util;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * This test is moved from {@link BigDecimalByteConverterTest} to prevent test with very long name.
 */
public class BigDecimalByteConverter2Test {
  @Test
  public void testBigDecimal10_pow_131072_minus_1() {
    BigDecimalByteConverterTest.testBinary(
        new BigDecimal(BigInteger.TEN.pow(131072).subtract(BigInteger.ONE))
    );
  }

  @Test
  public void largestScaleAndWeightThatFit() {
    BigDecimalByteConverterTest.testBinary(new BigDecimal("1e-16383"));
    BigDecimalByteConverterTest.testBinary(new BigDecimal("1e131071"));
  }

  @Test(timeout = 10000)
  public void valueOutsideTheBinaryFormatIsRefused() {
    final String[] values =
        {"999999.99e-262144", "1e131072", "1e-200000000", "0e-16384", "-1e-16384"};
    for (String value : values) {
      final BigDecimal number = new BigDecimal(value);
      assertThrows(value, IllegalArgumentException.class, () -> ByteConverter.numeric(number));
    }
  }
}
