# 作业时光

> 自主规划，快乐成长

“作业时光”帮助小学生家庭把老师的作业照片整理为一份可执行的晚间计划。家长在微信小程序中录入、确认和排期，孩子在平板端查看计划并逐项完成，双方会实时看到进度变化。

## 当前进度

仓库同时保留两部分：

- 原有高保真演示站点：产品全貌与交互原型，仍使用前端演示数据。
- 正式开发最小纵向闭环：家长端录入 → 模拟识别 → 确认并生成计划 → 孩子端查看并完成 → 家长端实时同步。

本闭环暂时使用模拟微信身份和模拟 OCR；家庭、孩子、作业、计划、打卡、星星和操作记录均由 Spring Boot API 写入 MySQL。上传的作业图片写入 MinIO。Redis 已纳入本地基础设施，留给后续登录会话、缓存和跨实例事件分发。

## 工程结构

```text
apps/parent-miniapp  家长端：Taro + React + TypeScript（微信小程序/H5 调试）
apps/child-pwa       孩子端：React + Vite PWA（平板竖屏）
packages/shared      双端共享 TypeScript 数据模型
server               Java 21 + Spring Boot API
app、components 等   原有高保真演示站点
docker-compose.yml   MySQL、Redis、MinIO 本地环境
scripts/smoke.ps1    最小业务闭环接口冒烟测试
```

## 本地运行

前置条件：Java 21、Docker Desktop、Node.js 22.13+、pnpm。

1. 启动数据服务：

   ```powershell
   docker compose up -d
   ```

2. 启动后端（新终端）：

   ```powershell
   cd server
   .\mvnw.cmd spring-boot:run
   ```

3. 启动孩子端（新终端，在仓库根目录）：

   ```powershell
   pnpm install
   pnpm dev:child
   ```

   打开 <http://localhost:5173/>。孩子端默认进入“小满”的演示账号，也可通过
   `?childId=demo-child-xiaoyu` 模拟另一台已绑定的孩子设备。

4. 启动家长端 H5 调试版（新终端，在仓库根目录）：

   ```powershell
   pnpm dev:parent
   ```

   打开 <http://localhost:5174/>。也可以运行 `pnpm build:parent`，再用微信开发者工具导入 `apps/parent-miniapp`；当前使用游客 AppID。

后端健康检查：<http://localhost:8080/actuator/health>。MinIO 管理台：<http://localhost:9001/>，本地默认账号见 `.env.example`，实际开发建议复制为 `.env` 并修改密码。

## 验证

后端运行后，在仓库根目录执行：

```powershell
.\scripts\smoke.ps1
cd server
.\mvnw.cmd test
```

前端生产构建：

```powershell
pnpm build:child
pnpm build:parent
pnpm build
```

## 当前边界

- 不接真实微信登录、OCR/AI 或云对象存储。
- 不迁移或覆盖现有已发布原型。
- 目前实时通知由单实例内存 WebSocket 广播完成；扩展为多实例部署时再接 Redis Pub/Sub。
- 当前演示家庭和两个孩子由 Flyway 初始化，仅用于本地开发验证。

完整的技术边界与下一步见 [正式开发说明](docs/development.md)。
