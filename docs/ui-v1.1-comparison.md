# V1.1 改版前后对照

原版基线为 `c8a4ee6`。下表的截图均由实际浏览器生成；前后采用同一基础业务夹具和对应流程节点，后台维护测试另行创建的测试空间如实显示。正式多阅览室演示截图另见[截图索引](screenshots/README.md)，不能把夹具数据当作学校运营规模。

| 页面 | 改版前 | 改版后 | 变化 |
|---|---|---|---|
| 学生首页 | [原图](ui-v1.1/before/03-student-home.png) | [新图](ui-v1.1/after/03-student-home.png) | 账号信息卡改为真实余座、待办、当前预约与规则入口 |
| 阅览室 | [原图](ui-v1.1/before/05-rooms.png) | [新图](ui-v1.1/after/05-rooms.png) | 日期时段筛选、实际空闲数、单空间预约指引 |
| 选座 | [原图](ui-v1.1/before/06-seats.png) | [新图](ui-v1.1/after/21-seat-selected.png) | 四类统计、图例、选中反馈及提交前信息确认 |
| 我的预约 | [原图](ui-v1.1/before/07-my-reservations.png) | [新图](ui-v1.1/after/07-my-reservations.png) | 活跃预约置顶、状态对应操作、独立历史过滤与时间详情 |
| 管理首页 | [原图](ui-v1.1/before/08-admin-home.png) | [新图](ui-v1.1/after/08-admin-home.png) | 真实指标、最近预约、签到关注和空间当前使用 |
| 空间后台 | [原图](ui-v1.1/before/09-admin-rooms.png) | [新图](ui-v1.1/after/09-admin-rooms.png) | 汇总、服务端搜索/状态筛选及紧凑表格 |
| 预约后台 | [原图](ui-v1.1/before/11-admin-reservations.png) | [新图](ui-v1.1/after/11-admin-reservations.png) | 状态颜色、筛选结果、取消审计和更多已有时间 |
| 统计 | [原图](ui-v1.1/before/12-statistics.png) | [新图](ui-v1.1/after/12-statistics.png) | 桌面双图、手机堆叠、明细、口径与空数据说明 |

[54张多状态演示图](screenshots/README.md)另覆盖手机、400/403、错误表单、分页及空预约。视觉检查之外，[完整回归结果](ui-v1.1/after/evidence.json)与[169项测试](test-results-v1.1.json)分别证明实际操作与业务/数据库规则。
