/*
 * Copyright (c) 2023, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.test.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.postgresql.PGProperty;
import org.postgresql.core.BaseConnection;
import org.postgresql.core.CachedQuery;
import org.postgresql.core.Oid;
import org.postgresql.core.ParameterList;
import org.postgresql.core.QueryExecutor;
import org.postgresql.core.ResultHandlerBase;
import org.postgresql.jdbc.PreferQueryMode;
import org.postgresql.test.TestUtil;
import org.postgresql.test.jdbc2.BaseTest4;
import org.postgresql.util.PSQLState;

import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.Set;

/**
 * TestCase to test handling of binary types.
 */
public class QueryExecutorTest extends BaseTest4 {
  /**
   * Make sure the functions for adding binary transfer OIDs for custom types are correct.
   *
   * @throws SQLException if a database error occurs
   */
  @Test
  public void testBinaryTransferOids() throws SQLException {
    QueryExecutor queryExecutor = con.unwrap(BaseConnection.class).getQueryExecutor();
    // get current OIDs (make a copy of them)
    @SuppressWarnings("deprecation")
    Set<? extends Integer> oidsReceive = queryExecutor.getBinaryReceiveOids();
    @SuppressWarnings("deprecation")
    Set<? extends Integer> oidsSend = queryExecutor.getBinarySendOids();
    // add a new OID to be transferred as binary data
    int customTypeOid = 91716;
    assertBinaryForReceive(customTypeOid, false,
        () -> "Custom type OID should not be binary for receive by default");
    // first for receiving
    queryExecutor.addBinaryReceiveOid(customTypeOid);
    // Verify
    assertBinaryForReceive(customTypeOid, true,
        () -> "Just added oid via addBinaryReceiveOid");
    assertBinaryForSend(customTypeOid, false,
        () -> "Just added oid via addBinaryReceiveOid");
    for (int oid : oidsReceive) {
      assertBinaryForReceive(oid, true,
          () -> "Previously registered BinaryReceiveOids should be intact after "
              + "addBinaryReceiveOid(" + customTypeOid + ")");
    }
    for (int oid : oidsSend) {
      assertBinaryForSend(oid, true,
          () -> "Previously registered BinarySendOids should be intact after "
              + "addBinaryReceiveOid(" + customTypeOid + ")");
    }
    // then for sending
    queryExecutor.addBinarySendOid(customTypeOid);
    // check new OID
    assertBinaryForReceive(customTypeOid, true, () -> "added oid via addBinaryReceiveOid and "
        + "addBinarySendOid");
    assertBinaryForSend(customTypeOid, true, () -> "added oid via addBinaryReceiveOid and "
        + "addBinarySendOid");
    for (int oid : oidsReceive) {
      assertBinaryForReceive(oid, true, () -> "Previously registered BinaryReceiveOids should be "
          + "intact after addBinaryReceiveOid(" + customTypeOid + ") and addBinarySendOid(" + customTypeOid + ")");
    }
    for (int oid : oidsSend) {
      assertBinaryForSend(oid, true, () -> "Previously registered BinarySendOids should be intact"
          + " after addBinaryReceiveOid(" + customTypeOid + ")");
    }
  }

  /**
   * A parameter value that cannot be rendered into the SQL text in simple mode has to be reported
   * like a failed Bind, and the replies to what was already sent (the BEGIN) have to be read, so
   * the next request does not read them.
   *
   * @throws SQLException if a database error occurs
   */
  @Test
  public void testSimpleQueryRenderingFailureKeepsConnectionInSync() throws SQLException {
    Properties props = new Properties();
    PGProperty.PREFER_QUERY_MODE.set(props, PreferQueryMode.SIMPLE.value());
    Connection simpleCon = TestUtil.openDB(props);
    try {
      simpleCon.setAutoCommit(false);
      QueryExecutor queryExecutor = simpleCon.unwrap(BaseConnection.class).getQueryExecutor();
      CachedQuery cachedQuery = queryExecutor.createQuery("SELECT ?", true, true);
      ParameterList params = cachedQuery.query.createParameterList();
      // A binary int4 value that is too short throws when it is rendered into the SQL text.
      params.setBinaryParameter(1, new byte[2], Oid.INT4);
      try {
        queryExecutor.execute(cachedQuery.query, params, new ResultHandlerBase(), 0, 0, 0);
        fail("Rendering a malformed parameter value is supposed to fail");
      } catch (SQLException e) {
        assertEquals(PSQLState.INVALID_PARAMETER_VALUE.getState(), e.getSQLState());
      }

      assertFalse("The connection should stay usable", simpleCon.isClosed());
      simpleCon.rollback();
      Statement stmt = simpleCon.createStatement();
      ResultSet rs = stmt.executeQuery("SELECT 'in sync'");
      assertTrue(rs.next());
      assertEquals("in sync", rs.getString(1));
      rs.close();
      stmt.close();
    } finally {
      TestUtil.closeDB(simpleCon);
    }
  }
}
