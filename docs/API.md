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
| `wimo.storage.provider` | `local` | `local` 落地 `RuoYiConfig.getProfile()`；`remote` 走对象存储网关 |
| `wimo.auth.code-ttl` | `300` | 验证码有效期（秒） |
| `wimo.auth.code-limit-seconds` | `60` | 发送频控（秒） |
| `wimo.auth.token-days` | `7` | 会员令牌有效期（天） |
| `wimo.auth.demo-code` | 空 | 固定演示验证码，便于自动化测试 |

> 生产环境请把 `wimo.auth.demo-code` 留空、`wimo.mail.provider` 切真实通道，
> 否则 `/api/wimo/auth/code` 会把验证码回显给调用方。

---

## 5. 数据库

`server/sql/wimo.sql` 建 5 张业务表、9 个菜单、8 条字典：

| 表 | 用途 |
| --- | --- |
| `wimo_user` | 会员（邮箱唯一、BCrypt 密码） |
| `wimo_mail_log` | 验证码发送日志 |
| `wimo_schema` | 13 个功能模板配置 |
| `wimo_task` | 生成任务（状态机、进度、payload、结果、耗时） |
| `wimo_task_result` | 结果文件明细 |

菜单 `2000–2008` 对应：映物业务目录、任务管理、查询/删除/重试按钮、数据看板、模板配置、模板编辑按钮、邮件日志。
字典：`wimo_task_status`、`wimo_task_group`。
