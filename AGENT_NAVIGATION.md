# 给其他 AI / Agent 的仓库导航

这是一份按职责定位文件的地图。开始修改前先读[AGENTS.md](AGENTS.md)中的约定；本导航不替代业务需求或开发规则。涉及目录、入口或职责变动时同步更新本文件。

## 建议阅读顺序

1. [AGENTS.md](AGENTS.md)：技术栈、事务、权限与历史保护的开发约定。
2. [README.md](README.md)：如何准备环境、启动应用及查看截图。
3. [docs/requirements.md](docs/requirements.md)：已确认的业务规则和统计口径。
4. [docs/tasks.md](docs/tasks.md)、[docs/acceptance.md](docs/acceptance.md)：已完成内容、实际验收及未运行检查。
5. 按下表找到Controller→Service→Mapper，再查看对应模板和测试。

截至2026-10-19，应用功能及交付材料完成，任务25/26；唯一未勾选的是T25真实Windows部署验收。最近完整验证为145项通过、0失败/错误/跳过，见[结果记录](docs/test-results.json)；这是已有运行证据，不表示阅读本导航时重新执行过。

## 根目录与工具

| 路径 | 用途 |
|---|---|
| [pom.xml](pom.xml) | Java 17、Spring Boot、MyBatis-Plus、MySQL、Thymeleaf、Bootstrap/ECharts及测试依赖。 |
| [mvnw](mvnw)、[mvnw.cmd](mvnw.cmd)、[Wrapper配置](.mvn/wrapper/maven-wrapper.properties) | Linux/macOS与Windows构建入口，固定Maven发行版及校验值。 |
| [compose.yaml](compose.yaml) | 本地MySQL、健康检查、仅本机端口、持久卷；不包含应用容器。 |
| [.env.example](.env.example) | 配置名称及空密码模板；本地`.env`被忽略，不应读取后贴到日志或提交。 |
| [scripts/init-dev-env.py](scripts/init-dev-env.py) | 仅在缺失时生成随机开发绑定，不覆盖已有配置。 |
| [scripts/dev.ps1](scripts/dev.ps1) | Windows的Setup/Verify/Start/Package/Check助手；实际Windows验收仍待执行。 |
| [scripts/package-delivery.py](scripts/package-delivery.py) | 从跟踪文件生成源码ZIP及SHA-256清单，保留Wrapper执行权限，排除私有配置和缓存。 |
| [.github/workflows/ci.yml](.github/workflows/ci.yml) | Linux真实MySQL测试、Windows解析/构建检查；配置存在不代表远端执行通过。 |
| [.gitignore](.gitignore)、[.gitattributes](.gitattributes) | 忽略本地密码、构建和IDE文件；控制脚本换行等属性。 |
| `target/` | Maven生成的classes、测试报告和jar，可重建，不作为源文件编辑。 |

## 按功能快速找入口

| 需求 | Web入口 | 主要业务 / SQL | 页面 |
|---|---|---|---|
| 注册登录与三角色权限 | `AccountController`、`HomeController` | `AccountService`、`SecurityConfig`、`CurrentAccountFilter` | `auth/`、角色`home.html` |
| 阅览室、座位维护 | `AdminSpaceController` | `AdminAccess`、`SpaceService`、空间Mapper与`ReservationGuardMapper` | `admin/rooms.html`、`seats.html` |
| 学生浏览和选座 | `StudentSpaceController`、`ReservationController` | `SpaceService`、`ReservationService`、`ReservationMapper`、`SeatMapper` | `student/rooms.html`、`seats.html` |
| 本人记录、取消、签到签退 | `ReservationController` | `ReservationService`、`ReservationLifecycleService` | `student/reservations.html` |
| 到期释放和自动完成 | 无产品手动触发接口 | `ReservationScheduler`→`ReservationMaintenance`→`ReservationLifecycleService` | 本人记录及座位状态随结果变化 |
| 个人资料、密码 | `StudentProfileController` | `StudentProfileService`、`AccountSessions`、`UserMapper` | `student/profile.html` |
| 管理员违规取消 | `AdminReservationController` | `AdminReservationService`、`AdminReservationMapper` | `admin/reservations.html` |
| 超管用户维护 | `UserAdminController` | `UserAdminService`、`AdminAccess`、`UserMapper` | `admin/users.html`、`user-new.html`、`user-edit.html` |
| 统计口径或图表 | `StatisticsController` | `StatisticsService`、`StatisticsMapper` | `admin/statistics.html`、`statistics.js` |

## Java文件职责

源码公共前缀为`src/main/java/com/seapullkb/library/`，以下链接逐个对应实际文件。

### 应用启动与配置

| 文件 | 职责 |
|---|---|
| [LibraryApplication.java](src/main/java/com/seapullkb/library/LibraryApplication.java) | Spring Boot主入口，启动组件扫描。 |
| [config/InitialDataRunner.java](src/main/java/com/seapullkb/library/config/InitialDataRunner.java) | 按profile触发初始化；test模式不自动创建演示数据。 |
| [config/TimeConfig.java](src/main/java/com/seapullkb/library/config/TimeConfig.java) | 提供Asia/Shanghai业务Clock；测试可注入固定或可变时钟。 |
| [config/ReservationSchedulingConfig.java](src/main/java/com/seapullkb/library/config/ReservationSchedulingConfig.java) | 按配置启用Spring调度并注册清理任务。 |
| [config/ReservationScheduler.java](src/main/java/com/seapullkb/library/config/ReservationScheduler.java) | 应用就绪补处理、周期扫描、避免同进程任务重叠。 |

### 认证、权限与会话

| 文件 | 职责 |
|---|---|
| [security/SecurityConfig.java](src/main/java/com/seapullkb/library/security/SecurityConfig.java) | 路由权限、表单登录、CSRF、Session登记、安全响应头及密码编码器。 |
| [security/AccountPrincipal.java](src/main/java/com/seapullkb/library/security/AccountPrincipal.java) | 认证主体；原凭据擦除后保留密码散列指纹，用于识别旧凭据会话。 |
| [security/CurrentAccountFilter.java](src/main/java/com/seapullkb/library/security/CurrentAccountFilter.java) | 每请求核对数据库中的启用状态、角色和密码指纹，注销失效主体。 |
| [service/AccountService.java](src/main/java/com/seapullkb/library/service/AccountService.java) | 账号查询、学生注册及BCrypt存储，注册角色固定为STUDENT。 |
| [service/AccountSessions.java](src/main/java/com/seapullkb/library/service/AccountSessions.java) | 事务提交后批量使指定用户的已登记会话失效。 |
| [service/AdminAccess.java](src/main/java/com/seapullkb/library/service/AdminAccess.java) | 重新校验管理权限，按ID顺序锁操作者和目标，包装空间维护写入。 |
| [service/InitialDataService.java](src/main/java/com/seapullkb/library/service/InitialDataService.java) | 初始化账号和演示空间；保留已有密码及数据，不生成预约历史。 |

### 业务服务：修改规则先看这里

| 文件 | 职责 |
|---|---|
| [service/SpaceService.java](src/main/java/com/seapullkb/library/service/SpaceService.java) | 阅览室和座位增改启停删除、历史保护、容量与学生座位网格。 |
| [service/ReservationRules.java](src/main/java/com/seapullkb/library/service/ReservationRules.java) | 7天预约范围、结束前30分钟截止、普通及补约签到截止计算。 |
| [service/ReservationService.java](src/main/java/com/seapullkb/library/service/ReservationService.java) | 创建预约、学生提前取消、本人记录查询和冲突判断。 |
| [service/ReservationLifecycleService.java](src/main/java/com/seapullkb/library/service/ReservationLifecycleService.java) | 签到、签退、逐条过期释放与到期自动完成。 |
| [service/ReservationMaintenance.java](src/main/java/com/seapullkb/library/service/ReservationMaintenance.java) | 按ID分批扫描到期记录，逐条处理、统计结果及隔离失败。 |
| [service/ReservationLocks.java](src/main/java/com/seapullkb/library/service/ReservationLocks.java) | 共享用户→阅览室→座位→预约取锁顺序，区分本人操作与系统清理。 |
| [service/ReservationTransactions.java](src/main/java/com/seapullkb/library/service/ReservationTransactions.java) | 独立READ_COMMITTED事务；只对MySQL死锁1213完整回滚后有限重试。 |
| [service/AdminReservationService.java](src/main/java/com/seapullkb/library/service/AdminReservationService.java) | 管理员条件查询及未签到违规取消，保留首次取消审计。 |
| [service/UserAdminService.java](src/main/java/com/seapullkb/library/service/UserAdminService.java) | 超管用户查询、新增管理员、资料/角色/状态/密码维护及账号保护。 |
| [service/StudentProfileService.java](src/main/java/com/seapullkb/library/service/StudentProfileService.java) | 本人资料修改、锁内核对当前密码、改密后注销会话。 |
| [service/StatisticsService.java](src/main/java/com/seapullkb/library/service/StatisticsService.java) | 汇总统计卡片、最近7天趋势和阅览室预约率。 |
| [service/QueryDates.java](src/main/java/com/seapullkb/library/service/QueryDates.java) | 后台查询日期的MySQL支持范围校验。 |

### 数据库访问：SQL与实体

| 文件 | 职责 |
|---|---|
| [mapper/UserMapper.java](src/main/java/com/seapullkb/library/mapper/UserMapper.java) | 用户查询与行锁、资料/权限/密码更新、未处理预约和超管数量检查。 |
| [mapper/ReadingRoomMapper.java](src/main/java/com/seapullkb/library/mapper/ReadingRoomMapper.java) | 阅览室行锁、启用座位容量和列表汇总。 |
| [mapper/SeatMapper.java](src/main/java/com/seapullkb/library/mapper/SeatMapper.java) | 座位行锁及指定日期时段的网格占用查询。 |
| [mapper/ReservationMapper.java](src/main/java/com/seapullkb/library/mapper/ReservationMapper.java) | 预约冲突、本人记录、有效状态投影、条件状态更新、到期ID扫描。 |
| [mapper/ReservationGuardMapper.java](src/main/java/com/seapullkb/library/mapper/ReservationGuardMapper.java) | 空间维护前的有效预约检查及座位历史数量。 |
| [mapper/AdminReservationMapper.java](src/main/java/com/seapullkb/library/mapper/AdminReservationMapper.java) | 管理员预约过滤、分页和带操作者/原因的条件取消SQL。 |
| [mapper/StatisticsMapper.java](src/main/java/com/seapullkb/library/mapper/StatisticsMapper.java) | 人数、次数、签到率分子分母、释放次数、趋势及座位占用聚合。 |
| [entity/UserAccount.java](src/main/java/com/seapullkb/library/entity/UserAccount.java) | user表实体。 |
| [entity/ReadingRoom.java](src/main/java/com/seapullkb/library/entity/ReadingRoom.java) | reading_room表实体。 |
| [entity/Seat.java](src/main/java/com/seapullkb/library/entity/Seat.java) | seat表实体；status只表示管理启用状态。 |
| [entity/Reservation.java](src/main/java/com/seapullkb/library/entity/Reservation.java) | reserve_record表实体；关联各时间及取消审计字段。 |
| [entity/Role.java](src/main/java/com/seapullkb/library/entity/Role.java) | STUDENT、LIBRARIAN、SUPER_ADMIN三种角色。 |
| [entity/ReservationStatus.java](src/main/java/com/seapullkb/library/entity/ReservationStatus.java) | 五种预约状态及页面标签。 |
| [entity/TimeSlot.java](src/main/java/com/seapullkb/library/entity/TimeSlot.java) | 三个固定时段、开始结束、最早签到和普通截止时间。 |

### Web入口：路由、表单与错误

| 文件 | 职责 |
|---|---|
| [web/HomeController.java](src/main/java/com/seapullkb/library/web/HomeController.java) | /按角色跳转、学生/管理首页和权限提示页。 |
| [web/AccountController.java](src/main/java/com/seapullkb/library/web/AccountController.java) | /login与/register；注册校验和重复账号反馈。 |
| [web/StudentSpaceController.java](src/main/java/com/seapullkb/library/web/StudentSpaceController.java) | /student/rooms及座位页，接收日期、时段和只看空闲条件。 |
| [web/ReservationController.java](src/main/java/com/seapullkb/library/web/ReservationController.java) | 学生预约提交、本人列表、取消、签到、签退。 |
| [web/StudentProfileController.java](src/main/java/com/seapullkb/library/web/StudentProfileController.java) | /student/profile资料和密码表单，成功改密后退出当前登录。 |
| [web/AdminSpaceController.java](src/main/java/com/seapullkb/library/web/AdminSpaceController.java) | /admin/rooms及座位维护表单，写入经AdminAccess保护。 |
| [web/AdminReservationController.java](src/main/java/com/seapullkb/library/web/AdminReservationController.java) | /admin/reservations筛选、分页、违规取消。 |
| [web/UserAdminController.java](src/main/java/com/seapullkb/library/web/UserAdminController.java) | /admin/users用户查询、新增管理员、资料、角色、启用及密码表单。 |
| [web/StatisticsController.java](src/main/java/com/seapullkb/library/web/StatisticsController.java) | /admin/statistics日期时段选择和统计模型。 |
| [web/PageAdvice.java](src/main/java/com/seapullkb/library/web/PageAdvice.java) | 为所有页面提供登录状态及角色导航属性。 |
| [web/SpaceErrorAdvice.java](src/main/java/com/seapullkb/library/web/SpaceErrorAdvice.java) | 全局受控400/403/404/405/409/500反馈及客户端下载中断处理；名称保留历史命名。 |
| [web/RegistrationForm.java](src/main/java/com/seapullkb/library/web/RegistrationForm.java) | 学生注册白名单字段、账号格式、密码确认及长度校验。 |
| [web/RoomForm.java](src/main/java/com/seapullkb/library/web/RoomForm.java) | 阅览室名称、位置、介绍校验。 |
| [web/SeatForm.java](src/main/java/com/seapullkb/library/web/SeatForm.java) | 座位编号校验。 |
| [web/BookingForm.java](src/main/java/com/seapullkb/library/web/BookingForm.java) | 预约日期和时段校验，不能指定用户或状态。 |
| [web/StudentProfileForm.java](src/main/java/com/seapullkb/library/web/StudentProfileForm.java) | 学生姓名、班级、手机号白名单。 |
| [web/StudentPasswordForm.java](src/main/java/com/seapullkb/library/web/StudentPasswordForm.java) | 当前密码、新密码、确认及UTF-8字节限制。 |
| [web/AdminCreateForm.java](src/main/java/com/seapullkb/library/web/AdminCreateForm.java) | 新增管理员白名单及初始密码校验。 |
| [web/UserEditForm.java](src/main/java/com/seapullkb/library/web/UserEditForm.java) | 超管编辑用户基本资料的白名单。 |
| [web/PasswordResetForm.java](src/main/java/com/seapullkb/library/web/PasswordResetForm.java) | 管理员重置密码及确认校验。 |

### 页面返回对象与业务异常

| 文件 | 职责 |
|---|---|
| [service/RoomSummary.java](src/main/java/com/seapullkb/library/service/RoomSummary.java) | 阅览室列表与实时启用/总座位数。 |
| [service/SeatView.java](src/main/java/com/seapullkb/library/service/SeatView.java) | 网格中的座位、启用状态、空闲/占用/本人预约标签。 |
| [service/ReservationSummary.java](src/main/java/com/seapullkb/library/service/ReservationSummary.java) | 本人预约展示、实际时间及可签到/签退/取消判断。 |
| [service/ReservationPage.java](src/main/java/com/seapullkb/library/service/ReservationPage.java) | 本人预约分页结果。 |
| [service/AdminReservationRow.java](src/main/java/com/seapullkb/library/service/AdminReservationRow.java) | 管理员预约行，含学生和取消执行人信息。 |
| [service/AdminReservationFilter.java](src/main/java/com/seapullkb/library/service/AdminReservationFilter.java) | 管理员预约查询条件的规范化和校验。 |
| [service/AdminPage.java](src/main/java/com/seapullkb/library/service/AdminPage.java) | 后台通用分页结果及页码校验。 |
| [service/UserSummary.java](src/main/java/com/seapullkb/library/service/UserSummary.java) | 不含密码的用户展示对象。 |
| [service/DailyStatistics.java](src/main/java/com/seapullkb/library/service/DailyStatistics.java) | 按使用日期汇总的指标及签到率。 |
| [service/RoomStatistics.java](src/main/java/com/seapullkb/library/service/RoomStatistics.java) | 阅览室启用/占用座位数及预约率。 |
| [service/LifecycleResult.java](src/main/java/com/seapullkb/library/service/LifecycleResult.java) | 状态操作的变更、幂等、过期及自动完成结果。 |
| [service/ReservationConflictException.java](src/main/java/com/seapullkb/library/service/ReservationConflictException.java) | 预约时间、状态或冲突不满足时的业务异常。 |
| [service/MaintenanceConflictException.java](src/main/java/com/seapullkb/library/service/MaintenanceConflictException.java) | 维护或改密保护规则不满足时的业务异常。 |
| [service/ResourceNotFoundException.java](src/main/java/com/seapullkb/library/service/ResourceNotFoundException.java) | 资源不存在或不属于当前操作者时的异常。 |

## 资源、页面与数据库

所有资源位于`src/main/resources/`。页面只渲染和提交表单，权限与状态规则由后端验证。

| 文件或目录 | 用途 |
|---|---|
| [application.yml](src/main/resources/application.yml) | 数据库、Session、错误信息、MyBatis与调度默认配置。 |
| [application-dev.yml](src/main/resources/application-dev.yml) | dev模式的演示密码绑定，不内置真实密码。 |
| [db/schema.sql](src/main/resources/db/schema.sql) | 四表、索引、外键与CHECK约束；只创建缺失表，不迁移已有结构。 |
| [templates/fragments/layout.html](src/main/resources/templates/fragments/layout.html) | 共用head、角色导航、跳至正文、页脚、本地CSS/JS引用。 |
| [templates/auth/](src/main/resources/templates/auth) | login.html登录、register.html学生注册。 |
| [templates/student/](src/main/resources/templates/student) | home.html首页、rooms.html阅览室、seats.html选座、reservations.html本人记录与签到签退、profile.html资料与密码。 |
| [templates/admin/](src/main/resources/templates/admin) | home.html管理首页、rooms.html阅览室、seats.html座位、reservations.html审计取消、statistics.html图表、users.html用户列表、user-new.html新增管理员、user-edit.html用户维护。 |
| [templates/error.html](src/main/resources/templates/error.html)、[templates/error/](src/main/resources/templates/error) | 默认错误页；403.html权限提示、space.html受控业务/参数/系统错误。 |
| [static/css/app.css](src/main/resources/static/css/app.css) | 共用颜色、卡片、座位网格、响应式、焦点与导航样式。 |
| [static/js/app.js](src/main/resources/static/js/app.js) | 弹窗、导航高亮、错误焦点、确认提示、重复提交和返回后恢复。 |
| [static/js/statistics.js](src/main/resources/static/js/statistics.js) | 读取不可执行JSON数据、绘制和缩放ECharts；不决定统计口径。 |

Bootstrap和ECharts由pom中的WebJar依赖提供，通过`/webjars/`访问；不应到static目录寻找或重复手工复制这些库。

## 测试：按修改范围选择

测试路径为`src/test/java/com/seapullkb/library/`。集成测试使用真实MySQL Testcontainers，需要Docker；test profile关闭普通后台调度，调度专用用例独立启用。

| 测试类 | 最近数量 | 覆盖内容 |
|---|---|---|
| [FoundationIntegrationTest](src/test/java/com/seapullkb/library/FoundationIntegrationTest.java) | 22 | 初始化、注册、登录、三角色、CSRF、BCrypt、Session退出。 |
| [SpaceIntegrationTest](src/test/java/com/seapullkb/library/SpaceIntegrationTest.java) | 17 | 空间CRUD、编号唯一、历史保护、停用与座位网格。 |
| [ReservationIntegrationTest](src/test/java/com/seapullkb/library/ReservationIntegrationTest.java) | 25 | 预约、本人查询分页、提前取消、双向冲突及与维护的真实并发。 |
| [ReservationRulesTest](src/test/java/com/seapullkb/library/ReservationRulesTest.java) | 5 | 日期、时段、预约和补约时间规则单元测试。 |
| [LifecycleIntegrationTest](src/test/java/com/seapullkb/library/LifecycleIntegrationTest.java) | 23 | 签到、签退、过期、自动完成、幂等与竞态。 |
| [SchedulerIntegrationTest](src/test/java/com/seapullkb/library/SchedulerIntegrationTest.java) | 1 | 真实Spring启动补处理和定时回调。 |
| [ReservationMaintenanceTest](src/test/java/com/seapullkb/library/ReservationMaintenanceTest.java) | 1 | 单条失败隔离与扫描计数，故障日志是刻意模拟。 |
| [AdminIntegrationTest](src/test/java/com/seapullkb/library/AdminIntegrationTest.java) | 32 | 管理员过滤/取消、用户维护、会话失效、统计及并发。 |
| [DeliveryIntegrationTest](src/test/java/com/seapullkb/library/DeliveryIntegrationTest.java) | 19 | 个人中心、旧凭据指纹、全写入CSRF、完整流程和安全错误页。 |

[application-test.yml](src/test/resources/application-test.yml)用于测试配置。故障注入、Clock控制与失败端点只属于测试或外部验证工具，不能加进产品路由。

```bash
./mvnw -B -ntp verify
./mvnw -B -ntp -Dtest=ReservationIntegrationTest,LifecycleIntegrationTest test
```

Windows使用`mvnw.cmd`；启动与环境导出细节按README。只改导航或说明时检查链接和差异即可，不必把未重跑的业务测试写成此次通过。

## 文档与交付材料

| 文件或目录 | 查什么 |
|---|---|
| [docs/requirements.md](docs/requirements.md) | 固定时段、身份、状态、冲突、取消与统计口径。 |
| [docs/tasks.md](docs/tasks.md) | T01–T26及阶段进度，保留未验收项目。 |
| [docs/acceptance.md](docs/acceptance.md) | B01–B15证据、并发检查、交付索引和外部环境限制。 |
| [docs/test-results.json](docs/test-results.json) | 上次完整执行的提交、平台、逐类和逐用例结果。 |
| [docs/testing.md](docs/testing.md)、[M2](docs/testing-m2.md)、[M3](docs/testing-m3.md)、[M4](docs/testing-m4.md)、[M5](docs/testing-m5.md)、[M6](docs/testing-m6.md) | 历史阶段的真实测试、发现与修复；较早阶段的“未实现”描述是历史记录。 |
| [docs/design.md](docs/design.md)、[docs/diagrams/](docs/diagrams) | 架构、ER和状态图；.dot是可编辑源，.svg是导出图。 |
| [docs/data-dictionary.md](docs/data-dictionary.md) | 四表字段、约束、索引及时间精度。 |
| [docs/screenshots/README.md](docs/screenshots/README.md) | 18张实际截图索引；PNG为原图，manifest.json是尺寸及校验值。 |
| [docs/deployment-windows.md](docs/deployment-windows.md) | Windows准备、启动、排错及待填写实机验收表。 |
| [docs/defense.md](docs/defense.md) | 论文技术素材、演示步骤、答辩问题与系统边界。 |
| [docs/references.md](docs/references.md)、[docs/licenses/](docs/licenses) | 相近项目调查、依赖/Wrapper授权说明及许可证副本。 |

## 接手修改前的核对

- 先运行`git status --short`，保留用户改动；再用`rg`定位已有入口，避免另写一套同职责业务。
- 调整时间、冲突或状态规则时，同时检查`ReservationRules`、`TimeSlot`、Mapper有效状态SQL、页面按钮和对应边界/并发测试。
- 调整账号权限或会话时，检查`SecurityConfig`、`CurrentAccountFilter`、`AdminAccess`、`AccountSessions`和对应Service，不能只隐藏按钮。
- 调整数据库时先核对schema及数据字典，保留历史；需要结构变更时提供显式迁移，不靠删除卷。
- 本地`.env`、数据库卷、`target/`和云环境的`/workspace/.cloud-setup/`不是业务源码；环境专用缓存或日志不应成为应用运行依赖。
- 最终同步导航、受影响文档和验证结果；提交/推送遵循AGENTS.md及用户授权。
