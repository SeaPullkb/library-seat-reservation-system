# 开发约定

- 本任务已在独立环境中，使用现有检出目录；除非用户要求，不创建 Git worktree。
- 技术栈固定为 Java 17、Spring Boot 3、MyBatis-Plus Boot 3 Starter、MySQL 8、Thymeleaf、Bootstrap 5、Spring Security + Session。后续图表用 ECharts，定时任务用 @Scheduled。
- 保持单体服务，不增加 Vue、Redis、JWT、微服务或消息队列。
- 按 docs/tasks.md 的阶段开发。实现前检查已有代码，业务规则以 docs/requirements.md 为准。
- Controller 处理请求和页面，Service 负责业务和事务，Mapper 处理数据库。不信任前端提交的身份或角色。
- 保留 CSRF 校验、BCrypt 密码散列及服务端权限控制。禁止把真实密钥或密码提交到 Git。
- 数据库仅四张核心业务表。保留历史预约；不级联删除；不得对所有历史记录的 seat_id + reserve_date + time_slot 直接设唯一约束。
- 预约冲突保护必须覆盖学生与座位两方面；签到、释放、取消遵循统一锁顺序和条件更新，签到后的 COMPLETED 继续占用该日期时段。
- M2维护操作先锁阅览室、再锁座位。后续预约写入按用户→阅览室→座位→预约记录顺序取锁，不能绕过父阅览室锁，否则会与停用检查产生竞态。
- 服务器按 Asia/Shanghai 计算业务时间。
- 每阶段构建并运行测试。当前集成测试使用 Testcontainers 的真实 MySQL 8，需要 Docker，不能用 H2 替代并发及数据库验收。
- 不把跳过的检查标为通过。更新任务进度、测试说明和 README 后再提交；未经授权不推送或合并分支。
