# GitHub 相近项目调查

调查日期：2026-10-19。搜索：`library seat reservation spring boot`。

| 项目 | 观察 | 本项目处理 |
|---|---|---|
| [zl2377746800/Library-Seat-Reservation-System](https://github.com/zl2377746800/Library-Seat-Reservation-System) | README描述为Spring Boot 3 + Vue 3 + MySQL；根目录未见许可证文件 | 前后端分离，与需求不符；不复制代码 |
| [17elr/library-seat-reservation-system](https://github.com/17elr/library-seat-reservation-system) | README描述为Java 21 + Spring Boot 3 + Thymeleaf + MyBatis，并有规则版本、事件与审计等扩展；根目录未见许可证文件 | 可参考业务分层和页面类型；不复制代码 |

本阶段独立实现。未把参考项目作为上游、未导入其代码或资源，也未改变原仓库历史。
若以后复用代码，需要先确认明确的授权、保留要求的许可声明，并逐项核对本项目的四表和固定时段规则。

Maven Wrapper脚本由Apache Maven Wrapper Plugin 3.3.4官方生成，保留脚本原有版权头；其Apache 2.0许可与NOTICE副本见 [licenses/](licenses/)。这不为本项目自有业务代码另行指定许可证。

M5图表使用Apache ECharts 5.6.0，通过Maven的`org.webjars.npm:echarts`引入官方发行资源（Apache 2.0），不从参考项目复制图表代码。资源随应用打包并本地提供，许可证元数据由依赖发行包保留。
