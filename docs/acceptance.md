# 最终验收与交付索引

本文件保留V1.0阶段的历史记录；当前V1.1进度和实测结果见 [V1.1验收](acceptance-v1.1.md)。

日期：2026-10-19（Asia/Shanghai）。应用实现对应提交`1936039`，后续提交补充交付文档、截图和脚本。

## 实际结果

- Linux、JDK 17、Maven 3.9.11、真实MySQL 8环境执行`./mvnw -B -ntp verify`：**145项，失败0、错误0、跳过0，BUILD SUCCESS**。
- 其中139项为Testcontainers集成用例，5项时间规则单元测试、1项故障隔离测试。没有连接日常开发数据库，没有用H2替代，也没有Docker不可用时自动跳过。
- 独立Chromium完成注册到统计的完整流程，实际检查18张页面截图、手机布局、表单标签、主标题和CSP；无JS异常、CSP违规、HTTP500或页面横向溢出。
- PowerShell 7.5.4工具包经官方SHA-256校验，在Linux上解析Windows脚本，并实际验证随机密码生成、不覆盖配置、按字面导出、非零退出及端口占用保护。
- Windows操作系统、Windows Maven Wrapper、Docker Desktop启动和全流程演示：**未执行**。12次真实TCP中断下载的回归检查通过，无重复响应渲染或服务器错误。
- 远端GitHub Actions：**未执行**，只完成配置。T25保留待实机验收。

逐类、逐用例结果见[test-results.json](test-results.json)，阶段细节见[测试M6](testing-m6.md)及[测试M1–M5](testing-m5.md)。环境中的原始构建日志为`/workspace/.cloud-setup/final-verify.log`。

## B01–B15业务验收

| 用例 | 场景 | 实测依据 | 结果 |
|---|---|---|---|
| B01 | 预约空闲座位 | ReservationIntegrationTest、DeliveryIntegrationTest完整流程、Chromium | 通过 |
| B02 | 两人同时抢同一座位 | concurrentStudentsCompetingForSameSeatHaveExactlyOneWinner | 通过，恰好一个成功 |
| B03 | 同学生同时抢两个座位 | concurrentSameStudentBookingDifferentRoomsHasExactlyOneWinner | 通过，恰好一个成功 |
| B04 | 拒绝过去日期 | ReservationRulesTest、ReservationIntegrationTest | 通过 |
| B05 | 开始前取消未签到预约 | cancellationBeforeStartKeepsAuditFieldsAndRepeatedCancellationIsIdempotent | 通过，历史保留 |
| B06 | 签到后拒绝取消 | cannotCancelAtStartOrAfterStartOrAfterCheckin、管理员使用中取消拒绝 | 通过 |
| B07 | 合法窗口签到 | normalCheckinOpensExactlyFifteenMinutesBeforeStartAndIsIdempotent、Chromium | 通过 |
| B08 | 超过截止拒绝签到 | checkinAtDeadlineSucceedsButOneSecondLaterReleasesAndRejectsEvenWithoutSweep | 通过，不依赖先扫库 |
| B09 | 释放后其他学生补约 | overdueOriginalCanBeRebookedCheckedInAndCheckedOutWithoutExtendingEnd、Chromium | 通过 |
| B10 | 补约10分钟未签到释放 | lateRebookingGetsExactlyTenMinutesAndMayExpireAgain、Chromium | 通过 |
| B11 | 提前签退仍锁时段 | earlyCheckoutLocksBothSeatAndStudentForWholeSlotAndDoesNotRewriteOnRepeat | 通过 |
| B12 | 时段结束自动完成 | lateCheckoutWithoutSchedulerCompletesAtSlotEndInsteadOfRequestTime、真实调度回调、Chromium | 通过 |
| B13 | 学生访问用户管理 | Foundation、Admin、Delivery权限矩阵及Chromium403 | 通过 |
| B14 | 历史座位改编号 | historicalSeatCanBeRenamedButNeverDeleted | 通过 |
| B15 | 有有效未来预约不能停用 | currentAndFutureEffectiveBookingsBlockSeatAndWholeRoomDisable | 通过 |

## 边界、重复与竞态

7个自然日、跨月、结束前恰好30分钟、普通开始前15分钟、截止时刻及之后、补约恰好10分钟、结束时刻均有测试。验证重复预约、重复取消、重复签到/签退、重复扫描不产生重复记录或覆盖原时间。

真正同时就绪的工作线程验证学生和座位冲突、预约与空间停用/删除、取消与预约、签到与取消/释放、签退与自动完成、管理员取消与签到/释放、账号禁用/角色变化与预约、两超管交叉降级、个人改密与管理员重置。核对数据库最终状态、行数、审计和时间，不凭HTTP状态码判断成功。

## 交付材料

| 材料 | 位置 |
|---|---|
| 源码、Maven Wrapper、四表SQL、集成测试 | 仓库根目录与src/ |
| 需求及任务状态 | [requirements.md](requirements.md)、[tasks.md](tasks.md) |
| 架构、ER、状态图及可编辑源 | [design.md](design.md)、[diagrams/](diagrams/) |
| 字段、约束与索引 | [data-dictionary.md](data-dictionary.md) |
| 13个主要页面及手机/错误截图 | [截图索引](screenshots/README.md) |
| Windows手册及待填写实机记录 | [deployment-windows.md](deployment-windows.md) |
| Windows准备、测试、启动、检查脚本 | [dev.ps1](../scripts/dev.ps1) |
| 论文技术素材与8分钟演示步骤 | [defense.md](defense.md) |
| 完整测试汇总 | [test-results.json](test-results.json) |
| 源码压缩包生成器 | [package-delivery.py](../scripts/package-delivery.py) |

交付包由生成器从Git跟踪文件读取，附文件SHA-256清单与提交号，排除.env、Git元数据、构建缓存和数据库卷。jar需要本地JDK 17及MySQL配置；源码包包含Windows脚本和手册，密码在使用者本地生成。

## 尚待外部环境完成

当前仅T25的真实Windows部署验收未完成，详见Windows手册中的逐项记录。单实例、线上签到、当前容量作为历史预约率分母等是已确认的系统边界，不应在答辩中宣称集群、真实到馆或历史容量快照。没有推送或合并远端分支，远端CI不会因本地通过而自动变成通过。
