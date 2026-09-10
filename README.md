# 作业时光

> 自主规划，快乐成长

“作业时光”帮助小学生家庭把老师的作业照片整理为一份可执行的晚间计划。家长在微信小程序中录入、确认和排期，学生在平板端查看计划并逐项完成，双方会实时看到进度变化。

## 当前进度

仓库同时保留两部分：

- 原有高保真演示站点：产品全貌与交互原型，仍使用前端演示数据。
- 正式业务闭环：家长端录入与排期 → 学生端开始、计时、完成或处理超时 → 剩余计划动态顺延 → 双端同步与调整审计 → 成长趋势与周目标结算 → 徽章、奖励审批、星星账本及嘀嘀皮肤装备。

目前已经完成阶段 0、阶段 2 的最小闭环，阶段 3～4 的执行与动态排期、阶段 5 的激励与周报，以及阶段 6 的真实 OCR 适配和个性化估时。正式业务端支持腾讯云高精度通用文字识别、识别原文与置信度留存、失败后人工补录、逐项修订，并以“年级基准 + 最近 60 天加权历史 + 异常值保护”形成每名学生独立的时间曲线。阶段 1 的真实微信登录仍暂缓，当前继续使用模拟微信身份。

## 工程结构

```text
apps/parent-miniapp  家长端：Taro + React + TypeScript（微信小程序/H5 调试）
apps/student-pwa       学生端：React + Vite PWA（平板竖屏）
packages/shared      双端共享 TypeScript 数据模型
server               Java 21 + Spring Boot API
app、components 等   原有高保真演示站点
docker-compose.yml   MySQL、Redis、MinIO 本地环境
scripts/smoke.ps1    最小业务闭环接口冒烟测试
```

## 本地运行

前置条件：Java 21、Docker Desktop、Node.js 22.13+、pnpm。当前工程已用 Node.js 24.16.0 验证；不需要修改其他工程使用的全局 Node.js 14。

如当前终端仍指向 Node.js 14，只在本工程终端临时使用已安装的 Node.js 24：

```powershell
$env:Path='C:\Program Files\nodejs;'+$env:Path
node --version
```

1. 启动数据服务：

   ```powershell
   docker compose up -d
   ```

2. 启动后端（新终端）：

   ```powershell
   cd server
   .\mvnw.cmd spring-boot:run
   ```

3. 启动学生端（新终端，在仓库根目录）：

   ```powershell
   pnpm install
   pnpm dev:student
   ```

   打开 <http://localhost:5173/>。学生端默认进入“小满”的演示账号，也可通过
   `?studentId=demo-student-keke` 模拟另一台已绑定的学生设备。

4. 启动家长端 H5 调试版（新终端，在仓库根目录）：

   ```powershell
   pnpm dev:parent
   ```

   打开 <http://localhost:5174/>。也可以运行 `pnpm build:parent`，再用微信开发者工具导入 `apps/parent-miniapp`；当前使用游客 AppID。

后端健康检查：<http://localhost:8080/actuator/health>。MinIO 管理台：<http://localhost:9001/>，本地默认账号见 `.env.example`，实际开发建议复制为 `.env` 并修改密码。

### 启用真实 OCR（可选）

默认 `OCR_PROVIDER=mock`，预置示例、人工录入和全部排期功能都可本地运行。需要验证真实照片时，请先在腾讯云开通文字识别服务，然后只在启动后端的本地终端设置环境变量：

```powershell
$env:OCR_PROVIDER='tencent'
$env:TENCENT_CLOUD_SECRET_ID='你的 SecretId'
$env:TENCENT_CLOUD_SECRET_KEY='你的 SecretKey'
$env:TENCENT_CLOUD_REGION='ap-guangzhou'
cd server
.\mvnw.cmd spring-boot:run
```

密钥不要写入源码或提交到 Git。未配置密钥时，上传照片会创建可追踪的作业批次并转为“需要人工录入”，不会伪造云端识别结果。

## 验证

后端运行后，在仓库根目录执行：

```powershell
.\scripts\smoke.ps1
cd server
.\mvnw.cmd test
```

前端生产构建：

```powershell
pnpm build:student
pnpm build:parent
pnpm build
```

## 当前边界

- 不接真实微信登录和生产云对象存储；阶段 1 将在产品闭环稳定后单独实施。
- 真实 OCR 供应商适配已经完成，但仓库不包含云密钥；实际云端调用需开发者自行开通服务并配置本机环境变量。
- 不迁移或覆盖现有已发布原型；正式双端继续在本地端口独立开发。
- 目前实时通知由单实例内存 WebSocket 广播完成；扩展为多实例部署时再接 Redis Pub/Sub。
- 当前演示家庭和两名学生由 Flyway 初始化，仅用于本地开发验证。

完整的技术边界与下一步见 [正式开发说明](docs/development.md)。
