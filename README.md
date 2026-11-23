# 映物 VIVID

AI 电商视觉生成平台。**13 个功能**的表单驱动生成闭环，从用户端到后端到管理端全部可本地跑通。

```
wimogen-platform/
├── web/                 # 用户端 · Nuxt 3（SSR / 中英双语 / SEO / 可静态部署）
├── server/              # 后端 · 若依 RuoYi-Vue（Spring Boot + MyBatis + MySQL + Redis）
│   ├── ruoyi-wimo/      # 映物业务模块（账号、任务、模板、看板、适配器）
│   ├── sql/wimo.sql     # 5 张业务表 + 9 个菜单 + 8 条字典
│   └── Dockerfile
├── admin/               # 管理端 · 若依 Vue3（任务管理 / 数据看板 / 模板配置 / 邮件日志）
├── deploy/              # 前端容器化 Dockerfile 与 nginx 配置
├── docs/API.md          # 完整接口文档
├── scripts/start-local.sh
├── docker-compose.yml   # MySQL + Redis + Spring Boot（+ 可选前端 profile）
└── README.md
```

## 快速开始

### 方式 A：一键本地启动（推荐开发用）

```bash
chmod +x scripts/start-local.sh
./scripts/start-local.sh up       # 启动全部
./scripts/start-local.sh status   # 查看状态
./scripts/start-local.sh down     # 停止
```

脚本会自动复用本机 MySQL / Redis（没有则用 Docker 起）、按需 Maven 构建后端、
并行拉起 Nuxt 用户端与若依管理端，并把上传目录、示例素材目录改成当前机器的绝对路径。

| 入口 | 地址 |
| --- | --- |
| 用户端（中文 / 英文） | http://localhost:3000/zh/ecommerce-form · /en/ecommerce-form |
| 管理端 | http://localhost:80 （`admin / admin123`） |
| 后端健康检查 | http://localhost:8080/api/wimo/task/health |
| Swagger | http://localhost:8080/swagger-ui.html |

### 方式 B：Docker Compose

```bash
cd server && mvn -DskipTests package && cd ..     # 先产出 jar（server/Dockerfile 复制已构建 jar）
docker compose up -d                              # MySQL + Redis + Spring Boot
docker compose --profile frontend up -d           # 追加 Nuxt 用户端 + 管理端（含 nginx 反代）
```

首次启动 MySQL 会自动执行 `server/sql/` 下的 `quartz.sql`、`ry_*.sql`、`wimo.sql`。
前端容器通过 nginx 把 `/prod-api` 与 `/profile` 反代到后端，因此**容器内的用户端直接连的是真实后端**，
不是静态演示降级。端口映射：MySQL 3306、Redis 6379、后端 8080、用户端 3001、管理端 8081。

> 若本机已装 MySQL/Redis/后端，先停掉对应服务，或修改 `docker-compose.yml` 的 `ports` 避免冲突。

### 方式 C：手动逐端启动

```bash
# 1. 起 MySQL + Redis，导入 server/sql/ry_*.sql、quartz.sql、wimo.sql
#    账号 ruoyi/ruoyi123，库名 ry（见 application-druid.yml）

# 2. 后端
cd server && mvn -DskipTests package
java -jar ruoyi-admin/target/ruoyi-admin.jar

# 3. 用户端（指向真实后端）
cd web && pnpm install
NUXT_PUBLIC_API_BASE=http://localhost:8080 pnpm dev

# 4. 管理端
cd admin && pnpm install
npx vite --port 80
```

---

## 用户端 `web/`

Nuxt 3 + `@nuxtjs/i18n`（`prefix` 策略，`/zh/...` 与 `/en/...`）、`useSeoMeta`、`hreflang`、
canonical、深浅色主题、PC/移动端自适应（移动端 logo 唤起左侧菜单抽屉、输入控件统一 16px 避免 iOS 聚焦缩放）。

- **13 个功能**：电商图片 7 个 + 模特穿戴 3 个 + 视频 3 个（含「探店视频」）
- **schema 驱动表单**：`config/menu.ts` / `config/tasks.ts` 定义上传槽位、字段、生成设置与右侧示意图；
  左侧切换功能时表单、封面/示例视频、校验规则同步切换
- **探店视频**：门头照 + 环境照 + 菜品照（4–8 张）+ 博主照（**必填**）+ 店铺名/位置（**选填**）
  + 比例/语言/清晰度/时长/模型；无平台、无国家地区
- **账号**：登录 / 注册双 Tab，登录默认邮箱 + 密码，注册才需验证码 + 双密码 + 协议；
  另支持验证码登录与找回密码
- **真实后端接入**：`composables/useWimoApi.ts` 统一封装，`X-Wimo-Token` 传递会员令牌；
  后端不可达时自动降级为本地演示（dataURL 预览 + 模拟进度），保证纯静态站也能演示

### 静态部署（GitHub Pages）

```bash
cd web
NUXT_APP_BASE_URL=/wimo/ npx nuxt generate
# 产物根需带 .nojekyll（否则 _nuxt 被 Jekyll 忽略）与跳转用 index.html，推送到 gh-pages 分支
```

线上地址：https://jayden1314520.github.io/wimo/

> **重要边界**：GitHub Pages 是纯静态站。它可以独立演示界面与交互，但**无法运行 Spring Boot**。
> 线上若要真实注册、上传、生成与查看记录，必须部署后端，并把用户端的
> `NUXT_PUBLIC_API_BASE` 配成公网 API 域名（或走同域 `/prod-api` 反代）。

---

## 后端 `server/ruoyi-wimo/`

在若依 3.9.2 上新增的业务模块，不改动框架本体。

| 能力 | 实现 |
| --- | --- |
| 账号体系 | 注册 / 密码登录 / 验证码登录 / 找回密码 / 资料 / 登出；BCrypt 加密；Redis 验证码（300s）、60s 频控、7 天令牌 |
| 任务闭环 | 创建 → 后端按模板校验 → 持久化 → 线程池异步 8 步生成 → 结果落库 → 轮询进度；支持重试 / 取消 / 删除 / 分页记录 |
| 状态机 | `0` 待生成 / `1` 生成中 / `2` 完成 / `3` 失败 / `4` 已取消，`progress` 0–100 |
| 13 功能模板 | 启动时由 `wimo/schemas.json` 初始化到 `wimo_schema`；管理端可在线启停、改名、改 schema JSON |
| 素材与结果 | 上传按用户隔离落地 `/profile/upload/yyyy/MM/dd/u<userId>/`；结果写 `wimo_task_result` 并可经 `/profile/result/` 访问 |
| AI 帮写 | 规则模板生成中英结构化卖点文案，可替换真实 LLM |
| 归属隔离 | 登录用户只能操作自己的任务；未登录仅可访问匿名任务 |
| 令牌隔离 | 会员令牌走 `X-Wimo-Token`，避免若依后台 JWT 过滤器误解析 UUID |

### 可插拔适配器（默认全本地模拟）

三类外部依赖都定义了接口 + 本地实现 + 远程实现，改配置即可切换，**无需改代码**：
邮件（`WimoMailSender`）、生成器（`WimoGenerator`）、存储（`WimoStorage`）。

`server/ruoyi-admin/src/main/resources/application.yml` 中：

```yaml
wimo:
  mail:      { provider: mock, http-endpoint: '', http-token: '' }
  generator: { provider: mock, remote-url: '', remote-token: '', step-ms: 350, result-count: 4 }
  storage:   { provider: local }
  auth:      { code-ttl: 300, code-limit-seconds: 60, token-days: 7, demo-code: '' }
```

| 想接什么 | 怎么改 |
| --- | --- |
| 真实邮件（SMTP / 邮件 API） | `wimo.mail.provider=http` + `http-endpoint`；或自行实现 `WimoMailSender` Bean |
| 真实生图 / 生视频模型 | `wimo.generator.provider=remote` + `remote-url`，网关返回 `{"results":["url",...]}` |
| 对象存储 | `wimo.storage.provider=remote`，实现 `WimoStorage` |

> 生产环境务必把 `wimo.auth.demo-code` 留空并切换真实邮件通道，
> 否则 `/api/wimo/auth/code` 会把验证码通过 `demoCode` 回显。

完整接口清单、请求/返回示例、错误码见 **[docs/API.md](docs/API.md)**。

---

## 管理端 `admin/`

若依后台左侧「映物业务」目录（菜单 `2000–2008`）：

| 页面 | 路由 | 能力 |
| --- | --- | --- |
| 任务管理 | `/wimo/task` | 按标题/功能/状态筛选、真分页、详情（素材 + 结果 + 提交参数 JSON）、重试、取消、删除 |
| 数据看板 | `/wimo/dashboard` | 6 张统计卡、状态分布饼图、功能分布柱状图、趋势折线、适配器状态、最近任务；可切近 7/14/30 天 |
| 模板配置 | `/wimo/schema` | 13 个模板的启停开关、改名、排序、schema JSON 在线编辑（带 JSON 合法性校验） |
| 邮件日志 | `/wimo/mail` | 按邮箱/场景筛选、分页、验证码与发送结果 |

---

## 验证情况

本地真实环境（MySQL + Redis + Spring Boot + Nuxt + 若依管理端）端到端验证：

| 验证 | 结果 |
| --- | --- |
| 后端 API 全链路 E2E | **35 / 35**（健康检查、模板、验证码/注册/登录/找回密码/频控、AI 帮写、上传、创建任务、schema 后端校验、结果落库与可访问、记录归属、重试、取消、越权拒绝、登出） |
| 用户端浏览器 E2E | **17 / 17**（注册 → 后端验证码 → 上传 → 真实生成结果 → 自动打开记录抽屉 → 登出，无脚本错误） |
| 管理端页面与接口 E2E | **20 / 20**（4 个页面渲染、3 张 echarts 图表、13 个模板、邮件筛选分页、任务真分页） |

---

## 素材说明

`web/public/images/preview/` 下的封面图与示例视频取自参考站，仅用于界面还原与本地模拟生成演示；
正式商用请替换为自有版权素材或真实 AI 生成结果。运行时产生的 `server/uploadPath/`
已加入 `.gitignore`，不会入库。
