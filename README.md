<<<<<<< HEAD
# agent
agent的自主学习
=======
# Terminal Agent V0.1

Java 17、Maven 3.8+、PostgreSQL 16。当前版本提供认证基础，不含对话和工作区功能。

## 构建和测试

```powershell
mvn clean verify
```

## 数据库与 HTTPS

设置环境变量 `POSTGRES_PASSWORD` 后执行 `docker compose up -d`。数据库仅映射本机 5432 端口，持久化在 Docker volume；生产部署请自行配置独立数据库、密码、备份及访问控制。Flyway 在服务启动时执行迁移。

服务端必须配置 `DATABASE_URL`（例如 `jdbc:postgresql://localhost:5432/terminal_agent`）、`DATABASE_USER`、`DATABASE_PASSWORD`、`TLS_KEYSTORE`（PKCS12 文件路径）和 `TLS_KEYSTORE_PASSWORD`。可配置 `PORT`，默认 8443。生产环境使用受信任证书；开发时生成本地证书并将证书导入客户端 Java 信任库，客户端不会跳过证书验证。不要提交密钥或密码。

## 初始化和运行

在交互终端运行 `java -jar server/target/server-0.1.0.jar --init-admin`，按提示输入管理员用户名和密码；创建完成后按 Ctrl+C 停止，正常运行 `java -jar server/target/server-0.1.0.jar`。初始化只允许数据库中没有管理员时执行；请保护数据库和服务器操作权限。

将 `AGENT_SERVER_URL` 设为服务端 HTTPS 地址（不带末尾斜杠），运行 `java -jar client/target/client-0.1.0.jar`。密码不回显，令牌只保存在当前进程内。`/status` 查看当前用户；`/logout` 撤销令牌；`/exit` 仅退出进程，令牌将在服务端到期。

管理员登录接口 `POST /api/login` 接受 JSON `username` 和 `password`，返回令牌。使用 `Authorization: Bearer <令牌>` 调用 `POST /api/users` 创建普通用户（JSON 字段同上），并调用 `POST /api/logout` 撤销；不要在命令行参数中传入密码或令牌。用户名允许字母、数字、下划线、点和连字符，长度 3–100；密码至少 12 字符且 UTF-8 编码不超过 72 字节。

登录限流当前为单实例内存实现，按直接连接 IP 限制；多实例或反向代理部署需要共享限流及可信代理配置。PostgreSQL 16 集成和 Windows 交互已验证；完整安全审查仍需在发布前完成。Linux 暂不开发和测试。

## 数据库集成测试

集成测试会删除测试库中的 `users` 和 `auth_sessions` 数据，**只可连接专用测试数据库**。先准备空的 PostgreSQL 测试库，再设置 `TEST_DATABASE_URL`、`TEST_DATABASE_USER`、`TEST_DATABASE_PASSWORD`，并设置 `TLS_KEYSTORE=unused`、`TLS_KEYSTORE_PASSWORD=unused`（集成测试关闭 TLS，仅用于进程内 MockMvc），运行 `mvn verify`。未配置 `TEST_DATABASE_URL` 时集成测试会跳过；查看测试报告确认实际执行。

Windows PowerShell 使用 `$env:NAME='value'` 设置测试变量。CI 当前仅包含 Windows 构建；PostgreSQL 集成测试需单独配置测试数据库。

开发库备份可通过 `pg_dump -Fc` 导出到受限目录，恢复时用 `pg_restore` 导入新建的空库；先验证备份和数据库版本兼容，避免覆盖现有库。正式发布前应演练恢复及访问控制。

## V0.1 验证记录

2026-10-28：Windows 11、Java 17 和 PostgreSQL 16.15 下，`mvn verify` 完成空库迁移和认证集成测试；使用本地测试证书在 Windows 交互终端验证管理员初始化、HTTPS 登录、密码不回显、状态查询与退出。真实 HTTPS 交互使用独立 PostgreSQL 17 测试库；PostgreSQL 16 使用集成测试验证。测试容器已停止。
>>>>>>> 5b641c3 (实现用户登陆)
