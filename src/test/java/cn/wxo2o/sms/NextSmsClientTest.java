package cn.wxo2o.sms;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import okhttp3.Authenticator;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NextSmsClientTest {
    static final String KEY = "sdk-test-key";
    static final byte[] BODY = "{\"content\":\"测试<&\",\"mobile\":\"13800000000\"}".getBytes(StandardCharsets.UTF_8);
    static final String AUTH = "HMAC-SHA256 1700000000000, Wr11HDfrj7fb5zY2bpMu6ayZEk65v4L4bOJPFPFeJx0=";
    record Received(String path, byte[] body, String authorization, int port, String proxyAuthorization) { }
    final List<Received> received = new CopyOnWriteArrayList<>();
    HttpServer server;
    ExecutorService executor;
    String url;
    NextSmsClient client;
    volatile int code = 200;
    volatile String reply = "{\"status\":0,\"msgid\":\"-5096302560201831814\",\"result\":[]}";
    volatile int delayMs;
    volatile boolean proxyAuth;
    volatile boolean retryAfterZero;
    final AtomicInteger active = new AtomicInteger();
    final AtomicInteger peak = new AtomicInteger();
    CountDownLatch entered;
    CountDownLatch release;

    @BeforeEach void setup() throws IOException {
        executor = Executors.newCachedThreadPool();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            var request = new Received(exchange.getRequestURI().toString(), exchange.getRequestBody().readAllBytes(),
                    exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRemoteAddress().getPort(),
                    exchange.getRequestHeaders().getFirst("Proxy-Authorization"));
            received.add(request);
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                if (entered != null) entered.countDown();
                if (release != null) release.await(5, TimeUnit.SECONDS);
                if (delayMs != 0) Thread.sleep(delayMs);
                int status = code;
                if (proxyAuth && request.proxyAuthorization() == null) {
                    status = 407;
                    exchange.getResponseHeaders().add("Proxy-Authenticate", "Basic realm=local-test");
                }
                if (status == 302) exchange.getResponseHeaders().add("Location", url + "/unexpected");
                if (retryAfterZero) exchange.getResponseHeaders().add("Retry-After", "0");
                byte[] body = reply.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet(); exchange.close();
            }
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new NextSmsClient("test_sp", KEY, url, HttpOptions.defaults().withProxy(Proxy.NO_PROXY, Authenticator.NONE));
    }

    @AfterEach void teardown() {
        if (release != null) release.countDown();
        client.close(); server.stop(0); executor.shutdownNow();
    }

    @Test void fetchDecodesQueuedJsonStrings() throws Exception {
        JsonObject record = new JsonObject();
        record.addProperty("msgId", "-5096302560201831814");
        record.addProperty("phone", 13900000000L);
        record.addProperty("status", "DELIVRD");
        JsonArray records = new JsonArray();
        records.add(record.toString()); records.add(record);
        JsonObject response = new JsonObject();
        response.addProperty("status", 0); response.add("result", records);
        reply = response.toString();
        record.addProperty("msgid", "-5096302560201831814");
        record.addProperty("phone", "13900000000");
        for (JsonObject result : List.of(client.getReport(), client.getUpstream())) {
            assertEquals(record, result.getAsJsonArray("result").get(0));
            assertEquals(record, result.getAsJsonArray("result").get(1));
        }
        for (String bad : List.of("{", "[]", "null", "1")) {
            records = new JsonArray(); records.add(bad); response.add("result", records);
            reply = response.toString();
            assertEquals(SmsException.Kind.PROTOCOL, assertThrows(SmsException.class, () -> client.getReport()).getKind());
        }
    }

    @Test void sharedCryptoVectors() {
        assertEquals(AUTH.replace(", ", ","), Security.authorization(BODY, KEY, 1700000000000L));
        assertEquals("TwEgr4bCo7P7lOg0PGb+hA==", Security.encrypt("测试<&".getBytes(StandardCharsets.UTF_8), KEY));
        assertEquals("13921350591", new String(Security.decrypt("f8/LCvVIYGXCWXyZT3NCAQ==", "Z345_#$"), StandardCharsets.UTF_8));
        for (String key : List.of("short", "1234567890123456", "1234567890123456extra", "测试密钥123456789012")) {
            assertArrayEquals(BODY, Security.decrypt(Security.encrypt(BODY, key), key));
        }
        assertThrows(IllegalArgumentException.class, () -> Security.decrypt("bad-base64", KEY));
        assertThrows(IllegalArgumentException.class, () -> Security.decrypt("", KEY));
    }

    @Test void signatureTamperAndWindow() {
        assertTrue(Security.verify(BODY, AUTH.replace(", ", ","), KEY, 1700000000000L));
        assertTrue(Security.verify(BODY, AUTH, KEY, 1700001800000L));
        assertTrue(Security.verify(BODY, AUTH, KEY, 1699998200000L));
        assertFalse(Security.verify(BODY, AUTH, KEY, 1700001800001L));
        assertFalse(Security.verify(BODY, AUTH, KEY, 1699998199999L));
        assertFalse(Security.verify(new byte[0], AUTH, KEY, 1700000000000L));
        assertFalse(Security.verify(BODY, AUTH, "other", 1700000000000L));
        assertFalse(Security.verify(BODY, "HMAC-SHA256 9999999999999999999, " + AUTH.split(", ")[1], KEY, 1700000000000L));
    }

    @Test void sevenEndpointsExactBytesAndConnectionReuse() throws Exception {
        Sms sms = new Sms("测试<&", "13800000000", "001", "m".repeat(128), "s".repeat(128), "1800000000000");
        assertEquals("-5096302560201831814", client.send(sms).get("msgid").getAsString());
        client.secureSend(sms); client.getReport(); client.getUpstream(1000);
        client.getBalance(); client.getDailyStats("20261118"); client.getSignDailyStats("20261118");
        assertEquals(List.of("send", "secureSend", "getReport", "getUpstream", "getBalance", "getDailyStats", "getSignDailyStats"),
                received.stream().map(r -> r.path().split("/")[2]).toList());
        for (Received r : received) {
            assertEquals(2, r.authorization().split(" ").length);
            assertEquals(2, r.authorization().split(" ")[1].split(",").length);
            assertTrue(r.path().endsWith("/test_sp"));
            assertTrue(Security.verify(r.body(), r.authorization(), KEY, System.currentTimeMillis()));
            assertNull(r.proxyAuthorization());
        }
        assertEquals(sms.json(), JsonParser.parseString(new String(received.get(0).body(), StandardCharsets.UTF_8)));
        String cipher = JsonParser.parseString(new String(received.get(1).body(), StandardCharsets.UTF_8)).getAsJsonObject().get("content").getAsString();
        assertEquals(sms.json(), JsonParser.parseString(new String(Security.decrypt(cipher, KEY), StandardCharsets.UTF_8)));
        assertEquals(0, received.get(4).body().length);
        assertEquals(1, received.stream().map(Received::port).distinct().count());
    }

    @Test void callbackAndPhone() throws Exception {
        String ciphertext = Security.encrypt("13800000000".getBytes(StandardCharsets.UTF_8), KEY);
        byte[] raw = ("{\n\"status\":0, \"result\":[{\"phone\":\"" + ciphertext + "\",\"encrypted\":1,\"spId\":\"test_sp\"}]}").getBytes(StandardCharsets.UTF_8);
        String header = Security.authorization(raw, KEY, System.currentTimeMillis());
        JsonObject item = client.parseCallback(raw, header).getAsJsonArray("result").get(0).getAsJsonObject();
        assertEquals("13800000000", client.decodePhone(item));
        assertEquals(1, item.get("encrypted").getAsInt());
        item.addProperty("spId", "other");
        assertThrows(SmsException.class, () -> client.decodePhone(item));
        assertThrows(SmsException.class, () -> client.parseCallback(new byte[0], header));
        assertEquals(0, JsonParser.parseString(NextSmsClient.callbackAck()).getAsJsonObject().get("status").getAsInt());
    }

    @Test void errorResponsesNeverRetryOrRedirect() throws Exception {
        for (int status : List.of(302, 503)) {
            code = status;
            SmsException error = assertThrows(SmsException.class, client::getBalance);
            assertEquals(SmsException.Kind.HTTP, error.getKind()); assertEquals(status, error.getCode());
        }
        code = 200; reply = "{\"status\":10016,\"msg\":\"private-message\"}";
        SmsException api = assertThrows(SmsException.class, client::getBalance);
        assertEquals(SmsException.Kind.API, api.getKind()); assertEquals(10016, api.getCode());
        assertFalse(api.getMessage().contains("private-message"));
        for (String bad : List.of("{}", "[]", "<html>bad</html>", "{\"status\":\"0\"}", "{\"status\":false}", "{\"status\":0}{}")) {
            reply = bad;
            assertEquals(SmsException.Kind.PROTOCOL, assertThrows(SmsException.class, client::getBalance).getKind());
        }
        assertEquals(9, received.size());
    }

    @Test void asyncBoundCancellationAndPermitRecovery() throws Exception {
        var defaults = HttpOptions.defaults();
        var options = new HttpOptions(defaults.connectTimeout(), defaults.readTimeout(), defaults.callTimeout(),
                1, 1, defaults.keepAlive(), Proxy.NO_PROXY, Authenticator.NONE);
        entered = new CountDownLatch(1); release = new CountDownLatch(1);
        try (NextSmsClient limited = new NextSmsClient("test_sp", KEY, url, options)) {
            CompletableFuture<JsonObject> first = limited.sendAsync(new Sms("test", "13800000000"));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            ExecutionException busy = assertThrows(ExecutionException.class,
                    () -> limited.sendAsync(new Sms("test", "13800000000")).get(1, TimeUnit.SECONDS));
            assertEquals(SmsException.Kind.BUSY, ((SmsException) busy.getCause()).getKind());
            assertTrue(first.cancel(true)); release.countDown();
            boolean recovered = false;
            for (int i = 0; i < 100; i++) {
                try { limited.getBalance(); recovered = true; break; }
                catch (SmsException e) { if (e.getKind() != SmsException.Kind.BUSY) throw e; Thread.sleep(10); }
            }
            assertTrue(recovered);
        }
    }

    @Test void secureAsyncAndAsyncFailure() throws Exception {
        Sms sms = new Sms("test", "13800000000");
        client.secureSendAsync(sms).get(3, TimeUnit.SECONDS);
        assertEquals("/sms/secureSend/test_sp", received.get(0).path());
        reply = "{\"status\":10016}";
        ExecutionException error = assertThrows(ExecutionException.class,
                () -> client.sendAsync(sms).get(3, TimeUnit.SECONDS));
        assertEquals(SmsException.Kind.API, ((SmsException) error.getCause()).getKind());
        reply = "{\"status\":0}";
        assertEquals(0, client.sendAsync(sms).get(3, TimeUnit.SECONDS).get("status").getAsInt());
    }

    @Test void timeoutAndSizeLimit() throws Exception {
        delayMs = 300;
        var options = new HttpOptions(Duration.ofSeconds(1), Duration.ofMillis(50), Duration.ofMillis(100),
                1, 1, Duration.ofMinutes(1), Proxy.NO_PROXY, Authenticator.NONE);
        try (NextSmsClient limited = new NextSmsClient("test_sp", KEY, url, options)) {
            assertThrows(IOException.class, limited::getBalance);
        }
        delayMs = 0; reply = " ".repeat(8 * 1024 * 1024 + 1);
        assertEquals(SmsException.Kind.PROTOCOL, assertThrows(SmsException.class, client::getBalance).getKind());
    }

    @Test void explicitAuthenticatedHttpProxy() throws Exception {
        proxyAuth = true;
        Proxy proxy = new Proxy(Proxy.Type.HTTP, server.getAddress());
        var options = HttpOptions.defaults().withProxy(proxy, HttpOptions.basicProxyAuthentication("test-user", "test-password"));
        try (NextSmsClient proxied = new NextSmsClient("test_sp", KEY, "http://sms.invalid", options)) {
            proxied.getBalance();
        }
        assertEquals(2, received.size());
        assertEquals("http://sms.invalid/sms/getBalance/test_sp", received.get(1).path());
        assertNotNull(received.get(1).proxyAuthorization());
        assertTrue(received.get(1).authorization().startsWith("HMAC-SHA256 "));
    }

    @Test void defaultJdkProxySelector() throws Exception {
        ProxySelector previous = ProxySelector.getDefault();
        AtomicInteger selected = new AtomicInteger();
        ProxySelector.setDefault(new ProxySelector() {
            public List<Proxy> select(URI uri) { selected.incrementAndGet(); return List.of(new Proxy(Proxy.Type.HTTP, server.getAddress())); }
            public void connectFailed(URI uri, SocketAddress address, IOException error) { }
        });
        try (NextSmsClient proxied = new NextSmsClient("test_sp", KEY, "http://sms.invalid", HttpOptions.defaults())) {
            proxied.getBalance(); assertTrue(selected.get() > 0);
            assertEquals("http://sms.invalid/sms/getBalance/test_sp", received.get(0).path());
        } finally { ProxySelector.setDefault(previous); }
    }

    @Test void directServerMustNotReceiveProxyCredentials() throws Exception {
        proxyAuth = true;
        var options = HttpOptions.defaults().withProxy(Proxy.NO_PROXY,
                HttpOptions.basicProxyAuthentication("proxy-user", "proxy-password"));
        try (var direct = new NextSmsClient("test_sp", KEY, url, options)) {
            assertThrows(IOException.class, direct::getBalance);
        }
        assertEquals(1, received.size());
        assertNull(received.get(0).proxyAuthorization());
    }

    @Test void serviceUnavailableRetryAfterMustNotResend() {
        code = 503; retryAfterZero = true;
        assertThrows(SmsException.class, () -> client.send(new Sms("test", "13800000000")));
        assertEquals(1, received.size());
    }

    @Test void asyncConcurrentRequestsAndLocalTiming() throws Exception {
        delayMs = 20;
        Sms sms = new Sms("test", "13800000000");
        long start = System.nanoTime();
        for (int i = 0; i < 32; i++) client.send(sms);
        long sequential = System.nanoTime() - start;
        start = System.nanoTime();
        for (int batch = 0; batch < 4; batch++) {
            List<CompletableFuture<JsonObject>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) futures.add(client.sendAsync(sms));
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        }
        long concurrent = System.nanoTime() - start;
        assertEquals(64, received.size()); assertTrue(peak.get() > 1); assertTrue(peak.get() <= 8);
        System.out.printf("LOCAL MOCK ONLY: 32 sync=%dms, 32 async(concurrency=8)=%dms, peak=%d%n",
                sequential / 1_000_000, concurrent / 1_000_000, peak.get());
    }

    @Test void inputValidationAndClose() {
        assertThrows(IllegalArgumentException.class, () -> client.getDailyStats("2026-11-18"));
        assertThrows(RuntimeException.class, () -> client.getDailyStats("20260230"));
        assertThrows(IllegalArgumentException.class, () -> new NextSmsClient("../test", KEY));
        client.close();
        assertEquals(SmsException.Kind.CLOSED, assertThrows(SmsException.class, client::getBalance).getKind());
        assertTrue(received.isEmpty());
    }
}
