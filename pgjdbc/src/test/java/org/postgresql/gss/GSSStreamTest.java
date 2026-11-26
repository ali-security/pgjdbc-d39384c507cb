/*
 * Copyright (c) 2026, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.gss;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.MessageProp;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks that the GSS encrypted transport rejects replayed, reordered and unencrypted tokens and
 * that the outgoing stream never downgrades confidentiality.
 */
class GSSStreamTest {

  /**
   * Fake {@link GSSContext}: wrap/unwrap are identity transformations that report the configured
   * flags in the supplied {@link MessageProp}, the way a real mechanism does.
   */
  private static class FakeGss {
    boolean duplicate;
    boolean old;
    boolean unseq;
    boolean gap;
    boolean unwrapPrivacy = true;
    boolean wrapClearsPrivacy;
    final List<Boolean> wrapRequestedPrivacy = new ArrayList<>();

    GSSContext context() {
      return (GSSContext) Proxy.newProxyInstance(
          GSSContext.class.getClassLoader(),
          new Class<?>[] {GSSContext.class},
          (proxy, method, args) -> {
            String name = method.getName();
            if (args != null && args.length == 4 && args[0] instanceof byte[]) {
              byte[] in = (byte[]) args[0];
              int off = (Integer) args[1];
              int len = (Integer) args[2];
              MessageProp prop = (MessageProp) args[3];
              if ("wrap".equals(name)) {
                wrapRequestedPrivacy.add(prop.getPrivacy());
                if (wrapClearsPrivacy) {
                  prop.setPrivacy(false);
                }
                return Arrays.copyOfRange(in, off, off + len);
              }
              if ("unwrap".equals(name)) {
                prop.setPrivacy(unwrapPrivacy);
                prop.setSupplementaryStates(duplicate, old, unseq, gap, 0, null);
                return Arrays.copyOfRange(in, off, off + len);
              }
            }
            throw new UnsupportedOperationException(name);
          });
    }
  }

  private static byte[] frame(byte[] token) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(token.length >>> 24);
    out.write(token.length >>> 16);
    out.write(token.length >>> 8);
    out.write(token.length);
    out.write(token, 0, token.length);
    return out.toByteArray();
  }

  private static GSSInputStream inputStream(FakeGss gss, byte[] token, MessageProp prop) {
    return new GSSInputStream(new ByteArrayInputStream(frame(token)), gss.context(), prop);
  }

  @Test
  void readsEncryptedInSequenceToken() throws IOException {
    FakeGss gss = new FakeGss();
    byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
    GSSInputStream in = inputStream(gss, payload, new MessageProp(0, true));
    byte[] buf = new byte[payload.length];
    assertEquals(payload.length, in.read(buf, 0, buf.length));
    assertArrayEquals(payload, buf);
  }

  private static void assertOutOfSequenceRejected(FakeGss gss) {
    GSSInputStream in = inputStream(gss, new byte[] {1, 2, 3}, new MessageProp(0, true));
    IOException e = assertThrows(IOException.class, () -> in.read(new byte[3], 0, 3));
    assertEquals("Backend sent a GSS packet out of sequence.", e.getMessage());
  }

  @Test
  void rejectsReplayedToken() {
    FakeGss gss = new FakeGss();
    gss.duplicate = true;
    assertOutOfSequenceRejected(gss);
  }

  @Test
  void rejectsOldToken() {
    FakeGss gss = new FakeGss();
    gss.old = true;
    assertOutOfSequenceRejected(gss);
  }

  @Test
  void rejectsUnsequencedToken() {
    FakeGss gss = new FakeGss();
    gss.unseq = true;
    assertOutOfSequenceRejected(gss);
  }

  @Test
  void rejectsTokenAfterGap() {
    FakeGss gss = new FakeGss();
    gss.gap = true;
    assertOutOfSequenceRejected(gss);
  }

  @Test
  void rejectsUnencryptedToken() {
    FakeGss gss = new FakeGss();
    gss.unwrapPrivacy = false;
    GSSInputStream in = inputStream(gss, new byte[] {1, 2, 3}, new MessageProp(0, true));
    IOException e = assertThrows(IOException.class, () -> in.read(new byte[3], 0, 3));
    assertEquals("Backend sent a GSS packet that is not encrypted.", e.getMessage());
  }

  @Test
  void writeFailsWhenWrapWouldNotEncrypt() {
    FakeGss gss = new FakeGss();
    gss.wrapClearsPrivacy = true;
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    GSSOutputStream out =
        new GSSOutputStream(sink, gss.context(), new MessageProp(0, true), 16);
    IOException e = assertThrows(IOException.class, () -> {
      out.write(new byte[] {1, 2, 3});
      out.flush();
    });
    assertEquals("Outgoing GSS message would not use confidentiality.", e.getMessage());
    assertEquals(0, sink.size(), "no unencrypted token must be sent");
  }

  @Test
  void unencryptedIncomingTokenDoesNotDowngradeOutgoingWrap() throws IOException {
    FakeGss gss = new FakeGss();
    GSSContext context = gss.context();
    // PGStream shares one MessageProp between the input and the output stream
    MessageProp shared = new MessageProp(0, true);
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    GSSOutputStream out = new GSSOutputStream(sink, context, shared, 16);

    gss.unwrapPrivacy = false;
    GSSInputStream in =
        new GSSInputStream(new ByteArrayInputStream(frame(new byte[] {9})), context, shared);
    assertThrows(IOException.class, () -> in.read(new byte[1], 0, 1));
    // the backend-controlled unwrap result was written into the shared MessageProp
    assertFalse(shared.getPrivacy());

    out.write(new byte[] {1, 2, 3});
    out.flush();
    assertEquals(1, gss.wrapRequestedPrivacy.size());
    assertTrue(gss.wrapRequestedPrivacy.get(0), "outgoing wrap must still request privacy");
    assertArrayEquals(frame(new byte[] {1, 2, 3}), sink.toByteArray());
  }
}
