# 映物 VIVID · 本地运行与体系设计说明

> 本文回答三件事：**① 怎么在本地把整套项目跑起来**；**② 「钻石计费规则树 / 模型 / 工作流智能体 / 钻石账户 / 对象存储」是怎么设计的、在哪儿配**；**③ 一次生成到底扣多少钻石、怎么算出来的**。
> 面向开发者，命令均在项目根目录 `/workspace/wimogen-platform`（你 clone 下来的仓库根）执行。

---

## 0. 一眼看懂这次的架构

```
                      ┌──────────────────────────── 管理端 admin (RuoYi-Vue3) ────────────────────────────┐
                      │  ★计费规则树(能力→档位→模型→工作流)  模型管理  智能体工作流  钻石管理  数据看板   │
                      │   配置落库 wimo_capability / wimo_price_node / wimo_model / wimo_workflow / 钻石字段│
                      └───────────────────────────────┬──────────────────────────────────────────────────┘
                                                      │ 读取 / 即时改价
   用户端 web (Nuxt)  ─────────────────────────────────┼─────────► 后端 server (Spring Boot / 若依)
   · 维度档位/模型下拉由能力树驱动                    │            · 能力层 WimoCapabilityService（树根）
   · 提交前实时预估 + 逐层费用拆解                    │            · 统一工作流引擎 WimoWorkflowEngine
   · 顶栏显示余额                                      │            · 计费引擎 WimoBillingEngine（沿树求值）
   · 生成结果网格                                      │            · 钻石两阶段结算 WimoDiamondService
                                                      │            · 生成器/邮件/存储 可插拔适配器
                                                      └── 素材上传 ──► 七牛 Kodo / Cloudflare R2 / 本地
```

**核心设计观点（对应你的需求）：**

> **钻石配置是一棵树，不是一个平铺列表。** 五类能力（文本生成 / 文生图 / 图生图 / 图生视频 / 文生视频）是 5 个树根，**每个分支的基础钻石都不同**；每个分支下面挂着「清晰度、时长、画质、语言、字数」等**维度档位节点**，以及「模型倍率」和「工作流基础价」——它们都与最终消耗相关。

> **所有任务走一个统一引擎，但「一个菜单 = 一个工作流 = 一个智能体」**。引擎固定 6 个阶段，每个菜单挂在某个能力分支下，可继承或覆盖能力层的基础价。计费引擎沿树**逐层求值**：能力默认 → 工作流覆盖/继承 → Σ维度档位倍率 → 模型倍率 → 取整限价，并把整条求值路径写进 `billing_snapshot`。管理端在「计费规则树」页改任一节点，用户端下拉、预估价、实际扣费立即生效，无需改代码、无需重新发版。

**计费规则树（四级）：**

```
能力 wimo_capability（树根，5 支，基础价各不同）
├── 维度分组 wimo_price_node.group（清晰度 / 时长 / 画质 / 字数 / 语言）
│   └── 档位 wimo_price_node.option（如 4K ×1.6、720P ×1.4、60秒…）  ← 逐档可调价
├── 模型 wimo_model（每模型 costMultiplier 倍率）
└── 工作流 wimo_workflow（13 个菜单，costSource=override 用自己的价 / inherit 继承能力层）
```

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

按你当前的版本选对应脚本，**都是幂等的，可重复执行、不清空任何数据**：

```bash
# v1 → v2（模型 / 智能体工作流 / 钻石体系）
mysql -uroot ry < server/upgrade/wimo_upgrade.sql

# v2 → v3（★能力层 + 计费规则树，本次新增）
mysql -uroot ry < server/upgrade/wimo_upgrade_v3.sql
```

- `wimo_upgrade.sql`：`alter table add column if not exists` 补钻石/计费字段、建 `wimo_model`/`wimo_workflow`/`wimo_diamond_log`、给已有会员补发体验钻石、覆盖菜单字典。
- `wimo_upgrade_v3.sql`：建 `wimo_capability`（5 个能力分支）与 `wimo_price_node`（维度档位）两张表、给 `wimo_workflow` 补 `cost_source`、菜单扩到 `2000–2017`（新增「计费规则树」）、字典新增「文本生成」能力与「计费维度」。

> **全新库直接 `mysql -uroot ry < server/sql/wimo.sql` 一次到位即可**，两个升级脚本都不用跑。
> 升级脚本只建表结构，**能力层与档位的初始数据由后端启动时从 `capabilities.json` / `price-nodes.json` 幂等灌入**（不覆盖管理端已有改价）。所以升级后**务必重启一次后端**。

> 若你暂时不跑 v3 脚本也没关系：计费引擎检测到新表不存在会自动回退到 `wimo_workflow.multipliers` 旧配置，功能照常可用，只是管理端「计费规则树」页会是空的。

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

## 7. 管理端配置：★计费规则树 / 模型 / 智能体 / 钻石

登录管理端 `admin / admin123`，左侧「映物业务」下有 8 个菜单，其中**「计费规则树」是本次新增的核心**。

### 7.0 计费规则树（能力 → 维度档位 → 模型 → 工作流）

菜单「计费规则树」是一棵**可展开、可逐节点调价的四级树**，一张页面就能看清「某一支能力到底怎么算钱」：

| 层级 | 存储 | 能配什么 | 改动影响 |
| --- | --- | --- | --- |
| ① 能力分支 | `wimo_capability` | 5 类能力各自的**基础价 / 计费单位 / 单位单价 / 价格区间 / 默认模型** | `costSource=inherit` 的工作流立即采用新基础价 |
| ② 维度档位 | `wimo_price_node` | 清晰度、时长、画质、字数、语言的**每一档倍率 factor**（如 4K ×1.6、720P ×1.4）、启停、增删档 | **改一个 4K 档 = 该能力下全部工作流的 4K 消耗一起变**（不再是 13 份 JSON 各改一遍） |
| ③ 模型 | `wimo_model` | 每模型 `costMultiplier` 倍率 | 用户端下拉与预估价即时变化（在「模型管理」页维护，树里只读投影） |
| ④ 工作流 | `wimo_workflow` | 13 个菜单，`costSource=override/inherit` 决定用自身基础价还是继承能力层 | 单个智能体的差异化定价 |

页面右侧可**即时试算**：选一个工作流 + 清晰度/时长/模型，点「试算」直接走真实计费引擎，返回逐层 breakdown，用来验证「刚改的档位有没有生效」。

**沿树逐层求值公式（WimoBillingEngine）：**

```
cost = clamp( ceil( (能力基础价 + 单位单价 × 单位数) × Π 档位倍率 × 模型倍率 ), minCost, maxCost )

  1 能力默认   wimo_capability.baseCost / unitCost / minCost / maxCost（每一支不同）
  2 工作流     costSource=override 用 wimo_workflow 的基础价；=inherit 继承能力层
  3 维度倍率   从 wimo_price_node 按(能力,维度)取选中档位的 factor 连乘
               图片 resolution + quality；视频 videoResolution + duration；文案 wordCount + language
  4 单位计价   units：图片按张(resultCount) / 视频按秒(duration) / 文案按千字(kiloword)
  5 模型倍率   wimo_model.costMultiplier
  6 取整限价   ceil 后 clamp 到 [minCost, maxCost]
```

> **向后兼容**：如果某能力下没有任何启用的价格节点（例如老库没跑 v3 升级），计费引擎自动回退到 `wimo_workflow.multipliers` 的旧 JSON，不会 500。所有档位倍率都与既有工作流配置完全对齐，**切换后历史询价结果一分不变**。

初始数据来自后端资源文件，启动时幂等灌入（不覆盖管理端已有改动）：
- `server/ruoyi-wimo/src/main/resources/wimo/capabilities.json` —— 5 个能力分支
- `server/ruoyi-wimo/src/main/resources/wimo/price-nodes.json` —— 维度档位（自动生成维度分组节点）
- 也可在「计费规则树」页点「重载默认」调用 `POST /wimo/capability/reload` 重新灌入。

**5 个能力分支的基础价（默认，管理端可改）：**

| 能力 | capabilityKey | 基础价 | 计费单位 | 单价 | 挂的工作流 |
| --- | --- | --- | --- | --- | --- |
| 文本生成 | `text2text` | 6 钻 | 千字 kiloword | 2.0 | **仅预留**（字数/语言/模型档位已配好，暂不挂用户端菜单） |
| 文生图 | `text2img` | 12 钻 | 张 count | 3.0 | —（预留模型池） |
| 图生图 | `img2img` | 10 钻 | 张 count | 2.0 | 10 个图片工作流 |
| 图生视频 | `img2video` | 22 钻 | 秒 second | 1.6 | 商品视频 / 带货视频 / 探店视频 |
| 文生视频 | `text2video` | 26 钻 | 秒 second | 1.8 | —（预留模型池） |

### 7.1 模型管理（五类能力）

菜单「模型管理」维护 `wimo_model`，字段含义：

| 字段 | 作用 |
| --- | --- |
| `capability` | **文本生成 `text2text` / 文生图 `text2img` / 图生图 `img2img` / 图生视频 `img2video` / 文生视频 `text2video`** |
| `modelKey` | 唯一标识，用户端下拉的 value，提交任务时带上 |
| `costMultiplier` | **钻石计费的模型倍率**（如 Pro 模型 1.6 / 2.0 / 2.2） |
| `provider` / `endpoint` / `credentialRef` | 真实模型网关地址与密钥引用名（密钥放环境变量，不明文入库） |
| `configJson` | 默认参数（支持的最大时长、清晰度档位等，用户端可读取做联动） |
| `enabled` | 停用后从所有用户端下拉移除 |

**用户端怎么选模型**：web 端 `TaskForm` 按当前菜单的工作流向 `/api/wimo/capability/tree?workflow=xxx` 拉取所属能力分支（含档位与模型倍率），模型列表也可由 `/api/wimo/model/list?workflow=xxx` 兜底。所以你在这里新增/停用/改倍率，用户端下拉和费用立即变化。

### 7.2 智能体工作流（一个菜单 = 一个工作流）

菜单「智能体工作流」维护 `wimo_workflow`，13 条对应 13 个功能。它们挂在某个能力分支下，可**覆盖或继承**能力层的基础价，并各自设定模型白名单与执行步骤。

**实例（探店视频 `storeVisit`，挂在 img2video，override base=22、每秒1.6）：**

| 配置 | 计算 | 消耗 |
| --- | --- | --- |
| 720P / 5秒 / Seedance 极速(×1.0) | `(22+1.6×5)×1.4×1.0` | **42 钻石** |
| 1080P / 30秒 / Seedance 2.5 Pro(×2.0) | `(22+1.6×30)×1.8×2.0` | **252 钻石** |

**实例（商品套图 `productSet`，base=8、每张2、默认4张）：**

| 配置 | 计算 | 消耗 |
| --- | --- | --- |
| 2K / 标准画质 / 映物图像(×1.0) | `(8+2×4)×1.0×1.0×1.0` | **16 钻石** |
| 4K / 高画质 / 映物 Pro(×1.6) | `(8+2×4)×1.6×1.4×1.6` | **58 钻石** |

> 所以「视频和清晰度、时长、模型都有关」这句话，落地上就是：视频类能力 `unit=second` + `unitField=duration` + `videoResolution` 档位 + 模型倍率；图片类 `unit=count` + `resolution/quality` 档位。**档位倍率现在统一在能力树的 `wimo_price_node` 里配**（改一处影响整支），工作流只保留自己的基础价与模型白名单。

可编辑字段：`capability` 所属能力分支、`costSource`（`override` 用自身基础价 / `inherit` 继承能力层）、`baseCost` 基础价、`unit/unitCost/unitField` 计费单位、`minCost/maxCost` 上下限、`defaultModel/allowedModels` 模型池、`stepsJson` 执行步骤（决定生成进度推进的步数，也是智能体编排说明）、`enabled` 启停。`multipliers` 维度倍率 JSON 保留作**老库回退**，能力树启用后不再读它。

**每次下单都会把整条求值路径写入 `wimo_task.billing_snapshot`**：`capability`、`costSource`、`priceSource`（`capability-tree` / `workflow-multipliers`）、基础价、单位数、每个档位命中的 factor（`factors`/`factorProduct`）、模型倍率、取整前后、最终价，以及**逐层 `breakdown` 明细**（能力基础 → 单位计价 → 各维度 → 模型 → 限价，每层带小计）。管理端「任务管理 → 详情 → 计费快照」与用户端「费用拆解」都可追溯「为什么扣了这么多」；后续改规则不影响历史账单。

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
- 表单「生成设置」里的**清晰度 / 画质档位与模型下拉由能力树驱动**（`capability/tree`），档位标签会带上倍率（如「4K · ×1.6」），不再写死。
- 表单底部有 **「本次预计消耗 X 钻石」**，改清晰度 / 时长 / 模型会实时向 `/api/wimo/billing/quote` 询价并更新；余额不足时提交按钮禁用 + 红字提示。
- **点费用条可展开「逐层费用拆解」**：能力基础价 → 单位计价（几秒/几张×单价）→ 每个维度档位倍率 → 模型倍率 → 取整限价，每层显示取值、倍率与累计小计，一眼看懂「为什么是 42 钻」。
- 提交后 toast 提示「已预扣 N 钻石，生成失败自动退还」，生成完成刷新余额。
- 后端不可达时（如 GitHub Pages 静态演示）自动降级：档位/模型回退内置选项、预估给演示价并附一份**本地费用拆解**、生成走本地模拟，页面不白屏。

---

## 9. 验证

本次 v3 全量回归结果：**7 组 / 共 200 项，全部通过，0 失败**。脚本放在 `/tmp`（联调临时产物，不入库），跑之前先按第 4 节把后端、用户端、管理端、静态站都起起来。

```bash
# 后端接口全链路（注册/验证码/登录/上传/生成/记录/重试/取消/计费）          35 / 35
python3 /tmp/wimo_api_e2e.py
# 钻石账户与计费回归（两阶段结算、余额不足、并发防超扣、重试不重复扣费）      44 / 44
python3 /tmp/wimo_diamond_e2e.py
# ★能力树 / 逐层求值专项（档位改价即时生效、视频叠加、文本生成预留、向后兼容）  53 / 53
python3 /tmp/wimo_capability_e2e.py
# 管理端页面与接口（8 个页面、3 张图表、含★计费规则树）                       29 / 29
python3 /tmp/wimo_admin_e2e.py
# 用户端浏览器全链路（真实后端）                                             17 / 17
python3 /tmp/wimo_web_e2e.py
# 静态站降级（无后端时全链路本地模拟，不白屏）                                10 / 10
python3 /tmp/wimo_pages_e2e.py
# 静态产物回归（中英 / 深色 / 移动端 / 探店字段 / 菜单抽屉）                   12 / 12
python3 /tmp/wimo_local_pages_e2e.py
# 能力树 UI 截图（用户端费用拆解 + 管理端规则树，输出到 /workspace/wimo-screenshots）
python3 /tmp/wimo_capability_ui.py
```

> 三个计费类脚本都会**改价 / 启停 / 增删档位来做即时生效验证，并在结尾还原**，可反复重跑；脚本之间请串行执行，避免并发写同一套规则造成断言互相干扰。

快速手测计费（返回里带 `breakdown` 逐层明细）：

```bash
curl -s -X POST http://localhost:8080/api/wimo/billing/quote \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"storeVisit","common":{"videoResolution":"1080p","duration":30,"videoModel":"seedance25","resultCount":4}}'
# → data.cost = 252，data.snapshot.priceSource = capability-tree
# → data.breakdown 逐层：
#   能力基础 22 → 单位计价 (22 + 1.6×30 = 70) → 时长档 30s ×1.0 → 清晰度 1080p ×1.8 (=126)
#   → 模型 seedance25 ×2.0 (=252) → 取整限价 → 合计 252 钻
# 手测样例：img2video 的「清晰度 + 时长 + 模型」三重叠加，正是视频比图片贵的原因
```

查看整棵计费规则树：

```bash
curl -s 'http://localhost:8080/api/wimo/capability/tree?workflow=storeVisit'
# → 返回 img2video 分支：基础价 22 / 每秒 1.6 / videoResolution 与 duration 档位 / 模型倍率
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
| 改了能力树的 4K 档但某工作流没变 | 该工作流可能是 `costSource=override` 且自己的 `multipliers` 里也有旧值。能力树节点**优先**于工作流 JSON，只对该能力下的维度生效；若你把该能力所有档位都停用了，引擎会回退到 `workflow.multipliers` |
| 计费规则树页是空的 | 没跑 `wimo_upgrade_v3.sql` 建表，或建表后没重启后端灌初始数据。执行脚本 + 重启，或点页面「重载默认」 |
| 用户端费用条点不开明细 | 只有 `quote.breakdown` 非空时才可展开；静态站降级也内置了一份本地明细，可正常展开 |
| GitHub Pages 上是「假」生成 | Pages 是纯静态站，没有 Java 后端，走本地模拟。要真账号 + 真生成，必须部署 Spring Boot 并把 `NUXT_PUBLIC_API_BASE` 指向公网 API，或走同域 `/prod-api` 反代（容器方案已备好） |

---

## 11. 目录速查

```
web/        Nuxt3 用户端（i18n / schema 驱动表单 / 真实后端接入）
  config/tasks.ts            13 个功能的表单 schema
  composables/useWimoApi.ts  后端 API 封装（含能力树/模型/智能体/预估/钻石）
  composables/useAuth.ts     登录态 + 钻石余额
  components/TaskForm.vue    能力树驱动的档位下拉 + 实时预估 + 逐层费用拆解
server/
  ruoyi-admin/               若依启动模块 + application.yml（wimo.* 配置在此）
  ruoyi-wimo/                映物业务模块
    service/WimoCapabilityService.java   ★能力层 + 计费规则树组装
    service/WimoPriceNodeService.java    ★档位节点 CRUD / 启停
    service/WimoWorkflowEngine.java     统一工作流引擎（6 阶段）
    service/WimoBillingEngine.java      ★沿树逐层求值 + breakdown 明细
    service/WimoDiamondService.java     两阶段结算
    service/WimoModelService.java       模型池
    service/WimoWorkflowService.java    13 智能体
    controller/WimoCapabilityAdminController.java  ★计费树管理接口（含试算）
    controller/WimoMarketController.java          ★capability/tree 用户端接口
    adapter/QiniuStorage.java           七牛 Kodo
    adapter/R2Storage.java              Cloudflare R2
    resources/wimo/{schemas,models,workflows,capabilities,price-nodes}.json  启动初始化数据
  sql/wimo.sql               全新库完整建表（v3：能力层/价格节点/菜单/字典）
  upgrade/wimo_upgrade.sql      v1→v2 增量升级（幂等，不清数据）
  upgrade/wimo_upgrade_v3.sql   ★v2→v3 增量升级（能力树，幂等，不清数据）
admin/
  src/views/wimo/priceTree/index.vue   ★计费规则树（四级可展开、逐节点调价、即时试算）
  src/views/wimo/{model,workflow,diamond,dashboard,task,schema,mail}/index.vue
  src/api/wimo/capability.js           ★能力树/档位节点 API
  src/api/wimo/{model,workflow,diamond,task,dashboard,schema,mail}.js
docs/API.md                 全量接口文档
docs/LOCAL_RUN.md           本文
docker-compose.yml          编排（含 --profile frontend）
scripts/start-local.sh      本地一键启动
```
