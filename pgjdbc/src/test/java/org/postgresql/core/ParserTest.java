/*
 * Copyright (c) 2003, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.postgresql.jdbc.EscapeSyntaxCallMode;
import org.postgresql.jdbc.EscapedFunctions;
import org.postgresql.util.PSQLState;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * Test cases for the Parser.
 * @author Jeremy Whiting jwhiting@redhat.com
 */
public class ParserTest {

  /**
   * Test to make sure delete command is detected by parser and detected via
   * api. Mix up the case of the command to check detection continues to work.
   */
  @Test
  public void testDeleteCommandParsing() {
    char[] command = new char[6];
    "DELETE".getChars(0, 6, command, 0);
    assertTrue("Failed to correctly parse upper case command.", Parser.parseDeleteKeyword(command, 0));
    "DelEtE".getChars(0, 6, command, 0);
    assertTrue("Failed to correctly parse mixed case command.", Parser.parseDeleteKeyword(command, 0));
    "deleteE".getChars(0, 6, command, 0);
    assertTrue("Failed to correctly parse mixed case command.", Parser.parseDeleteKeyword(command, 0));
    "delete".getChars(0, 6, command, 0);
    assertTrue("Failed to correctly parse lower case command.", Parser.parseDeleteKeyword(command, 0));
    "Delete".getChars(0, 6, command, 0);
    assertTrue("Failed to correctly parse mixed case command.", Parser.parseDeleteKeyword(command, 0));
  }

  /**
   * Test UPDATE command parsing.
   */
  @Test
  public void testUpdateCommandParsing() {
    char[] command = new char[6];
    "UPDATE".getChars(0, 6, command, 0);
    assertTrue("Failed to correctly parse upper case command.", Parser.parseUpdateKeyword(command, 0));
    "UpDateE".getChars(0, 6, command, 0);
    assertTrue("Failed to correctly parse mixed case command.", Parser.parseUpdateKeyword(command, 0));
    "updatE".getChars(0, 6, command, 0);
    assertTrue("Failed to correctly parse mixed case command.", Parser.parseUpdateKeyword(command, 0));
    "Update".getChars(0, 6, command, 0);
    assertTrue("Failed to correctly parse mixed case command.", Parser.parseUpdateKeyword(command, 0));
    "update".getChars(0, 6, command, 0);
    assertTrue("Failed to correctly parse lower case command.", Parser.parseUpdateKeyword(command, 0));
  }

  /**
   * Test MOVE command parsing.
   */
  @Test
  public void testMoveCommandParsing() {
    char[] command = new char[4];
    "MOVE".getChars(0, 4, command, 0);
    assertTrue("Failed to correctly parse upper case command.", Parser.parseMoveKeyword(command, 0));
    "mOVe".getChars(0, 4, command, 0);
    assertTrue("Failed to correctly parse mixed case command.", Parser.parseMoveKeyword(command, 0));
    "movE".getChars(0, 4, command, 0);
    assertTrue("Failed to correctly parse mixed case command.", Parser.parseMoveKeyword(command, 0));
    "Move".getChars(0, 4, command, 0);
    assertTrue("Failed to correctly parse mixed case command.", Parser.parseMoveKeyword(command, 0));
    "move".getChars(0, 4, command, 0);
    assertTrue("Failed to correctly parse lower case command.", Parser.parseMoveKeyword(command, 0));
  }

  /**
   * Test WITH command parsing.
   */
  @Test
  public void testWithCommandParsing() {
    char[] command = new char[4];
    "WITH".getChars(0, 4, command, 0);
    assertTrue("Failed to correctly parse upper case command.", Parser.parseWithKeyword(command, 0));
    "wITh".getChars(0, 4, command, 0);
    assertTrue("Failed to correctly parse mixed case command.", Parser.parseWithKeyword(command, 0));
    "witH".getChars(0, 4, command, 0);
    assertTrue("Failed to correctly parse mixed case command.", Parser.parseWithKeyword(command, 0));
    "With".getChars(0, 4, command, 0);
    assertTrue("Failed to correctly parse mixed case command.", Parser.parseWithKeyword(command, 0));
    "with".getChars(0, 4, command, 0);
    assertTrue("Failed to correctly parse lower case command.", Parser.parseWithKeyword(command, 0));
  }

  /**
   * Test SELECT command parsing.
   */
  @Test
  public void testSelectCommandParsing() {
    char[] command = new char[6];
    "SELECT".getChars(0, 6, command, 0);
    assertTrue("Failed to correctly parse upper case command.", Parser.parseSelectKeyword(command, 0));
    "sELect".getChars(0, 6, command, 0);
    assertTrue("Failed to correctly parse mixed case command.", Parser.parseSelectKeyword(command, 0));
    "selecT".getChars(0, 6, command, 0);
    assertTrue("Failed to correctly parse mixed case command.", Parser.parseSelectKeyword(command, 0));
    "Select".getChars(0, 6, command, 0);
    assertTrue("Failed to correctly parse mixed case command.", Parser.parseSelectKeyword(command, 0));
    "select".getChars(0, 6, command, 0);
    assertTrue("Failed to correctly parse lower case command.", Parser.parseSelectKeyword(command, 0));
  }

  @Test
  public void testEscapeProcessing() throws Exception {
    assertEquals("DATE '1999-01-09'", Parser.replaceProcessing("{d '1999-01-09'}", true, false));
    assertEquals("DATE '1999-01-09'", Parser.replaceProcessing("{D  '1999-01-09'}", true, false));
    assertEquals("TIME '20:00:03'", Parser.replaceProcessing("{t '20:00:03'}", true, false));
    assertEquals("TIME '20:00:03'", Parser.replaceProcessing("{T '20:00:03'}", true, false));
    assertEquals("TIMESTAMP '1999-01-09 20:11:11.123455'", Parser.replaceProcessing("{ts '1999-01-09 20:11:11.123455'}", true, false));
    assertEquals("TIMESTAMP '1999-01-09 20:11:11.123455'", Parser.replaceProcessing("{Ts '1999-01-09 20:11:11.123455'}", true, false));

    assertEquals("user", Parser.replaceProcessing("{fn user()}", true, false));
    assertEquals("cos(1)", Parser.replaceProcessing("{fn cos(1)}", true, false));
    assertEquals("extract(week from DATE '2005-01-24')", Parser.replaceProcessing("{fn week({d '2005-01-24'})}", true, false));

    assertEquals("\"T1\" LEFT OUTER JOIN t2 ON \"T1\".id = t2.id",
            Parser.replaceProcessing("{oj \"T1\" LEFT OUTER JOIN t2 ON \"T1\".id = t2.id}", true, false));

    assertEquals("ESCAPE '_'", Parser.replaceProcessing("{escape '_'}", true, false));

    // nothing should be changed in that case, no valid escape code
    assertEquals("{obj : 1}", Parser.replaceProcessing("{obj : 1}", true, false));
  }

  @Test
  public void testModifyJdbcCall() throws SQLException {
    assertEquals("select * from pack_getValue(?) as result", Parser.modifyJdbcCall("{ ? = call pack_getValue}", true, ServerVersion.v9_6.getVersionNum(), 3, EscapeSyntaxCallMode.SELECT).getSql());
    assertEquals("select * from pack_getValue(?,?)  as result", Parser.modifyJdbcCall("{ ? = call pack_getValue(?) }", true, ServerVersion.v9_6.getVersionNum(), 3, EscapeSyntaxCallMode.SELECT).getSql());
    assertEquals("select * from pack_getValue(?) as result", Parser.modifyJdbcCall("{ ? = call pack_getValue()}", true, ServerVersion.v9_6.getVersionNum(), 3, EscapeSyntaxCallMode.SELECT).getSql());
    assertEquals("select * from pack_getValue(?,?,?,?)  as result", Parser.modifyJdbcCall("{ ? = call pack_getValue(?,?,?) }", true, ServerVersion.v9_6.getVersionNum(), 3, EscapeSyntaxCallMode.SELECT).getSql());
    assertEquals("select * from lower(?,?) as result", Parser.modifyJdbcCall("{ ? = call lower(?)}", true, ServerVersion.v9_6.getVersionNum(), 3, EscapeSyntaxCallMode.SELECT).getSql());
    assertEquals("select * from lower(?,?) as result", Parser.modifyJdbcCall("{ ? = call lower(?)}", true, ServerVersion.v9_6.getVersionNum(), 3, EscapeSyntaxCallMode.CALL_IF_NO_RETURN).getSql());
    assertEquals("select * from lower(?,?) as result", Parser.modifyJdbcCall("{ ? = call lower(?)}", true, ServerVersion.v9_6.getVersionNum(), 3, EscapeSyntaxCallMode.CALL).getSql());
    assertEquals("select * from lower(?,?) as result", Parser.modifyJdbcCall("{call lower(?,?)}", true, ServerVersion.v9_6.getVersionNum(), 3, EscapeSyntaxCallMode.SELECT).getSql());
    assertEquals("select * from lower(?,?) as result", Parser.modifyJdbcCall("{call lower(?,?)}", true, ServerVersion.v9_6.getVersionNum(), 3, EscapeSyntaxCallMode.CALL_IF_NO_RETURN).getSql());
    assertEquals("select * from lower(?,?) as result", Parser.modifyJdbcCall("{call lower(?,?)}", true, ServerVersion.v9_6.getVersionNum(), 3, EscapeSyntaxCallMode.CALL).getSql());
    assertEquals("select * from lower(?,?) as result", Parser.modifyJdbcCall("{ ? = call lower(?)}", true, ServerVersion.v11.getVersionNum(), 3, EscapeSyntaxCallMode.SELECT).getSql());
    assertEquals("select * from lower(?,?) as result", Parser.modifyJdbcCall("{ ? = call lower(?)}", true, ServerVersion.v11.getVersionNum(), 3, EscapeSyntaxCallMode.CALL_IF_NO_RETURN).getSql());
    assertEquals("call lower(?,?)", Parser.modifyJdbcCall("{ ? = call lower(?)}", true, ServerVersion.v11.getVersionNum(), 3, EscapeSyntaxCallMode.CALL).getSql());
    assertEquals("select * from lower(?,?) as result", Parser.modifyJdbcCall("{call lower(?,?)}", true, ServerVersion.v11.getVersionNum(), 3, EscapeSyntaxCallMode.SELECT).getSql());
    assertEquals("call lower(?,?)", Parser.modifyJdbcCall("{call lower(?,?)}", true, ServerVersion.v11.getVersionNum(), 3, EscapeSyntaxCallMode.CALL_IF_NO_RETURN).getSql());
    assertEquals("call lower(?,?)", Parser.modifyJdbcCall("{call lower(?,?)}", true, ServerVersion.v11.getVersionNum(), 3, EscapeSyntaxCallMode.CALL).getSql());
  }

  @Test
  public void testUnterminatedEscape() throws Exception {
    assertEquals("{oj ", Parser.replaceProcessing("{oj ", true, false));
  }

  @Test
  @Ignore(value = "returning in the select clause is hard to distinguish from insert ... returning *")
  public void insertSelectFakeReturning() throws SQLException {
    String query =
        "insert test(id, name) select 1, 'value' as RETURNING from test2";
    List<NativeQuery> qry =
        Parser.parseJdbcSql(
            query, true, true, true, true, true);
    boolean returningKeywordPresent = qry.get(0).command.isReturningKeywordPresent();
    Assert.assertFalse("Query does not have returning clause " + query, returningKeywordPresent);
  }

  @Test
  public void insertSelectReturning() throws SQLException {
    String query =
        "insert test(id, name) select 1, 'value' from test2 RETURNING id";
    List<NativeQuery> qry =
        Parser.parseJdbcSql(
            query, true, true, true, true, true);
    boolean returningKeywordPresent = qry.get(0).command.isReturningKeywordPresent();
    Assert.assertTrue("Query has a returning clause " + query, returningKeywordPresent);
  }

  @Test
  public void insertReturningInWith() throws SQLException {
    String query =
        "with x as (insert into mytab(x) values(1) returning x) insert test(id, name) select 1, 'value' from test2";
    List<NativeQuery> qry =
        Parser.parseJdbcSql(
            query, true, true, true, true, true);
    boolean returningKeywordPresent = qry.get(0).command.isReturningKeywordPresent();
    Assert.assertFalse("There's no top-level <<returning>> clause " + query, returningKeywordPresent);
  }

  @Test
  public void insertBatchedReWriteOnConflict() throws SQLException {
    String query = "insert into test(id, name) values (:id,:name) ON CONFLICT (id) DO NOTHING";
    List<NativeQuery> qry = Parser.parseJdbcSql(query, true, true, true, true, true);
    SqlCommand command = qry.get(0).getCommand();
    Assert.assertEquals(34, command.getBatchRewriteValuesBraceOpenPosition());
    Assert.assertEquals(44, command.getBatchRewriteValuesBraceClosePosition());
  }

  @Test
  public void insertBatchedReWriteOnConflictUpdateBind() throws SQLException {
    String query = "insert into test(id, name) values (?,?) ON CONFLICT (id) UPDATE SET name=?";
    List<NativeQuery> qry = Parser.parseJdbcSql(query, true, true, true, true, true);
    SqlCommand command = qry.get(0).getCommand();
    Assert.assertFalse("update set name=? is NOT compatible with insert rewrite", command.isBatchedReWriteCompatible());
  }

  @Test
  public void insertBatchedReWriteOnConflictUpdateConstant() throws SQLException {
    String query = "insert into test(id, name) values (?,?) ON CONFLICT (id) UPDATE SET name='default'";
    List<NativeQuery> qry = Parser.parseJdbcSql(query, true, true, true, true, true);
    SqlCommand command = qry.get(0).getCommand();
    Assert.assertTrue("update set name='default' is compatible with insert rewrite", command.isBatchedReWriteCompatible());
  }

  @Test
  public void insertMultiInsert() throws SQLException {
    String query =
        "insert into test(id, name) values (:id,:name),(:id,:name) ON CONFLICT (id) DO NOTHING";
    List<NativeQuery> qry = Parser.parseJdbcSql(query, true, true, true, true, true);
    SqlCommand command = qry.get(0).getCommand();
    Assert.assertEquals(34, command.getBatchRewriteValuesBraceOpenPosition());
    Assert.assertEquals(56, command.getBatchRewriteValuesBraceClosePosition());
  }

  @Test
  public void valuesTableParse() throws SQLException {
    String query = "insert into values_table (id, name) values (?,?)";
    List<NativeQuery> qry = Parser.parseJdbcSql(query, true, true, true, true, true);
    SqlCommand command = qry.get(0).getCommand();
    Assert.assertEquals(43,command.getBatchRewriteValuesBraceOpenPosition());
    Assert.assertEquals(49,command.getBatchRewriteValuesBraceClosePosition());

    query = "insert into table_values (id, name) values (?,?)";
    qry = Parser.parseJdbcSql(query, true, true, true, true, true);
    command = qry.get(0).getCommand();
    Assert.assertEquals(43,command.getBatchRewriteValuesBraceOpenPosition());
    Assert.assertEquals(49,command.getBatchRewriteValuesBraceClosePosition());
  }

  @Test
  public void createTableParseWithOnDeleteClause() throws SQLException {
    String[] returningColumns = {"*"};
    String query = "create table \"testTable\" (\"id\" INT SERIAL NOT NULL PRIMARY KEY, \"foreignId\" INT REFERENCES \"otherTable\" (\"id\") ON DELETE NO ACTION)";
    List<NativeQuery> qry = Parser.parseJdbcSql(query, true, true, true, true, true, returningColumns);
    SqlCommand command = qry.get(0).getCommand();
    Assert.assertFalse("No returning keyword should be present", command.isReturningKeywordPresent());
    Assert.assertEquals(SqlCommandType.CREATE, command.getType());
  }

  @Test
  public void createTableParseWithOnUpdateClause() throws SQLException {
    String[] returningColumns = {"*"};
    String query = "create table \"testTable\" (\"id\" INT SERIAL NOT NULL PRIMARY KEY, \"foreignId\" INT REFERENCES \"otherTable\" (\"id\")) ON UPDATE NO ACTION";
    List<NativeQuery> qry = Parser.parseJdbcSql(query, true, true, true, true, true, returningColumns);
    SqlCommand command = qry.get(0).getCommand();
    Assert.assertFalse("No returning keyword should be present", command.isReturningKeywordPresent());
    Assert.assertEquals(SqlCommandType.CREATE, command.getType());
  }

  @Test
  public void alterTableParseWithOnDeleteClause() throws SQLException {
    String[] returningColumns = {"*"};
    String query = "alter table \"testTable\" ADD \"foreignId\" INT REFERENCES \"otherTable\" (\"id\") ON DELETE NO ACTION";
    List<NativeQuery> qry = Parser.parseJdbcSql(query, true, true, true, true, true, returningColumns);
    SqlCommand command = qry.get(0).getCommand();
    Assert.assertFalse("No returning keyword should be present", command.isReturningKeywordPresent());
    Assert.assertEquals(SqlCommandType.ALTER, command.getType());
  }

  @Test
  public void alterTableParseWithOnUpdateClause() throws SQLException {
    String[] returningColumns = {"*"};
    String query = "alter table \"testTable\" ADD \"foreignId\" INT REFERENCES \"otherTable\" (\"id\") ON UPDATE RESTRICT";
    List<NativeQuery> qry = Parser.parseJdbcSql(query, true, true, true, true, true, returningColumns);
    SqlCommand command = qry.get(0).getCommand();
    Assert.assertFalse("No returning keyword should be present", command.isReturningKeywordPresent());
    Assert.assertEquals(SqlCommandType.ALTER, command.getType());
  }

  @Test
  public void testParseV14functions() throws SQLException {
    String[] returningColumns = {"*"};
    String query = "CREATE OR REPLACE FUNCTION asterisks(n int)\n"
        + "  RETURNS SETOF text\n"
        + "  LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE\n"
        + "BEGIN ATOMIC\n"
        + "SELECT repeat('*', g) FROM generate_series (1, n) g; \n"
        + "END;";
    List<NativeQuery> qry = Parser.parseJdbcSql(query, true, true, true, true, true, returningColumns);
    Assert.assertNotNull(qry);
    Assert.assertEquals("There should only be one query returned here", 1, qry.size());
  }

  /**
   * A closing brace inside a literal, quoted identifier, dollar quote or comment must not end a
   * JDBC escape, otherwise the rest of the literal is translated as SQL.
   */
  @Test
  public void braceInsideQuotedTextDoesNotEndEscape() throws SQLException {
    assertEquals("DATE '2001-01-01}'", Parser.replaceProcessing("{d '2001-01-01}'}", true, false));
    assertEquals("TIME '20:00:03}'", Parser.replaceProcessing("{t '20:00:03}'}", true, false));
    assertEquals("TIMESTAMP '1999-01-09 }'",
        Parser.replaceProcessing("{ts '1999-01-09 }'}", true, false));
    assertEquals("ESCAPE '}'", Parser.replaceProcessing("{escape '}'}", true, false));
    assertEquals("t1 LEFT OUTER JOIN t2 ON t1.a = '}'",
        Parser.replaceProcessing("{oj t1 LEFT OUTER JOIN t2 ON t1.a = '}'}", true, false));
    assertEquals("\"t}\" LEFT OUTER JOIN t2 ON \"t}\".id = t2.id",
        Parser.replaceProcessing("{oj \"t}\" LEFT OUTER JOIN t2 ON \"t}\".id = t2.id}", true, false));
    assertEquals("t1 LEFT OUTER JOIN t2 ON t1.a = $$}$$",
        Parser.replaceProcessing("{oj t1 LEFT OUTER JOIN t2 ON t1.a = $$}$$}", true, false));
    assertEquals("t1 LEFT OUTER JOIN t2 ON t1.a = t2.a /* } */",
        Parser.replaceProcessing("{oj t1 LEFT OUTER JOIN t2 ON t1.a = t2.a /* } */}", true, false));
    assertEquals("cos(1) || '}'", Parser.replaceProcessing("{fn cos(1) || '}'}", true, false));
    // an escape inside the literal is part of the value and must not be translated
    assertEquals("DATE '{fn user()}'", Parser.replaceProcessing("{d '{fn user()}'}", true, false));
  }

  /**
   * A user value with doubled quotes inside an escaped literal is copied verbatim. A {@code {fn}}
   * escape inside it must not be translated, as the translation can move the closing quote of the
   * literal and turn the rest of the value into SQL.
   */
  @Test
  public void escapeInsideQuotedValueIsNotTranslated() throws SQLException {
    String value = "}{fn locate('a','b',1)}' UNION SELECT passwd FROM pg_shadow --";
    String literal = "'" + value.replace("'", "''") + "'";
    String query = "SELECT * FROM t WHERE d = {d " + literal + "}";
    assertEquals("SELECT * FROM t WHERE d = DATE " + literal,
        Parser.replaceProcessing(query, true, true));
    assertEquals("SELECT * FROM t WHERE d = DATE " + literal,
        Parser.replaceProcessing(query, true, false));
  }

  /**
   * A value quoted by doubling its quotes keeps the closing brace inside the literal, so the text
   * after it is not parsed as SQL (where a {@code --} would hide the real end of the literal).
   */
  @Test
  public void braceInsideQuotedValueWithDoubledQuotes() throws SQLException {
    String value = "}' --";
    String query = "SELECT {d '" + value.replace("'", "''") + "'} AS d, ?";
    assertEquals("SELECT DATE '}'' --' AS d, ?", Parser.replaceProcessing(query, true, false));
    assertEquals("SELECT DATE '}'' --' AS d, ?", Parser.replaceProcessing(query, true, true));
  }

  /**
   * An unterminated literal inside an escape is reported instead of being closed by a brace.
   */
  @Test
  public void unterminatedLiteralInsideEscapeFails() {
    try {
      Parser.replaceProcessing("{d '2001-01-01}", true, false);
      fail("Unterminated string literal inside {d} escape should fail");
    } catch (SQLException e) {
      assertEquals(PSQLState.SYNTAX_ERROR.getState(), e.getSQLState());
    }
  }

  /**
   * After a doubled quote the rest of an {@code E''} string still treats a backslash as an escape
   * character, so {@code \'} does not end it.
   */
  @Test
  public void escapeStringWithDoubledQuote() throws SQLException {
    char[] query = "x E'a''\\'b' y".toCharArray();
    assertEquals(10, Parser.parseSingleQuotes(query, 3, true));
    assertEquals(10, Parser.parseSingleQuotes(query, 3, false));

    // the placeholder is inside the string for the backend, so it must not be a bind
    List<NativeQuery> qry = Parser.parseJdbcSql(
        "SELECT E'a''\\' ? ', ?", true, true, true, false, false);
    assertEquals(1, qry.size());
    assertEquals("SELECT E'a''\\' ? ', $1", qry.get(0).nativeSql);
    assertEquals(1, qry.get(0).bindPositions.length);

    // statement separators inside the string must not split the query
    qry = Parser.parseJdbcSql("SELECT E'a''\\'; SELECT 1; '", true, true, true, false, false);
    assertEquals(1, qry.size());
  }

  /**
   * The backend reads {@code E''} after a vertical tab (whitespace since PostgreSQL 17) and after
   * an unpaired surrogate (sent as {@code ?}, an operator character), so a backslash escapes the
   * quote there.
   */
  @Test
  public void escapeStringAfterVerticalTabOrUnpairedSurrogate() throws SQLException {
    char[] query = "\u000bE'\\' ? '".toCharArray();
    assertEquals(query.length - 1, Parser.parseSingleQuotes(query, 2, true));
    query = "\ud800E'\\' ? '".toCharArray();
    assertEquals(query.length - 1, Parser.parseSingleQuotes(query, 2, true));

    List<NativeQuery> qry = Parser.parseJdbcSql(
        "SELECT\u000bE'\\' ? ', ?", true, true, true, false, false);
    assertEquals(1, qry.size());
    assertEquals("SELECT\u000bE'\\' ? ', $1", qry.get(0).nativeSql);
    assertEquals(1, qry.get(0).bindPositions.length);
  }

  /**
   * A doubled quote and a continuation on a new line do not end a single-quoted string.
   */
  @Test
  public void singleQuoteContinuation() {
    assertEquals(5, Parser.parseSingleQuotes("'a''b'".toCharArray(), 0, true));
    assertEquals(5, Parser.parseSingleQuotes("'a''b'".toCharArray(), 0, false));
    assertEquals(6, Parser.parseSingleQuotes("'a'\n'b'".toCharArray(), 0, true));
    assertEquals(9, Parser.parseSingleQuotes("'a' \r\n 'b'".toCharArray(), 0, true));
    assertEquals(11, Parser.parseSingleQuotes("'a' -- c\n'b'".toCharArray(), 0, true));
    assertEquals(7, Parser.parseSingleQuotes("'a'\u000b\n'b'".toCharArray(), 0, true));
    // the continuation of an E'' string is also an E'' string
    char[] query = " E'a'\n'\\' ?'".toCharArray();
    assertEquals(query.length - 1, Parser.parseSingleQuotes(query, 2, true));
    // no newline, no continuation
    assertEquals(2, Parser.parseSingleQuotes("'a' 'b'".toCharArray(), 0, true));
    assertEquals(2, Parser.parseSingleQuotes("'a' -'b'".toCharArray(), 0, true));
    assertEquals(2, Parser.parseSingleQuotes("'a' -- c".toCharArray(), 0, true));
    assertEquals(2, Parser.parseSingleQuotes("'a'\nb'".toCharArray(), 0, true));
  }

  /**
   * The backend accepts any non-ASCII character in a dollar quote tag.
   */
  @Test
  public void dollarQuoteTagWithNonAsciiCharacters() throws SQLException {
    char[] query = "$§$a$§$".toCharArray();
    assertEquals(query.length - 1, Parser.parseDollarQuotes(query, 0));
    query = "$a b$x$a b$".toCharArray();
    assertEquals(query.length - 1, Parser.parseDollarQuotes(query, 0));
    query = "$😀$x$😀$".toCharArray();
    assertEquals(query.length - 1, Parser.parseDollarQuotes(query, 0));
    // a $ after a non-ASCII identifier character is part of the identifier
    query = "a§$x$".toCharArray();
    assertEquals(2, Parser.parseDollarQuotes(query, 2));
    // an unpaired surrogate is sent as ?, an operator character
    query = "\ud800$x$a$x$".toCharArray();
    assertEquals(query.length - 1, Parser.parseDollarQuotes(query, 1));
    query = "$a\ud800$x$a\ud800$".toCharArray();
    assertEquals(0, Parser.parseDollarQuotes(query, 0));

    // placeholders and statement separators inside the dollar quote are part of the value
    List<NativeQuery> qry = Parser.parseJdbcSql(
        "SELECT $§$ ? ; $§$, ?", true, true, true, false, false);
    assertEquals(1, qry.size());
    assertEquals("SELECT $§$ ? ; $§$, $1", qry.get(0).nativeSql);
    assertEquals(1, qry.get(0).bindPositions.length);

    // escapes inside the dollar quote are part of the value
    assertEquals("SELECT $§${d '2001-01-01'}$§$",
        Parser.replaceProcessing("SELECT $§${d '2001-01-01'}$§$", true, false));
  }

  /**
   * A negative argument of {fn right} or {fn timestampdiff} must not produce a {@code --} comment.
   */
  @Test
  public void escapedFunctionWithNegativeArgument() throws SQLException {
    String sql = Parser.replaceProcessing("{fn right('abc',-1)}", true, false);
    assertEquals("substring('abc' from (length('abc')+1-(-1)))", sql);
    assertFalse(sql, sql.contains("--"));

    sql = Parser.replaceProcessing("{fn timestampdiff(SQL_TSI_DAY,-1,x)}", true, false);
    assertEquals("extract( day from ((x)-(-1)))", sql);
    assertFalse(sql, sql.contains("--"));

    // the first argument may end with a minus as well
    sql = Parser.replaceProcessing("{fn timestampdiff(SQL_TSI_DAY,y,x-)}", true, false);
    assertFalse(sql, sql.contains("--"));
  }

  /**
   * A parse is valid for the other value of standard_conforming_strings only when no backslash can
   * move the end of a string literal around a placeholder.
   */
  @Test
  public void nativeQueryIsParsedForStandardConformingStrings() throws SQLException {
    NativeQuery on = Parser.parseJdbcSql("SELECT ?, '\\', ?", true, true, true, false, false).get(0);
    assertEquals("SELECT $1, '\\', $2", on.nativeSql);
    assertTrue(on.isParsedFor(true));
    assertFalse(on.isParsedFor(false));

    NativeQuery off = Parser.parseJdbcSql("SELECT ?, '\\''", false, true, true, false, false).get(0);
    assertEquals(1, off.bindPositions.length);
    assertTrue(off.isParsedFor(false));
    assertFalse(off.isParsedFor(true));

    NativeQuery noBackslash = Parser.parseJdbcSql("SELECT ?, ''", true, true, true, false, false).get(0);
    assertTrue(noBackslash.isParsedFor(true));
    assertTrue(noBackslash.isParsedFor(false));

    NativeQuery noBinds = Parser.parseJdbcSql("SELECT '\\'", true, true, true, false, false).get(0);
    assertTrue(noBinds.isParsedFor(true));
    assertTrue(noBinds.isParsedFor(false));

    NativeQuery legacy = new NativeQuery("SELECT $1, '\\'", new int[]{7}, false, SqlCommand.BLANK);
    assertTrue(legacy.isParsedFor(true));
    assertFalse(legacy.isParsedFor(false));
  }

  @Test
  @SuppressWarnings("deprecation")
  public void legacyEscapedFunctionWithNegativeArgument() throws SQLException {
    String sql = EscapedFunctions.sqlright(Arrays.asList("'abc'", "-1"));
    assertEquals("substring('abc' from (length('abc')+1-(-1)))", sql);
    assertFalse(sql, sql.contains("--"));

    sql = EscapedFunctions.sqltimestampdiff(Arrays.asList("SQL_TSI_DAY", "-1", "x"));
    assertEquals("extract( day from ((x)-(-1)))", sql);
    assertFalse(sql, sql.contains("--"));
  }
}
