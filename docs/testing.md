# M1 测试记录

日期：2026-10-19（Asia/Shanghai）。运行环境：Linux、Temurin JDK 17.0.20.1、Maven 3.9.11、Docker、真实MySQL 8.0。

## 自动化集成测试

命令：`./mvnw -B -ntp verify`。

结果：**22项执行，0失败，0错误，0跳过；BUILD SUCCESS**。
用例位于 `src/test/java/com/seapullkb/library/FoundationIntegrationTest.java`；报告生成于忽略的 `target/surefire-reports/`。

覆盖内容：

- 精确四表、唯一约束、外键、合法预约状态约束、历史预约引用保护。
- 同一座位时段允许保留 EXPIRED 和后续新记录，不对全部历史记录错误设唯一约束。
- 业务Clock时区为Asia/Shanghai；数据库建表和开发数据可重复初始化，不覆盖已有密码。
- 非dev初始化只创建超级管理员；缺少初始密码时失败且不留半成品账号。
- 登录页、注册页、Bootstrap和CSS的真实模板/资源渲染。
- 注册强制学生角色和启用状态，不接收客户端ID；密码使用BCrypt。
- 重复账号（含大小写冲突）、非法账号、短密码、确认不一致、无效手机号、超过BCrypt字节限制的Unicode密码。
- 无CSRF的注册、登录、退出请求被拒绝。
- 三角色真实凭据认证、Session保持、登录后跳转与页面渲染。
- 学生无法进入后台、图书馆管理员无法进入超管用户页、超管继承管理权限。
- 禁用账号和错误密码无法登录；退出失效Session；显示姓名经过HTML转义。
- 两个线程同时注册同一账号恰好一个成功，失败方收到重复账号业务错误。

首轮测试发现MySQL CHECK约束的驱动异常被Spring映射为非分类SQLException，而非测试原先假设的DataIntegrityViolationException。约束实际生效；断言已改为核对根SQLException及具体约束名，随后完整重跑通过。

## 打包应用 HTTP 验证

使用 `docker compose up -d --wait` 启动持久开发数据库，加载本地`.env`，再用 `java -jar target/library-seat-reservation-system-0.1.0-SNAPSHOT.jar` 启动。

通过独立CookieJar执行真实HTTP表单请求，结果：

- student登录后进入学生页，访问用户管理返回403，退出后受保护页要求重新登录。
- librarian登录后进入管理页，访问用户管理返回403，退出后Session失效。
- admin登录后进入管理页，可访问超管入口，退出后Session失效。
- 无权限页面实际返回403并显示中文提示。
- 打包的Bootstrap资源可在本地返回200，不依赖CDN。
- 重启应用和再次执行Compose启动后，原有开发账号仍可登录，数据未被重置。

## 验证边界

本次仅验收T01–T04。用户管理页只实现了权限入口，完整管理功能为T19。
预约、座位管理、签到签退、补约、定时释放和统计尚未实现，因此未宣称通过相应业务用例。
并发注册不等于并发抢座验证。Windows命令已写入README，但未在真实Windows机器执行，T25保持未完成。
页面经过HTTP渲染验证，尚未做浏览器截图和全量交互验收。
GitHub Actions工作流已加入；本次验证在当前云机器执行，未触发远端CI。
