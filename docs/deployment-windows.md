# Windows部署与验收

本手册对应当前源码。**当前执行环境是Linux；Windows脚本已用实际PowerShell解析并验证其中可移植的辅助逻辑，但Windows上的Maven Wrapper、Docker Desktop、服务启动及完整演示尚未实机执行，T25保留待验收。**

## 需要安装

- Windows 10/11受支持版本及完整[JDK 17](https://adoptium.net/temurin/releases/?version=17)。配置JAVA_HOME，`java -version`和`javac -version`均应为17。
- [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/)，使用WSL2和Linux容器。先启动Docker Desktop，确保`docker info`及`docker compose version`成功。
- Windows PowerShell 5.1或PowerShell 7。无需安装Maven，仓库自带`mvnw.cmd`及发行包校验值。
- 首次构建需要访问Maven Central，首次运行需要拉取MySQL镜像。不要关闭TLS验证。

解压源码到普通可写目录，在项目根目录打开终端。路径含空格时使用引号。

## 准备、测试、启动

```powershell
# 1. 本地随机生成配置并启动数据库，不覆盖已有.env
.\scripts\dev.ps1 -Action Setup

# 2. 运行真实MySQL隔离测试。应用若正在运行，请先Ctrl+C停止
.\scripts\dev.ps1 -Action Verify

# 3. 打包并前台启动；此命令本身跳过测试，不能代替上一步
.\scripts\dev.ps1 -Action Start
```

另开一个终端检查就绪：

```powershell
.\scripts\dev.ps1 -Action Check
```

在本机浏览器打开`http://127.0.0.1:8080`。默认演示账号为`student`、`librarian`、`admin`，密码取本地`.env`的`APP_DEMO_PASSWORD`。不要把该文件或密码截图加入论文、发到群聊或提交Git。脚本不打印密码。

`.env`由Compose自动读取，但Java本身不会读取；脚本显式、安全地按字面导出已知变量，不执行配置中的表达式。仅Setup生成缺失文件；Start保留现有配置和数据。脚本检测应用端口占用，防止覆盖正在使用的jar，不会结束未知进程。

若系统策略阻止本地脚本，可在当前终端按本机管理要求允许运行，或按README的手工命令导出变量后调用`.\mvnw.cmd`；不要修改全局安全策略来解决项目依赖问题。

## 使用已安装的MySQL 8

1. 创建utf8mb4的`library_seat`数据库及专用应用用户，授予该库的建表、索引、查询、写入权限。
2. 设置`.env`中的DB_HOST、DB_PORT、DB_NAME、DB_USERNAME、DB_PASSWORD。
3. 执行`.\scripts\dev.ps1 -Action Start -ExistingMySql`，跳过Compose。也可先用Navicat以UTF-8导入`src/main/resources/db/schema.sql`。
4. Verify仍需要Docker，因为集成测试使用独立Testcontainers数据库，不应改为连接日常开发库。

schema.sql只创建缺失表，不迁移已有列；当前最终版本仍是四表，不需要删库重建。保留原数据库和`.env`，遇到密码不匹配先核对绑定，不删除已有卷。

## 停止、重启与备份

应用终端Ctrl+C停止；`docker compose stop`停止数据库且保留卷。再次Setup或Start会复用数据。**不要执行`docker compose down -v`，除非明确希望删除数据库。**

备份可用Navicat导出四表结构和数据到本地私有位置；恢复先在新的测试库核对，避免覆盖已有历史。不要把含真实用户的数据库备份加入交付源码。

真实部署时不启用dev，首次初始化通过APP_BOOTSTRAP_ADMIN_PASSWORD创建超管。HTTPS及Secure Cookie通过部署配置启用；只部署单实例。此开发脚本不是生产进程管理器。

## 常见问题

| 现象 | 核对方法 |
|---|---|
| javac找不到或版本不是17 | 安装完整JDK 17，检查JAVA_HOME及PATH，重新打开终端 |
| Docker不可用 | 启动Docker Desktop，启用WSL2和Linux容器，检查docker info |
| 3306被占用 | 在.env调整DB_PORT；Compose映射随之变化，勿误停已有MySQL |
| 8080被占用 | 停止本项目旧终端，或设置SERVER_PORT；脚本不结束未知进程 |
| 数据库密码不匹配 | .env改变不会修改已初始化卷的密码，保留数据后核对原绑定 |
| 页面跳回登录 | 修改密码/角色/状态会注销旧会话，使用新密码和权限重新登录 |
| 无签到按钮 | 仅在对应开放窗口显示，未来预约需等到开始前15分钟 |
| 旧过期记录尚未写入 | 调度每轮结束等待60秒；页面按时间判定，不能靠延迟任务延长签到 |
| Maven首次下载失败 | 检查网络、代理和证书，不关闭TLS或修改校验值 |

## 实机验收记录（待在Windows填写）

| 项目 | 待填写记录 |
|---|---|
| Windows版本、日期、执行者 | 未执行 |
| JDK及PowerShell版本 | 未执行 |
| Docker Desktop / WSL2版本 | 未执行 |
| Verify总数、失败、错误、跳过 | 未执行 |
| Check与三角色实际登录 | 未执行 |
| 预约、签到、签退、超时补约、统计 | 未执行 |
| 重启后账号与历史保留 | 未执行 |
| 证据路径与问题 | 未执行 |

逐项通过[验收表](acceptance.md)后再勾选T25。不能用Linux测试、Windows构建CI或浏览器Windows User-Agent替代该验收。

## V1.1 Windows自动验收入口

GitHub Actions的Windows任务依赖Linux全量测试通过，再使用Windows Wrapper构建。在全新检出目录执行 `scripts/validation/windows-runtime.ps1`：下载版本固定的官方MySQL8.0.44 Windows包，使用独立数据目录、仅本机33306端口和随机应用密码；执行45张完整业务浏览器回归、54张打包演示页面及五时刻释放/补约/自动完成验收。不会使用系统中已有的MySQL服务或数据目录；存在 `.env` 或验收目录时拒绝覆盖。

此入口要求PowerShell7、Java17、Python与Playwright；CI负责准备这些工具。生成的密码仅写入忽略的本地配置，上传物只选择截图、结果和运行日志，不上传配置、MySQL数据或运行jar。结束时关闭自己启动的临时MySQL。

入口配置和Linux脚本解析已检查；当前尚未观察真实Windows任务执行结果。只有Actions任务成功且证据完整，或按上方手册完成Windows人工验收并填表后，才能将Windows门槛标记通过。普通Windows本地启动仍使用 `scripts/dev.ps1`，不要求为了运行系统安装浏览器测试工具。
