# 数据字典

结构来源：[schema.sql](../src/main/resources/db/schema.sql)。字符集utf8mb4，存储引擎InnoDB，ID为自增BIGINT。业务时间按Asia/Shanghai写入DATETIME。

## user

| 字段 | 类型 | 可空 | 作用 |
|---|---|---|---|
| id | BIGINT | 否 | 主键 |
| username | VARCHAR(50) | 否 | 唯一账号，4–50位字母数字下划线，创建后不可改 |
| password | VARCHAR(100) | 否 | BCrypt散列 |
| real_name | VARCHAR(50) | 否 | 姓名 |
| class_name | VARCHAR(100) | 是 | 学生班级，学生注册及自助修改必填 |
| phone | VARCHAR(20) | 否 | 手机号，表单校验11位 |
| role | VARCHAR(20) | 否 | STUDENT / LIBRARIAN / SUPER_ADMIN |
| status | TINYINT | 否 | 1启用、0禁用，默认1 |
| created_at | DATETIME | 否 | 创建时间 |

唯一索引`uk_user_username`；CHECK约束角色及启用状态。管理员新增固定为LIBRARIAN，普通注册固定为STUDENT。

## reading_room

| 字段 | 类型 | 可空 | 作用 |
|---|---|---|---|
| id | BIGINT | 否 | 主键 |
| room_name | VARCHAR(100) | 否 | 阅览室名称 |
| location | VARCHAR(200) | 否 | 位置 |
| description | VARCHAR(255) | 是 | 介绍 |
| status | TINYINT | 否 | 启用状态，默认1 |
| created_at | DATETIME | 否 | 创建时间 |

容量从启用座位实时计算，不保存重复的容量列。停用阅览室不改写其各座位的启用状态。

## seat

| 字段 | 类型 | 可空 | 作用 |
|---|---|---|---|
| id | BIGINT | 否 | 主键 |
| room_id | BIGINT | 否 | 阅览室外键 |
| seat_no | VARCHAR(20) | 否 | 室内编号 |
| status | TINYINT | 否 | 管理员启用状态；不表示某日期时段的占用 |
| created_at | DATETIME | 否 | 创建时间 |

联合唯一索引`uk_seat_room_number(room_id, seat_no)`。默认排序规则不区分大小写，应用先去掉首尾空白。座位不可移动到另一阅览室；修改编号保留ID及历史关联。

## reserve_record

| 字段 | 类型 | 可空 | 作用 |
|---|---|---|---|
| id | BIGINT | 否 | 一次预约的主键 |
| user_id | BIGINT | 否 | 预约学生外键 |
| seat_id | BIGINT | 否 | 座位外键 |
| reserve_date | DATE | 否 | 使用日期，不是提交日期 |
| time_slot | TINYINT | 否 | 1上午、2下午、3晚上 |
| status | VARCHAR(20) | 否 | 五种预约状态 |
| checkin_deadline | DATETIME | 否 | 本次普通或补约的签到截止 |
| checkin_at | DATETIME | 是 | 实际签到时间 |
| checkout_at | DATETIME | 是 | 主动签退时间，或自动完成的时段结束时间 |
| released_at | DATETIME | 是 | 执行超时释放的时间 |
| cancelled_at | DATETIME | 是 | 取消时间 |
| cancelled_by | BIGINT | 是 | 取消操作者用户外键 |
| cancel_reason | VARCHAR(255) | 是 | 学生主动取消或管理员填写原因 |
| created_at | DATETIME | 否 | 提交时间 |
| updated_at | DATETIME | 否 | 最近写入时间 |

索引：`idx_reserve_seat_slot_status`保护座位日期时段查询，`idx_reserve_user_slot_status`支持学生冲突及历史查询，`idx_reserve_status_deadline`支持状态截止查询。外键涉及学生、座位和取消操作者；没有历史时段全局唯一约束。

DATETIME精度为秒；补约截止在当前时间加10分钟后向下取整到秒，避免多授予时间。截止秒仍可签到，下一个时刻超时；请求持锁后读取Clock，不能信任前端时间。
