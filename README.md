# 高校自习室预约与考勤管理系统

> 课程设计 · 软件工程综合实践 · 前后端分离 Web 系统

基于《自习室预约系统_完整开发实施手册 V1.0》《需求规格说明书 SRS V1.1》《项目开发计划 V1.0》实现的全栈系统：
**分时预约 → 现场码签到 → 暂离管控 → 违约扣分与自动释放** 的闭环管理。

## 当前验证状态（2026-10-16 实测）

| 项目 | 命令 | 结果 |
|:---|:---|:---|
| 后端编译 | `mvn compile` | ✅ 通过（219 个 Java 文件，0 错误） |
| 后端单测 | `mvn test` | ✅ 43/43 通过（8 个测试类：时钟、区间重叠、格点、期限推导、状态机、奖励公式、密码策略、有效在座时长） |
| 后端打包 | `mvn package` | ✅ `target/studyroom-1.0.0.jar`（约 40 MB，含嵌套依赖，打包时同步执行单测） |
| Spring 容器启动 | `java -jar ... --server.port=18081` | ✅ 启动成功，全部 Bean 装配通过、21 个 Mapper XML 与接口一一对应 |
| 安全信封 | `curl /api/v1/me` | ✅ 401 `{"code":"AUTH_REQUIRED","message":"请先登录",...}`；`X-Trace-Id` 响应头存在 |
| 前端类型检查 | `npm run typecheck` | ✅ 0 错误（vue-tsc） |
| 前端单测 | `npm run test` | ✅ 38/38 通过（4 个测试文件：格式转换、规则 store、auth store、服务端时间偏移） |
| 前端生产构建 | `npm run build` | ✅ 通过（全部页面路由产出 chunk） |
| 集成测试（真实 MySQL） | `mvn verify -Pintegration` | ✅ 49/49 通过（2026-10-15，含 `SchemaInvariantIT` 6 项数据库不变量） |

**端到端状态**：数据库迁移、首位管理员初始化、参考数据与演示夹具导入、集成测试均已于 2026-10-15
在真实 MySQL 上打通；从零重建的完整操作序列见 `docs/数据库启用步骤.md`。
数据库密码（`DB_PASSWORD` / `TEST_DB_PASSWORD`）由操作者注入环境变量，不写入任何文件。

## 功能总览

| 端 | 功能 |
|:---|:---|
| 学生端 | 学号登录、个人概览（信用/公告/未读通知）、按日期时段与标签筛选自习室、可视化选座、预约/取消、六位现场码签到、暂离/回座、主动签退、历史订单、信用流水、违约申诉（含凭证上传）、站内通知、修改密码 |
| 管理员端 | 自习室与座位维护（编号可修改、批量生成/停用/闭馆影响策略）、规则版本发布、申诉审核与信用人工调整、学生账号管理（创建/编辑信息/删除无历史账号/禁用/黑名单/重置密码/信用调整）、运营统计看板、公告管理、审计日志、修改密码 |
| 后台 | 每 60 秒超时结算（爽约/暂离超时/到期自动完成）、信用幂等入账、站内通知、启动补扫、维护清理 |

## 技术栈（版本冻结，可复现）

| 层 | 选型 |
|:---|:---|
| 后端 | Java 21 · Spring Boot 3.5.16 · Spring Security（OAuth2 Resource Server + JWT HS256）· MyBatis 3.0.5 · Flyway · MySQL 8 · springdoc-openapi 2.8.17 |
| 前端 | Vue 3.5.44 · Vite 7.1.12 · TypeScript 5.9.3 · Vue Router 4.6.3 · Pinia 3.0.4 · Element Plus 2.14.7 · Axios 1.20.0 |
| 测试 | JUnit 5 / Mockito（后端单测）· Vitest 3.2.4（前端）· Playwright 1.64.1（E2E） |
| 工程 | Node 22.22.2 · Maven 3.9.16 · Git |

版本在 `backend/pom.xml` 与 `frontend/package.json` 中精确锁定（无 `^`/`~`），前端提交 `package-lock.json`，后续只允许 `npm ci`。

## 目录结构

```
├── backend/                     # Spring Boot 单体后端（包根 edu.campus.studyroom）
│   ├── pom.xml
│   └── src/main/
│       ├── java/edu/campus/studyroom/
│       │   ├── common/          # 响应信封/错误码/时钟/追踪/幂等/分页
│       │   ├── security/        # JWT/会话校验/密码策略/限流/权限链
│       │   ├── auth user resource rule reservation attendance
│       │   ├── settlement credit appeal file notification notice stats audit scheduler seed
│       │   └── StudyRoomApplication.java
│       └── resources/
│           ├── application.yml / application-local.yml / application-test.yml
│           ├── mapper/*.xml     # MyBatis SQL
│           └── db/migration/    # V001 表结构 / V002 默认规则与 guard / V003 删除房间楼层列
├── frontend/                    # Vue 3 前端（src/{api,types,router,stores,layouts,views,components,composables,utils,styles}）
├── docs/                        # openapi.json、内部契约、测试场景、决策记录
├── scripts/                     # 实体生成等辅助脚本
└── 筹备阶段/                     # 需求阶段文档（SRS、开发计划、用例图），保持不动
```

## 核心设计

- **写串行化协调行**：本期核心业务写事务统一先锁 `business_guard(id=1)`（`SELECT ... FOR UPDATE`），
  在单机 MySQL 内串行提交，证明"同座位/同学生不重叠"的正确性；查询、公告、上传、登录密码校验不持此锁。
- **服务端时钟唯一**：业务时区 Asia/Shanghai，实体与库内存 `DATETIME(3)`，跨接口用带 `+08:00` 的 ISO-8601；
  前端倒计时以响应头 `serverTime` 为基准，不倒推本地结算。
- **规则快照**：下单时固化规则 JSON；旧订单的处罚/奖励按自身快照，默认值（120/30/2/5、扣 10 等）不写死。
- **信用幂等**：`credit_log.event_key` 唯一；`after=clamp(before+requested,0,100)`；封顶部分记 `capped_delta`。
- **命令幂等**：核心写请求要求 `Idempotency-Key: UUID`；同 key 同参重放当前视图，异参 409。
- **会话可即时失效**：JWT 仅 2 小时，但每次请求都校验 `auth_session` 与 `auth_version`，
  退出/禁用/改密立即生效；单账号最多 5 个会话。
- **历史不删除**：订单、违约、流水、公告均不物理删除；资源变更写 `resource_status_event` 完整快照，
  配合 `retained_service` 还原历史可服务区间。

## 快速开始（本机开发）

### 1. 数据库（需有建库权限的管理账号，执行一次）

```sql
CREATE DATABASE studyroom_dev  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE studyroom_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- 迁移账号：对本项目库 DDL/DML；运行账号：仅 SELECT/INSERT/UPDATE/DELETE（无 CREATE/DROP/GRANT/FILE）
```

表结构由 Flyway 在首次启动时自动迁移（`V001__schema.sql` → `V002__rule_seed.sql` → `V003__drop_study_room_floor.sql`），**禁止** `DROP DATABASE` 与 `clean`。

### 2. 后端

PowerShell 会话级环境变量（关闭终端即失效，不写系统全局，不提交 Git）：

```powershell
$env:JAVA_HOME="D:\JDK_21"
$env:DB_USER="studyroom_app"           # 运行账号
$env:DB_PASSWORD="<安全输入的实际密码>"
$env:UPLOAD_DIR="D:\studyroom-data\uploads"
$env:JWT_SECRET_BASE64=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

```powershell
cd backend
&"D:\Maven\apache-maven-3.9.16\bin\mvn.cmd" clean verify        # 编译 + 单测（集成测试需 test 库，见下）
&"D:\Maven\apache-maven-3.9.16\bin\mvn.cmd" spring-boot:run -Dspring-boot.run.profiles=local
```

- 健康检查：<http://127.0.0.1:8080/actuator/health>（仅 UP/DOWN）
- OpenAPI（local profile 开放）：<http://127.0.0.1:8080/v3/api-docs> · <http://127.0.0.1:8080/swagger-ui>
- 端口提示：若 shell 环境存在 `SERVER__PORT` 等 Spring 风格环境变量会覆盖 `server.port`（表现为随机端口被占用），显式追加 `--server.port=8080` 即可。

### 3. 初始化首位管理员与演示数据（CLI，显式执行）

```powershell
# 1) 首位管理员（仅在空库执行一次；课程演示用 admin / admin123，正式环境必须改密码）
$env:BOOTSTRAP_ACCOUNT = "admin"
$env:BOOTSTRAP_PASSWORD = "admin123"
&"D:\JDK_21\bin\java.exe" -jar target\studyroom-1.0.0.jar --bootstrap-admin

# 2) 参考数据 + 演示夹具（local profile；会创建校区/楼宇/房间/座位/虚构学生与历史样本）
&"D:\JDK_21\bin\java.exe" -jar target\studyroom-1.0.0.jar --seed-reference-data --allow-demo-fixtures
```

初始密码一次性输出、不落日志。演示学生 `20240001`—`20240008`，统一口令 `stu123`；两端右上角用户菜单均提供「修改密码」入口。

### 4. 前端

```powershell
cd frontend
npm ci
npm run build
npm run dev
```

访问 <http://127.0.0.1:5173>（Vite 已将 `/api`、`/v3/api-docs` 代理到 8080）。

### 5. 测试

```powershell
cd backend
&"D:\Maven\apache-maven-3.9.16\bin\mvn.cmd" test                       # 纯单测（无需数据库）
&"D:\Maven\apache-maven-3.9.16\bin\mvn.cmd" verify -Pintegration       # 真实 MySQL 集成测试（studyroom_test）
cd ..\frontend
npm run test        # Vitest
npm run e2e         # Playwright（需先后端在线）
```

> 集成/并发测试要求真实 MySQL（本课程不引入 Docker/Testcontainers）。未配置 `TEST_DB_*` 时集成测试默认跳过，
> 纯函数单测（时间格点、区间重叠、奖励计算、状态机、规则校验）始终执行。

## 部署形态

- 课程本机演示：后端 `java -jar`（127.0.0.1:8080）+ 前端 Vite dev server（127.0.0.1:5173）。
- 单机正式演示：前端 `dist` 由 Nginx/Caddy 同源静态托管，反向代理 `/api/v1`、私有文件接口、health 到后端；
  SPA 非 `/api` 路由回退 `index.html`，API 404 不得变成 200 HTML。
- 公开部署必须 HTTPS；防火墙只开放前端入口，3306 不暴露公网。
- 备份：`mysqldump --single-transaction --routines --triggers` + 私有上传目录 + 迁移版本号；
  恢复必须先在独立 restore 库验证。

## Git 协作约定

- `main` 保持可演示；开发走 `feature/*`，合并前至少一名同伴审查。
- 提交信息：`feat/fix/test/docs/chore + 模块 + 需求ID`，例：`feat(reservation): FR-S05 add guarded booking`。
- 严禁提交：数据库密码、JWT 密钥、私有凭证、`target/`、`node_modules/`、`dist/`、IDE 用户配置（见 `.gitignore`）。
- 数据库成功迁移不可改原文件，只新增递增脚本（`V003__...`）。

## 已知限制（课程版刻意简化）

- 内存限流（重启清空）；单实例部署；未引入 Redis/MQ/微服务。
- 固定六位现场码只模拟到场验证，不证明本人真实在场；系统分配使用权，不保证物品已清场。
- 前端令牌仅存内存，刷新页面需重新登录。
- 100 并发性能目标以实测为准（第 20 章门禁），不在文档中预设达标。

## 文档索引

- `docs/openapi.json` —— REST 契约冻结版
- `docs/后端内部契约.md` —— 模块分层/事务/幂等/时间约定
- `docs/测试场景.md` —— T01—T50 验收用例目录 + 一致性证据 SQL
- `docs/决策记录.md` —— D-01—D-17 实施决策与变更流程
- `docs/数据库启用步骤.md` —— 建库授权、迁移、bootstrap 管理员、seed 导入、集成测试的完整操作序列
- `筹备阶段/` —— SRS V1.1、项目开发计划 V1.0、用例图
