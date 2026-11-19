# 讯启平台短信服务 API 接入文档

GitHub 下载：[完整 API 文档](https://raw.githubusercontent.com/next-sms-secmsg-cn/next-sms-sdk-java/main/docs/API.md) · [Postman 请求集合](https://raw.githubusercontent.com/next-sms-secmsg-cn/next-sms-sdk-java/main/docs/next-sms.postman_collection.json) · [curl 测试脚本](https://raw.githubusercontent.com/next-sms-secmsg-cn/next-sms-sdk-java/main/docs/next-sms-curl-test.sh)。

# 1、概述

## 1.1 目的

提供短信发送平台 HTTP/HTTPS 接口的统一接入规范，便于客户完成接口开发与联调。

## 1.2 范围

适用于平台对外提供的短信发送、状态报告、上行消息、余额查询及日统计接口。

## 1.3 术语

| 术语    | 英文                                 | 中文             |
| ----- | ---------------------------------- | -------------- |
| HTTP  | Hypertext Transfer Protocol        | 超文本传输协议        |
| HTTPS | Hypertext Transfer Protocol Secure | 安全超文本传输协议      |
| JSON  | JavaScript Object Notation         | 一种轻量级的数据交换格式   |
| spId  | Service Provider Id                | 我方提供的发送账号的唯一标识 |

# 2、约束

+ 推荐使用 HTTPS，本文请求地址统一使用 HTTPS 根地址 `https://nsms-api.wxo2o.cn`。HTTP 接入需确认部署环境已开放对应入口，不应直接将生产地址降级为 HTTP。
+ 请求方法统一为 POST。
+ 有请求体时使用 JSON 格式、UTF-8 编码和 `Content-Type: application/json`；余额查询例外，使用空请求体。响应内容使用 JSON 格式。
+ 所有接口均使用预共享密钥进行鉴权。
+ JSON 示例为便于阅读采用缩进格式。实际发送时请生成单行 JSON，并对同一份请求体签名和发送；不得在签名后改变空格、转义或字段顺序。

# 3、鉴权认证

## 3.1 介绍

平台与客户共享接口密钥 `spKey`。客户发起请求时，对请求体与时间戳计算签名，并将签名放入 `Authorization` 请求头。平台使用同一密钥校验签名。状态报告及上行推送使用相同规则，由客户侧验签。

## 3.2 构造签名

### 3.2.1 构造 signature_origin 字符串

构造公式如下：

```text
signature_origin = body_content + timestamp
```

这里的 `+` 表示直接拼接，不包含加号、分隔符或额外空格。

构造参数说明如下：

```plain
- body_content：实际发送的单行 JSON 请求体；余额查询使用空字符串

- timestamp：当前 Unix 时间戳的毫秒值，以十进制字符串表示；与平台时间相差超过 30 分钟时，返回 status=100005
```

### 3.2.2 计算签名

将 `signature_origin` 和 `spKey` 分别按 UTF-8 编码，以 `spKey` 为密钥计算 HMAC-SHA256，再将原始摘要字节进行标准 Base64 编码，得到 `signature`。不要先转为十六进制字符串再做 Base64 编码。HMAC 是消息认证算法，不是请求体加密。

### 3.2.3 构造请求头

将生成的 `signature` 放入请求头，格式如下：

```text
Authorization: HMAC-SHA256 ${timestamp},${signature}
```

构造参数说明如下：

```plain
- timestamp：与计算签名时完全相同的时间戳；Java 可通过 System.currentTimeMillis() 获取

- signature：3.2.2中生成的最终签名

- HMAC-SHA256 与 timestamp 之间只有一个英文空格；逗号前后不要加空格
```

# 4、接口列表

## 4.1 短信发送接口（单内容多号码）

### 4.1.1 描述

支持将同一条短信内容发送给一个或多个号码。

### 4.1.2 请求基本信息

```plain
- 协议：推荐 HTTPS

- 请求方法：POST

- 数据传递结构：JSON

- 请求地址：

    -  https://nsms-api.wxo2o.cn/sms/send/{spId}

- 接口统一签名认证
```

### 4.1.3 请求参数

| 参数名称     | 类型     | 是否必须 | 最大长度      | 说明                                                                  |
| -------- | ------ | ---- | --------- | ------------------------------------------------------------------- |
| content  | string | 必须   | 2400      | 短信内容；当前服务端按 Java String.length() 校验，最大 2400 个 UTF-16 代码单元。签名及通道另有约束时仍需满足对应规则 |
| mobile   | string | 必须   | 由数量决定     | 手机号码，多个号码用英文逗号 `,` 分隔，单次最多 10000 个号码 |
| extCode  | string | 可选   | 12        | 扩展码，必须可解析为数字                                                        |
| msgid    | string | 可选   | 128        | 自定义本次提交的消息 ID；不传时由平台生成并返回。传入时平台按原值返回，用于业务关联，不代表幂等重试保证 |
| sId      | string | 可选   | 512        | 批次号，可用于客户侧按批次对短信分组；当前服务端最大长度为 512 |
| sendTime | string | 可选   | long 范围的十进制字符串 | 计划发送时间的 Unix 毫秒时间戳，最远不超过未来 30 天；不传或距离当前时间不超过 5 分钟时，按即时短信发送 |

### 4.1.4 请求示例

```json
{
    "content": "【讯启平台】您的验证码为123456，在10分钟内有效。",
    "mobile": "13800001111,8613955556666,+8613545556666",
    "extCode": "123456",
    "msgid": "8629637681836384963",
    "sId": "123456789abcdefg"
}
```

### 4.1.5 响应示例

```json
{
    "status": 0,
    "msgid": "8629637681836384963"
}
```

参数说明

+ status：状态码，详见 4.8.1。`0` 表示平台受理成功，不代表终端已收到短信。
+ msgid：本次提交的消息 ID；同一请求的多个号码可共用此 ID，不能仅按 msgid 对状态报告去重。


## 4.2 状态报告推送

### 4.2.1 描述

平台将短信下发的状态报告推送给客户。

### 4.2.2 推送方式

```plain
- POST

- Content-Type: application/json

- 请求头按照 3.2 构造签名，客户侧应验证签名

- 客户需提供可访问的推送 URL
```

### 4.2.3 推送参数

| 参数名称   | 类型         | 是否必须 | 说明           |
| ------ | ---------- | ---- | ------------ |
| status | int        | 必须   | 推送消息的外层状态，正常为 0 |
| result | JSON Array | 必须   | 状态反馈信息（数组） |

result 参数说明

| 参数名称     | 类型     | 是否必须        | 说明                       |
| -------- | ------ | ----------- | ------------------------ |
| phone    | string | 必须          | 手机号码                     |
| msgid    | string | 必须          | 与提交响应的msgid一致            |
| status   | string | 必须          | 短信状态，例如 DELIVRD，代码含义见 4.8.2 |
| fee      | int    | 必须          | 短信计费条数                   |
| sId      | string | 可选（提交时传才会有） | 批次号，可用于客户侧按照批次号对短信进行分组   |
| donetime | string | 必须          | 短信到达时间，格式：yyyyMMddHHmmss |

### 4.2.4 推送示例

```json
{
    "status": 0,
    "result": [
        {
            "phone": "13800001111",
            "msgid": "8629637681836384963",
            "status": "DELIVRD",
            "donetime": "20261118153922",
            "fee": 1,
            "sId": "123456789abcdefg"
        }
    ]
}
```

### 4.2.5 响应示例

```json
{
    "status": 0,
    "msg": ""
}
```

参数说明

+ status：状态码，0表示成功，非0表示失败

**客户侧完成验签并成功持久化后，返回 HTTP 200 和上述确认正文；处理失败时不要返回成功确认。重复推送仍可能发生，客户侧需实现去重。**

+ msg：错误信息

### 4.2.6 推送机制

+ 状态报告默认每次推送 1～500 条，具体数量可由平台配置。
+ 客户需返回正确格式的成功响应，否则触发重推机制。
+ 触发重推后，每隔 1 分钟重新推送；收到成功响应后停止。达到最多 5 次推送后停止，并记录失败日志。

## 4.3 状态报告主动获取

### 4.3.1 描述

客户可主动获取短信状态报告。状态报告最多缓存 48 小时，超过 48 小时未获取的数据将被丢弃。

### 4.3.2 请求基本信息

```plain
- 协议：推荐 HTTPS

- 请求方法：POST

- 数据传递结构：JSON

- 请求地址： https://nsms-api.wxo2o.cn/sms/getReport/{spId}

- 接口统一签名认证
```

### 4.3.3 请求参数

| 参数名称    | 类型  | 是否必须 | 说明                               |
| ------- | --- | ---- | -------------------------------- |
| maxSize | int | 可选   | 建议显式传入 10～1000；当前服务端未传时为 100。正数超出范围时，本地队列读取按 500 处理；不要传入 0、负数或 null |

### 4.3.4 请求示例

```json
{
    "maxSize": 100
}
```

### 4.3.5 响应示例

主动拉取与推送的外层结构相似，但当前服务端的 `result` 是 JSON 字符串数组，需要逐项再次解析。状态报告中的消息 ID 字段为 `msgId`，号码为 JSON 数值：

```json
{
    "status": 0,
    "result": [
        "{\"phone\":13800001111,\"msgId\":\"8629637681836384963\",\"status\":\"DELIVRD\",\"donetime\":\"20261118153922\",\"fee\":1,\"sId\":\"123456789abcdefg\"}"
    ]
}
```

本文附录的三个 SDK 会自动解析字符串元素，将 `msgId` 统一为 `msgid`、号码统一为字符串。自行接入 HTTP 时，应兼容字符串元素及已解析的对象元素。正常无数据时 `result` 为 `[]`，不表示后续不会再产生报告。主动拉取具有消费语义，获取后应及时持久化。

## 4.4 上行推送

### 4.4.1 描述

平台将终端回复的上行消息推送给客户。

### 4.4.2 推送方式

```plain
- POST

- Content-Type: application/json

- 请求头按照 3.2 构造签名，客户侧应验证签名

- 客户需提供可访问的推送 URL
```

### 4.4.3 推送参数

| 参数名称   | 类型         | 是否必须 | 说明           |
| ------ | ---------- | ---- | ------------ |
| status | int        | 必须   | 上行响应状态       |
| result | JSON Array | 必须   | 上行回复内容（数组） |

result 参数说明

| 参数名称        | 类型     | 是否必须        | 说明                       |
| ----------- | ------ | ----------- | ------------------------ |
| phone       | string | 必须          | 手机号码                     |
| extCode     | string | 必须          | 提交短信时指定的 extCode        |
| content     | string | 必须          | 上行内容                     |
| receivetime | string | 必须          | 上行接收时间，格式：yyyyMMddHHmmss |
| sId         | string | 可选（提交时传才会有） | 对应短信提交时的sId              |
| sign        | string | 可选          | 对应短信提交时的签名（无下发上行没有此字段）   |
| msgid       | string | 必须          | 对应短信提交时的msgid            |

### 4.4.4 推送示例

```json
{
    "status": 0,
    "result": [
        {
            "phone": "13800001111",
            "extCode": "682",
            "content": "上行回复内容",
            "receivetime": "20261118153922",
            "sId": "123456789abcdefg",
            "sign": "签名",
            "msgid": "8629637681836384963"
        }
    ]
}
```

### 4.4.5 响应示例

```json
{
    "status": 0,
    "msg": ""
}
```

参数说明

+ status：状态码，0表示成功，非0表示失败

**客户侧完成验签并成功持久化后，返回 HTTP 200 和上述确认正文；处理失败时不要返回成功确认。重复推送仍可能发生，客户侧需实现去重。**

+ msg：错误信息

### 4.4.6 推送机制

+ 上行消息默认每次推送 1～500 条，具体数量可由平台配置。
+ 客户需返回正确格式的成功响应，否则触发重推机制。
+ 触发重推后，每隔 1 分钟重新推送；收到成功响应后停止。达到最多 5 次推送后停止，并记录失败日志。

## 4.5 上行主动获取

### 4.5.1 描述

客户可主动获取上行消息。上行消息最多缓存 48 小时，超过 48 小时未获取的数据将被丢弃。

### 4.5.2 请求基本信息

```plain
- 协议：推荐 HTTPS

- 请求方法：POST

- 数据传递结构：JSON

- 请求地址： https://nsms-api.wxo2o.cn/sms/getUpstream/{spId}

- 接口统一签名认证
```

### 4.5.3 请求参数

| 参数名称    | 类型  | 是否必须 | 最大长度 | 说明                               |
| ------- | --- | ---- | ---- | -------------------------------- |
| maxSize | int | 可选   | 不适用 | 与 4.3.3 相同，建议显式传入 10～1000；当前服务端未传时为 100 |

### 4.5.4 请求示例

```json
{
    "maxSize": 100
}
```

### 4.5.5 响应示例

当前服务端的 `result` 为 JSON 字符串数组，需逐项再次解析；上行消息使用 `msgid` 字段，号码为 JSON 数值：

```json
{
    "status": 0,
    "result": [
        "{\"phone\":13800001111,\"extCode\":\"123456\",\"content\":\"回复内容\",\"receivetime\":\"20261118154022\",\"sId\":\"123456789abcdefg\",\"sign\":\"讯启平台\",\"msgid\":\"8629637681836384963\"}"
    ]
}
```

三个 SDK 会自动完成字符串元素解析与号码类型转换。无数据时 `result` 为 `[]`。拉取后应及时持久化，不能假定重新拉取会得到相同数据。

## 4.6 预付费账号余额查询

### 4.6.1 描述

预付费账号剩余余额查询。

余额不足提醒可在客户平台的账号设置中配置，具体入口以账号开通时提供的平台地址为准。

### 4.6.2 请求基本信息

```plain
- 协议：推荐 HTTPS

- 请求方法：POST

- 数据传递结构：JSON

- 请求地址： https://nsms-api.wxo2o.cn/sms/getBalance/{spId}

- 接口统一签名认证
```

### 4.6.3 请求参数

无。请求体必须为空，签名时 `body_content` 使用空字符串，而不是 `{}` 或字符串 `""`。

### 4.6.4 请求示例

向 `https://nsms-api.wxo2o.cn/sms/getBalance/{spId}` 发起带 `Authorization` 的 POST 请求，不发送请求体。签名原文仅为时间戳字符串。

### 4.6.5 响应示例

```json
{
    "status": 0,
    "result": 10000
}
```

`status` 含义见 4.8.1；`result` 是剩余短信额度，单位为条，不是金额。

## 4.7 获取发送账号日统计

### 4.7.1 描述

查询发送账号 spId 指定日期的短信发送统计。

### 4.7.2 请求基本信息

```plain
- 协议：推荐 HTTPS

- 请求方法：POST

- 数据传递结构：JSON

- 请求地址： https://nsms-api.wxo2o.cn/sms/getDailyStats/{spId}

- 接口统一签名认证
```

### 4.7.3 请求参数

| 参数名称 | 类型     | 是否必须 | 最大长度 | 说明                          |
| ---- | ------ | ---- | ---- | --------------------------- |
| date | string | 必须   | 8    | 日期格式：yyyyMMdd，例如： 20200101 |

### 4.7.4 请求示例

```json
{
    "date": "20200101"
}
```

### 4.7.5 响应示例

```json
{
    "status": 0,
    "result": {
        "spId": "apiSendUser01",
        "date": "20200101",
        "sendCount": 1000,
        "feeCount": 1500,
        "successCount": 1400,
        "failCount": 80,
        "exceptionCount": 0,
        "noRespCount": 20
    }
}
```

字段说明：`spId` 为发送账号，`date` 为 `yyyyMMdd` 格式字符串；`sendCount` 为提交数，`feeCount` 为拆分计费条数，`successCount`、`failCount`、`exceptionCount`、`noRespCount` 分别为成功、失败、异常、未反馈条数。无数据时返回 `{"status":0,"result":null}`。

提交数与拆分计费条数口径不同，例如一条长短信可能拆为多个计费分片，不应直接按两个字段是否相等判断数据异常。

## 4.8 返回错误码参考

### 4.8.1 提交或查询 status 代码表

| status 代码 | 代码含义                      |
| --------- | ------------------------- |
| 0         | 成功                        |
| 100001    | 鉴权失败                      |
| 100002    | 必选参数为空                    |
| 100003    | 参数格式错误                    |
| 100004    | 系统错误                      |
| 100005    | timestamp过期               |
| 1001      | 账号不能为空                    |
| 1002      | 账号不合法                     |
| 1003      | ip不能为空                    |
| 1004      | ip校验失败                    |
| 1005      | 密码不能为空                    |
| 1006      | 密码错误                      |
| 1007      | 手机号码不能为空或者手机号码位数不正确或者数量过多 |
| 1008      | 缺少手机参数                    |
| 1009      | 扩展必须为数字                   |
| 10010     | msgid长度限制                 |
| 10011     | 内容为空                      |
| 10012     | 内容含关键字                    |
| 10013     | 内容超过系统支持最大长度              |
| 10014     | 签名超长或为空                   |
| 10016     | 余额不足                      |
| 10017     | 账号已禁用                     |
| 10018     | 该用户此时间段禁止提交               |
| 10019     | 不允许主动获取和主动推送同时调用          |
| 10020     | 打包短信错误                    |
| 10021     | 批量短信数量超限                  |
| 10022     | 定时短信添加错误                  |
| 10023     | sId过长                     |
| 10024     | 定时短信时间异常                  |
| 10025     | 国际短信接口不支持国内号码             |
| 10026     | 不支持国际短信                   |

### 4.8.2 状态报告反馈错误代码表

> 仅针对当前短信发送平台返回的内部反馈代码释义，不在此表中的代表运营商返回的状态码。

| 代码                   | 含义             |
| -------------------- | -------------- |
| BLACK                | 黑名单            |
| NOWAY                | 未匹配通道          |
| CHECK                | 审核不通过          |
| SIGN                 | 签名错误，未报备       |
| KEYWORD              | 内容含有非法关键词      |
| LIMIT                | 超发限制           |
| PHONERR              | 无归属地           |
| SWITCH               | 通道切出           |
| DELIVRD              | 反馈成功           |
| TDBLACK              | 退订黑            |
| TSBLACK              | 投诉黑            |
| RDBLACK              | 红名单            |
| AICHECK              | 智能拦截，一般是发送账号有误 |
| INSUFFICIENT_BALANCE | 余额不足           |
| UNDELIV              | 反馈失败           |
| TESTER_DELIVRD       | 压测专用码          |
| ERRLMSG              | CMPP长短信异常      |

## 4.9 短信加密发送接口（按需使用）

### 4.9.1 描述

支持将单内容多号码发送请求加密后提交。

```plain
使用 AES/ECB/PKCS5Padding 对明文请求体加密，密钥由 spKey 按 4.9.4 的规则派生；密文再进行 Base64 编码。
```

普通发送接口已通过 HTTPS 保护传输。加密发送额外增加应用层加解密和 Base64 编码开销，仅在业务需要时使用，仍应通过 HTTPS 访问。AES 模式属于现有协议约定，不替代 TLS。

### 4.9.2 请求基本信息

```plain
- 协议：推荐 HTTPS

- 请求方法：POST

- 数据传递结构：JSON

- 请求地址：

    -  https://nsms-api.wxo2o.cn/sms/secureSend/{spId}

- 接口统一签名认证
```

### 4.9.3 请求参数

| 参数名称    | 类型     | 是否必须 | 最大长度 | 说明               |
| ------- | ------ | ---- | ---- | ---------------- |
| content | string | 必须   |      | 请求体加密后的Base64字符串 |

### 4.9.4 加密代码

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtil {
    // 保持服务端协议：按 Java 字符串长度截断或左侧补 a，再进行 UTF-8 编码。
    private static byte[] keyBytes(String key) {
        String value = key.length() >= 16
                ? key.substring(0, 16) : "a".repeat(16 - key.length()) + key;
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static String encrypt(String plaintext, String key)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes(key), "AES"));
        return Base64.getEncoder().encodeToString(
                cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    public static String decrypt(String ciphertext, String key)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes(key), "AES"));
        return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)),
                StandardCharsets.UTF_8);
    }
}
```

示例使用 JDK 17 标准库，不依赖 Hutool 或日志框架。使用平台下发的 `spKey`，不要自行更改密钥派生规则；HMAC 签名仍使用完整的原始 `spKey`，不是 AES 派生密钥。

### 4.9.5 请求明文示例

```json
{
    "content": "【讯启平台】您的验证码为123456，在10分钟内有效。",
    "mobile": "13800001111,8613955556666,+8613545556666",
    "extCode": "123456",
    "sId": "123456789abcdefg"
}
```

### 4.9.6 加密后的请求示例

先将 4.9.5 的完整明文 JSON 加密，再将密文放入外层 JSON 的 `content` 字段。签名使用最终发送的外层 JSON 字符串，不是明文 JSON，也不是单独的 Base64 密文。

以下密文使用演示密钥 `Z345_#$`，对 4.9.5 示例去除排版空白后的单行 JSON 加密生成；演示密钥不能用于生产。

```json
{
    "content": "egNcWgEjLiSfL7bYJdqpf2yKOCOopQ6AmQ8W4j4CoTQzuRlXdEw3FPSGpjVphixEeYh7LJkJpmP0OoaVI5kRXRTAT/Ny1a1Anzy2Ri6pgBpaYkzlHljPFBfgPNlGnDYSINebgUFr5wbgDvSGtcIoHdPoByb+srwaovZoose7q0Ms2pdn/kZOhym2hBNUta95EXfXDYKX4FjqFW2mENhFJEZUpsTSjfTpose6+ctt0NKocANGk14mk8qglHlyYebk"
}
```

### 4.9.7 响应示例

```json
{
    "status": 0,
    "msgid": "8629637681836384963"
}
```

## 4.10 状态报告推送-手机号码加密

### 4.10.1 描述

平台将短信下发的状态报告推送给客户，并对报告中的手机号码进行加密。

```plain
使用 4.9.4 的 AES 算法和密钥派生规则，仅对 phone 字段加密并进行 Base64 编码，其他字段保持原结构。
```

### 4.10.2 推送方式

```plain
- POST

- Content-Type: application/json

- 请求头按照 3.2 构造签名，客户侧应验证签名

- 客户需提供可访问的推送 URL
```

### 4.10.3 加密代码

加密及解密代码见 4.9.4；客户使用 decrypt 方法还原号码。

### 4.10.4 推送参数

| 参数名称   | 类型         | 是否必须 | 说明           |
| ------ | ---------- | ---- | ------------ |
| status | int        | 必须   | 推送消息的外层状态，正常为 0 |
| result | JSON Array | 必须   | 状态反馈信息（数组） |

result 参数说明

| 参数名称      | 类型      | 是否必须        | 说明                                      |
| --------- | ------- | ----------- | --------------------------------------- |
| phone     | string  | 必须          | 手机号码，AES加密                              |
| msgid     | string  | 必须          | 与提交响应的msgid一致                           |
| status    | string  | 必须          | 短信状态                                    |
| fee       | int     | 必须          | 短信计费条数                                  |
| sId       | string  | 可选（提交时传才会有） | 批次号，可用于客户侧按照批次号对短信进行分组                  |
| donetime  | string  | 必须          | 短信到达时间，格式：yyyyMMddHHmmss                |
| spId      | string  | 必须          | encrypted=1 时，用于选择解密密钥 spKey 的发送账号 |
| encrypted | Integer | 必须          | 1 表示手机号码是AES加密                          |

### 4.10.5 推送示例

演示账号 `test_spId_001` 的演示密钥为 `Z345_#$`，下列 phone 密文对应 `13800001111`。实际接入使用平台提供的账号和密钥。

```json
{
    "status": 0,
    "result": [
        {
            "phone": "qbIS7P3IYrlfYA4dI8xFFw==",
            "msgid": "8629637681836384963",
            "status": "DELIVRD",
            "donetime": "20261118153922",
            "fee": 1,
            "sId": "123456789abcdefg",
            "encrypted": 1,
            "spId": "test_spId_001"
        }
    ]
}
```

### 4.10.6 响应示例

```json
{
    "status": 0,
    "msg": ""
}
```

参数说明

+ status：状态码，0表示成功，非0表示失败

**客户侧完成验签并成功持久化后，返回 HTTP 200 和上述确认正文；处理失败时不要返回成功确认。重复推送仍可能发生，客户侧需实现去重。**

+ msg：错误信息

### 4.10.7 推送机制

推送机制见 4.2.6。

## 4.11 上行推送-手机号码加密

### 4.11.1 描述

平台将终端回复的上行消息推送给客户，并对上行消息中的手机号码进行加密。

```plain
使用 4.9.4 的 AES 算法和密钥派生规则，在上行推送时仅加密 phone 字段，其他字段保持原结构。
```

### 4.11.2 推送方式

```plain
- POST

- Content-Type: application/json

- 请求头按照 3.2 构造签名，客户侧应验证签名

- 客户需提供可访问的推送 URL
```

### 4.11.3 加密代码

加密及解密代码见 4.9.4；客户使用 decrypt 方法还原号码。

### 4.11.4 推送参数

| 参数名称   | 类型         | 是否必须 | 说明           |
| ------ | ---------- | ---- | ------------ |
| status | int        | 必须   | 上行响应状态       |
| result | JSON Array | 必须   | 上行回复内容（数组） |

result 参数说明

| 参数名称        | 类型      | 是否必须        | 说明                                      |
| ----------- | ------- | ----------- | --------------------------------------- |
| phone       | string  | 必须          | 手机号码，AES加密                              |
| extCode     | string  | 必须          | 提交短信时指定的 extCode                       |
| content     | string  | 必须          | 上行内容                                    |
| receivetime | string  | 必须          | 上行接收时间，格式：yyyyMMddHHmmss                |
| sId         | string  | 可选（提交时传才会有） | 对应短信提交时的sId                             |
| sign        | string  | 可选          | 对应短信提交时的签名（无下发上行没有此字段）                  |
| msgid       | string  | 必须          | 对应短信提交时的msgid                           |
| spId        | string  | 必须          | encrypted=1 时，用于选择解密密钥 spKey 的发送账号 |
| encrypted   | Integer | 必须          | 1 表示 phone 为 AES 密文；非 1 或无此字段时为明文号码   |

### 4.11.5 推送示例

phone 使用与 4.10.5 相同的演示号码和演示密钥。

```json
{
    "status": 0,
    "result": [
        {
            "phone": "qbIS7P3IYrlfYA4dI8xFFw==",
            "extCode": "682",
            "content": "上行回复内容",
            "receivetime": "20261118153922",
            "sId": "123456789abcdefg",
            "sign": "签名",
            "msgid": "8629637681836384963",
            "encrypted": 1,
            "spId": "test_spId_001"
        }
    ]
}
```

### 4.11.6 响应示例

```json
{
    "status": 0,
    "msg": ""
}
```

参数说明

+ status：状态码，0表示成功，非0表示失败

**客户侧完成验签并成功持久化后，返回 HTTP 200 和上述确认正文；处理失败时不要返回成功确认。重复推送仍可能发生，客户侧需实现去重。**

+ msg：错误信息

### 4.11.7 推送机制

推送机制见 4.4.6。


## 4.12 获取发送账号签名日统计

### 4.12.1 描述

按短信签名查询发送账号 spId 指定日期的发送统计。

### 4.12.2 请求基本信息

```plain
- 协议：推荐 HTTPS

- 请求方法：POST

- 数据传递结构：JSON

- 请求地址： https://nsms-api.wxo2o.cn/sms/getSignDailyStats/{spId}

- 接口统一签名认证
```

### 4.12.3 请求参数

| 参数名称 | 类型     | 是否必须 | 最大长度 | 说明                          |
| ---- | ------ | ---- | ---- | --------------------------- |
| date | string | 必须   | 8    | 日期格式：yyyyMMdd，例如： 20251103 |

### 4.12.4 请求示例

```json
{
    "date": "20251103"
}
```

### 4.12.5 响应示例

```json
{
    "status": 0,
    "result": [
        {
            "spId": "apiSendUser01",
            "date": "20251103",
            "sign": "实名签名1",
            "sendCount": 16,
            "feeCount": 33,
            "successCount": 2,
            "failCount": 31,
            "exceptionCount": 0,
            "noRespCount": 0
        },
        {
            "spId": "apiSendUser01",
            "date": "20251103",
            "sign": "实名签名2",
            "sendCount": 2,
            "feeCount": 4,
            "successCount": 4,
            "failCount": 0,
            "exceptionCount": 0,
            "noRespCount": 0
        }
    ]
}
```

统计字段的含义与 4.7.5 相同，另有 `sign` 字段表示短信签名。无数据时 `result` 为 `[]`，不是 `null`。

## 附录 API参考

### A. 官方 SDK 与安装入口

以下为本版 API 对应的 SDK，覆盖正文 4.1～4.12 中的现行发送、查询及回调能力，不使用旧版 SDK 的模板发送、多内容批量发送等接口。三个项目独立使用，均采用 Apache-2.0 许可证。说明更新日期：2026-11-19。

| 语言 | 环境要求 | 仓库与详细说明 | 安装标识 |
| --- | --- | --- | --- |
| Java | JDK 17+、Maven | [Java SDK](https://github.com/next-sms-secmsg-cn/next-sms-sdk-java) | `cn.secmsg:next-sms-sdk-java:1.0.0`，已发布 Maven Central |
| Python 3 | Python 3.9+ | [Python SDK](https://github.com/next-sms-secmsg-cn/next-sms-sdk-python) | 包名 `next-sms-sdk`，导入名 `next_sms`；当前通过 GitHub 安装，未发布 PyPI |
| Go | Go 1.22+ | [Go SDK](https://github.com/next-sms-secmsg-cn/next-sms-sdk-go) | `github.com/next-sms-secmsg-cn/next-sms-sdk-go` |

Python、Go 安装示例固定到本次验证的源码提交，便于复现；后续升级请先核对仓库的版本说明，不应把 `main` 的自动变化当作已经测试的新版本。

#### A.1 公共配置与调用约定

| 配置 | 用途 |
| --- | --- |
| `NEXT_SMS_SP_ID` | 平台提供的接口发送账号 `spId` |
| `NEXT_SMS_SP_KEY` | 对应账号的接口密钥 `spKey`，由环境变量或密钥管理服务注入 |
| `NEXT_SMS_BASE_URL` | 可选，默认 `https://nsms-api.wxo2o.cn`；只填写根地址，不拼接 `/sms/send/{spId}` |
| `NEXT_SMS_MOBILE` | 运行发送示例时使用的已授权测试号码，多号码以英文逗号分隔 |
| `NEXT_SMS_CONTENT` | 运行发送示例时使用的短信正文，包含已获准使用的签名 |
| `NEXT_SMS_MSG_ID` | 调用方生成并持久化的业务流水号；示例要求传入，协议本身允许省略 |

SDK 自动完成 POST、JSON 序列化、UTF-8 编码、时间戳和 HMAC 鉴权，无需调用方再次生成 Authorization。调用主机需保持系统时间准确。

运行本文发送示例会真实提交短信；请先配置测试账号、授权号码及内容。三种语言示例是替代方案，不要把同一业务短信依次用三个示例发送。

发送返回 `status=0` 表示平台受理成功，不等于手机已收到。最终结果以状态报告为准。自定义 `msgid` 用于关联业务流水，不表示接口提供幂等重试保证。超时、连接中断或取消后可能已经受理，不能直接补发。

#### A.2 方法与 API 对照

| 能力 | 正文章节 | Java | Python | Go |
| --- | --- | --- | --- | --- |
| 单内容多号码发送 | 4.1 | `send(sms)` / `sendAsync(sms)` | `send(sms)` | `Send(ctx, sms)` |
| 加密发送 | 4.9 | `secureSend(sms)` / `secureSendAsync(sms)` | `secure_send(sms)` | `SecureSend(ctx, sms)` |
| 主动拉取状态报告 | 4.3 | `getReport(500)` | `get_report(max_size=500)` | `GetReport(ctx, 500)` |
| 主动拉取上行 | 4.5 | `getUpstream(500)` | `get_upstream(max_size=500)` | `GetUpstream(ctx, 500)` |
| 余额 | 4.6 | `getBalance()` | `get_balance()` | `GetBalance(ctx)` |
| 发送账号日统计 | 4.7 | `getDailyStats("20261118")` | `get_daily_stats("20261118")` | `GetDailyStats(ctx, "20261118")` |
| 发送账号签名日统计 | 4.12 | `getSignDailyStats("20261118")` | `get_sign_daily_stats("20261118")` | `GetSignDailyStats(ctx, "20261118")` |
| 状态、上行回调验签 | 4.2、4.4、4.10、4.11 | `parseCallback(rawBody, authorization)` | `parse_callback(raw_body, authorization)` | `ParseCallback(rawBody, authorization)` |
| 回调号码解密 | 4.10、4.11 | `decodePhone(item)` | `decode_phone(item)` | `DecodePhone(phone, encrypted, spId)` |

普通 HTTPS 发送已经具备传输加密，只有业务明确需要时才改用加密发送；两种发送方法二选一，不能先普通发送再加密发送同一条短信。统计日期格式统一为 `yyyyMMdd`。

### B. Java SDK

#### B.1 Maven 安装

在调用项目的 `pom.xml` 中加入：

```xml
<dependency>
  <groupId>cn.secmsg</groupId>
  <artifactId>next-sms-sdk-java</artifactId>
  <version>1.0.0</version>
</dependency>
```

通过 Maven Central 获取，不需要配置私有仓库。SDK 基于 OkHttp 和 Gson，不依赖 Spring；普通 JAR 不内嵌依赖，Maven 会解析传递依赖。Java 包名前缀为 `cn.secmsg`。

#### B.2 完整同步发送示例

将下面类放入调用项目的 `SendSmsExample.java`，配置 A.1 的环境变量后运行。

```java
import cn.secmsg.HttpOptions;
import cn.secmsg.NextSmsClient;
import cn.secmsg.Sms;
import cn.secmsg.SmsException;
import com.google.gson.JsonObject;
import java.io.IOException;

public class SendSmsExample {
    public static void main(String[] args) throws IOException {
        String baseUrl = System.getenv().getOrDefault(
                "NEXT_SMS_BASE_URL", NextSmsClient.DEFAULT_BASE_URL);
        try (var client = new NextSmsClient(
                System.getenv("NEXT_SMS_SP_ID"), System.getenv("NEXT_SMS_SP_KEY"),
                baseUrl, HttpOptions.defaults())) {
            Sms sms = new Sms(
                    System.getenv("NEXT_SMS_CONTENT"),
                    System.getenv("NEXT_SMS_MOBILE"),
                    "001", System.getenv("NEXT_SMS_MSG_ID"), "demo-batch", null);
            JsonObject response = client.send(sms);
            String messageId = response.get("msgid").getAsString();
            System.out.println("accepted msgid=" + messageId);
        } catch (SmsException error) {
            System.err.println("SDK error kind=" + error.getKind()
                    + ", code=" + error.getCode());
            throw error;
        }
    }
}
```

`Sms` 参数依次是 `content`、`mobile`、`extCode`、`msgid`、`sId`、`sendTime`。可选字段可用 `null`；定时发送的 `sendTime` 是毫秒时间戳字符串，不是秒或日期字符串。内容长度、单次号码数、扩展码及定时范围以正文 4.1 为准，SDK 不自动拆批。

此例适合单次脚本；在 Web 服务中应按账号复用长生命周期客户端，停机时先停止新请求并等待在途请求完成，再调用 `close()`，不要每条短信创建一个客户端。

#### B.3 查询和拉取示例

以下是已有 `NextSmsClient client` 的业务方法内片段，外层处理 `IOException`。按需调用对应方法，不要求每次发送后执行所有查询。

```java
JsonObject reports = client.getReport(500);
for (var item : reports.getAsJsonArray("result")) {
    JsonObject report = item.getAsJsonObject();
    String messageId = report.get("msgid").getAsString();
    String deliveryStatus = report.get("status").getAsString();
    // 将状态报告持久化，并按业务流水关联发送记录。
}

JsonObject upstream = client.getUpstream(500);
long balance = client.getBalance().get("result").getAsLong();
JsonObject daily = client.getDailyStats("20261118");
JsonObject signDaily = client.getSignDailyStats("20261118");
```

返回对象为 Gson `JsonObject`；消息 ID 按字符串保存，计数和余额使用 `long`。日统计没有数据时 `result` 可能是 JSON `null`，读取具体字段前按正文响应结构判断。

#### B.4 异步发送与代理

需要异步发送时，用下面片段替换 B.2 中的同步 `send`，不要在同一业务上同时调用两者：

```java
var future = client.sendAsync(sms);
JsonObject response = future.join();
```

`join()` 仅用于展示等待结果；服务端可用完成回调接入自己的业务执行器。异步失败通过 future 的异常取得原因。默认最多 32 个在途请求（同步与异步合计），达到上限返回 `BUSY`，SDK 不无限排队。连接池用于复用连接，连接数不是号码 TPS。

Java 默认沿用 JDK `ProxySelector`，可在启动参数配置代理：

```sh
java -Dhttps.proxyHost=proxy.example.internal -Dhttps.proxyPort=3128 \
  '-Dhttp.nonProxyHosts=localhost|127.*|192.168.*' -jar your-app.jar
```

`your-app.jar` 指调用方应用，不是 SDK JAR。HTTP 目标使用 `http.proxyHost/http.proxyPort`；JDK 不自动将 `HTTPS_PROXY` 当作上述 JVM 参数。显式代理及代理认证见 [Java SDK 代理说明](https://github.com/next-sms-secmsg-cn/next-sms-sdk-java#jdk与显式代理)。

### C. Python 3 SDK

#### C.1 安装

以下安装要求本机有 Git，固定到当前已验证提交；包的运行依赖由 pip 安装。

```sh
python3 -m venv .venv
. .venv/bin/activate
python -m pip install "git+https://github.com/next-sms-secmsg-cn/next-sms-sdk-python.git@76cf636caf25fb6d3b9a5627b36214164477ea41"
```

该 SDK 当前未发布 PyPI，不要把 `pip install next-sms-sdk` 作为本版安装命令。也可下载官方仓库源码后，在源码目录运行 `python -m pip install .`。

#### C.2 完整发送示例

保存为 `send_sms.py`，配置 A.1 环境变量后执行 `python send_sms.py`。

```python
import os
from next_sms import ApiError, Client, HttpError, Sms


def main():
    client = Client(
        os.environ["NEXT_SMS_SP_ID"],
        os.environ["NEXT_SMS_SP_KEY"],
        base_url=os.environ.get("NEXT_SMS_BASE_URL", "https://nsms-api.wxo2o.cn"),
        timeout=30,
    )
    sms = Sms(
        content=os.environ["NEXT_SMS_CONTENT"],
        mobile=os.environ["NEXT_SMS_MOBILE"],
        ext_code="001",
        msgid=os.environ["NEXT_SMS_MSG_ID"],
        sid="demo-batch",
    )
    try:
        response = client.send(sms)
        print("accepted msgid=" + response["msgid"])
    except ApiError as error:
        print("API status=" + str(error.status))
        raise
    except HttpError as error:
        print("HTTP status=" + str(error.status))
        raise


if __name__ == "__main__":
    main()
```

Python 属性名 `ext_code`、`sid`、`send_time` 分别映射协议的 `extCode`、`sId`、`sendTime`；定时时间传毫秒字符串，可选字段不设置或传 `None`。

#### C.3 查询和拉取示例

以下片段放在已有 `client` 的业务函数内；所有方法返回字典。

```python
reports = client.get_report(max_size=500)
for report in reports["result"]:
    message_id = report["msgid"]
    delivery_status = report["status"]
    # 将状态报告持久化，并关联发送记录。

upstream = client.get_upstream(max_size=500)
balance = client.get_balance()["result"]
daily = client.get_daily_stats("20261118")["result"]
sign_daily = client.get_sign_daily_stats("20261118")["result"]
```

无日统计数据时 `daily` 可能为 `None`。HTTP 使用标准库 urllib，`timeout` 为连接/阻塞 socket 操作超时，不是整批任务总时限。本版没有异步接口或内置线程池；需要并发时由调用方使用有界工作池，不应宣称它具有 Java OkHttp 式连接池能力。代理沿用 urllib 的 `HTTPS_PROXY`、`NO_PROXY` 等标准环境设置。

### D. Go SDK

#### D.1 安装

在调用项目中执行；项目已有 `go.mod` 时跳过 `go mod init`：

```sh
go mod init example.com/sms-demo
go get github.com/next-sms-secmsg-cn/next-sms-sdk-go@ec3a3c70b9ea716e3c8a38436f9eade4222ae842
```

该命令将固定提交解析为 Go 伪版本并写入 `go.mod`，请一并提交 `go.mod` 和 `go.sum`。SDK 本身使用标准库，没有外部运行依赖。

#### D.2 完整发送示例

保存为 `main.go`，配置 A.1 环境变量后执行 `go run .`。

```go
package main

import (
    "context"
    "errors"
    "fmt"
    "os"
    "time"

    nextsms "github.com/next-sms-secmsg-cn/next-sms-sdk-go"
)

func send() error {
    client, err := nextsms.NewClient(
        os.Getenv("NEXT_SMS_SP_ID"), os.Getenv("NEXT_SMS_SP_KEY"),
        nextsms.Options{BaseURL: os.Getenv("NEXT_SMS_BASE_URL"), Timeout: 30 * time.Second},
    )
    if err != nil {
        return err
    }
    defer client.CloseIdleConnections()

    ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
    defer cancel()
    response, err := client.Send(ctx, nextsms.Sms{
        Content: os.Getenv("NEXT_SMS_CONTENT"),
        Mobile: os.Getenv("NEXT_SMS_MOBILE"),
        ExtCode: "001", MsgID: os.Getenv("NEXT_SMS_MSG_ID"), SID: "demo-batch",
    })
    if err != nil {
        return err
    }
    fmt.Println("accepted msgid=" + response.MsgID)
    return nil
}

func main() {
    if err := send(); err != nil {
        var sdkError *nextsms.Error
        if errors.As(err, &sdkError) {
            fmt.Fprintf(os.Stderr, "SDK error kind=%s code=%d\n", sdkError.Kind, sdkError.Code)
        } else {
            fmt.Fprintln(os.Stderr, "request failed; check configuration or transport; do not auto-resend")
        }
        os.Exit(1)
    }
}
```

`BaseURL` 为空时使用默认根地址。`Sms.SendTime` 为毫秒字符串，空字符串表示不指定。业务服务中复用 `Client`，可供多个 goroutine 调用；每次业务操作使用自己的 context。不要把已经超时或取消的 context 继续用于新请求。

#### D.3 解析状态报告示例

以下是可加入调用项目的函数，需导入 `context`、`encoding/json` 和 `nextsms`（导入路径同上）。其他查询方法见 A.2。

```go
func fetchReports(ctx context.Context, client *nextsms.Client) ([]nextsms.Report, error) {
    response, err := client.GetReport(ctx, 500)
    if err != nil {
        return nil, err
    }
    var reports []nextsms.Report
    if err := json.Unmarshal(response.Result, &reports); err != nil {
        return nil, err
    }
    return reports, nil
}
```

`Response.Result` 是 `json.RawMessage`，应按接口解码：状态报告为 `[]nextsms.Report`，上行为 `[]nextsms.Upstream`，账号日统计为 `nextsms.DailyStats`，签名日统计为 `[]nextsms.DailyStats`，余额为 `int64`。日统计可能为 JSON `null`，可解码到 `*nextsms.DailyStats` 后判断是否为 `nil`；不要把空统计当作一条全零统计。

Go 的 Transport 复用连接，沿用 `HTTP_PROXY`、`HTTPS_PROXY`、`NO_PROXY`；调用方应限制并发 goroutine 数量。`CloseIdleConnections()` 只回收空闲连接，不中断在途请求。

### E. 状态报告与上行回调

SDK 不会自动启动 HTTP 服务。调用方需提供正文定义的推送地址，由已有的 HTTP 框架接收请求，再调用 SDK 验签与解析。状态报告、上行以及加密号码变体共用同一套验签入口。

1. 在 HTTP 入口限制请求体大小，取得原始 body 字节及 `Authorization` 请求头。
2. 选择该推送地址对应账号的 SDK 客户端；多账号不能共用错误的 `spKey`。
3. 验签通过后解析业务记录，只有 `encrypted=1` 时才解密号码。
4. 持久化业务结果并去重；全部成功后返回 HTTP 200、`Content-Type: application/json` 及确认正文。
5. 验签、解析、解密或持久化失败时不能确认成功，由调用方 HTTP 层返回失败。

原始 body 不能先解析 JSON 再序列化后验签，否则空格、转义或字段顺序变化会破坏签名。去重键应按业务确定，不能仅用批次 `msgid` 合并多个号码或分片。

#### E.1 Java 回调处理片段

在已有 HTTP handler 中使用，`rawBody` 为 `byte[]`，`authorizationHeader` 为请求头字符串，`client` 为正确账号的 `NextSmsClient`：

```java
JsonObject payload = client.parseCallback(rawBody, authorizationHeader);
for (var value : payload.getAsJsonArray("result")) {
    JsonObject item = value.getAsJsonObject();
    String phone = client.decodePhone(item);
    // 此处必须实现持久化与去重；状态和上行分别按正文的字段处理。
}
// 仅在上述业务处理全部完成后，设置 HTTP 200 / application/json 并返回：
String ack = NextSmsClient.callbackAck();
```

#### E.2 Python 回调处理片段

`raw_body` 为 `bytes`，其余变量来自调用方 HTTP 框架：

```python
payload = client.parse_callback(raw_body, authorization_header)
for item in payload["result"]:
    phone = client.decode_phone(item)
    # 此处必须实现持久化与去重。
# 仅在全部业务处理成功后作为 HTTP 200 / application/json 的响应体：
ack_bytes = client.callback_ack()
```

#### E.3 Go 回调处理函数

以下函数由 HTTP handler 调用。`persist` 是调用方实现的持久化和去重函数；未成功持久化时不会返回成功确认。所需导入为 `encoding/json` 和 `nextsms`。

```go
func handleReportCallback(client *nextsms.Client, rawBody []byte, authorization string,
    persist func(nextsms.Report, string) error) ([]byte, error) {
    payload, err := client.ParseCallback(rawBody, authorization)
    if err != nil {
        return nil, err
    }
    var reports []nextsms.Report
    if err := json.Unmarshal(payload.Result, &reports); err != nil {
        return nil, err
    }
    for _, report := range reports {
        phone, err := client.DecodePhone(report.Phone, report.Encrypted, report.SpID)
        if err != nil {
            return nil, err
        }
        if err := persist(report, phone); err != nil {
            return nil, err
        }
    }
    return nextsms.CallbackAck(), nil
}
```

上行回调将 `[]nextsms.Report` 改为 `[]nextsms.Upstream`，并实现对应业务处理。HTTP handler 收到错误时返回失败，成功时才返回确认字节。确认正文统一为 `{"status":0,"msg":""}`，SDK 不会替调用方发送 HTTP 响应。

### F. 错误处理与接入检查

| 情况 | Java | Python | Go |
| --- | --- | --- | --- |
| 平台非零 status | `SmsException`，`Kind.API`、`getCode()` | `ApiError.status` | `*nextsms.Error`，`Kind="API"`、`Code` |
| 非成功 HTTP 状态 | `Kind.HTTP` | `HttpError.status` | `Kind="HTTP"` |
| 非法响应结构或过大响应 | `Kind.PROTOCOL` | `ProtocolError` | `Kind="PROTOCOL"` |
| 回调验签失败 | `Kind.SIGNATURE` | `SignatureError` | `Kind="SIGNATURE"` |
| 网络或超时 | `IOException`；异步读取 future 异常原因 | urllib/socket 网络异常 | 原网络错误或 context 错误 |
| 本地参数不合法 | `IllegalArgumentException` 等 | `ValueError` 等 | 返回参数错误 |

三个 SDK 均不做应用层自动重试，不跟随 HTTP 重定向，默认保留 TLS 证书校验。主动拉取报告/上行具有消费语义，成功取得的数据应及时持久化，网络异常时也不能假定再次拉取会得到相同数据。不要同时部署多个无协调的消费者拉取同一账号队列。

接入时请确认：

- 使用正确的 `spId/spKey` 和 HTTPS 根地址，未把密钥写入仓库、示例或日志。
- 号码、内容、扩展码、定时字段符合正文约束；SDK 不自动更改号码或拆批。
- 保存受理返回的消息 ID，并将状态报告关联至业务记录；不要将消息 ID 转成浮点数。
- 明确使用主动拉取还是回调推送，并配置对应的持久化、失败处理和去重。
- 服务中复用客户端，限制并发，关闭前等待在途操作结束。
- 日志仅记录必要的错误类型、状态码与业务流水，不直接输出整份响应、手机号、正文或密钥。

更多示例与源码以各语言仓库的 README、`docs/API.md` 和 examples 目录为准。

### G. Postman 调用脚本

#### G.1 导入与配置

配套文件 [next-sms.postman_collection.json](https://raw.githubusercontent.com/next-sms-secmsg-cn/next-sms-sdk-java/main/docs/next-sms.postman_collection.json) 可从 GitHub 下载并导入 Postman。集合包含 `send`、`secureSend`、`getBalance`、`getReport`、`getUpstream`、`getDailyStats`、`getSignDailyStats` 七个 POST 请求，已配置以下脚本。

1. 在 Postman 选择 Import，导入集合文件。
2. 新建专用测试 Environment，按下表填写变量，并选中该环境。账号密钥只保存在本地值中，不共享、不导出真实密钥。
3. 先单独执行 `getBalance` 或日统计查询，验证地址与鉴权。
4. 需要测试发送时，设置授权测试号码、内容和业务流水号，并将 `confirmSend` 设为字符串 `YES`。只执行普通发送或加密发送中的一种，完成后清空确认值。

| 变量 | 用途或示例 |
| --- | --- |
| `baseUrl` | `https://nsms-api.wxo2o.cn`，末尾不要加 `/` |
| `spId` | 平台下发的发送账号 |
| `spKey` | 对应接口密钥，仅填本地值，并标记为敏感变量 |
| `mobile` | 已授权的测试号码；多个号码用英文逗号分隔 |
| `content` | 带已授权签名的测试短信内容 |
| `msgid` | 自行生成并保存的本次提交业务流水号 |
| `extCode`、`sId`、`sendTime` | 可选；不使用时留空，sendTime 使用毫秒时间戳字符串 |
| `date` | 统计日期，例如 `20261118` |
| `maxSize` | 例如 `500`，范围 10～1000 |
| `confirmSend` | 默认留空；仅在授权发送测试时填写 `YES` |

不要直接 Run 整个集合：普通发送和加密发送都会产生提交，报告与上行拉取也会消费队列。调用脚本不会修改真实账号或自动重试失败请求。

#### G.2 请求设置与前置脚本

手动创建请求时，名称必须与接口名相同，例如 `getBalance`；URL 为 `{{baseUrl}}/sms/getBalance/{{spId}}`，其余请求替换接口名即可。方法设为 POST，Authorization 选择 No Auth，避免覆盖脚本生成的头。所有请求的 Body 选择 raw / JSON，内容只填写 `{{requestBody}}`，不要加引号。余额查询时该变量会被设为空字符串。

在集合的 Scripts → Pre-request 中配置以下代码。脚本从变量构建 JSON，生成一次时间戳，并对最终发送的请求体签名；不要在子请求中另行修改请求体。使用支持 `pm.require` 与 `pm.execution.skipRequest` 的当前 Postman 桌面版。首次加载固定版本 `crypto-js@4.2.0` 需要访问包源。相关能力见 [Postman 包导入说明](https://learning.postman.com/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-require/) 和 [跳过请求说明](https://learning.postman.com/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-execution/)。

```javascript
try {
    const CryptoJS = pm.require('npm:crypto-js@4.2.0');
    const required = (name) => {
        const value = pm.variables.get(name);
        if (typeof value !== 'string' || value.length === 0) {
            throw new Error('Missing variable: ' + name);
        }
        return value;
    };
    const spId = required('spId');
    const spKey = required('spKey');
    if (!/^[A-Za-z0-9_-]+$/.test(spId)) throw new Error('Invalid spId');
    const endpoint = pm.info.requestName;
    let body;
    switch (endpoint) {
        case 'send':
        case 'secureSend': {
            if (pm.variables.get('confirmSend') !== 'YES') {
                throw new Error('Set confirmSend=YES only for an authorized send test');
            }
            const payload = {
                content: required('content'), mobile: required('mobile'),
                msgid: required('msgid')
            };
            for (const name of ['extCode', 'sId', 'sendTime']) {
                const value = pm.variables.get(name);
                if (value !== undefined && value !== '') payload[name] = String(value);
            }
            body = JSON.stringify(payload);
            if (endpoint === 'secureSend') {
                const keyText = spKey.length >= 16 ? spKey.slice(0, 16) : spKey.padStart(16, 'a');
                const aesKey = CryptoJS.enc.Utf8.parse(keyText);
                if (![16, 24, 32].includes(aesKey.sigBytes)) throw new Error('Invalid AES key length');
                const encrypted = CryptoJS.AES.encrypt(body, aesKey, {
                    mode: CryptoJS.mode.ECB, padding: CryptoJS.pad.Pkcs7
                }).ciphertext.toString(CryptoJS.enc.Base64);
                body = JSON.stringify({content: encrypted});
            }
            break;
        }
        case 'getReport':
        case 'getUpstream': {
            const maxSize = Number(required('maxSize'));
            if (!Number.isInteger(maxSize) || maxSize < 10 || maxSize > 1000) {
                throw new Error('maxSize must be an integer between 10 and 1000');
            }
            body = JSON.stringify({maxSize});
            break;
        }
        case 'getBalance':
            body = '';
            break;
        case 'getDailyStats':
        case 'getSignDailyStats': {
            const date = required('date');
            if (!/^\d{8}$/.test(date)) throw new Error('date must use yyyyMMdd');
            body = JSON.stringify({date});
            break;
        }
        default:
            throw new Error('Keep the original endpoint request name');
    }
    pm.variables.set('requestBody', body);
    const timestamp = String(Date.now());
    const signature = CryptoJS.HmacSHA256(body + timestamp, spKey).toString(CryptoJS.enc.Base64);
    pm.request.headers.upsert({key: 'Content-Type', value: 'application/json; charset=utf-8'});
    pm.request.headers.upsert({key: 'Authorization', value: 'HMAC-SHA256 ' + timestamp + ',' + signature});
} catch (error) {
    pm.execution.skipRequest();
    throw error;
}
```

加密发送由脚本自动执行 AES/ECB 加密，再对外层 JSON 进行 HMAC 签名；其他请求仅签名。`requestBody` 是本次运行的局部变量，不需要填写或保存为环境变量。通过变量替换生成请求体，避免直接修改不可变的 `pm.request.body`，参见 [Postman 请求对象说明](https://learning.postman.com/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-request/)。

`crypto-js` 在此仅用于 Postman 联调和兼容现有 AES/ECB 协议，已固定为 4.2.0；该库已停止维护，不建议将此测试脚本作为生产加密组件，业务接入使用前面的 SDK。

#### G.3 响应检查脚本

在集合的 Scripts → Post-response 中配置：

```javascript
pm.test('HTTP 200', function () {
    pm.response.to.have.status(200);
});
pm.test('API status=0', function () {
    const response = pm.response.json();
    pm.expect(response.status).to.eql(0);
    if (['send', 'secureSend'].includes(pm.info.requestName)) {
        pm.expect(response.msgid).to.be.a('string');
    }
});
```

发送测试通过仅表示受理成功，不代表最终送达。拉取结果中的字符串元素、`msgId` 与数值号码应按 4.3.5、4.5.5 处理；Postman 不会替业务系统持久化报告。

### H. curl 测试脚本

#### H.1 运行环境与配置

配套文件 [next-sms-curl-test.sh](https://raw.githubusercontent.com/next-sms-secmsg-cn/next-sms-sdk-java/main/docs/next-sms-curl-test.sh) 可从 GitHub 下载，使用 Bash、Python 3.9+ 标准库和 curl，无需安装三个 SDK。Python 只负责 JSON 序列化和 HMAC 计算，网络请求由 curl 发出。脚本不关闭 TLS 校验、不跟随重定向、不自动重试；连接超时 5 秒，总超时 30 秒。

在 macOS/Linux 的 Bash 中配置账号；密钥用静默输入，避免出现在 shell 历史中：

```sh
export NEXT_SMS_BASE_URL='https://nsms-api.wxo2o.cn'
read -r -p 'spId: ' NEXT_SMS_SP_ID
read -r -s -p 'spKey: ' NEXT_SMS_SP_KEY
printf '\n'
export NEXT_SMS_SP_ID NEXT_SMS_SP_KEY
```

先执行查询测试，以下命令每条都会独立发出请求，请按需执行：

```sh
bash next-sms-curl-test.sh getBalance
bash next-sms-curl-test.sh getDailyStats 20261118
bash next-sms-curl-test.sh getSignDailyStats 20261118
# 以下两条会消费对应队列，按需选择，不要用生产账号随意试拉取。
bash next-sms-curl-test.sh getReport 100
bash next-sms-curl-test.sh getUpstream 100
```

普通发送测试：

```sh
read -r -p '授权测试号码: ' NEXT_SMS_MOBILE
read -r -p '带签名的测试内容: ' NEXT_SMS_CONTENT
export NEXT_SMS_MOBILE NEXT_SMS_CONTENT
export NEXT_SMS_MSG_ID="curl-$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
export NEXT_SMS_CONFIRM_SEND=YES
bash next-sms-curl-test.sh send
unset NEXT_SMS_CONFIRM_SEND
```

加密发送测试仅在需要时单独执行：使用 4.9.4 的 Java 加密方法或 SDK，按当前账号密钥生成外层 `{"content":"Base64密文"}` 并保存为 `encrypted-body.json`，然后执行下面的替代命令。不能直接使用正文的演示密文配合真实账号，也不要再次发送刚才已受理的业务短信。

```sh
NEXT_SMS_CONFIRM_SEND=YES bash next-sms-curl-test.sh secureSend encrypted-body.json
```

#### H.2 完整脚本

以下代码与配套 `next-sms-curl-test.sh` 一致：

```bash
#!/usr/bin/env bash
set -euo pipefail

# Python only builds JSON and HMAC; curl sends the HTTP request.
python3 - "$@" <<'PY'
import base64
import hashlib
import hmac
import json
import os
import re
import subprocess
import sys
import time
from urllib.parse import urlsplit

if len(sys.argv) < 2:
    sys.exit('Usage: bash next-sms-curl-test.sh <send|getBalance|getReport|getUpstream|getDailyStats|getSignDailyStats|secureSend> [date|maxSize|encrypted-body.json]')
endpoint = sys.argv[1]
args = sys.argv[2:]
sp_id = os.environ['NEXT_SMS_SP_ID']
sp_key = os.environ['NEXT_SMS_SP_KEY']
base_url = os.environ.get('NEXT_SMS_BASE_URL', 'https://nsms-api.wxo2o.cn').rstrip('/')
url = urlsplit(base_url)
if not re.fullmatch(r'[A-Za-z0-9_-]+', sp_id) or not sp_key:
    sys.exit('Invalid account configuration')
if url.scheme not in ('http', 'https') or not url.hostname or url.username or url.password or url.query or url.fragment:
    sys.exit('Invalid API root URL')

if endpoint in ('send', 'secureSend'):
    if os.environ.get('NEXT_SMS_CONFIRM_SEND') != 'YES':
        sys.exit('Set NEXT_SMS_CONFIRM_SEND=YES only for an authorized send test')
    if endpoint == 'send':
        if args:
            sys.exit('send takes no arguments; configure NEXT_SMS_* environment variables')
        payload = {'content': os.environ['NEXT_SMS_CONTENT'], 'mobile': os.environ['NEXT_SMS_MOBILE'],
                   'msgid': os.environ['NEXT_SMS_MSG_ID']}
        for name, field in [('NEXT_SMS_EXT_CODE', 'extCode'), ('NEXT_SMS_SID', 'sId'), ('NEXT_SMS_SEND_TIME', 'sendTime')]:
            value = os.environ.get(name)
            if value:
                payload[field] = value
    else:
        if len(args) != 1:
            sys.exit('secureSend requires an encrypted-body.json file generated with the same spKey')
        with open(args[0], encoding='utf-8') as source:
            payload = json.load(source)
        if not isinstance(payload, dict) or set(payload) != {'content'} or not isinstance(payload['content'], str):
            sys.exit('Expected an encrypted JSON object containing only content')
        ciphertext = base64.b64decode(payload['content'], validate=True)
        if not ciphertext or len(ciphertext) % 16:
            sys.exit('Invalid AES ciphertext length')
elif endpoint == 'getBalance':
    if args:
        sys.exit('getBalance takes no arguments')
    payload = None
elif endpoint in ('getReport', 'getUpstream'):
    if len(args) > 1:
        sys.exit('Expected at most one maxSize argument')
    size = int(args[0]) if args else 500
    if not 10 <= size <= 1000:
        sys.exit('maxSize must be between 10 and 1000')
    payload = {'maxSize': size}
elif endpoint in ('getDailyStats', 'getSignDailyStats'):
    if len(args) != 1 or not re.fullmatch(r'\d{8}', args[0]):
        sys.exit('Expected one date argument in yyyyMMdd format')
    payload = {'date': args[0]}
else:
    sys.exit('Unknown endpoint')

body = b'' if payload is None else json.dumps(payload, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
timestamp = str(time.time_ns() // 1_000_000)
signature = base64.b64encode(hmac.new(sp_key.encode('utf-8'), body + timestamp.encode('ascii'), hashlib.sha256).digest()).decode('ascii')
command = ['curl', '--silent', '--show-error', '--connect-timeout', '5', '--max-time', '30',
           '--proto', '=http,https', '--request', 'POST', '--header', 'Content-Type: application/json; charset=utf-8',
           '--header', 'Authorization: HMAC-SHA256 ' + timestamp + ',' + signature,
           '--data-binary', '@-', '--write-out', '\n%{http_code}', base_url + '/sms/' + endpoint + '/' + sp_id]
response = subprocess.run(command, input=body, stdout=subprocess.PIPE, check=False)
if response.returncode:
    sys.exit('curl failed (exit %d); do not automatically repeat a send or queue pull' % response.returncode)
raw, http_status = response.stdout.rsplit(b'\n', 1)
if not 200 <= int(http_status) < 300:
    sys.exit('HTTP error: ' + http_status.decode('ascii'))
result = json.loads(raw)
print(json.dumps(result, ensure_ascii=False, indent=2))
if result['status'] != 0:
    sys.exit(1)
PY
```

脚本成功时退出码为 0，HTTP 失败、业务 `status` 非 0 或本地错误时退出码非 0。响应原文以 JSON 输出到终端，可能包含号码或上行内容，不要重定向到共享日志，也不要开启 `set -x`、`curl -v` 或 `--trace` 记录敏感数据。测试完成后执行 `unset NEXT_SMS_SP_KEY` 清除当前 shell 的密钥变量。

超时或断连时，请先核对业务受理和报告情况；不要直接重跑发送或拉取命令。自定义 msgid 不提供幂等保证。

### I. 真实接口验证记录

2026-11-19 北京时间 14:43:52～14:45:28，使用授权测试账号访问 `https://nsms-api.wxo2o.cn`，完成以下验证：

| 接口 | Postman 集合 | curl 脚本 |
| --- | --- | --- |
| `getBalance` | HTTP 200、status=0 | HTTP 2xx、status=0 |
| `getDailyStats` | status=0，返回当日统计 | status=0，返回当日统计 |
| `getSignDailyStats` | status=0，返回 1 条统计 | status=0，返回 1 条统计 |
| `getUpstream` | status=0，空数组 | status=0，空数组 |
| `send` | status=0，受理 1 条 | status=0，受理 1 条 |
| `secureSend` | status=0，受理 1 条 | status=0，受理 1 条 |
| `getReport` | status=0 | status=0，后续拉取完成回执核对 |

共发送 4 条独立测试短信，4 条均取得 `DELIVRD` 回执，每条 fee=1，总计 4 个成功分片，缺失 0 条。普通发送、加密发送、余额空请求体和查询请求的签名均通过真实接口校验。

Postman 集合通过官方 `postman-runtime 7.56.3` 执行原始前置脚本与响应断言；外部包固定使用 `crypto-js 4.2.0`。这是实际 HTTPS 接口联调，不是仅模拟签名；未在 Postman/Apifox 桌面界面逐项执行。上行仅验证空队列响应，未制造终端回复；测试环境的成功回执不作为真实运营商终端送达证明。

