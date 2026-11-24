# 映物 VIVID

AI 电商视觉生成平台。**13 个功能 = 13 个智能体工作流**的表单驱动生成闭环，含**★钻石计费规则树（能力→档位→模型→工作流四级可配）、模型管理、钻石账户、七牛云 / Cloudflare R2 对象存储**，从用户端到后端到管理端全部可本地跑通。

```
wimogen-platform/
├── web/                 # 用户端 · Nuxt 3（SSR / 中英双语 / SEO / 可静态部署 / 逐层费用拆解）
├── server/              # 后端 · 若依 RuoYi-Vue（Spring Boot + MyBatis + MySQL + Redis）
│   ├── ruoyi-wimo/      # 映物业务模块（账号、工作流引擎、能力树计费、钻石、模型、模板、看板、适配器）
│   ├── sql/wimo.sql     # 10 张业务表 + 18 个菜单 + 6 类字典（全新库，v3）
│   ├── upgrade/         # wimo_upgrade.sql(v1→v2) + wimo_upgrade_v3.sql(v2→v3 能力树)，幂等不清数据
│   └── Dockerfile
├── admin/               # 管理端 · 若依 Vue3（★计费规则树 / 任务 / 看板 / 模板 / 邮件 / 模型 / 工作流 / 钻石）
├── deploy/              # 前端容器化 Dockerfile 与 nginx 配置
├── docs/API.md          # 完整接口文档（含能力树接口与 breakdown 示例）
├── docs/LOCAL_RUN.md    # ★ 本地运行 + 计费规则树 / 七牛/R2 / 模型 / 智能体 / 钻石体系设计说明
├── scripts/start-local.sh
├── docker-compose.yml   # MySQL + Redis + Spring Boot（+ 可选前端 profile）
└── README.md
```

> **📘 想在本地把项目跑起来，直接看 [docs/LOCAL_RUN.md](docs/LOCAL_RUN.md)**：包含一键脚本、Docker Compose、手动逐端启动、默认账号、**计费规则树如何逐节点调价**、七牛/R2 凭证配置、模型/智能体/钻石如何配、常见坑。

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
- **★能力树驱动的费用预估**：清晰度/画质档位与模型下拉由 `/api/wimo/capability/tree` 下发（标签带倍率），
  表单底「本次预计消耗」随清晰度/时长/模型实时询价，**点击费用条展开逐层费用拆解**
  （能力基础价 → 单位计价 → 各档位倍率 → 模型倍率 → 取整限价，每层显示取值与累计小计）；
  余额不足禁用提交。静态站无后端时用内置本地明细降级，仍可展开查看

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
| 智能体工作流 | **一个菜单 = 一个工作流**：13 个智能体各有能力/模型池/计费规则/执行步骤，共用统一引擎 `WimoWorkflowEngine`（模板校验 → 装载智能体 → 计价 → 预扣 → 落库 → 异步执行） |
| **★钻石计费规则树** | `wimo_capability` 5 个能力分支（文本生成/文生图/图生图/图生视频/文生视频）**基础价各不同**，`wimo_price_node` 把清晰度/时长/画质/字数/语言等维度档位节点化（逐档可调倍率），`wimo_model` 模型倍率，`wimo_workflow` 可覆盖或继承能力层。`WimoCapabilityService` 启动从 JSON 幂等灌入并组装四级树 |
| 钻石计费 | `WimoBillingEngine` **沿树逐层求值**：`clamp(ceil((能力基础价 + 单位单价×单位数) × Π档位倍率 × 模型倍率), min, max)`；视频按秒 + 清晰度 + 时长 + 模型，图片按张 + 清晰度/画质 + 模型；输出逐层 `breakdown`。能力层无节点时自动回退旧 `multipliers` JSON（兼容老库） |
| 钻石账户 | `WimoDiamondService` 两阶段结算（预扣 → 成功扣减 / 失败·取消退还），原子 UPDATE 防超扣、全量流水留痕、余额不足拒绝下单、重试不重复扣费 |
| 计费快照 | 每单把**整条求值路径**写 `wimo_task.billing_snapshot`（能力/价格来源/各档位命中倍率/逐层 breakdown/取整前后），改规则不影响历史账单，管理端详情与用户端费用拆解均可追溯 |

> 完整设计说明与本地运行步骤见 **[docs/LOCAL_RUN.md](docs/LOCAL_RUN.md)**。

### 可插拔适配器（默认全本地模拟）

三类外部依赖都定义了接口 + 本地实现 + 远程实现，改配置即可切换，**无需改代码**：
邮件（`WimoMailSender`）、生成器（`WimoGenerator`）、存储（`WimoStorage`）。

`server/ruoyi-admin/src/main/resources/application.yml` 中：

```yaml
wimo:
  mail:      { provider: mock, http-endpoint: '', http-token: '' }
  generator: { provider: mock, remote-url: '', remote-token: '', step-ms: 350, result-count: 4 }
  storage:   { provider: local }   # local | qiniu | r2 | remote
  diamond:   { register-gift: 100, charge-anonymous: false }
  auth:      { code-ttl: 300, code-limit-seconds: 60, token-days: 7, demo-code: '' }
```

| 想接什么 | 怎么改 |
| --- | --- |
| 真实邮件（SMTP / 邮件 API） | `wimo.mail.provider=http` + `http-endpoint`；或自行实现 `WimoMailSender` Bean |
| 真实生图 / 生视频模型 | `wimo.generator.provider=remote` + `remote-url`，网关返回 `{"results":["url",...]}`；模型清单在管理端「模型管理」按四类能力配置 |
| **七牛云 Kodo** | `wimo.storage.provider=qiniu` + `qiniu.access-key/secret-key/bucket/domain`（凭证与区域说明见 LOCAL_RUN.md §6） |
| **Cloudflare R2** | `wimo.storage.provider=r2` + `r2.account-id/access-key/secret-key/bucket/public-domain`（S3 SigV4，纯 JDK 实现） |
| 其他对象存储 | `wimo.storage.provider=remote`，实现 `WimoStorage` |

> 生产环境务必把 `wimo.auth.demo-code` 留空并切换真实邮件通道，
> 否则 `/api/wimo/auth/code` 会把验证码通过 `demoCode` 回显。

完整接口清单、请求/返回示例、错误码见 **[docs/API.md](docs/API.md)**。

---

## 管理端 `admin/`

若依后台左侧「映物业务」目录（菜单 `2000–2017`）：

| 页面 | 路由 | 能力 |
| --- | --- | --- |
| 任务管理 | `/wimo/task` | 按标题/功能/状态筛选、真分页、详情（素材 + 结果 + 提交参数 JSON + **计费快照 + 逐层明细 + 消耗钻石 + 结算状态**）、重试、取消、删除 |
| 数据看板 | `/wimo/dashboard` | 6 张统计卡 + **4 张钻石卡**、状态分布饼图、功能分布柱状图、趋势折线、适配器状态、最近任务（含消耗钻石列） |
| 模板配置 | `/wimo/schema` | 13 个模板的启停开关、改名、排序、schema JSON 在线编辑（带 JSON 合法性校验） |
| 邮件日志 | `/wimo/mail` | 按邮箱/场景筛选、分页、验证码与发送结果 |
| **★计费规则树** | `/wimo/priceTree` | **四级可展开树**（能力→维度档位/模型/工作流）：逐节点改基础价、档位倍率、启停、增删档，重载默认，右侧**即时试算**验证改价生效；改一个 4K 档即影响该能力全部工作流 |
| **模型管理** | `/wimo/model` | 五类能力（含文本生成预留）模型 CRUD、启停、计费倍率、endpoint、默认参数 JSON |
| **智能体工作流** | `/wimo/workflow` | 13 个菜单的能力归属/`costSource`（覆盖或继承）/基础价/计费单位/单位单价/上下限/模型池/执行步骤/启停 |
| **钻石管理** | `/wimo/diamond` | 会员钱包、钻石流水（按类型/会员筛选分页）、人工赠送/扣减、总览卡片 |

---

## 验证情况

本地真实环境（MySQL + Redis + Spring Boot + Nuxt + 若依管理端）端到端验证：

> 本次 v3「钻石计费规则树」全量回归：**7 组 / 共 200 项，全部通过，0 失败**（真实 MariaDB + Redis + Spring Boot + Nuxt + 若依管理端本地环境）。

| 验证 | 结果 |
| --- | --- |
| 后端 API 全链路 E2E | **35 / 35**（健康检查、模板、验证码/注册/登录/找回密码/频控、AI 帮写、上传、创建任务、schema 后端校验、结果落库与可访问、记录归属、重试、取消、越权拒绝、登出） |
| 钻石账户与计费回归 E2E | **44 / 44**（两阶段预扣/扣减/退还、余额不足拒绝、并发防超扣、重试不重复扣费、5 类能力、13 个模型、看板钻石字段） |
| **★ 能力树 / 逐层求值专项 E2E** | **53 / 53**（5 分支基础价互不相同、四级树结构、用户端按工作流过滤、`breakdown` 逐层明细、视频清晰度×时长×模型三重叠加、4K 档改价即时生效、档位启停/增删、`costSource=inherit`、`text2text` 预留、老库回退 `workflow.multipliers`、既有价格零漂移） |
| 管理端页面与接口 E2E | **29 / 29**（8 个页面渲染、3 张 echarts 图表、13 个模板、邮件筛选分页、任务真分页、含★计费规则树、无脚本错误） |
| 用户端浏览器 E2E | **17 / 17**（注册 → 后端验证码 → 上传 → 真实生成结果 → 自动打开记录抽屉 → 登出，无脚本错误） |
| 静态站降级 E2E | **10 / 10**（无后端时验证码/注册/生成/预估/费用拆解全本地降级，不白屏） |
| 静态产物回归 E2E | **12 / 12**（中英双语、深色主题、移动端、探店字段纠偏、菜单抽屉、无横向溢出） |
| 能力树 UI 截图核验 | 用户端「逐层费用拆解」6 行（22 → 30 → … → 30 钻）、管理端计费规则树 88 个节点，两端均无脚本错误 |

复现命令见 [`docs/LOCAL_RUN.md` 第 9 节](./docs/LOCAL_RUN.md)。

---

## 素材说明

`web/public/images/preview/` 下的封面图与示例视频取自参考站，仅用于界面还原与本地模拟生成演示；
正式商用请替换为自有版权素材或真实 AI 生成结果。运行时产生的 `server/uploadPath/`
已加入 `.gitignore`，不会入库。
