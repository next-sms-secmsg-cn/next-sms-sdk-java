# next-sms Java SDK

JDK **17+**，独立 Maven 项目。HTTP使用 **OkHttp 5.5.1**，JSON使用Gson 2.13.2；无Spring依赖。版本依据：[Maven Central OkHttp JVM元数据](https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp-jvm/maven-metadata.xml)，核对日期2026-11-19。

仓库：[next-sms-secmsg-cn/next-sms-sdk-java](https://github.com/next-sms-secmsg-cn/next-sms-sdk-java)。许可证：[Apache-2.0](LICENSE)。

## 构建与安装

```sh
java -version  # 必须17或更高
mvn clean verify
mvn install
```

产物：`target/next-sms-sdk-java-1.0.0.jar`、源码jar和Javadoc jar。普通jar不内嵌依赖，通过Maven消费；发布到 Maven Central 后下游即可解析传递依赖。

```xml
<dependency>
  <groupId>cn.secmsg</groupId>
  <artifactId>next-sms-sdk-java</artifactId>
  <version>1.0.0</version>
</dependency>
```

Maven 坐标为 `cn.secmsg:next-sms-sdk-java:1.0.0`；Java 包名统一为 `cn.secmsg`。试用过未发布版本的调用方需将旧包名 import 改为 `cn.secmsg`。

中央库发布使用显式 `central` profile，普通 `mvn clean verify` 不签名、不上传。发布准备、Token、GPG 和首次上线步骤见 [Maven Central 发布手册](docs/PUBLISHING.md)。

## 同步发送

```java
import cn.secmsg.*;
import com.google.gson.JsonObject;

try (var client = new NextSmsClient(System.getenv("NEXT_SMS_SP_ID"), System.getenv("NEXT_SMS_SP_KEY"))) {
    Sms sms = new Sms("【测试签名】测试消息", System.getenv("NEXT_SMS_MOBILE"),
            "001", "your-business-message-id", "your-batch-id", null);
    JsonObject response = client.send(sms);
    String msgid = response.get("msgid").getAsString();
    // 保存业务流水；不要打印短信正文和完整号码。status=0仅表示受理。
}
```

服务中应按账号复用一个长生命周期客户端，不要每条消息new/close。上述try-with-resources仅演示脚本生命周期。`Sms`参数顺序为content、mobile、extCode、msgid、sId、sendTime；sendTime为毫秒字符串或null。

## 接口

| 方法 | 对应API |
| --- | --- |
| send / sendAsync | 单内容多号码发送 |
| secureSend / secureSendAsync | AES加密发送 |
| getReport() / getReport(maxSize) | 主动拉取状态报告，默认500 |
| getUpstream() / getUpstream(maxSize) | 主动拉取上行，默认500 |
| getBalance() | 余额 |
| getDailyStats("20261118") | 账号日统计 |
| getSignDailyStats("20261118") | 签名日统计 |

返回Gson `JsonObject` 保留协议字段及未知扩展字段。通过 `response.get("result").getAsLong()` 读余额，`getAsJsonArray("result")` 读报告；日统计无数据可能null。禁止把消息ID转double。

## HTTP调优与异步发送

默认：连接超时5秒、读超时30秒、完整调用超时30秒；最多32个在途请求（同步和异步合计）、16个空闲连接、空闲保留5分钟。OkHttp协商HTTP/2，服务端不支持则使用HTTP/1.1连接复用。并发上限按请求数，不是手机号数或计费分片TPS。连接池大小也不等于并发上限。

```java
var options = new HttpOptions(
    java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(20),
    java.time.Duration.ofSeconds(30), 32, 16, java.time.Duration.ofMinutes(5),
    null, okhttp3.Authenticator.NONE);
var client = new NextSmsClient(spId, spKey, "https://nsms-api.wxo2o.cn", options);
var future = client.sendAsync(new Sms(content, mobiles));
future.whenComplete((response, error) -> {
    // 将处理转交业务执行器；这里不要执行慢数据库事务阻塞OkHttp回调线程。
});
// future.cancel(true)取消底层HTTP调用，但不保证服务端尚未受理。
// 所有future完成后才能client.close()。
```

满额立即返回 `SmsException.Kind.BUSY`，此时没有发送该次HTTP请求，不在SDK内部无限排队。业务使用有界队列/生产速率控制；不应一次创建百万future。可运行 `src/test/java/.../NextSmsClientTest.java` 的本地并发测试观察连接复用及串并行耗时。

关闭自动网络重试、HTTP重定向及跨协议重定向。额外拦截OkHttp对 `503 + Retry-After: 0` 的自动重发并直接返回HTTP错误，已有回归测试。超时不等于未发送成功，不能无条件补发。`close()`会取消在途请求并回收客户端自有资源；应用优雅停机先停接新请求、等待future再关闭。

## JDK与显式代理

默认 `options.proxy=null`，沿用客户端创建时的JDK默认 `ProxySelector`；不覆盖系统代理选择。启动参数示例：

```sh
java -Dhttps.proxyHost=proxy.example.internal -Dhttps.proxyPort=3128 \
  '-Dhttp.nonProxyHosts=localhost|127.*|192.168.*' -jar your-app.jar
# HTTP接口另用 -Dhttp.proxyHost / -Dhttp.proxyPort
# SOCKS代理：-DsocksProxyHost=proxy.example.internal -DsocksProxyPort=1080
```

只有适用的目标协议才读取对应参数。JDK不自动把HTTP_PROXY/HTTPS_PROXY环境变量当成这些JVM参数。自定义系统ProxySelector应在SDK客户端构造前设置。

显式HTTP代理（覆盖系统ProxySelector）：

```java
var proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP,
        new java.net.InetSocketAddress("proxy.example.internal", 3128));
var auth = HttpOptions.basicProxyAuthentication(
        System.getenv("SMS_PROXY_USER"), System.getenv("SMS_PROXY_PASSWORD"));
var options = HttpOptions.defaults().withProxy(proxy, auth);
var client = new NextSmsClient(spId, spKey, NextSmsClient.DEFAULT_BASE_URL, options);
```

无代理认证则传 `okhttp3.Authenticator.NONE`。凭据只用于HTTP代理 `Proxy-Authorization`，短信服务的 `Authorization`仍是HMAC。认证失败不会循环尝试。Basic代理认证只用在受信网络/受信代理上。

显式SOCKS：`new Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(host, port))`，搭配 `Authenticator.NONE`。SOCKS认证由JDK网络栈负责，不使用HTTP的basicProxyAuthentication。强制直连用 `withProxy(Proxy.NO_PROXY, Authenticator.NONE)`。

## 状态与上行回调

```java
// 从HTTP框架读取原始body字节，限制大小后交给SDK；不要先解析再序列化。
JsonObject callback = client.parseCallback(rawBody, authorizationHeader);
for (var value : callback.getAsJsonArray("result")) {
    String phone = client.decodePhone(value.getAsJsonObject());
    // 落库并做业务去重。
}
// 全部持久化成功后返回HTTP200、Content-Type application/json：
String responseBody = NextSmsClient.callbackAck();
```

`parseCallback`同时适用于状态报告、上行及其加密号码变体；不自动确认成功、不修改原数据。`decodePhone`只在encrypted=1时解密，并检查spId。

## 错误处理与安全

- `SmsException.Kind.API`：`getCode()`获取平台状态，`getResponse()`显式获取平台响应。
- HTTP：`Kind.HTTP`及HTTP状态；PROTOCOL：缺少status、非法JSON/UTF-8或超过8MiB；SIGNATURE：验签失败/过期；BUSY：在途请求满；CLOSED：已关闭。
- 网络错误为 `IOException`，异步错误从future中读取根因；本地参数错误为 `IllegalArgumentException`/日期格式异常。
- SDK不记录业务正文/密钥。仅从环境变量或你的密钥管理器注入配置，不提交真实值。

详细接口、服务端业务限制及回调去重语义见 [docs/API.md](docs/API.md)。不包含旧版模板SDK，也不自动拆批或补发。
