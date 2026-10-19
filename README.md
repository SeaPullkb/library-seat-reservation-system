# 高校图书馆座位预约与签到管理系统

AI/agent 接手入口：[仓库导航](AGENT_NAVIGATION.md)（文件职责与功能定位），修改前同时阅读 [开发约定](AGENTS.md)。

基于 Spring Boot 的毕业设计，采用 Thymeleaf 单体架构。当前已完成 M1–M6（T01–T22）：账号与权限、空间维护、预约签到完整流程、管理员预约管理、超管用户维护和统计图表。

个人中心、13个主要页面、安全检查、完整测试和交付材料已完成；当前25/26项，唯一待验收为Windows实机部署。完整规则见 [需求基线](docs/requirements.md)，进度见 [开发任务表](docs/tasks.md)，交付范围和未运行检查见 [最终验收](docs/acceptance.md)。

## 页面截图与交付材料

[查看全部18张实际截图](docs/screenshots/README.md)，包含13个主要页面和手机布局；截图使用隔离测试数据与受控业务时钟，不代表真实运营数据。

- [最终验收与145项测试](docs/acceptance.md)，[逐用例结果](docs/test-results.json)。
- [Windows部署手册](docs/deployment-windows.md)及`scripts/dev.ps1`准备、测试、启动、就绪检查。
- [架构/ER/状态图](docs/design.md)、[数据字典](docs/data-dictionary.md)、[论文与答辩素材](docs/defense.md)。
- 生成不含本地密码的源码交付包：`python3 scripts/package-delivery.py --output /path/outside-checkout/library-seat-source.zip`。

Windows启动助手（完整JDK 17及Docker Desktop已安装）：

```powershell
.\scripts\dev.ps1 -Action Setup
.\scripts\dev.ps1 -Action Verify
.\scripts\dev.ps1 -Action Start
# 另一个终端执行
.\scripts\dev.ps1 -Action Check
```

当前仅在Linux验证PowerShell可移植辅助逻辑，Windows Wrapper、Docker Desktop和实际启动未运行；T25未勾选。GitHub Actions配置Linux全量集成测试和Windows构建检查，本轮没有远端执行结果。

## 技术与目录

- Java 17、Maven 3.9.11（附 Windows / Linux Maven Wrapper，发行包固定 SHA-256）。
- Spring Boot 3.5.16、MyBatis-Plus Boot 3 Starter 3.5.17、MySQL 8.0。
- Thymeleaf、Bootstrap 5.3.8、ECharts 5.6.0（前端资源随应用打包，无需访问CDN）、Spring Security + Session、BCrypt。
- 业务时间统一使用 `Asia/Shanghai` 的 `Clock`，数据库连接时区一致。

```text
src/main/java/com/seapullkb/library/
  config/       初始化、业务时钟、定时任务
  entity/       实体与角色
  mapper/       MyBatis-Plus 数据访问
  security/     认证及接口权限
  service/      账号、空间、预约事务及状态流转
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
当前完整验证为145项，0失败、0错误、0跳过。测试范围和实际结果记录于 [M1测试说明](docs/testing.md)、[M2测试说明](docs/testing-m2.md)、[M3测试说明](docs/testing-m3.md)、[M4测试说明](docs/testing-m4.md)、[M5测试说明](docs/testing-m5.md) 和 [M6测试说明](docs/testing-m6.md)。

## 当前权限

| 路径 | 访问要求 |
|---|---|
| `/login`、`/register`、静态资源 | 公开 |
| `/` | 登录后按角色跳转 |
| `/student` 及子路径 | STUDENT |
| `/admin` 及子路径 | LIBRARIAN 或 SUPER_ADMIN |
| `/admin/users` 及子路径 | SUPER_ADMIN，查询与维护用户、新增管理员 |
| `/admin/rooms`、`/admin/rooms/{id}/seats` | 管理员及超管的阅览室、座位维护 |
| `/admin/reservations` | 管理员及超管，预约查询、筛选与分页 |
| `POST /admin/reservations/{id}/cancel` | 管理员及超管取消未签到违规预约，必须有原因和CSRF |
| `/admin/statistics` | 管理员及超管，使用统计与图表 |
| `/student/rooms`、`/student/rooms/{id}/seats` | 学生阅览室列表、日期时段座位查询与预约 |
| `/student/reservations` | 本人的预约记录、状态筛选及分页 |
| `/student/profile` | 本人的资料查询及修改；子路径`/password`验证当前密码后改密并注销旧会话 |
| `POST /student/rooms/{roomId}/seats/{seatId}/reservations` | 学生预约，必须有CSRF |
| `POST /student/reservations/{id}/cancel` | 本人提前取消，必须有CSRF |
| `POST /student/reservations/{id}/checkin` | 本人签到，必须有CSRF |
| `POST /student/reservations/{id}/checkout` | 本人签退，必须有CSRF |

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
- “我的预约”仅查询本人，支持五状态筛选和每页20条。已过截止的PENDING按EXPIRED、已到时段结束的USING按COMPLETED显示和筛选，查询不会写入状态；签到、签退时间展示数据库已保存的实际值。
- 只允许在时段开始前取消尚未签到的有效预约；取消记录保留操作者、原因和时间，重复取消不再次变更。取消后同日期时段可重新预约。

## 签到、签退和到期处理

- 在“我的预约”操作。普通预约从时段开始前15分钟至开始后15分钟可签到，截止时刻有效；超过普通截止才创建的补约，可在预约后10分钟内签到。后端取锁后读取服务器时间，不接收客户端签到时间。
- 超过截止的未签到记录转为EXPIRED并保留释放时间；座位重新开放，原学生也可重新预约。即使扫描尚未运行，直接签到也会提交过期状态后提示失败。
- 仅已签到的USING可主动签退，转为COMPLETED并保存实际签退时间。提前签退后，学生和座位在原日期时段继续占用；重复点击不会重写时间。
- 使用中记录到时段结束自动完成，签退时间记为该时段结束时刻；任务延迟或重启不会把补处理时刻当成实际签退时间，也不会覆盖已主动签退的记录。
- 默认启动后立即补处理，再由Spring定时任务每次执行完等待60秒扫描。每批最多读取200个ID，每条在独立事务中重新取锁和判断；单条失败记日志并在下轮重试，其他记录继续处理。
- 配置位于 `application.yml` 的 `app.reservation-scheduler`：`enabled`、`interval-ms`、`initial-delay-ms`。业务测试关闭后台调度避免干扰，独立集成测试启用并验证真正的Spring定时回调。
- 网页签到仅记录线上操作，不验证学生真实到馆；当前未引入二维码、定位或硬件。

## 后台预约、用户与统计

- 管理首页进入“管理预约”，可组合账号/姓名关键词、阅览室、使用日期、时段与五种状态查询，每页20条。有效未签到预约可在弹窗填写原因后违规取消，记录保留操作人、原因和时间；重复提交不覆盖首次记录。
- 超管进入“用户管理”，查询账号、姓名、角色及状态；维护姓名、班级和手机号，新增图书馆管理员，另行调整角色、启禁用或重置密码。账号名不可修改，不提供删除用户操作。
- 禁用或调整角色前先处理该用户的PENDING/USING预约；已过期但尚未由调度落库的记录也须先释放。不能禁用自己或修改自身角色，保留至少一个启用超管。账号变更不删除预约历史。
- 管理员初始/重置密码为12–64字符且UTF-8不超过72字节，页面不回显。密码、角色或状态变化后旧登录会话失效；重新登录使用新权限。项目按单实例部署，会话登记及账号管理串行保护均在当前JVM内。
- “查看数据统计”展示所选使用日期的预约人数、次数、签到率和已执行释放次数，以及截至今天的7天人数趋势、所选日期时段的各阅览室预约率。零分母显示“—”；图表附明细表，精确定义见需求基线。统计来自实际数据库，不自动生成演示预约。

## 云环境与后续开发

在当前云环境先执行 `bash /workspace/.cloud-setup/install.sh`，再在每个终端 `source /workspace/.cloud-setup/activate.sh`，以启用Java 17、Maven、代理及证书信任；这些是环境专用配置，不是Windows必需的项目文件。

应用功能与交付材料已完成；Windows实机部署T25仍待实际Windows环境验证。后续状态写入仍需遵循AGENTS.md的统一取锁顺序和条件更新，避免覆盖最新状态。

参考项目调查见 [docs/references.md](docs/references.md)。当前代码独立实现，没有复制未获授权的第三方项目源码。
