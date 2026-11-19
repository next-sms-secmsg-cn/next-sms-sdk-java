# Maven Central 发布手册

## 当前状态

发布坐标为 `cn.secmsg:next-sms-sdk-java:1.0.0`，JDK 17。Java 包名仍是 `cn.wxo2o.sms`，不改调用方 import；接口默认地址不变。

已配置普通 JAR、sources JAR、Javadoc JAR、GPG 签名及 Central Portal 上传插件。发布功能仅在 `-Pcentral` 下启用，`autoPublish=false`，上传通过验证后仍需在 Portal 手动点击 Publish。

许可证为 Apache-2.0，源码仓库为 `next-sms-secmsg-cn/next-sms-sdk-java`，维护者使用该 GitHub 账号的公开名称和主页。POM 不含占位元数据，也没有使用其他 GitHub 账号。当前尚未发布到 Maven Central。

## 1. 确认公开元数据

检查 POM 的 `name`、`description`、`url`、`licenses`、`developers`、`scm`，以及项目根目录的 LICENSE。维护者未公开邮箱，因此仅登记公开账号、名称和主页，不推测或暴露私人邮箱。以后更换仓库时需同步修改 POM 的 SCM 地址。

源码仓库、维护者资料和许可证将随 POM 公开；源码 JAR 也将公开，不应包含账号、密码或内部配置。

## 2. 验证命名空间

在 [Central Portal](https://central.sonatype.com) 登录并验证 `cn.secmsg`。在域名 `secmsg.cn` 的根记录添加 TXT，主机记录通常为 `@`，值使用 Portal 当次提供的 Verification Key。

```sh
dig +short TXT secmsg.cn
```

确认 TXT 生效后再点击 Confirm，等待命名空间显示 Verified。GitHub 登录不等于已验证自有域名。不要把 TXT 加到 `cn.secmsg` 或任意自定义子域名。

## 3. Central User Token

在 Portal 的账号页面生成 User Token，得到一对 username/password，不是 GitHub PAT，也不是短信接口账号。

本目录 `settings-central.xml.example` 只含环境变量引用，可直接用 `-s` 指向它；若已有用户 settings.xml，也可以只合并其中 id 为 `central` 的 server，保留原镜像、代理及其他仓库设置。

在终端隐藏输入两项 Token（Bash 和 zsh 均支持以下命令）：

```sh
printf 'Central token username: '
read -rs CENTRAL_USERNAME
printf '\nCentral token password: '
read -rs CENTRAL_PASSWORD
printf '\n'
export CENTRAL_USERNAME CENTRAL_PASSWORD
```

不要把 Token 写入 POM、命令行参数或提交到 Git，也不要用 `mvn -X` 输出含凭据的诊断。

## 4. 安装并配置 GPG

macOS 首次安装及生成密钥：

```sh
brew install gnupg
gpg --full-generate-key
gpg --list-secret-keys --keyid-format LONG
```

生成用于签名的密钥，设置有效期及强口令；身份信息应与公开维护者信息一致。记录完整 fingerprint，不使用短 key ID。发布公钥：

```sh
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys YOUR_FULL_FINGERPRINT
export GPG_TTY=$(tty)
```

私钥由本机 GPG 管理，口令通过 gpg-agent/pinentry 输入，不写在 POM 或 settings.xml 中。插件开启 `bestPractices=true`，禁止通过不安全的 Maven 参数传递口令。自动化环境需要口令时使用插件支持的 `MAVEN_GPG_PASSPHRASE` 秘密环境变量，并通过 CI 密钥管理注入。

## 5. 本地构建与签名检查

```sh
git clone https://github.com/next-sms-secmsg-cn/next-sms-sdk-java.git
cd next-sms-sdk-java
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean verify
```

会执行测试并生成三个包：

```text
target/next-sms-sdk-java-1.0.0.jar
target/next-sms-sdk-java-1.0.0-sources.jar
target/next-sms-sdk-java-1.0.0-javadoc.jar
```

确认第 1 节元数据并准备好 GPG 后，只签名，不上传：

```sh
mvn -Pcentral -Dgpg.keyname=YOUR_FULL_FINGERPRINT clean verify
```

应生成以上三个 JAR 及 POM 的 `.asc` 签名。这里不要设置 `gpg.skip=true`，也不要跳过测试；无签名的包不能发布到 Central。

## 6. 上传并手动发布

```sh
mvn -s docs/settings-central.xml.example -Pcentral \
  -Dgpg.keyname=YOUR_FULL_FINGERPRINT clean deploy
```

此命令会上传到 Central 并等待校验，不会自动公开。前往 [Deployments](https://central.sonatype.com/publishing/deployments)，确认状态为 VALIDATED，检查坐标、版本及资料，然后点击 Publish。

可选：首次只生成完整签名 bundle、不上传：

```sh
mvn -Pcentral -Dgpg.keyname=YOUR_FULL_FINGERPRINT \
  -DskipUpload=true clean deploy
```

产物为 `target/central-publishing/central-bundle.zip`。`skipUpload` 只跳过上传，仍需要正确元数据和 GPG。不要用 `skipPublishing` 代替，它会连 bundle 都不生成。

完成后清除当前终端的 Token：

```sh
unset CENTRAL_USERNAME CENTRAL_PASSWORD
```

## 7. 消费与后续版本

发布可用后，使用默认 Maven Central 仓库即可，不需要配置 Sonatype 私有仓库：

```xml
<dependency>
  <groupId>cn.secmsg</groupId>
  <artifactId>next-sms-sdk-java</artifactId>
  <version>1.0.0</version>
</dependency>
```

上传但尚未 Publish 的失败部署可以在 Portal 删除后修正；一旦公开发布，该版本不可覆盖。后续修改需在 POM 中提升版本，再测试、签名、发布。不要在发送超时后为验证 SDK 而无条件重发业务短信。

## 排错

| 现象 | 排查 |
| --- | --- |
| GPG 找不到命令或私钥 | 检查 `gpg --version`、`gpg --list-secret-keys` 和完整 fingerprint |
| GPG 无法弹出口令框 | 设置 `GPG_TTY=$(tty)`，在交互终端运行，不要先使用 `-B` 批处理 |
| 上传 401/403 | 检查 Central Token、server id=central，以及 cn.secmsg 是否 Verified |
| 校验缺少 sources/javadoc/签名 | 从本模块执行完整 `-Pcentral clean deploy`，不要只上传普通 JAR |
| Javadoc no comment 警告 | 当前部分 API 没有 Javadoc 注释；包仍生成，详细使用说明见 README 和 docs/API.md |
| 版本已存在 | 确认已发布记录，提升版本，不能覆盖已公开版本 |

## 参考

- [Central 命名空间规则](https://central.sonatype.org/register/namespace/)
- [Central 发布要求](https://central.sonatype.org/publish/requirements/)
- [Central Maven 插件](https://central.sonatype.org/publish/publish-portal-maven/)
- [Central Token](https://central.sonatype.org/publish/generate-portal-token/)
- [Maven GPG 签名参数](https://maven.apache.org/plugins/maven-gpg-plugin/sign-mojo.html)

本次仅验证普通构建、Javadoc 产物和 Central profile 配置；验证机器没有 GPG，未执行真实签名、上传或发布。
