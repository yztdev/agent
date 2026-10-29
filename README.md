<<<<<<< HEAD
# agent
agent的自主学习
=======
# Terminal Agent V0.1

Java 17、Maven 3.8+、PostgreSQL 17（本地 Docker 镜像）。当前版本提供认证基础，不含对话和工作区功能。

## 构建和测试

```powershell
mvn clean verify
```

## 数据库与 HTTPS

设置环境变量 `POSTGRES_PASSWORD` 后执行 `docker compose up -d`。已有数据卷由 PostgreSQL 17 创建，因此必须使用兼容的 17 版本镜像，不能直接用 16 启动；Flyway 可能提示该版本尚未经过官方验证。数据库仅映射本机 5432 端口，持久化在 Docker volume；生产部署请自行配置独立数据库、密码、备份及访问控制。Flyway 在服务启动时执行迁移。

服务端必须配置 `DATABASE_URL`（例如 `jdbc:postgresql://localhost:5432/terminal_agent`）、`DATABASE_USER`、`DATABASE_PASSWORD`、`TLS_KEYSTORE`（PKCS12 文件路径）和 `TLS_KEYSTORE_PASSWORD`。可配置 `PORT`，默认 8443。生产环境使用受信任证书；开发时生成本地证书并将证书导入客户端 Java 信任库，客户端不会跳过证书验证。不要提交密钥或密码。

## 初始化和运行

在交互终端运行 `java -jar server/target/server-0.1.0.jar --init-admin`，按提示输入管理员用户名和密码；创建完成后按 Ctrl+C 停止，正常运行 `java -jar server/target/server-0.1.0.jar`。初始化只允许数据库中没有管理员时执行；请保护数据库和服务器操作权限。

将 `AGENT_SERVER_URL` 设为服务端 HTTPS 地址（不带末尾斜杠），运行 `java -jar client/target/client-0.1.0.jar`。密码不回显，登录令牌保存在 Windows 凭据管理器中；不可用时仅保存在当前进程内。`/status` 查看当前用户；`/logout` 撤销令牌；`/exit` 仅退出进程，令牌将在服务端到期。

管理员登录接口 `POST /api/login` 接受 JSON `username` 和 `password`，返回令牌。使用 `Authorization: Bearer <令牌>` 调用 `POST /api/users` 创建普通用户（JSON 字段同上），并调用 `POST /api/logout` 撤销；不要在命令行参数中传入密码或令牌。用户名允许字母、数字、下划线、点和连字符，长度 3–100；密码至少 12 字符且 UTF-8 编码不超过 72 字节。

登录限流当前为单实例内存实现，按直接连接 IP 限制；多实例或反向代理部署需要共享限流及可信代理配置。PostgreSQL 16 集成和 Windows 交互已验证；完整安全审查仍需在发布前完成。Linux 暂不开发和测试。

## 数据库集成测试

集成测试会删除测试库中的 `users` 和 `auth_sessions` 数据，**只可连接专用测试数据库**。先准备空的 PostgreSQL 测试库，再设置 `TEST_DATABASE_URL`、`TEST_DATABASE_USER`、`TEST_DATABASE_PASSWORD`，并设置 `TLS_KEYSTORE=unused`、`TLS_KEYSTORE_PASSWORD=unused`（集成测试关闭 TLS，仅用于进程内 MockMvc），运行 `mvn verify`。未配置 `TEST_DATABASE_URL` 时集成测试会跳过；查看测试报告确认实际执行。

Windows PowerShell 使用 `$env:NAME='value'` 设置测试变量。CI 当前仅包含 Windows 构建；PostgreSQL 集成测试需单独配置测试数据库。

开发库备份可通过 `pg_dump -Fc` 导出到受限目录，恢复时用 `pg_restore` 导入新建的空库；先验证备份和数据库版本兼容，避免覆盖现有库。正式发布前应演练恢复及访问控制。

## V0.1 验证记录

2026-10-28：Windows 11、Java 17 和 PostgreSQL 16.15 下，`mvn verify` 完成空库迁移和认证集成测试；使用本地测试证书在 Windows 交互终端验证管理员初始化、HTTPS 登录、密码不回显、状态查询与退出。真实 HTTPS 交互使用独立 PostgreSQL 17 测试库；PostgreSQL 16 使用集成测试验证。测试容器已停止。
>>>>>>> 5b641c3 (实现用户登陆)

## Windows 免安装 Java 的客户端

在开发机上设置 `JAVA_HOME` 指向包含 `jpackage` 的 JDK 17，运行 `./scripts/package-client.ps1`，生成 `dist/agent/agent.exe` 及其内置运行环境。分发时必须复制整个 `dist/agent` 文件夹，不能只复制 exe。输出目录已存在时脚本会停止，避免覆盖文件。

在另一台 Windows 电脑的 PowerShell 中进入该文件夹，运行：

```powershell
.\agent.exe connect https://服务器IP:8443
```

自行签发的证书需通过可信渠道事先分发给客户端，使用 `--ca-cert C:\path\server.crt` 显式信任。证书的 subjectAltName 必须包含实际访问的 IP 或域名，服务端地址需被客户端网络访问，防火墙需按部署要求允许访问端口。不要关闭证书验证；公网部署应使用可信证书和安全网络配置。当前客户端为 V0.1，只支持登录、状态和退出；AI 对话、本机工作区和文件操作尚未实现。

## 一条命令启动客户端

把整个 `dist/agent` 文件夹复制到其他 Windows 电脑的固定位置，在该文件夹运行 `./install-client.ps1`，将该目录加入当前用户 PATH；不需要管理员权限。打开新的 PowerShell 窗口输入 `agent`。首次输入服务器 HTTPS 地址，以及自签名证书路径（使用系统信任时留空）；登录成功后客户端将地址和证书路径保存在 `%APPDATA%\terminal-agent\client.properties`，不保存密码。登录令牌保存在 Windows 凭据管理器，可用时自动恢复登录。之后运行 `agent` 即可连接并登录。服务器或证书变更时用 `agent connect https://IP:端口 --ca-cert 证书路径` 更新，正常证书无需 `--ca-cert`。安装脚本不会复制程序，移动文件夹后需重新运行；移除可从用户 PATH 手动删除对应目录。

PowerShell 中的 `/agent` 不是普通命令名，请使用 `agent`；程序内部的 `/status`、`/logout` 和 `/exit` 保持不变。

## 简化启动服务端（Windows）

在项目目录执行 `./start-server.ps1`。默认连接本机 `localhost:5432/terminal_agent`、用户 `agent`，并启动现有 `agent-postgres-1` 容器。首次输入数据库密码和证书文件密码；脚本用 Windows 当前账户的加密机制保存到 `%APPDATA%\terminal-agent-server`，之后无需重新输入。默认读取 `%USERPROFILE%\terminal-agent-dev.p12`；如文件位于其他位置，在启动前设置 `TLS_KEYSTORE`。环境变量可覆盖默认数据库配置及密码。密码修改或保存错误时运行 `./start-server.ps1 -ResetCredentials` 重新输入。加密凭据与 Windows 用户及电脑绑定，不能直接拷贝到其他账号使用。首次设置只保存凭据，连接成功与否仍以启动结果为准。

## 登录凭据与安装包

客户端按服务端地址将登录令牌保存在 Windows 凭据管理器，启动时向服务端验证；令牌失效后重新登录。`/logout` 撤销并移除凭据；`/exit` 保留登录。凭据管理器不可用时只使用当前进程会话。不同服务端使用独立凭据。

可运行 `scripts/package-installer.ps1` 从已生成的应用目录创建 Windows 安装包；需要 JDK 17 的 jpackage 和 WiX Toolset 3。jpackage 不自动设置 PATH，安装后仍需在安装目录运行 `install-client.ps1`。现有应用目录包及安装脚本可直接使用。

## 直接通过 JAR 启动（.env 配置）

在项目根目录编辑 `.env`，填写数据库密码、证书路径和证书密码。启动数据库后，在同一目录运行 `java -jar server/target/server-0.1.0.jar`，程序自动读取 `.env`。配置文件含明文密码，已加入 `.gitignore`，应限制文件访问权限，勿共享或提交。路径相对于运行命令时的当前目录；环境变量可覆盖配置。`start-server.ps1` 是另一种选择，通过环境变量注入 Windows 用户加密凭据。

客户端内置 `server.crt` 公开证书，只在连接 `119.29.165.28` 且未指定自定义证书时使用。首次运行输入 `https://119.29.165.28:8443` 后不需要填写证书路径。服务器更换证书后须通过可信渠道核验新证书，并替换 `client/src/main/resources/trusted-server.crt` 重新打包；私钥不得放入客户端。

## V0.2 试运行配置

服务端 `.env` 中配置 `DEEPSEEK_API_KEY` 和 `DEEPSEEK_MODEL`；`DEEPSEEK_MAX_OUTPUT` 可选，默认 1024。当前不检查用户剩余配额，也不再使用 `DEEPSEEK_CONTEXT_LIMIT` 进行请求判断；实际用量仍记录，供应商调用会产生费用。密钥仅保存在服务端，不提交或分发。

部署新 JAR 前备份数据库。Flyway 会自动执行 V2、V3 迁移，新用户默认 Token 额度为 0；管理员在 `https://服务器地址:8443/admin.html` 登录并设置配额。用户在终端输入问题进行单轮对话，用 `/quota` 查看余额。当前无跨提问记忆及工具调用。

模型返回用量缺失、网络错误等情况标记为待核对并保留预留。管理后台可输入请求 ID、经核实的实际输入与输出 Token 和核对理由进行结算；不得把未知用量当成零。服务异常终止可能留下 `reserved` 状态，本版本尚未提供自动回收，处理前须核实上游请求状态。流式请求会实际消耗供应商额度；生产使用前应在测试账号上完成接口联调及安全审查。

## 当前 Token 策略

用户余额暂不限制提问：无额度或额度为负时仍可请求。每次请求保留用量记录，但不预留用户配额；供应商返回用量后仍计入已用量，失败且用量未知时标记待核对。余额可为负。服务端仍需要有效的 DeepSeek API 密钥和模型配置；模型服务调用仍会产生费用，系统不会因用户额度不足自动停止。

## Java 源码目录

客户端 Java 源码位于 `client/src/main/java`；服务端 Java 源码及测试位于 `server/src/main/java` 和 `server/src/test/java`。服务端仍由 `server/pom.xml` 单独构建，其数据库迁移、配置和管理后台资源仍在 `server/src/main/resources`。使用 `mvn -pl server -am package` 构建服务端，使用 `mvn -pl client -am package` 构建客户端。
