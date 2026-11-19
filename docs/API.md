# 协议范围与处理约定

## 依据

- API 页面：https://nsms.wxo2o.cn/#/docs/api
- 正文数据：https://nsms.wxo2o.cn/api/publicDocuments/api
- 读取日期：2026-11-19；文档接口 `updatedAt=1794365631358`。
- 仅使用「概述、约束、鉴权认证、接口列表」；后面的旧 SDK 附录不作为实现依据。
- 对余额空 body 与加密规则，额外核对 next-sms 的 `SmsController.getBalance`、`CryptoUtil`，未修改服务端。

## 接口清单

以下均为 POST，完整路径为 `/sms/{方法}/{spId}`，默认域名 `https://nsms-api.wxo2o.cn`。

| 方法 | 请求 | 成功响应 |
| --- | --- | --- |
| send | content、mobile 及可选字段 | status、msgid |
| secureSend | `{"content":"AES密文Base64"}` | status、msgid |
| getReport | `{"maxSize":500}` | status、result 状态报告数组 |
| getUpstream | `{"maxSize":500}` | status、result 上行数组 |
| getBalance | 空 body，不是 `{}` | status、result 余额条数 |
| getDailyStats | `{"date":"20261118"}` | status、result 日统计对象；无数据可能 null |
| getSignDailyStats | `{"date":"20261118"}` | status、result 签名日统计数组 |

发送参数：`content`、`mobile` 必填；号码使用逗号分隔，保留 `86` / `+86` 前缀；`extCode`、`msgid`、`sId`、`sendTime` 可选。`sendTime` 按文档作为毫秒时间戳字符串传递；无此参数则省略。字段大小写严格保留，不把 `msgid` 改为 `msgId`。

服务端约束：内容≤1005；单次号码≤10000；扩展码≤12且为数字；msgid/sId≤128；预约不超过30天，5分钟以内按即时处理。SDK 校验基本必填/类型和统计日期，不自行删号、去重、拆批、截断内容；上述业务约束与平台策略由服务端校验并返回原状态码。`maxSize` 文档范围10～1000，越界按平台默认500；SDK不改写调用者的整数值。

余额、计数使用整数处理，不转换为浮点数。消息ID保持字符串，允许负号和长数字。日统计日期文档存在数字/字符串两种表示，保留原值（Go 的 `DailyStats.Date` 使用 `json.Number` 兼容二者）。

主动拉取回执和上行的 `result` 在线上也会返回 JSON 字符串数组。SDK 将字符串项解析成对象；实际回执的 `msgId` 在缺少 `msgid` 时补充为文档约定的 `msgid`，保留原字段，数字 `phone` 转为字符串，其他字段不变。非法 JSON 或非对象项抛出协议错误，不静默丢弃。Go 的 `Response.Result` 为规范化数组，`Response.Raw` 保留原始响应。主动拉取会消费队列数据，收到后应立即持久化，不能假设重试还会返回同一批。

## 鉴权与加密

签名：`Base64(HMAC-SHA256(spKey UTF-8, body原始字节 + timestamp十进制ASCII))`。

请求头：`Authorization: HMAC-SHA256 {timestamp},{signature}`，逗号后不加空格。2026-11-19 真实测试确认：文档示例的逗号后空格会触发当前服务端按空格拆分时的参数错误 100003；三个 SDK 统一发送无空格格式。timestamp 为 UTC epoch 毫秒，与本地时区无关。签名后不再改变 body 序列化结果。回调验签兼容逗号后有、无空格的两种格式，同样使用原始 body，允许前后30分钟时间差，常量时间比较签名。

加密发送仅为兼容协议 4.9：AES/ECB/PKCS5Padding（AES块大小16，对应PKCS7填充）。密钥按文档Java规则取前16个UTF-16代码单元，不足在左侧补 `a`，随后UTF-8编码。平台密钥通常为ASCII；如果非ASCII密钥归一化后不是合法AES字节长度，明确报错，不自创派生方式。签名覆盖外层密文JSON，而非内层明文。普通HTTPS发送为默认推荐路径。

4.10/4.11 的加密号码通过独立 `decodePhone` / `decode_phone` / `DecodePhone` 处理。`encrypted=1` 时先确认 `spId` 与当前客户端一致，再解密；其他值/缺省按明文返回。原回调对象不被修改。多个账号的回调请根据接入路由选择账号客户端，不能先信任未验签内容来覆盖当前密钥。

## 回调与错误

状态回调 result 字段：phone、msgid、status、fee、donetime、可选sId；上行字段：phone、msgid、content、extCode、receivetime、可选sId/sign；加密号码增加 encrypted/spId。

先读取有限大小的原始请求体，再验签并解析，业务持久化成功后返回 HTTP200 和 `{"status":0,"msg":""}`。失败时不要返回成功确认。状态推送和上行推送均可能重推，业务侧需持久化去重；不能仅凭 msgid 去重，因为单内容多号码共用 msgid。按业务主键结合账号、号码、msgid及报告类型/分片语义设计去重。

文档约定平台按1分钟间隔最多重推5次。时间窗验签不等于完整防重放，窗口内重复仍由业务侧处理。主动拉取有消费语义，不能把拉取失败后无条件重试当成无损恢复。文档缓存48小时；推送与主动拉取不能同时使用（10019）。

SDK区分 HTTP错误、业务status非0、无效协议响应、回调签名失败；网络/超时保留底层错误。非0状态码保留给调用者判断，不内置不完整的运营商状态枚举。异常摘要不输出短信正文、手机号、spKey或原始响应；原响应仅在显式访问时提供，应用日志也须脱敏。

常见提交错误：100001鉴权失败，100002缺参，100003格式错，100004系统错误，100005时间戳过期，1004 IP校验失败，10016余额不足，10017账号禁用，10019拉取/推送冲突。`status=0`仅代表接口受理，短信投递结果以状态报告为准。报告 `DELIVRD` 为成功，其他值保留原文（含运营商返回码）。

所有响应/已签名回调解析上限8MiB；HTTP重定向不跟随，避免将签名或消息转发到其他地址。TLS保持默认证书和主机名校验，不提供跳过校验开关。SDK不记录请求正文，无自动发送重试；请求超时、取消或网络中断时，平台是否已受理可能未知，先查业务流水/回执再决定补发。自定义msgid并不意味着接口保证幂等。

## 测试与边界

自动化使用模拟HTTP服务和固定加密向量，覆盖七个接口路径、原始字节签名、中文、128字符ID、空余额请求、回调验签和解密、无效JSON、非0状态、HTTP错误、重定向、超时、并发。Java额外覆盖代理认证、系统ProxySelector、连接复用、并发拒绝和取消。

没有进行真实短信投递、生产压测、实际公网代理或运营商回执联调。Java本地并发测试验证的是HTTP客户端行为，不是平台TPS承诺。HTTP/2、TLS及SOCKS握手依赖所选HTTP库，当前未做真实服务端端到端测试。
