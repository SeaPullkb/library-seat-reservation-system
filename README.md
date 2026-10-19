# 高校图书馆座位预约与签到管理系统

基于 Spring Boot 的毕业设计，采用 Thymeleaf 单体架构。当前已完成 M1–M3（T01–T12）：工程、四表初始化、注册登录、角色权限、空间维护、座位预约、我的预约和提前取消。

**签到、签退、定时释放和统计尚未实现**，页面明确标注后续开放的功能。完整规则见 [需求基线](docs/requirements.md)，进度见 [开发任务表](docs/tasks.md)。

## 技术与目录

- Java 17、Maven 3.9.11（附 Windows / Linux Maven Wrapper，发行包固定 SHA-256）。
- Spring Boot 3.5.16、MyBatis-Plus Boot 3 Starter 3.5.17、MySQL 8.0。
- Thymeleaf、Bootstrap 5.3.8（随应用打包，无需访问CDN）、Spring Security + Session、BCrypt。
- 业务时间统一使用 `Asia/Shanghai` 的 `Clock`，数据库连接时区一致。

```text
src/main/java/com/seapullkb/library/
  config/       初始化、业务时钟
  entity/       实体与角色
  mapper/       MyBatis-Plus 数据访问
  security/     认证及接口权限
  service/      账号及初始化业务
  web/          表单、控制器、页面公共数据
src/main/resources/
  db/schema.sql 四表结构、索引、外键与检查约束
  templates/    Thymeleaf 页面及公共布局
  static/css/   本地样式
src/test/       真实 MySQL 集成测试
docs/           需求、任务、测试及参考调查
```

## 本地快速启动（Docker MySQL）

前提：安装 **完整 JDK 17**、Docker Engine / Docker Desktop 及 Docker Compose。无需单独安装 Maven。

1. 配置本地环境文件。已安装 Python 3 时，可执行：

   ```bash
   python3 scripts/init-dev-env.py
   ```

   此命令为数据库和演示账号生成随机密码，仅保存到忽略的 `.env` 中，不输出密码、不覆盖已有文件。没有 Python 时，将 `.env.example` 复制成 `.env`，填写 `DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`APP_DEMO_PASSWORD`；演示密码至少12个字符且UTF-8不超过72字节。

2. 启动数据库：

   ```bash
   docker compose up -d --wait
   ```

   Compose 自动读取 `.env`，初始化 `library_seat` 数据库及应用用户。仅将MySQL端口映射到本机，数据保存在命名卷中。不要运行 `docker compose down -v`，除非明确希望删除本地数据库。

3. 将 `.env` 变量导出给 Java 进程，再启动应用。

   Linux / macOS / Git Bash：

   ```bash
   set -a
   source .env
   set +a
   ./mvnw spring-boot:run
   ```

   Windows PowerShell：

   ```powershell
   Get-Content .env | ForEach-Object {
       if ($_ -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
           [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
       }
   }
   .\mvnw.cmd spring-boot:run
   ```

   应用默认只监听本机8080端口，在本地浏览器打开登录页即可。云环境配置阶段不提供网页预览链接。

`.env` 被 Compose 自动加载，但 Spring Boot **不会**自动读取它，第三步的导出不可省略。

## 演示账号与数据

仅当 `SPRING_PROFILES_ACTIVE=dev` 时创建以下演示数据：

| 账号 | 角色 | 密码来源 |
|---|---|---|
| student | 学生 | 本地 `.env` 的 `APP_DEMO_PASSWORD` |
| librarian | 图书馆管理员 | 同上 |
| admin | 超级管理员 | 同上 |

另有第一阅览室、A01–A20共20个座位；**不生成虚假的预约或统计记录**。
重复启动不会覆盖已有账号密码或重复添加演示座位。修改 `.env` 不会重置数据库内现有账号密码，也不会修改已初始化的MySQL卷密码。

默认配置不启用 `dev`；非开发模式首次启动通过 `APP_BOOTSTRAP_ADMIN_PASSWORD` 创建 `admin`，不会创建学生、图书馆管理员或演示阅览室。已有启用的超级管理员时不需要重复提供该密码。真实部署还需按部署环境配置HTTPS和安全Session Cookie。

## 使用现有 MySQL 8

也可以跳过 Compose，在本地MySQL中创建采用 `utf8mb4` 的 `library_seat` 数据库和具有该库表结构/数据读写权限的专用用户，再设置 `.env` 中的 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME` 和 `DB_PASSWORD`。

应用启动执行 `src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`），由 Java 初始化器使用 BCrypt 写入初始账号。也可先用 Navicat 或MySQL客户端手工导入该SQL。不会删除已有表或数据。

该脚本用于初始建表，**不会迁移已有表结构**；后续更改结构时需要单独的迁移脚本。不得用删库重建代替迁移。

## 构建和测试

```bash
./mvnw -B -ntp verify
```

Windows 使用 `mvnw.cmd`。测试由 Testcontainers 自动启动独立的真实MySQL 8，结束后清理，不会连接开发数据库；需要Docker可用和首次拉取镜像的网络权限。数据库/权限测试不使用H2，也不会在Docker不可用时悄悄跳过。

只打包、不运行测试时显式执行：

```bash
./mvnw -B -ntp -DskipTests package
```

随后可用 `java -jar target/library-seat-reservation-system-0.1.0-SNAPSHOT.jar` 启动，仍需先导出数据库和初始账号变量。
测试范围和实际结果记录于 [M1测试说明](docs/testing.md)、[M2测试说明](docs/testing-m2.md) 和 [M3测试说明](docs/testing-m3.md)。

## 当前权限

| 路径 | 访问要求 |
|---|---|
| `/login`、`/register`、静态资源 | 公开 |
| `/` | 登录后按角色跳转 |
| `/student` 及子路径 | STUDENT |
| `/admin` 及子路径 | LIBRARIAN 或 SUPER_ADMIN |
| `/admin/users` 及子路径 | SUPER_ADMIN（当前仅权限入口，完整管理为T19） |
| `/admin/rooms`、`/admin/rooms/{id}/seats` | 管理员及超管的阅览室、座位维护 |
| `/student/rooms`、`/student/rooms/{id}/seats` | 学生阅览室列表、日期时段座位查询与预约 |
| `/student/reservations` | 本人的预约记录、状态筛选及分页 |
| `POST /student/rooms/{roomId}/seats/{seatId}/reservations` | 学生预约，必须有CSRF |
| `POST /student/reservations/{id}/cancel` | 本人提前取消，必须有CSRF |

普通注册只创建学生，忽略客户端提交的角色、启用状态和ID。禁用账号不能登录；注销必须携带CSRF令牌并使Session失效。

## 阅览室与座位管理

- 管理首页进入“管理阅览室与座位”，可用弹窗新增或编辑阅览室、位置、介绍；容量由启用座位数实时计算。
- 进入阅览室的“管理座位”，可添加、修改编号、启停用和删除座位。同一阅览室的编号唯一（数据库不区分大小写，输入首尾空白会去除）。座位不允许在阅览室之间搬移。
- 当前或未来仍有效的待签到/使用中预约阻止座位及所属阅览室停用。停用阅览室不会重写各座位自己的启用状态。
- 任何预约历史都阻止座位物理删除；可在处理完有效预约后停用。编辑编号保持座位ID不变，历史关联继续有效。
- 学生只看见开放阅览室，可切换今天及未来6天、上午/下午/晚上的座位状态。座位网格区分空闲、已占用、我的预约和已停用，已完成仍锁定对应时段，逾期未签到不视为有效占用。
- 网格支持只看空闲。开放预约且本人无同日期时段占用时，空闲座位显示预约按钮；确认座位、日期、时段后提交。距结束不足30分钟时不显示预约按钮，后端也拒绝直接提交。

## 预约与取消

- 支持今天及未来6个自然日，上午08:00–12:00、下午14:00–18:00、晚上19:00–22:00。
- 距结束恰好30分钟可预约，超过该截止时间则拒绝。普通预约的签到截止是时段开始后15分钟；超过普通截止才新预约的记录，截止为当前时间后10分钟，不延长时段。
- 学生与座位在同日期时段都不允许重复有效占用；PENDING未过期、USING及COMPLETED均阻止再次预约。用户ID、初始状态和签到截止由后端生成，不接受前端指定。
- 事务按学生→阅览室→座位→预约记录取锁，READ_COMMITTED下重新校验；与空间停用/删除操作协调。仅对MySQL死锁1213完整回滚后最多尝试三次，不重试业务冲突。
- 创建成功时可将目标座位的旧超时PENDING条件更新为EXPIRED，保留原历史；其他座位的过期记录按时间认定无效，不跨座位批量修改。
- “我的预约”仅查询本人，支持五状态筛选和每页20条。已过截止的PENDING按EXPIRED显示和筛选，查询不会写入状态。
- 只允许在时段开始前取消尚未签到的有效预约；取消记录保留操作者、原因和时间，重复取消不再次变更。取消后同日期时段可重新预约。
- **本阶段尚无签到、签退按钮或自动扫描任务**。虽然已记录补约截止并按时间判断过期，完整签到及补约闭环仍需M4。

## 云环境与后续开发

在当前云环境先执行 `bash /workspace/.cloud-setup/install.sh`，再在每个终端 `source /workspace/.cloud-setup/activate.sh`，以启用Java 17、Maven、代理及证书信任；这些是环境专用配置，不是Windows必需的项目文件。

下一阶段为T13–T17：签到、签退、超时自动释放、补约闭环及到期自动完成。后续状态写入仍需遵循AGENTS.md的统一取锁顺序和条件更新，避免覆盖最新状态。

参考项目调查见 [docs/references.md](docs/references.md)。当前代码独立实现，没有复制未获授权的第三方项目源码。
