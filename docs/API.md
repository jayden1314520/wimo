# 映物 VIVID · 接口文档

所有接口由 `server/ruoyi-wimo` 模块提供，基于若依 RuoYi-Vue（Spring Boot）实现。
本地默认地址 `http://localhost:8080`；前端通过 `NUXT_PUBLIC_API_BASE` 指向该地址。

## 1. 约定

### 统一返回体

```json
{ "code": 200, "msg": "操作成功", "data": { } }
```

| code | 含义 |
| --- | --- |
| 200 | 成功 |
| 500 | 业务失败（`msg` 为可直接展示给用户的原因，如「验证码已过期，请重新获取」） |
| 401 | 未登录 / 令牌失效（管理端由若依安全框架返回） |

分页类接口（管理端 `TableDataInfo`）返回 `{ "code": 200, "rows": [], "total": 8, "msg": "操作成功" }`。

### 认证

| 端 | 方式 |
| --- | --- |
| 用户端 `/api/wimo/**` | 会员令牌放在 **`X-Wimo-Token`** 请求头。**不要**放 `Authorization`，否则会被若依后台 JWT 过滤器误解析成 JWT 而报错。未带令牌时按匿名（`userId=0`）处理。 |
| 管理端 `/wimo/**` | 若依后台令牌 `Authorization: Bearer <token>`，由 `/login` 获取，受 `@PreAuthorize` 权限控制。 |

### CORS

`/api/wimo/**` 已在 `WimoWebMvcConfig` 中放开跨域（`allowedOriginPatterns("*")`，允许 GET/POST/PUT/DELETE/OPTIONS）。

### 任务状态机

| status | 含义 |
| --- | --- |
| `0` | 待生成 |
| `1` | 生成中（`progress` 0–100） |
| `2` | 已完成 |
| `3` | 失败（`errorMsg`） |
| `4` | 已取消 |

---

## 2. 用户端接口

### 2.1 健康检查

`GET /api/wimo/task/health`

```json
{ "code": 200, "data": { "ok": true, "generator": "mock", "mail": "mock", "storage": "local" } }
```

用于前端判断后端可达性与当前适配器状态；不可达时用户端自动降级为本地演示。

### 2.2 功能模板

`GET /api/wimo/schema/list` → 返回启用的 13 个模板，前端据此渲染表单：

```json
{
  "code": 200,
  "data": [{
    "key": "storeVisit", "nameZh": "探店视频", "nameEn": "Store Visit",
    "group": "video", "icon": "store", "sortNo": 13,
    "schema": {
      "uploads": [
        { "key": "storefront", "labelKey": "storefront", "max": 1, "min": 1 },
        { "key": "storeEnv", "labelKey": "storeEnv", "max": 6, "min": 1 },
        { "key": "dishes", "labelKey": "dishes", "max": 8, "min": 4, "hintKey": "dishes" },
        { "key": "blogger", "labelKey": "blogger", "max": 1, "min": 1 }
      ],
      "fields": [
        { "key": "storeName", "type": "text", "optional": true },
        { "key": "location", "type": "text", "optional": true }
      ],
      "common": ["ratio", "language", "videoResolution", "duration", "videoModel"],
      "preview": { "kind": "image", "file": "cover-storeVisit.jpg" }
    }
  }]
}
```

> 模板存于 `wimo_schema` 表，由 `wimo/schemas.json` 启动时初始化，管理端可在线编辑；
> **提交任务时后端会按模板重新校验，不信任前端的必填判断**。

### 2.3 账号

| 接口 | 方法 | 请求体 | 返回 `data` |
| --- | --- | --- | --- |
| `/api/wimo/auth/code` | POST | `{ "email": "a@b.com", "scene": "register" }` | `{ "sent": true, "provider": "mock", "expiresIn": 300, "demoCode": "574111" }` |
| `/api/wimo/auth/register` | POST | `{ "email", "code", "password", "confirmPassword", "agreed": true }` | `{ "userId": 5, "email", "token" }` |
| `/api/wimo/auth/login/password` | POST | `{ "email", "password" }` | `{ "userId", "email", "token" }` |
| `/api/wimo/auth/login/code` | POST | `{ "email", "code" }` | `{ "userId", "email", "token" }` |
| `/api/wimo/auth/password/reset` | POST | `{ "email", "code", "password" }` | `null` |
| `/api/wimo/auth/profile` | GET | 需 `X-Wimo-Token` | `{ "userId", "email", "nickname", "createTime" }` |
| `/api/wimo/auth/logout` | POST | 需 `X-Wimo-Token` | `null` |

- `scene` 取 `register` / `login` / `reset`，三者验证码互相隔离；
- 同一邮箱同场景 **60 秒频控**（`wimo.auth.code-limit-seconds`）；
- 验证码存 Redis，有效期 300 秒；注册成功后立即删除，不可重复使用；
- 密码用 BCrypt 加密，令牌为 Redis 中的 UUID，有效期 7 天；
- `mock` 邮件通道不真发信，验证码通过 `demoCode` 回显，便于无邮箱联调。

### 2.4 AI 帮写

`POST /api/wimo/ai/write`

```json
// 请求
{ "taskType": "productSet", "field": "sellingPoints", "context": "运动鞋", "lang": "zh" }
// 响应
{ "code": 200, "data": { "text": "1. 商品/主体名称：运动鞋\n2. 核心卖点：..." } }
```

规则模板适配器，中英文各一套结构化文案。可替换为真实 LLM（`WimoAiWriterService`）。

### 2.5 素材上传

| 接口 | 方法 | 参数 | 返回 `data` |
| --- | --- | --- | --- |
| `/api/wimo/task/upload` | POST | `multipart/form-data`，字段名 `file` | `{ "url": "/profile/upload/2026/11/24/u5/xxx.jpg" }` |
| `/api/wimo/task/uploads` | POST | `multipart/form-data`，字段名 `files`（多个） | `[{ "url": "..." }, { "url": "..." }]` |

- 允许扩展名：`.jpg .jpeg .png .webp .gif .bmp .mp4 .mov .webm`；
- 本地存储按用户隔离：`/profile/upload/yyyy/MM/dd/u<userId>/<uuid><ext>`；
- 单文件上限 10MB、单次请求 20MB（若依 `spring.servlet.multipart`）；
- 返回 URL 可通过 `GET /profile/...` 直接访问。

### 2.6 任务

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/wimo/task` | POST | 创建任务 |
| `/api/wimo/task/{taskId}` | GET | 查询任务（含 `resultFiles` 明细） |
| `/api/wimo/task/list?status=&page=1&size=10` | GET | 我的生成记录（分页） |
| `/api/wimo/task/{taskId}/retry` | POST | 重试 |
| `/api/wimo/task/{taskId}/cancel` | POST | 取消 |
| `/api/wimo/task/{taskId}` | DELETE | 删除自己的任务 |

创建任务请求体：

```json
{
  "taskType": "storeVisit",
  "title": "探店 · 城西小馆",
  "uploads": {
    "storefront": ["/profile/upload/.../a.jpg"],
    "storeEnv": ["/profile/upload/.../b.jpg"],
    "dishes": ["/profile/upload/.../c1.jpg", "...c2.jpg", "...c3.jpg", "...c4.jpg"],
    "blogger": ["/profile/upload/.../d.jpg"]
  },
  "values": { "storeName": "", "location": "" },
  "common": {
    "ratio": "3:4", "language": "zh", "videoResolution": "720p",
    "duration": 5, "videoModel": "seedance2fast"
  },
  "resultMode": "default"
}
```

响应：

```json
{ "code": 200, "data": { "taskId": 7, "status": "0", "taskType": "storeVisit" } }
```

随后前端轮询 `GET /api/wimo/task/{taskId}`，`progress` 0→100、`status` 变为 `2` 后读取 `results`：

```json
{
  "code": 200,
  "data": {
    "taskId": 7, "userId": 5, "taskType": "storeVisit", "title": "探店 · 城西小馆",
    "status": "2", "progress": 100, "resultCount": 4, "retryCount": 0, "costMs": 3000,
    "results": "/profile/result/2026/11/24/a.jpg,/profile/result/2026/11/24/b.jpg,...",
    "resultFiles": [
      { "resultId": 21, "fileUrl": "/profile/result/2026/11/24/a.jpg", "fileType": "image", "sortNo": 0 }
    ],
    "createTime": "2026-11-24 00:02:25", "finishTime": "2026-11-24 00:02:28"
  }
}
```

**归属规则**：登录用户只能查看/重试/取消/删除自己的任务；未登录只能查看匿名（`userId=0`）任务。

**后端 schema 校验示例**（不依赖前端）：

| 场景 | 返回 |
| --- | --- |
| 探店菜品只传 3 张 | `500 「dishes」需上传 4–8 个素材，当前 3 个` |
| 探店缺博主照 | `500 「blogger」需上传 1–1 个素材，当前 0 个` |
| 商品套图缺卖点 | `500 「sellingPoints」为必填项` |

---

## 3. 管理端接口

均需若依后台登录令牌，权限标识见下表。

### 3.1 任务管理 `/wimo/task`

| 接口 | 方法 | 权限 | 说明 |
| --- | --- | --- | --- |
| `/wimo/task/list?pageNum=1&pageSize=10&status=&taskType=&title=` | GET | `wimo:task:list` | 分页列表（PageHelper 真分页） |
| `/wimo/task/{taskId}` | GET | `wimo:task:query` | 详情，附 `resultFiles` |
| `/wimo/task/{taskIds}` | DELETE | `wimo:task:remove` | 删除，支持逗号分隔多 ID |
| `/wimo/task/retry/{taskId}` | POST | `wimo:task:retry` | 重新生成（不受归属限制） |
| `/wimo/task/cancel/{taskId}` | POST | `wimo:task:retry` | 取消 |
| `/wimo/task/status/{taskId}/{status}` | POST | `wimo:task:retry` | 运维直接改状态 |

### 3.2 数据看板 `/wimo/dashboard`

`GET /wimo/dashboard?days=7`（权限 `wimo:dashboard:list`）

```json
{
  "code": 200,
  "data": {
    "overview": {
      "total": 8, "done": 6, "failed": 0, "running": 0, "today": 4, "avgCost": 3200,
      "users": 5, "schemas": 13, "mailLogs": 13,
      "statusMap": { "0": 0, "1": 0, "2": 6, "3": 0, "4": 2 }
    },
    "byType": [{ "task_type": "productSet", "cnt": 4 }],
    "trend": [{ "d": "2026-11-23", "cnt": 4 }, { "d": "2026-11-24", "cnt": 4 }],
    "recent": [ /* 最近 10 条任务 */ ],
    "providers": { "generator": "mock", "mail": "mock", "storage": "local" }
  }
}
```

### 3.3 模板配置 `/wimo/schema`

| 接口 | 方法 | 权限 | 说明 |
| --- | --- | --- | --- |
| `/wimo/schema/list` | GET | `wimo:schema:list` | 13 个模板（含 `schemaJson`） |
| `/wimo/schema/{id}` | GET | `wimo:schema:list` | 详情 |
| `/wimo/schema` | PUT | `wimo:schema:edit` | 更新（**全字段**，需带 `id/taskKey/nameZh/nameEn/groupKey/icon/sortNo/enabled/schemaJson/remark`） |
| `/wimo/schema/providers` | GET | — | 适配器切换提示 |

> `enabled = 0` 时用户端 `/api/wimo/schema/list` 不再返回该模板。

### 3.4 邮件日志 `/wimo/mail`

`GET /wimo/mail/list?email=&scene=&pageNum=1&pageSize=10`（权限 `wimo:mail:list`）

```json
{
  "code": 200,
  "data": {
    "total": 7, "pageNum": 1, "pageSize": 10,
    "rows": [{ "id": 1, "email": "a@b.com", "scene": "register", "code": "574111",
               "provider": "mock", "success": "1", "remark": "", "createTime": "..." }]
  }
}
```

`total` 与查询条件保持一致（筛选后收紧），可正确翻页。

### 3.5 模型管理 `/wimo/model`

| 接口 | 方法 | 权限 | 说明 |
| --- | --- | --- | --- |
| `/wimo/model/list` | GET | `wimo:model:list` | 全部模型（含 endpoint/密钥引用/倍率） |
| `/wimo/model/{id}` | GET | `wimo:model:list` | 详情 |
| `/wimo/model` | POST | `wimo:model:edit` | 新增（modelKey 唯一） |
| `/wimo/model` | PUT | `wimo:model:edit` | 更新 |
| `/wimo/model/{id}` | DELETE | `wimo:model:remove` | 删除 |

关键字段：`capability`（`text2img`/`img2img`/`img2video`/`text2video`）、`costMultiplier`（钻石倍率）、`enabled`、`configJson`。

### 3.6 智能体工作流 `/wimo/workflow`

| 接口 | 方法 | 权限 | 说明 |
| --- | --- | --- | --- |
| `/wimo/workflow/list` | GET | `wimo:workflow:list` | 返回 `{rows: 13 个工作流, total, models: 全部模型}` |
| `/wimo/workflow/{id}` | GET | `wimo:workflow:list` | 详情 |
| `/wimo/workflow` | PUT | `wimo:workflow:edit` | 更新计费规则（`workflowKey`/`createTime` 不可改，JSON 字段会校验合法性） |

### 3.7 钻石管理 `/wimo/diamond`

| 接口 | 方法 | 权限 | 说明 |
| --- | --- | --- | --- |
| `/wimo/diamond/overview` | GET | `wimo:diamond:list` | 余额/冻结/累计消耗/累计发放 + 流水数 + 按类型汇总 + 7 天消耗趋势 |
| `/wimo/diamond/users` | GET | `wimo:diamond:list` | 全部会员钱包 |
| `/wimo/diamond/logs?userId=&changeType=&pageNum=&pageSize=` | GET | `wimo:diamond:list` | 流水分页 |
| `/wimo/diamond/adjust` | POST | `wimo:diamond:edit` | 人工调整，body `{userId, delta, remark}`（delta 可负，不允许扣成负） |

流水类型：`gift` 注册赠送 / `recharge` 充值 / `freeze` 生成预扣 / `consume` 生成扣减 / `refund` 失败退还 / `adjust` 人工调整。

---

## 3.8 用户端 · 模型 / 智能体 / 计费 / 钻石（新增）

均为 `@Anonymous`，会员令牌可选（走 `X-Wimo-Token`）。

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/wimo/model/list?workflow=&capability=` | GET | 可选模型；传 `workflow` 自动套用该智能体能力 + 白名单 |
| `/api/wimo/workflow/list` | GET | 13 个智能体摘要（起步价/计费单位/上下限/默认模型） |
| `/api/wimo/market/config` | GET | 一次性返回工作流 + 模型 + 余额，减少首屏请求 |
| `/api/wimo/billing/quote` | POST | **提交前预估价**，body 同 `WimoTaskSubmit` |
| `/api/wimo/diamond/balance` | GET | 当前会员钱包 `{balance, frozen, total, used, anonymous}` |
| `/api/wimo/diamond/logs?limit=20` | GET | 当前会员流水 |

**`POST /api/wimo/billing/quote` 示例：**

```bash
curl -X POST http://localhost:8080/api/wimo/billing/quote \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"storeVisit","common":{"videoResolution":"1080p","duration":30,"videoModel":"seedance25","resultCount":4}}'
```

```json
{
  "code": 200,
  "data": {
    "cost": 252, "modelKey": "seedance25", "balance": 100, "enough": false,
    "anonymous": false, "workflowKey": "storeVisit",
    "snapshot": {
      "workflowName": "探店视频", "capability": "img2video",
      "baseCost": 22, "unit": "second", "units": 30, "unitCost": 1.6,
      "subtotal": 70, "modelFactor": 2.0,
      "factors": [{ "field": "videoResolution", "value": "1080p", "factor": 1.8 }],
      "beforeCeil": 252, "minCost": 10, "maxCost": 2000, "total": 252,
      "formula": "clamp(ceil((base + unitCost*units) * Πfactors * modelFactor), min, max)"
    }
  }
}
```

**计费公式**：`clamp(ceil((baseCost + unitCost × units) × Π维度倍率 × 模型倍率), minCost, maxCost)`
图片按结果张数 + `resolution`/`quality` 倍率，视频按秒 + `videoResolution` 倍率，再乘模型 `costMultiplier`。

**创建任务 `POST /api/wimo/task` 返回体新增字段**：`costDiamonds`（本单消耗）、`balance`（预扣后余额）、`modelKey`。任务详情新增 `costDiamonds / diamondState / modelKey / billingSnapshot`。`diamondState`：`0` 冻结待结算、`1` 已扣减、`2` 已退还、`9` 不计费（匿名演示）。余额不足时返回 `R.fail("钻石余额不足…")` 且不落库任务。

---

## 4. 可插拔适配器

`application.yml` 的 `wimo.*`（或用环境变量覆盖），改完重启后端即可切换，无需改代码。

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `wimo.mail.provider` | `mock` | `mock` 写日志不真发信；`http` POST 到 `wimo.mail.http-endpoint` |
| `wimo.mail.http-endpoint` / `http-token` | 空 | HTTP 邮件服务地址与令牌 |
| `wimo.generator.provider` | `mock` | `mock` 复制示例素材为结果；`remote` POST 到模型网关 |
| `wimo.generator.remote-url` / `remote-token` | 空 | 模型网关地址与令牌，响应 `{"results":["url",...]}` |
| `wimo.generator.step-ms` | `350` | 模拟生成每步耗时（8 步） |
| `wimo.generator.result-count` | `4` | 模拟结果文件数 |
| `wimo.mock.sample-dir` | `web/public/images/preview` | 模拟结果素材目录 |
| `wimo.storage.provider` | `local` | `local` 本地磁盘 / `qiniu` 七牛 Kodo / `r2` Cloudflare R2 / `remote` 通用对象存储网关 |
| `wimo.storage.qiniu.access-key/secret-key/bucket/domain/region` | 空 | 七牛凭证（AK/SK/空间/CDN 域名/区域 z0~as0） |
| `wimo.storage.r2.account-id/access-key/secret-key/bucket/public-domain/endpoint` | 空 | Cloudflare R2（S3 SigV4，region 固定 `auto`） |
| `wimo.diamond.register-gift` | `100` | 注册赠送钻石 |
| `wimo.diamond.charge-anonymous` | `false` | 匿名演示用户是否扣钻石（false 保证 Pages 静态站可用） |
| `wimo.auth.code-ttl` | `300` | 验证码有效期（秒） |
| `wimo.auth.code-limit-seconds` | `60` | 发送频控（秒） |
| `wimo.auth.token-days` | `7` | 会员令牌有效期（天） |
| `wimo.auth.demo-code` | 空 | 固定演示验证码，便于自动化测试 |

> 生产环境请把 `wimo.auth.demo-code` 留空、`wimo.mail.provider` 切真实通道，
> 否则 `/api/wimo/auth/code` 会把验证码回显给调用方。

---

## 5. 数据库

`server/sql/wimo.sql`（全新库）建 8 张业务表、16 个菜单、5 类字典；已有库用幂等增量脚本 `server/upgrade/wimo_upgrade.sql`。

| 表 | 用途 |
| --- | --- |
| `wimo_user` | 会员（邮箱唯一、BCrypt 密码、**钻石余额/冻结/累计**） |
| `wimo_mail_log` | 验证码发送日志 |
| `wimo_schema` | 13 个功能模板配置 |
| `wimo_model` | **AI 模型注册表**（四类能力、倍率、endpoint） |
| `wimo_workflow` | **13 个智能体工作流**（能力/模型池/计费规则/执行步骤） |
| `wimo_task` | 生成任务（状态机、进度、payload、结果、耗时、**计费快照**） |
| `wimo_task_result` | 结果文件明细 |
| `wimo_diamond_log` | **钻石流水**（每次余额/冻结变动留痕） |

菜单 `2000–2015`：映物业务目录、任务管理、数据看板、模板配置、邮件日志、**模型管理、智能体工作流、钻石管理**及各自按钮权限。
字典：`wimo_task_status`、`wimo_task_group`、`wimo_model_capability`、`wimo_diamond_change`、`wimo_billing_unit`。

> 本地运行与配置详情见 [`docs/LOCAL_RUN.md`](./LOCAL_RUN.md)。
