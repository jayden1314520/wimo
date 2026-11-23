# 映物 VIVID

AI 电商视觉生成平台。三端 monorepo：

```
wimogen-platform/
├── web/      # 用户端 · Nuxt 3（SSR + 多语言，SEO 友好）
├── server/   # 后端 · 若依 RuoYi-Vue（Spring Boot + MyBatis + Redis + MySQL）
├── admin/    # 管理端 · 若依 Vue3 后台（任务管理 / 模板配置 / 数据看板）
└── docker-compose.yml   # 一键起 MySQL + Redis
```

## 用户端（web/）

基于 Nuxt 3.21 + @nuxtjs/i18n 9 的电商 AI 表单页，中英双语，支持 GitHub Pages 静态部署。

### 已实现

- **页面**：顶栏（品牌、语言、外观、登录）、左侧 176px 分组导航（12 个功能）、
  表单列（上传素材、卖点 & 要求 + AI 帮写、生成设置、结果配置、立即生成）、
  右侧预览区（功能示意图 / 示例视频、生成骨架屏）、生成记录抽屉
- **视频模块**：商品展示短片、带货视频（视频背景场景、镜头风格、清晰度、时长、Seedance 模型）
- **示意图联动**：左侧切换功能时，右侧封面或示例视频同步切换（9 张封面 + 3 段示例视频）
- **多语言**：中文 `/zh/...`、英文 `/en/...`，prefix 路由策略；新增语种只需
  在 `nuxt.config.ts` 的 `locales` 加一项并在 `i18n/locales/` 加同名 json
- **SEO**：SSR 渲染、`useSeoMeta`、`hreflang`（zh-CN / en-US / x-default）、canonical、
  语义化 HTML
- **交互**：本地图片上传预览、AI 帮写模拟填充、生成进度骨架动画、记录抽屉、深浅色主题
- **外观模式**：light / dark，localStorage 持久化

### 启动

```bash
cd web
pnpm install
pnpm dev        # http://localhost:3000（根路径按浏览器语言重定向）
```

### 部署（GitHub Pages）

从 `gh-pages` 分支发布，静态产物由 `NUXT_APP_BASE_URL=/wimo/ npx nuxt generate` 生成；
产物根目录需带 `.nojekyll`（否则 `_nuxt` 被 Jekyll 忽略）与跳转用 `index.html`。

线上地址：https://jayden1314520.github.io/wimo/

### 与若依后端联调

`.env` 中配置：

```
NUXT_PUBLIC_API_BASE=http://localhost:8080
```

页面接入点位于 `pages/ecommerce-form.vue` 的 `onSubmit`：
`POST /prod-api/api/wimo/task` 创建任务 → 轮询任务状态 → 渲染结果；
后端未启动时自动降级为本地模拟，保证页面可演示。

## 路线图

- [x] 阶段 1：Nuxt 用户端 + 中英双语 + SEO
- [x] 阶段 2：若依后端（任务表、生成记录、任务管理接口）
- [x] 阶段 3：若依管理端页面（任务查询 / 删除）
- [ ] 阶段 4：接入真实生图 / 生视频模型、docker-compose 部署脚本

> 素材说明：`web/public/images/preview/` 下的封面图与示例视频取自参考站，仅用于演示还原，
> 正式商用请替换为自有版权素材或真实 AI 生成结果。
