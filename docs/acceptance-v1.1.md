# V1.1 验收与交付

基线 `c8a4ee6`，开发分支 `feat/ui-polish`。本轮按 UI-00～UI-10 顺序独立实现并提交；UI-10对应 `f99e75f`，UI-11修正补约卡片的实际签到起点，并补充可重复验收工具、CI和材料。

## 已实际通过

- Java 17 / Maven 3.9.11 / 真实MySQL 8：169项测试，失败0、错误0、跳过0，BUILD SUCCESS。其中161项集成测试，8项规则或故障隔离测试。
- 可移植完整浏览器回归：注册、资料/改密及旧会话失效、预约、签到/签退、释放/补约、管理员审计取消、空间CRUD、权限、用户维护和精确统计；45张桌面/390px手机检查通过。
- 独立打包演示：4间阅览室、114座、27账号、多日历史和五种状态；54张实际桌面/手机截图，校验真实指标、时间详情、分页、空结果、错误表单、400/403，未发现JS/CSP/HTTP500或页面横向溢出。
- 另一独立打包演示跨09:05、09:11、09:21、09:21:01、12:00，验证截止时刻签到、晚一秒释放、10分钟补约及结束自动签退。
- 正常dev应用已运行新版jar，三角色实际登录、权限、统计图和退出通过。更新和只读检查前后的四表行内容、已有账号密码散列一致。
- 普通时钟保持服务器实际北京时间；演示时钟只接受独立库及dev,demo组合，种子拒绝非空库。演示运行文件和密码不进入Git。

| 证据 | 位置 |
|---|---|
| 169项逐用例结果 | [test-results-v1.1.json](test-results-v1.1.json) |
| 54张正式演示截图与校验清单 | [截图索引](screenshots/README.md)、[manifest](screenshots/manifest.json) |
| 同一外部夹具的改版前/后截图 | [before](ui-v1.1/before/)、[after](ui-v1.1/after/)、[回归结果](ui-v1.1/after/evidence.json) |
| 打包应用五时刻业务证明 | [demo-clock/evidence.json](ui-v1.1/demo-clock/evidence.json) |
| 普通运行及既有数据保护 | [packaged-smoke.json](ui-v1.1/packaged-smoke.json) |
| 逐项开发与阶段检查 | [ui-v1.1.md](ui-v1.1.md) |
| 演示启动、恢复与时钟推进 | [demo-v1.1.md](demo-v1.1.md) |

[逐页面改版对照](ui-v1.1-comparison.md)提供变化说明。

截图使用隔离夹具、实际应用和真实浏览器，不代表正常运营库中存在这些人数或预约。截图与功能操作测试分别记录。

## 复验命令

```bash
./mvnw -B -ntp verify
python -m pip install -r scripts/validation/requirements.txt
python -m playwright install chromium
python scripts/validation/browser-regression.py --no-build --output .local/browser-regression
# 新库的页面及时间边界操作见 demo-v1.1.md
```

完整浏览器脚本需要MySQL管理权限以新建临时库并授予应用用户该库权限；默认使用项目Compose，也支持 `--mysql-client` 指向本机MySQL。完成后只删除自身生成的临时库和对应权限。外部Java时钟夹具编译在`.local`，未打入产品jar，不增加产品HTTP测试接口。

## 主干合并记录

2026-10-20用户明确要求“合到主干”，本次采用原生Git，按 `feat/ui-polish` → `feat/final-delivery` → `main` 顺序保留历史合并。已验证的应用来源为 `0f401bd`，完整开发分支合并提交为 `f1ea916`，主干合并提交为 `462fad4`。两个合并均无冲突，合并后的文件树与已验收开发分支一致；随后仅更新交付状态说明。

GitHub API此前返回 `Forbidden`，因此本次未创建PR；此处记录的是Git合并，不是PR审批。169项测试及浏览器证据沿用已验证应用代码的结果，本次没有重新运行业务测试。

## 尚未验收的项目

- **真实Windows运行结果待确认**。CI已增加Windows原生MySQL 8、Windows Wrapper及真实Chromium完整业务/打包演示检查；本环境是Linux，只完成脚本解析和可移植逻辑验证，没有观察到远端运行结果。原T25继续待勾选。

原始V1.0验收记录保留于[acceptance.md](acceptance.md)，不以旧145项结果代替本轮验证。
