# 正式开发说明

## 本轮已对齐的产品边界

阶段 0 与阶段 2 的最小纵向闭环已经验证“真实数据是否能从家长端走到学生端并实时返回”：

1. 家长端使用本地模拟身份，在多名学生之间切换。
2. 家长选择作业照片或预置示例，后端把图片保存到 MinIO，并返回按年级生成的模拟识别任务，状态为“待确认”。
3. 家长确认任务和开始时间，后端按“轻任务热身 → 高难度任务 → 中等任务 → 轻任务收尾”排序，并按年级插入用眼休息。
4. 计划及任务写入 MySQL，学生端读取当天真实计划。
5. 学生在学生端完成任务后，状态、实际时长、星星和操作记录写回 MySQL，并通过 WebSocket 通知家长端刷新。

阶段 3～4 现已在这条真实数据链路上继续完成：

1. 学生开始任务后记录开始时间，完成时写入实际用时；同一计划只允许一个任务处于进行中。
2. 学生遇到超时或困难时可选择“暂时跳过”或“继续挑战 10 分钟”，服务端即时重算全部剩余时段，并明确新的预计完成时间。
3. 家长或学生均可调整完整计划顺序，操作人的姓名、关系和变更说明写入家庭协作审计记录，并通过 WebSocket 同步另一端。
4. 初期按年级规则估时；同一学生、学科和任务类型积累至少 2 个完成样本后，自动切换为个人历史平均耗时，并在家长确认页标注估算来源。
5. 双端均可查看周完成率、专注时长、星星、连胜、学科时间曲线、徽章和家庭鼓励墙。
6. 奖励支持内置嘀嘀皮肤与家庭心愿，也支持家庭自定义门槛；学生申请后由家长批准或暂缓，批准时通过星星流水原子结算。

阶段 5 已把最小成长能力扩展为可持续的激励闭环：

1. 周报按周查看 7 天完成趋势，并给出完成率、专注时长相对上周的变化和嘀嘀鼓励语。
2. 家长可为当前周设置“完成项数 + 专注分钟 + 奖励星星”目标，学生达成后一次性领取周结算奖励；重复领取由数据库状态和事务共同阻止。
3. 徽章增加进度、门槛与解锁时间，达成后写入独立的学生徽章表，不会因周数据变化而丢失。
4. 奖励商店支持全部奖励、嘀嘀皮肤、家庭心愿分类；皮肤审批通过后成为永久拥有物，可由学生切换并实时同步。
5. 家长和学生均可查看奖励申请历史与星星账本，明确每一笔获得和消耗的原因。
6. 周目标调整、周奖励领取、奖励审批和皮肤装备均进入家庭协作记录，并通过 WebSocket 通知另一端刷新。

阶段 6 已在上述闭环上加入真实 OCR 与个性化能力：

1. `POST /homework-batches/recognize` 保存原图后调用可替换 OCR Provider；首个实现为腾讯云高精度通用文字识别。
2. OCR 文本按学科、编号和分隔符拆分，推断任务类型、难度、用眼负荷与年级基准耗时；置信度和原始文本均保留供家长核对。
3. 云服务未配置、调用失败或无法拆分时，批次进入 `NEEDS_MANUAL_ENTRY`，仍可人工添加、修订或删除任务后生成计划。
4. 个性化估时采用最近 60 天同类任务优先、学科历史兜底的时间衰减加权，并把单次实际用时限制在基准的 0.5～2 倍范围，降低异常计时的影响。
5. 家长端新增学生时间曲线，展示有效样本量、可信度、相对年级节奏和分学科建议；不同学生的数据不会混用。

真实微信授权仍属于阶段 1，固定/临时作息管理继续留在后续阶段。

## 数据职责

| 数据 | 当前存储 | 后续演进 |
| --- | --- | --- |
| 家庭、成员、学生 | MySQL | 接入微信身份及家庭邀请关系 |
| 作业批次、OCR 元数据、拆分任务、计划、完成记录 | MySQL | 已包含原文/置信度、个性化估时来源、计划版本和调整审计 |
| 周报点评、每日趋势、周目标与结算 | MySQL | 后续加入学期维度和可配置统计周期 |
| 徽章、奖励定义、兑换审批、皮肤装备、星星流水 | MySQL | 后续加入奖励有效期和主题资源包 |
| 作业原图 | MinIO | 云上替换为兼容 S3 的对象存储 |
| 登录会话、热点缓存 | Redis（已配置，尚未进入业务闭环） | 接入正式登录后启用 |
| 页面实时事件 | 单实例 WebSocket | 多实例时使用 Redis Pub/Sub 转发 |

## API 入口

所有业务接口以 `/api/v1` 开头：

- `GET /demo/context`：读取本地演示家庭和学生。
- `POST /homework-batches/mock-recognize?childId=...`：上传可选图片并生成模拟识别结果。
- `GET /recognition-capabilities`：读取当前 OCR Provider 与真实识别可用状态。
- `POST /homework-batches/recognize?childId=...`：上传图片并执行真实 OCR；失败时返回可人工处理的批次。
- `POST /homework-batches/{batchId}/tasks`：为待确认批次人工补录作业项。
- `PUT /homework-tasks/{taskId}` / `DELETE /homework-tasks/{taskId}`：修订或删除待确认作业项。
- `POST /homework-batches/{batchId}/confirm-and-plan`：确认并生成计划。
- `GET /children/{childId}/today-plan`：读取当天计划。
- `POST /plan-items/{itemId}/start`：开始任务并记录计时起点。
- `POST /plan-items/{itemId}/complete`：完成任务、记录实际用时并奖励星星。
- `POST /plan-items/{itemId}/overrun-decision`：选择暂时跳过或继续挑战，并动态重排。
- `POST /plans/{planId}/reorder`：家长或学生提交完整任务顺序。
- `GET /children/{childId}/activities`：查看操作记录。
- `GET /children/{childId}/personalization-profile`：读取学生独立的近期估时画像。
- `GET /children/{childId}/weekly-report`：读取本周成长数据、徽章与点评。
- `GET /children/{childId}/weekly-report?weekStart=...`：读取指定自然周的趋势与上周对比。
- `POST /children/{childId}/weekly-comments`：添加家庭鼓励或学生自评。
- `POST /children/{childId}/weekly-goal`：家长设置或调整本周成长目标。
- `POST /children/{childId}/weekly-bonus/claim`：学生达标后领取一次性周奖励。
- `GET|POST /families/{familyId}/rewards?childId=...`：读取奖励商店或添加自定义奖励。
- `POST /rewards/{rewardId}/redeem`：学生发起奖励申请。
- `POST /reward-redemptions/{redemptionId}/review`：家长批准或暂缓奖励。
- `POST /rewards/{rewardId}/equip`：学生装备已经解锁的嘀嘀皮肤。
- `WS /ws/updates?childId=...`：订阅指定学生的计划事件。

数据库结构由 `server/src/main/resources/db/migration` 下的 Flyway 迁移管理。已经执行过的迁移不得改写；表结构调整应继续新增迁移文件。

## 开发约定

- Java 使用 21，Spring Boot 使用 Maven Wrapper 构建。
- Node.js 至少 22.13；本工程使用 24.16.0 验证，根目录使用 pnpm workspace 管理正式双端和共享包。只需在当前工程终端切换版本，不应修改其他项目的默认 Node.js 14。
- 家长小程序本机调试默认请求 `http://127.0.0.1:8080`。真机调试时应改为局域网可访问地址，并在微信开发者工具中配置合法域名策略。
- 任何正式部署都必须替换 `.env.example` 中的开发密码，不提交真实 `.env`。
- 原有 Sites 演示站点仍由根目录 `pnpm dev` / `pnpm build` 管理，与正式双端并行存在。
