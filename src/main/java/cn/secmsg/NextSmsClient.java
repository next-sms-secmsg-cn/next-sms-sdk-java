package cn.secmsg;

import com.google.gson.*;
import okhttp3.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NextSmsClient implements AutoCloseable {
    public static final String DEFAULT_BASE_URL = "https://nsms-api.wxo2o.cn";
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().setStrictness(Strictness.STRICT).create();
    private final String spId;
    private final String spKey;
    private final HttpUrl baseUrl;
    private final OkHttpClient http;
    private final Semaphore permits;
    private final AtomicBoolean closed = new AtomicBoolean();

    public NextSmsClient(String spId, String spKey) {
        this(spId, spKey, DEFAULT_BASE_URL, HttpOptions.defaults());
    }

    public NextSmsClient(String spId, String spKey, String baseUrl, HttpOptions options) {
        if (spId == null || !spId.matches("[A-Za-z0-9_-]+") || spKey == null || spKey.isEmpty()) {
            throw new IllegalArgumentException("valid spId and non-empty spKey are required");
        }
        HttpUrl url = HttpUrl.get(baseUrl);
        if (!url.username().isEmpty() || !url.password().isEmpty() || url.query() != null || url.fragment() != null) {
            throw new IllegalArgumentException("base URL must not contain credentials, query or fragment");
        }
        this.baseUrl = HttpUrl.get(baseUrl.replaceAll("/+$", "") + "/");
        this.spId = spId;
        this.spKey = spKey;
        this.permits = new Semaphore(options.maxConcurrentRequests());
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(options.maxConcurrentRequests());
        dispatcher.setMaxRequestsPerHost(options.maxConcurrentRequests());
        var builder = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(new ConnectionPool(options.maxIdleConnections(), options.keepAlive().toMillis(), TimeUnit.MILLISECONDS))
                .connectTimeout(options.connectTimeout()).readTimeout(options.readTimeout())
                .callTimeout(options.callTimeout()).retryOnConnectionFailure(false)
                .followRedirects(false).followSslRedirects(false)
                .addNetworkInterceptor(chain -> {
                    Response response = chain.proceed(chain.request());
                    // OkHttp retries 503 + Retry-After: 0 even with retryOnConnectionFailure(false).
                    if (response.code() == 503) {
                        response.close();
                        throw new SmsException(SmsException.Kind.HTTP, 503);
                    }
                    return response;
                })
                .proxyAuthenticator(options.proxyAuthenticator());
        if (options.proxy() != null) builder.proxy(options.proxy());
        http = builder.build();
    }

    public JsonObject send(Sms sms) throws IOException { return post("send", bytes(sms.json())); }
    public JsonObject secureSend(Sms sms) throws IOException { return post("secureSend", encrypted(sms)); }
    public CompletableFuture<JsonObject> sendAsync(Sms sms) { return postAsync("send", bytes(sms.json())); }
    public CompletableFuture<JsonObject> secureSendAsync(Sms sms) { return postAsync("secureSend", encrypted(sms)); }
    public JsonObject getReport() throws IOException { return getReport(500); }
    public JsonObject getReport(int maxSize) throws IOException { return fetch("getReport", maxSize); }
    public JsonObject getUpstream() throws IOException { return getUpstream(500); }
    public JsonObject getUpstream(int maxSize) throws IOException { return fetch("getUpstream", maxSize); }
    public JsonObject getBalance() throws IOException { return post("getBalance", new byte[0]); }
    public JsonObject getDailyStats(String date) throws IOException { return post("getDailyStats", date(date)); }
    public JsonObject getSignDailyStats(String date) throws IOException { return post("getSignDailyStats", date(date)); }

    private JsonObject fetch(String endpoint, int maxSize) throws IOException {
        JsonObject response = post(endpoint, field("maxSize", maxSize));
        if (!response.has("result") || !response.get("result").isJsonArray()) {
            throw new SmsException(SmsException.Kind.PROTOCOL, null);
        }
        JsonArray records = response.getAsJsonArray("result");
        for (int i = 0; i < records.size(); i++) {
            JsonElement record = records.get(i);
            // Queue-backed pull endpoints also return JSON-encoded strings.
            if (record.isJsonPrimitive() && record.getAsJsonPrimitive().isString()) {
                try {
                    record = JsonParser.parseString(record.getAsString());
                } catch (JsonParseException e) {
                    throw new SmsException(SmsException.Kind.PROTOCOL, null);
                }
            }
            if (!record.isJsonObject()) throw new SmsException(SmsException.Kind.PROTOCOL, null);
            JsonObject object = record.getAsJsonObject();
            if (!object.has("msgid") && object.has("msgId")) object.add("msgid", object.get("msgId"));
            JsonElement phone = object.get("phone");
            if (phone != null && phone.isJsonPrimitive() && phone.getAsJsonPrimitive().isNumber()) {
                object.addProperty("phone", phone.getAsString());
            }
            records.set(i, record);
        }
        return response;
    }

    private byte[] encrypted(Sms sms) {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("content", Security.encrypt(bytes(sms.json()), spKey));
        return bytes(wrapper);
    }

    private static byte[] bytes(JsonObject value) { return JSON.toJson(value).getBytes(StandardCharsets.UTF_8); }
    private static byte[] field(String name, int value) {
        JsonObject object = new JsonObject(); object.addProperty(name, value); return bytes(object);
    }
    private static byte[] date(String date) {
        if (date == null || !date.matches("[0-9]{8}")) throw new IllegalArgumentException("date must be yyyyMMdd");
        LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
        JsonObject object = new JsonObject(); object.addProperty("date", date); return bytes(object);
    }

    private Request request(String endpoint, byte[] body) {
        return new Request.Builder().url(Objects.requireNonNull(baseUrl.resolve("sms/" + endpoint + "/" + spId)))
                .header("Authorization", Security.authorization(body, spKey, System.currentTimeMillis()))
                .header("Accept", "application/json").post(RequestBody.create(body, JSON_TYPE)).build();
    }

    private void acquire() throws SmsException {
        if (closed.get()) throw new SmsException(SmsException.Kind.CLOSED, null);
        if (!permits.tryAcquire()) throw new SmsException(SmsException.Kind.BUSY, null);
        if (closed.get()) { permits.release(); throw new SmsException(SmsException.Kind.CLOSED, null); }
    }

    private JsonObject post(String endpoint, byte[] body) throws IOException {
        acquire();
        try (Response response = http.newCall(request(endpoint, body)).execute()) {
            return decode(response);
        } finally {
            permits.release();
        }
    }

    private CompletableFuture<JsonObject> postAsync(String endpoint, byte[] body) {
        try { acquire(); } catch (SmsException e) { return CompletableFuture.failedFuture(e); }
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        Call call = http.newCall(request(endpoint, body));
        future.whenComplete((value, error) -> { if (future.isCancelled()) call.cancel(); });
        try {
            call.enqueue(new Callback() {
                public void onFailure(Call call, IOException e) {
                    permits.release(); future.completeExceptionally(e);
                }
                public void onResponse(Call call, Response response) {
                    JsonObject value;
                    try (response) {
                        value = decode(response);
                    } catch (IOException | RuntimeException e) {
                        permits.release(); future.completeExceptionally(e); return;
                    }
                    permits.release(); future.complete(value);
                }
            });
        } catch (RuntimeException e) {
            permits.release(); future.completeExceptionally(e);
        }
        return future;
    }

    private static JsonObject decode(Response response) throws IOException {
        if (!response.isSuccessful()) throw new SmsException(SmsException.Kind.HTTP, response.code());
        if (response.body() == null) throw new SmsException(SmsException.Kind.PROTOCOL, null);
        return parse(response.body().byteStream().readNBytes(MAX_RESPONSE_BYTES + 1));
    }

    private static JsonObject parse(byte[] raw) throws SmsException {
        if (raw.length > MAX_RESPONSE_BYTES) throw new SmsException(SmsException.Kind.PROTOCOL, null);
        final JsonObject value;
        final int status;
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString();
            value = JSON.fromJson(text, JsonObject.class);
            if (value == null || !value.has("status") || !value.get("status").isJsonPrimitive()
                    || !value.getAsJsonPrimitive("status").isNumber()
                    || !value.get("status").getAsString().matches("-?[0-9]+")) {
                throw new SmsException(SmsException.Kind.PROTOCOL, null);
            }
            status = Integer.parseInt(value.get("status").getAsString());
        } catch (JsonParseException | IllegalStateException | NumberFormatException | CharacterCodingException e) {
            throw new SmsException(SmsException.Kind.PROTOCOL, null);
        }
        if (status != 0) throw new SmsException(SmsException.Kind.API, status, value);
        return value;
    }

    public JsonObject parseCallback(byte[] rawBody, String authorization) throws SmsException {
        if (!Security.verify(rawBody, authorization, spKey, System.currentTimeMillis())) {
            throw new SmsException(SmsException.Kind.SIGNATURE, null);
        }
        JsonObject value = parse(rawBody);
        if (!value.has("result") || !value.get("result").isJsonArray()) {
            throw new SmsException(SmsException.Kind.PROTOCOL, null);
        }
        return value;
    }

    public String decodePhone(JsonObject item) throws SmsException {
        if (item.has("encrypted") && item.get("encrypted").getAsInt() == 1) {
            if (!item.has("spId") || !spId.equals(item.get("spId").getAsString())) {
                throw new SmsException(SmsException.Kind.PROTOCOL, null);
            }
            return new String(Security.decrypt(item.get("phone").getAsString(), spKey), StandardCharsets.UTF_8);
        }
        return item.get("phone").getAsString();
    }

    public static String callbackAck() { return "{\"status\":0,\"msg\":\"\"}"; }

    /** Call only after awaiting all outstanding requests. */
    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            http.dispatcher().cancelAll();
            http.dispatcher().executorService().shutdown();
            http.connectionPool().evictAll();
        }
    }
}
