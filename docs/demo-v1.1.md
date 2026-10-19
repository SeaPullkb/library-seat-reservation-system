# V1.1 独立答辩演示

演示功能不进入正常 `dev` / 生产业务流程。只有同时启用 `dev,demo`、实际连接 `library_demo_` 前缀独立库，才允许演示时钟。生成器还要求四张表全部为空，并在事务中生成夹具，已有账号和密码不会被覆盖。

## 启动

准备 Java 17、项目 MySQL、Python 3.10+ 及被忽略的 `.env`。使用与正常开发相同的 MySQL 账号配置；不要把密码粘贴到命令或提交到 Git。

```bash
# 首次准备开发配置和 MySQL；已有 .env 不会覆盖
python scripts/init-dev-env.py
docker compose up -d --wait
# 另用 8082 端口启动演示；日期默认北京时间今天
python scripts/demo.py serve
```

脚本打包应用（此步骤跳过测试，不能代替 `./mvnw verify`），创建一个新的 `library_demo_日期_随机ID` 库、授予应用用户该库权限，并启动真实 Spring 应用。不会清空正常 `library_seat` 库，也不会改写原有账号密码。正常应用可继续使用8080端口。

Windows 可用 `py -3` 替代 `python`。若使用本机 MySQL 而非 Compose，追加 `--mysql-client "C:\你的MySQL目录\bin\mysql.exe"`；本地 `.env` 的 `MYSQL_ROOT_PASSWORD` 必须对应本机 root，且 `DB_USERNAME` 应用用户需已存在。脚本只对新演示库授予权限，不创建或改密现有 MySQL 用户。

## 演示内容

| 项目 | 样本 |
|---|---|
| 阅览室 | 4 间：人文、理工、静思、维护空间；3 间对学生开放 |
| 座位 | 分别20、30、40、24个，共114个；各有停用座位 |
| 账号 | student、librarian、admin 三种角色；另24个 demo_student_01～24 学生 |
| 密码 | 使用本地 `.env` 中的 `APP_DEMO_PASSWORD`，页面与文档不写死密码 |
| 预约 | 待签到、使用中、已完成、已取消、超时释放；另有释放后补约、未来预约 |
| 历史 | 演示日前6天的不同预约记录，统计按真实样本表查询 |
| 业务时间 | 初始固定在演示日09:05，北京时间；页面有黄色演示标识 |

初始开放空间启用87个座位，上午空闲80个；管理员首页正在使用2个、当前待签到3条、当日已释放2次。学生签到、签退或取消后，这些数字会随真实业务操作变化。

固定时钟用于可重复演示，不改变普通运行的服务器时钟；样本数据不会作为真实运营数据出现在正常库。

## 停止、继续及推进时间

终端按 Ctrl+C 停止，演示数据保留。启动输出给出该次演示的 `manifest.json` 路径；本地配置、稳定运行jar和日志存于 `.local/demos/`，全部被 Git 和源码包排除。

```bash
# 使用启动输出中的实际路径
python scripts/demo.py resume --manifest .local/demos/library_demo_YYYYMMDD_XXXXXXXX/manifest.json
# 先停止应用，再将业务时间推进到09:11；时钟不能回退
python scripts/demo.py resume --manifest .local/demos/library_demo_YYYYMMDD_XXXXXXXX/manifest.json --time 09:11
```

每次推进后重新登录。应用启动时会执行原有到期维护：09:11会释放未签到的上午补约；其他无当前时段占用的学生可以补约，新签到截止为该次预约后10分钟。继续推进到该截止后一秒可演示再次释放；推进到12:00可演示使用中预约自动完成，签退时间记录为时段结束时刻。已签退学生不能重复预约原时段，此规则保持不变。

也可用 `--date` 配合 `resume` 推进到未来日期。开始新演示必须重新运行 `serve` 或 `prepare`，不会用旧库强行重置。

```bash
# 显式移除本次独立库；请先停止它。其他库不受影响。
python scripts/demo.py remove --manifest .local/demos/library_demo_YYYYMMDD_XXXXXXXX/manifest.json
```

## 浏览器验收与截图

这是实际浏览器操作，非设计稿。需要额外安装测试工具；正常运行应用不需要 Playwright。

```bash
python -m pip install -r scripts/validation/requirements.txt
python -m playwright install chromium
# 先 prepare 新库，输出 manifest，然后 resume 在另一个终端启动它
python scripts/demo.py prepare
python scripts/demo.py resume --manifest 本次输出的manifest路径
python scripts/validation/demo-browser.py --manifest 本次输出的manifest路径 --output .local/demo-browser
```

该脚本要求新生成、尚未被操作的样本；检查真实指标、选座预览、签到/签退后的指标变化、用户分页、错误表单、空数据、400/403、脚本/CSP及桌面和手机溢出。它会真实改变这次演示库的学生预约状态，不操作正常库。截图和SHA-256清单写入指定输出目录；密码不会出现在截图或验收输出中。

Windows 实机和远端 CI 结果需单独确认，Linux 的截图不能代替 Windows 验收。
