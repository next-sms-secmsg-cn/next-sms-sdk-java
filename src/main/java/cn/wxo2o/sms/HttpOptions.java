package cn.wxo2o.sms;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import java.net.Proxy;
import java.time.Duration;

public record HttpOptions(Duration connectTimeout, Duration readTimeout, Duration callTimeout,
                          int maxConcurrentRequests, int maxIdleConnections, Duration keepAlive,
                          Proxy proxy, Authenticator proxyAuthenticator) {
    public HttpOptions {
        if (connectTimeout == null || readTimeout == null || callTimeout == null || keepAlive == null
                || connectTimeout.toMillis() < 1 || readTimeout.toMillis() < 1
                || callTimeout.toMillis() < 1 || keepAlive.toMillis() < 1
                || maxConcurrentRequests < 1 || maxIdleConnections < 0) {
            throw new IllegalArgumentException("timeouts and concurrency must be positive; idle connections >= 0");
        }
        if (proxyAuthenticator == null) throw new IllegalArgumentException("proxyAuthenticator is required");
    }

    public static HttpOptions defaults() {
        return new HttpOptions(Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(30),
                32, 16, Duration.ofMinutes(5), null, Authenticator.NONE);
    }

    public HttpOptions withProxy(Proxy proxy, Authenticator authenticator) {
        return new HttpOptions(connectTimeout, readTimeout, callTimeout, maxConcurrentRequests,
                maxIdleConnections, keepAlive, proxy, authenticator);
    }

    public static Authenticator basicProxyAuthentication(String username, String password) {
        String credential = Credentials.basic(username, password);
        return (route, response) -> {
            // A failed credential must not produce an unbounded proxy authentication loop.
            if (response.request().header("Proxy-Authorization") != null) return null;
            return response.request().newBuilder().header("Proxy-Authorization", credential).build();
        };
    }
}
