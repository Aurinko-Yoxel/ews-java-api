/*
 * The MIT License
 * Copyright (c) 2012 Microsoft Corporation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package microsoft.exchange.webservices.data.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;

import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/** Exercises the real HTTP retry chain without opening a network connection. */
@RunWith(Parameterized.class)
public class ExchangeServiceHttpRetryTest {

  @Parameterized.Parameters(name = "pooled={0}")
  public static Collection<Object[]> clients() {
    return Arrays.asList(new Object[][] {{false}, {true}});
  }

  private final boolean pooled;
  private TestExchangeService service;
  private CloseableHttpClient client;

  public ExchangeServiceHttpRetryTest(boolean pooled) {
    this.pooled = pooled;
  }

  @Before
  public void setUp() throws Exception {
    service = new TestExchangeService();
    client = service.client(pooled);
  }

  @After
  public void tearDown() {
    service.close();
  }

  @Test
  public void defaultsToThreeRetries() throws Exception {
    assertEquals(3, service.getHttpRequestRetryCount());
    assertAttempts(4);
  }

  @Test
  public void limitsAlreadyCreatedClientsToTwoRetries() throws Exception {
    service.setHttpRequestRetryCount(2);

    assertEquals(2, service.getHttpRequestRetryCount());
    assertSame(client, service.client(pooled));
    assertAttempts(3);
  }

  @Test
  public void zeroDisablesRetries() throws Exception {
    service.setHttpRequestRetryCount(0);

    assertAttempts(1);
  }

  @Test
  public void subsequentChangesApplyWithoutRebuildingTheClient() throws Exception {
    service.setHttpRequestRetryCount(0);
    assertAttempts(1);

    service.socketFactory.attempts = 0;
    service.setHttpRequestRetryCount(2);
    assertSame(client, service.client(pooled));
    assertAttempts(3);
  }

  @Test
  public void rejectsNegativeRetryCountsWithoutChangingThePolicy() throws Exception {
    try {
      service.setHttpRequestRetryCount(-1);
      fail("Expected a negative retry count to be rejected");
    } catch (IllegalArgumentException expected) {
      assertEquals(3, service.getHttpRequestRetryCount());
    }
    assertAttempts(4);
  }

  @Test
  public void doesNotRetryInterruptedIoFailures() throws Exception {
    service.setHttpRequestRetryCount(2);
    service.socketFactory.failure = new InterruptedIOException("Read timed out");

    assertAttempts(1);
  }

  @Test
  public void settingBeforeLazyClientCreationIsRespected() throws Exception {
    try (TestExchangeService freshService = new TestExchangeService()) {
      freshService.setHttpRequestRetryCount(2);
      executeFailure(freshService.client(pooled), freshService.socketFactory.failure);
      assertEquals(3, freshService.socketFactory.attempts);
    }
  }

  private void assertAttempts(int expected) throws Exception {
    executeFailure(client, service.socketFactory.failure);
    assertEquals(expected, service.socketFactory.attempts);
  }

  private static void executeFailure(CloseableHttpClient client, IOException failure) throws Exception {
    HttpPost request = new HttpPost("http://127.0.0.1/EWS/Exchange.asmx");
    request.setEntity(new StringEntity("<soap-request/>"));
    try {
      client.execute(request).close();
      fail("Expected the simulated connection failure");
    } catch (IOException expected) {
      assertSame(failure, expected);
    } finally {
      request.releaseConnection();
    }
  }

  private static class TestExchangeService extends ExchangeService {
    private FailingSocketFactory socketFactory;

    @Override
    protected Registry<ConnectionSocketFactory> createConnectionSocketFactoryRegistry() {
      socketFactory = new FailingSocketFactory();
      return RegistryBuilder.<ConnectionSocketFactory>create()
          .register("http", socketFactory)
          .build();
    }

    private CloseableHttpClient client(boolean pooled) throws Exception {
      if (pooled) {
        if (httpPoolingClient == null) {
          prepareHttpPoolingWebRequestForUrl(new URI("http://127.0.0.1/EWS/Exchange.asmx"), false, false);
        }
        return httpPoolingClient;
      }
      return httpClient;
    }
  }

  private static class FailingSocketFactory extends PlainConnectionSocketFactory {
    private int attempts;
    private IOException failure = new SocketException("Connection reset");

    @Override
    public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host,
        InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context)
        throws IOException {
      attempts++;
      throw failure;
    }
  }
}
