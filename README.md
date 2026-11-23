# WIMO · 星创

AI 电商视觉生成平台。三端 monorepo：

```
wimogen-platform/
├── web/      # 用户端 · Nuxt 3（SSR + 多语言，SEO 友好）——当前阶段
├── server/   # 后端 · 若依 RuoYi-Vue（Spring Boot + MyBatis + Redis + MySQL）——下一阶段
├── admin/    # 管理端 · 若依自带 Vue 后台（任务审核 / 模板配置 / 用户额度）——下一阶段
└── docker-compose.yml   # 一键起 MySQL + Redis（后端阶段补充）
```

## 用户端（web/）

基于 Nuxt 3.21 + @nuxtjs/i18n 9，高保真还原 WIMO 电商表单页。

### 已实现

- **页面还原**：顶栏（单任务/批量任务切换、语言、外观、登录）、左侧 176px 分组导航、
  表单列（上传商品图≤6、卖点 & 要求 + AI 帮写、生成设置、结果配置、立即生成）、
  右侧预览区（示例网格 / 生成骨架屏 / 结果）、生成记录抽屉
- **多语言**：中文 `/zh/...`、英文 `/en/...`，prefix 路由策略；新增语种只需
  在 `nuxt.config.ts` 的 `locales` 加一项并在 `i18n/locales/` 加同名 json
- **SEO**：SSR 渲染、`useSeoMeta`、`hreflang`（zh-CN / en-US / x-default）、canonical、
  语义化 HTML、懒加载图片
- **交互**：本地图片上传预览、AI 帮写模拟填充、生成进度骨架动画、记录抽屉、深浅色主题
- **外观模式**：light / dark，localStorage 持久化

### 启动

```bash
cd web
pnpm install
pnpm dev        # http://localhost:3000（根路径按浏览器语言重定向）
```

### 与若依后端联调（规划）

`.env` 中配置：

```
NUXT_PUBLIC_API_BASE=http://localhost:8080
```

页面中已预留接入点（`pages/ecommerce-form.vue` 的 `onSubmit` TODO）：
`POST /prod-api/api/v1/product-set/task` 创建任务 → 轮询任务状态 → 渲染结果。

## 路线图

- [x] 阶段 1：Nuxt 用户端高保真还原 + 中英双语 + SEO
- [ ] 阶段 2：若依后端（套图任务表、模板字典、生成记录、用户额度）
- [ ] 阶段 3：若依管理端页面（任务管理、模板配置、数据看板）
- [ ] 阶段 4：接入真实生图模型 / docker-compose 部署脚本

> 说明：`web/public/images/preview/` 下为程序生成的**占位示例图**，仅用于演示布局，
> 无版权风险；接入真实生成后替换。
