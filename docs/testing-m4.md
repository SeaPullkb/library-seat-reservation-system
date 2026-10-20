# M4 签到与补约测试记录

日期：2026-10-19（Asia/Shanghai）。环境：Temurin Java 17、Maven 3.9.11、真实MySQL 8、Docker、Chromium。

## 自动化测试

命令：`./mvnw -B -ntp verify`。

最终结果：**94项执行，0失败，0错误，0跳过，BUILD SUCCESS**。

| 测试类 | 数量 | 内容 |
|---|---|---|
| FoundationIntegrationTest | 22 | 账号、权限和初始化回归 |
| SpaceIntegrationTest | 17 | 空间维护及展示回归 |
| ReservationIntegrationTest | 25 | 预约、取消、冲突及并发回归 |
| ReservationRulesTest | 5 | 日期、时段和截止规则 |
| LifecycleIntegrationTest | 23 | 签到、签退、到期处理、权限与竞态 |
| SchedulerIntegrationTest | 1 | 启动补处理及真实Spring定时回调 |
| ReservationMaintenanceTest | 1 | 逐条失败隔离及处理计数 |

88项集成用例运行于Testcontainers隔离的真实MySQL，5项规则测试与1项失败隔离测试不依赖数据库。测试不连接持久开发数据库。失败隔离测试刻意模拟一条记录异常，其ERROR日志是预期证据；后续记录仍正常处理，测试本身通过。

## 时间、状态和权限

- 普通上午预约07:45开始签到，之前拒绝；08:15截止时刻允许，08:15:01拒绝并提交EXPIRED，不依赖扫描先执行。
- 未来预约不能提前签到；未签到不能签退。CANCELLED、EXPIRED、COMPLETED不能返回USING。
- 新补约按创建时间后10分钟截止，验证截止时刻签到；超时再次释放后可以重新预约。
- 提前签退保存实际时间，学生与座位仍锁定原日期时段；重复签到、签退保持原时间。
- 时段结束自动完成使用中记录，实际签退记为12:00而非延迟处理的13:00。结束后直接请求也按结束状态处理。
- 条件更新不覆盖已签到或终态记录。扫描重复执行不增加变更；205条过期记录加1条到期使用中记录验证跨200条分页。
- 验证所有者禁用后仍能清理旧预约；学生操作要求账号启用且角色为STUDENT，强制停用的座位拒绝签到。系统清理按所有者ID取锁，不要求其当前角色为学生。
- 验证非学生、CSRF缺失、他人记录和伪造时间参数；实际操作时间均来自服务端Clock。
- 个人页面显示实际时间和适时操作按钮；按时间投影过期/完成状态，GET不写数据库。

并发测试使用真实MySQL与同时启动的工作线程，验证重复签到、重复签退、签到与取消、截止时刻及超时签到与释放、签退与到期完成。核对最终状态、行数和原始时间，未用HTTP状态码代替数据断言。统一取锁与完整事务重试规则沿用M3。

## 定时任务实际触发

生产默认启用，应用就绪后立即补处理，之后每次执行完等待60秒再次扫描。ID分批读取，每条独立事务中重新取锁、读取当前时间；单条失败记录日志并留待下轮。

独立SchedulerIntegrationTest在数据库初始化后、应用就绪前写入到期夹具，验证ApplicationReady补处理；随后新增一条超时记录，等待真正的Spring调度回调完成处理，没有手动调用扫描方法代替回调。该测试为缩短等待将间隔设为100毫秒，并核对Spring注册的任务间隔；普通业务测试关闭后台扫描以避免与固定时钟断言互相干扰。

## Chromium浏览器闭环

通过外部验证启动器在临时数据库运行真实Spring应用，只替换测试Clock，不向产品加入改时间或手动扫描接口。浏览器通过真实注册、登录、表单和CSRF操作：

1. 学生A原预约超时后，学生B预约A01，显示10分钟补约截止；B签到、提前签退后A01仍占用。
2. A补约A02，超过截止1秒后由实际清理服务释放，个人记录显示EXPIRED。
3. 学生C重新预约A02，在新的10分钟截止时刻签到成功。
4. 到12:00执行清理，C显示COMPLETED和实际签退12:00；B原提前签退时间保持不变，A02仍锁定原时段。
5. 核对数据库中的两条EXPIRED、两条COMPLETED及签到/签退时间。手机390×844和桌面1280×900操作、截图检查通过，未发现页面横向溢出或JavaScript错误。

验证Clock使用2030-05-08，未修改系统或持久应用的业务时间。临时应用退出后删除本轮独立数据库，未修改已有开发记录。浏览器验证中时间推进与扫描由外部启动器调用真实业务服务；Spring自动触发另由上述集成测试独立验证。

当前环境证据（不属于源码运行依赖）：

- `/workspace/.cloud-setup/m4-verify.log`
- `/workspace/.cloud-setup/m4-browser-evidence.txt`（仅状态及时间，无凭据）
- `/workspace/.cloud-setup/screenshots/m4-using.png`
- `/workspace/.cloud-setup/screenshots/m4-rebooking-mobile.png`
- `/workspace/.cloud-setup/screenshots/m4-auto-completed.png`

## 验收范围

T13–T17完成，累计17/26项。网页签到只记录线上操作，不验证真实到馆。管理员违规取消、超管账号维护和统计为下一阶段T18–T20；全业务安全、完整交付、真实Windows与远端GitHub Actions本轮未验收。
