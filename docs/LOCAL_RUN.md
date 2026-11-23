# 映物 VIVID · 本地运行与体系设计说明

> 本文回答两件事：**① 怎么在本地把整套项目跑起来**；**② 这次新加的「模型 / 工作流智能体 / 钻石 / 对象存储」是怎么设计的、在哪儿配**。
> 面向开发者，命令均在项目根目录 `/workspace/wimogen-platform`（你 clone 下来的仓库根）执行。

---

## 0. 一眼看懂这次的架构

```
                      ┌─────────────────────── 管理端 admin (RuoYi-Vue3) ───────────────────────┐
                      │  模型管理(4 类能力)  智能体工作流(13 个菜单)  钻石管理(余额/流水/调整)  │
                      │         │ 配置落库 wimo_model / wimo_workflow / wimo_user 钻石字段      │
                      └─────────┼──────────────────────────────────────────────────────────────┘
                                │ 读取
   用户端 web (Nuxt)  ──────────┼─────────────────► 后端 server (Spring Boot / 若依)
   · 模型下拉动态拉取            │                    · 统一工作流引擎 WimoWorkflowEngine
   · 提交前实时预估钻石          │                    · 计费引擎 WimoBillingEngine
   · 顶栏显示余额                │                    · 钻石两阶段结算 WimoDiamondService
   · 生成结果网格                │                    · 生成器/邮件/存储 可插拔适配器
                                │                             │
                                └───────── 素材上传 ─────────► 七牛 Kodo / Cloudflare R2 / 本地
```

**核心设计观点（对应你的需求）：**

> **所有任务走一个统一引擎，但「一个菜单 = 一个工作流 = 一个智能体」**。
> 引擎是固定的 6 个阶段，而每个菜单的能力、可用模型池、基础价、计费单位、清晰度/时长/模型倍率、执行步骤，全部是 `wimo_workflow` 表里的一条可编辑记录。所以「商品套图」和「探店视频」是不同智能体，但共用同一套执行与结算骨架。管理端改配置，用户端立即生效，无需改代码、无需重新发版。

---

## 1. 前置依赖

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | **17+**（实测 20 也可） | 后端编译目标 17 |
| Maven | 3.9+ | 打 jar |
| MySQL / MariaDB | 5.7+ / 10.x | 库名 `ry`，账号见 `application-druid.yml` |
| Redis | 5+ | 验证码、频控、会员 token |
| Node.js | 18+（实测 22） | 用户端 / 管理端 |
| pnpm | 8+ | 包管理（npm/yarn 亦可） |
| Docker + Compose | 可选 | 一键编排（不想装 MySQL/Redis 时用） |

---

## 2. 最快方式：一键脚本

```bash
cd /workspace/wimogen-platform
./scripts/start-local.sh up
```

脚本会自动：复用或 docker 起 MySQL/Redis → 没 jar 就 `mvn package` → 起后端(8080) → 起用户端(3000) → 起管理端(80) → 等健康检查通过。

```bash
./scripts/start-local.sh status   # 查看 5 个服务状态
./scripts/start-local.sh down     # 停止它启动的进程
```

访问地址：

| 服务 | 地址 | 账号 |
| --- | --- | --- |
| 用户端（中文） | http://localhost:3000/zh/ecommerce-form | 点右上角「登录 / 注册」，注册即送 **100 钻石** |
| 用户端（英文） | http://localhost:3000/en/ecommerce-form | 同上 |
| 管理端 | http://localhost:80 | `admin / admin123`（若依后台） |
| 后端健康检查 | http://localhost:8080/api/wimo/task/health | 返回 `{"code":200,"data":{"ok":true,...}}` |
| 接口文档 | http://localhost:8080/swagger-ui.html | 若依自带 |

---

## 3. 最快方式：Docker Compose 全量编排

```bash
cd /workspace/wimogen-platform
docker compose up -d                        # 起 mysql + redis + server
docker compose --profile frontend up -d     # 再起 web(3001) + admin(8081)
docker compose ps
docker compose logs -f server
```

- 首次启动 MySQL 会自动导入 `server/sql/` 下的 `ry_*.sql`、`quartz.sql`、`wimo.sql`（**wimo.sql 已是含钻石/模型/工作流的最新完整结构**）。
- 前端容器通过 nginx 把 `/prod-api` 反代到 `server`，因此**无需**给 Nuxt 配公网 API 域名。
- 关闭：`docker compose --profile frontend down`。

> ⚠️ 如果你是**从旧版本升级**（数据库里已经有 `wimo_user/wimo_task` 等表），不要重新导入 `wimo.sql`（它会 drop 表清数据），改用增量脚本，见 §5。

---

## 4. 手动逐端启动（排障 / 理解结构时推荐）

### 4.1 建库导数据

```bash
# 全新库
mysql -uroot -e "create database if not exists ry default charset utf8mb4"
mysql -uroot ry < server/sql/ry_20261117.sql
mysql -uroot ry < server/sql/quartz.sql
mysql -uroot ry < server/sql/wimo.sql          # 会员/模板/模型/工作流/任务/结果/钻石流水 + 菜单字典
```

数据库连接串在 `server/ruoyi-admin/src/main/resources/application-druid.yml`，默认 `ruoyi/ruoyi123`；Redis 在 `application.yml`，默认本机 6379。

### 4.2 起基础设施

```bash
mysqld_safe --user=mysql &          # 或 systemctl start mariadb
redis-server --daemonize yes
mysqladmin ping && redis-cli ping  # 应输出 mysqld is alive / PONG
```

### 4.3 起后端

```bash
cd server
mvn -q -DskipTests package
java -jar ruoyi-admin/target/ruoyi-admin.jar
# 或指定上传目录与示例素材目录：
java -jar ruoyi-admin/target/ruoyi-admin.jar \
  --ruoyi.profile=$PWD/uploadPath \
  --wimo.mock.sample-dir=$PWD/../web/public/images/preview
```

启动日志出现 `映物功能模板初始化完成…13 个`、`映物智能体工作流初始化完成…13 个`、`映物 AI 模型初始化完成…11 个` 即成功。

### 4.4 起用户端（Nuxt）

```bash
cd web
pnpm install
NUXT_PUBLIC_API_BASE=http://localhost:8080 pnpm dev --port 3000
```

- 本地开发时 `NUXT_PUBLIC_API_BASE` 指向后端 8080；不配则默认 `/prod-api`（走同域反代）。
- 纯静态构建（GitHub Pages / nginx）：`NUXT_APP_BASE_URL=/wimo/ pnpm generate`，产物在 `web/.output/public`，**必须**带 `.nojekyll`。

### 4.5 起管理端（RuoYi-Vue3）

```bash
cd admin
pnpm install
pnpm dev            # vite 端口见 vite.config.js，代理 /dev-api → 后端
```

---

## 5. 从旧版本升级（不丢数据）

```bash
mysql -uroot ry < server/upgrade/wimo_upgrade.sql
```

脚本幂等（可重复执行），做四件事：`alter table add column if not exists` 补钻石/计费字段、建 `wimo_model`/`wimo_workflow`/`wimo_diamond_log` 三张表、给已有会员补发体验钻石、覆盖菜单 `2000–2015` 与字典。之后重启后端即可初始化 11 个模型 + 13 个工作流。

---

## 6. 对象存储：七牛云 & Cloudflare R2

> 图片默认仍走 `local`（本地磁盘，开箱即用）。**填好凭证后把 `wimo.storage.provider` 改成 `qiniu` 或 `r2` 即切换，无需改代码**。两个适配器均为纯 JDK 实现（七牛上传凭证 HMAC-SHA1 签名 / R2 的 S3 SigV4），不引入第三方 SDK。

配置位置：`server/ruoyi-admin/src/main/resources/application.yml` 的 `wimo.storage`，所有项都支持环境变量覆盖，容器部署直接注入即可。

### 6.1 七牛云 Kodo

```yaml
wimo:
  storage:
    provider: qiniu          # 或环境变量 WIMO_STORAGE_PROVIDER=qiniu
    qiniu:
      access-key: ${QINIU_ACCESS_KEY}       # 七牛「个人中心 → 密钥管理」AK
      secret-key: ${QINIU_SECRET_KEY}       # SK
      bucket:     ${QINIU_BUCKET}           # 空间名
      domain:     ${QINIU_DOMAIN}           # CDN 外链域名，如 https://cdn.example.com（必填）
      region:     ${QINIU_REGION:z0}        # z0华东 z1华北 z2华南 na0北美 as0东南亚
      upload-url: ''                       # 留空按 region 自动选上传域名
```

- 上传路径：`wimo/u<userId>/<uuid><ext>`，返回 `domain + / + key`。
- `domain` 必须配置且是**已绑定 CDN 加速域名**（或测试域名），否则返回的外链无法访问。
- 私有空间需要额外做下载凭证，当前实现面向**公开空间**（电商素材通常公开读）。若要私有读，在 `QiniuStorage` 里把返回 URL 换成带 `e/token` 的私有下载链接即可。

### 6.2 Cloudflare R2（海外 / S3 兼容）

```yaml
wimo:
  storage:
    provider: r2             # 或 WIMO_STORAGE_PROVIDER=r2
    r2:
      account-id:    ${R2_ACCOUNT_ID}
      access-key:    ${R2_ACCESS_KEY}       # R2 API token 的 Access Key ID
      secret-key:    ${R2_SECRET_KEY}
      bucket:        ${R2_BUCKET}
      public-domain: ${R2_PUBLIC_DOMAIN}    # 自定义域 或 https://pub-xxxx.r2.dev（必填）
      endpoint:      ${R2_ENDPOINT}         # 留空则用 https://<account-id>.r2.cloudflarestorage.com
```

- 在 Cloudflare 后台创建 R2 bucket → 「Manage R2 API tokens」生成带 **Object Read & Write** 权限的 token，拿到 AK/SK。
- `public-domain`：在 bucket 设置里开启公开访问会得到 `pub-xxxx.r2.dev`，或绑定自定义域名（推荐，便于 CDN 与防盗链）。
- 通过 AWS SigV4 `PUT Object` 上传，`region` 固定 `auto`（Cloudflare 官方要求），`x-amz-content-sha256` 为 payload 哈希。

### 6.3 邮件（你说到时候自己配，这里先留着）

```yaml
wimo:
  mail:
    provider: http                 # mock=不发真信(验证码在接口 demoCode 回显) / http=调你的邮件服务
    http-endpoint: ${WIMO_MAIL_ENDPOINT}
    http-token: ${WIMO_MAIL_TOKEN}
```

`http` 通道会 `POST {to, scene, code, subject, text}` 到你的 endpoint。本地开发保持 `mock` 即可，注册时验证码直接显示在接口返回里（前端弹窗也会提示）。

### 6.4 生成器（AI 模型）

```yaml
wimo:
  generator:
    provider: remote             # mock=复制示例素材为结果 / remote=调真实模型网关
    remote-url: ${WIMO_GENERATOR_URL}
    remote-token: ${WIMO_GENERATOR_TOKEN}
```

`remote` 时 `RemoteGenerator` 把任务 POST 到网关并解析 `{"results":["url",...]}`。要按「每个智能体调不同模型」，扩展点就在 `WimoModel.endpoint`（见 §7）：把模型 key 传给网关即可，当前 mock 生成器不区分模型（仅计费倍率不同）。

---

## 7. 管理端配置：模型 / 智能体 / 钻石

登录管理端 `admin / admin123`，左侧「映物业务」下有 7 个菜单。

### 7.1 模型管理（四类能力）

菜单「模型管理」维护 `wimo_model`，字段含义：

| 字段 | 作用 |
| --- | --- |
| `capability` | **文生图 `text2img` / 图生图 `img2img` / 图生视频 `img2video` / 文生视频 `text2video`** |
| `modelKey` | 唯一标识，用户端下拉的 value，提交任务时带上 |
| `costMultiplier` | **钻石计费的模型倍率**（如 Pro 模型 1.6 / 2.0） |
| `provider` / `endpoint` / `credentialRef` | 真实模型网关地址与密钥引用名（密钥放环境变量，不明文入库） |
| `configJson` | 默认参数（支持的最大时长、清晰度档位等，用户端可读取做联动） |
| `enabled` | 停用后从所有用户端下拉移除 |

**用户端怎么选模型**：web 端 `TaskForm` 按当前菜单的工作流向 `/api/wimo/model/list?workflow=xxx` 拉取，后端自动套用「该智能体的 capability + allowedModels 白名单」。所以你在这里新增/停用/改倍率，用户端下拉和费用立即变化。

### 7.2 智能体工作流（一个菜单 = 一个工作流）

菜单「智能体工作流」维护 `wimo_workflow`，13 条对应 13 个功能。这是**计费规则的核心**：

**计费公式（所有智能体共用，参数逐条不同）：**

```
cost = clamp( ceil( (baseCost + unitCost × units) × Π factor(维度) × modelFactor ), minCost, maxCost )

units       图片按「结果张数」(resultCount，默认4) / 视频按「秒」(duration，默认5) / flat=0
factor      维度倍率，来自 multipliers JSON
             图片：resolution(2k=1.0, 4k=1.6)、quality(low=0.8, mid=1.0, high=1.4)
             视频：videoResolution(480p=1.0, 720p=1.4, 1080p=1.8, 2k=2.4, 4k=3.2)
modelFactor 所选模型的 costMultiplier
```

**实例（探店视频 `storeVisit`，base=22、每秒1.6）：**

| 配置 | 计算 | 消耗 |
| --- | --- | --- |
| 720P / 5秒 / Seedance 极速(×1.0) | `(22+1.6×5)×1.4×1.0` | **42 钻石** |
| 1080P / 30秒 / Seedance 2.5 Pro(×2.0) | `(22+1.6×30)×1.8×2.0` | **252 钻石** |

**实例（商品套图 `productSet`，base=8、每张2、默认4张）：**

| 配置 | 计算 | 消耗 |
| --- | --- | --- |
| 2K / 标准画质 / 映物图像(×1.0) | `(8+2×4)×1.0×1.0×1.0` | **16 钻石** |
| 4K / 高画质 / 映物 Pro(×1.6) | `(8+2×4)×1.6×1.4×1.6` | **58 钻石** |

> 所以「视频和清晰度、时长、模型都有关」这句话，落地上就是：视频类工作流 `unit=second` + `unitField=duration` + `videoResolution` 倍率 + 模型倍率；图片类 `unit=count` + `resolution/quality` 倍率。每个菜单的基础价、单价、倍率都能在管理端单独改。

可编辑字段：`baseCost` 基础价、`unit/unitCost/unitField` 计费单位、`multipliers` 维度倍率 JSON、`minCost/maxCost` 上下限、`capability/defaultModel/allowedModels` 模型池、`stepsJson` 执行步骤（决定生成进度推进的步数，也是智能体编排说明）、`enabled` 启停。

**每次下单都会把完整计费明细写入 `wimo_task.billing_snapshot`**（基础价、单位数、每个倍率、模型倍率、取整前后、最终价），管理端「任务管理 → 详情 → 计费快照」可追溯「为什么扣了这么多」；后续改规则不影响历史账单。

### 7.3 钻石账户体系

菜单「钻石管理」：

- **注册赠送**：`wimo.diamond.register-gift`（默认 100），可在 `application.yml` 改。
- **两阶段结算**（关键，避免「生成失败还扣钱」）：
  1. 提交任务 → **预扣 `freeze`**：可用余额 → 冻结额（`update ... where diamond_balance >= cost` 原子操作，防并发超扣）
  2. 生成成功 → **扣减 `consume`**：冻结额 → 已消耗（累加 `diamond_used`）
  3. 失败 / 取消 → **退还 `refund`**：冻结额 → 可用余额
  4. 重试：**不重复扣费**（只有「失败时已退还」的任务才按原账单重新预扣）
- **余额不足**：`createTask` 直接拒绝并回滚脏任务，用户端提交按钮置灰并提示「本次需 X 钻石，当前 Y 钻石」。
- **匿名演示用户**（未登录 / GitHub Pages 静态站）默认 `charge-anonymous: false`，不扣费，保证演示可继续。
- 所有变动写 `wimo_diamond_log` 流水（变动前后余额/冻结额、关联任务、智能体、备注），管理端可筛选、可人工赠送/扣减（`wimo:diamond:edit`）。
- 数据看板新增钻石消耗卡片、任务列表新增「消耗钻石」列。

---

## 8. 用户端看到什么

- 顶栏登录后显示 **💎 钻石余额**，随预扣/扣减/退还实时刷新。
- 表单「生成设置」里的**模型下拉来自后端**（不再写死），视频清晰度选项扩展到 480p/720p/1080p/2k/4k。
- 表单底部有 **「本次预计消耗 X 钻石」**，改清晰度 / 时长 / 模型会实时向 `/api/wimo/billing/quote` 询价并更新；余额不足时提交按钮禁用 + 红字提示。
- 提交后 toast 提示「已预扣 N 钻石，生成失败自动退还」，生成完成刷新余额。
- 后端不可达时（如 GitHub Pages 静态演示）自动降级：模型回退内置选项、预估给演示价、生成走本地模拟，页面不白屏。

---

## 9. 验证

```bash
# 后端接口全链路（注册/验证码/登录/上传/生成/记录/重试/取消/计费）
python3 /tmp/wimo_api_e2e.py
# 用户端浏览器全链路（真实后端）
python3 /tmp/wimo_web_e2e.py
# 管理端 4+3 个页面与接口
python3 /tmp/wimo_admin_e2e.py
```

快速手测计费：

```bash
curl -s -X POST http://localhost:8080/api/wimo/billing/quote \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"storeVisit","common":{"videoResolution":"1080p","duration":30,"videoModel":"seedance25","resultCount":4}}'
# → data.cost = 252，data.snapshot 里有完整明细
```

完整接口清单见 [`docs/API.md`](./API.md)。

---

## 10. 常见坑

| 现象 | 原因 / 处理 |
| --- | --- |
| 管理端登录要验证码 | 本地可在若依 `sys_config` 关闭验证码，或用默认 `admin/admin123` 直接登录（本项目已关） |
| 用户端登录报网络错误 | `NUXT_PUBLIC_API_BASE` 没指向 8080；或后端没起。静态站会自动降级，属预期 |
| `NUXT_APP_BASE_URL=/wimo/` 后静态资源 404 | Pages 产物根目录需要 `.nojekyll`（否则 `_nuxt` 被 Jekyll 忽略）和跳转用 `index.html` |
| 会员 token 被若依 JWT 过滤器误解析 | 会员令牌走独立 header `X-Wimo-Token`，**不要**塞 `Authorization` |
| 七牛/R2 上传报「未配置 …」 | 把对应 `wimo.storage.*` 凭证填齐；`provider` 改回 `local` 可立即恢复 |
| 改了工作流价格但历史任务变了 | 不会变 —— 历史账单读 `billing_snapshot` 快照；只有新任务用新规则 |
| GitHub Pages 上是「假」生成 | Pages 是纯静态站，没有 Java 后端，走本地模拟。要真账号 + 真生成，必须部署 Spring Boot 并把 `NUXT_PUBLIC_API_BASE` 指向公网 API，或走同域 `/prod-api` 反代（容器方案已备好） |

---

## 11. 目录速查

```
web/        Nuxt3 用户端（i18n / schema 驱动表单 / 真实后端接入）
  config/tasks.ts            13 个功能的表单 schema
  composables/useWimoApi.ts  后端 API 封装（含模型/智能体/预估/钻石）
  composables/useAuth.ts     登录态 + 钻石余额
  components/TaskForm.vue    动态模型下拉 + 实时钻石预估
server/
  ruoyi-admin/               若依启动模块 + application.yml（wimo.* 配置在此）
  ruoyi-wimo/                映物业务模块
    service/WimoWorkflowEngine.java   统一工作流引擎（6 阶段）
    service/WimoBillingEngine.java    计费公式
    service/WimoDiamondService.java   两阶段结算
    service/WimoModelService.java     模型池
    service/WimoWorkflowService.java  13 智能体
    adapter/QiniuStorage.java         七牛 Kodo
    adapter/R2Storage.java            Cloudflare R2
    resources/wimo/{schemas,models,workflows}.json   启动初始化数据
  sql/wimo.sql               全新库完整建表（含菜单/字典）
  upgrade/wimo_upgrade.sql   旧库增量升级（幂等，不清数据）
admin/
  src/views/wimo/{model,workflow,diamond,dashboard,task,schema,mail}/index.vue
  src/api/wimo/{model,workflow,diamond,task,dashboard,schema,mail}.js
docs/API.md                 全量接口文档
docs/LOCAL_RUN.md           本文
docker-compose.yml          编排（含 --profile frontend）
scripts/start-local.sh      本地一键启动
```
