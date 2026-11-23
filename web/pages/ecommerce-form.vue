<template>
  <AppSidebar :active="active" />

  <div class="main">
    <TaskForm
      :key="active"
      :generating="generating"
      :schema-key="active"
      @submit="onSubmit"
    />
    <PreviewPanel
      :key="`preview-${active}`"
      :preview="preview"
      :items="displayItems"
      :generating="generating"
      :progress="progress"
      :has-result="!!result"
      @records="openRecords"
    />
  </div>

  <RecordsDrawer :open="recordsOpen" :records="records" @close="recordsOpen = false" />
</template>

<script setup lang="ts">
import { getSchema } from '~/config/tasks'

const { t, locale } = useI18n()

/* 功能选择由 URL query 驱动（?task=xxx），侧栏是真实路由链接 */
const route = useRoute()
const active = computed(() => (route.query.task as string) || 'productSet')

const recordsOpen = ref(false)

/* ---------------- SEO ---------------- */
const requestURL = useRequestURL()
const localePath = useLocalePath()

useSeoMeta({
  title: () => t('seo.title'),
  description: () => t('seo.description'),
  keywords: () => t('seo.keywords'),
  ogTitle: () => t('seo.title'),
  ogDescription: () => t('seo.description'),
  ogType: 'website',
  ogLocale: () => (locale.value === 'zh' ? 'zh_CN' : 'en_US'),
  twitterCard: 'summary_large_image'
})

useHead(() => ({
  htmlAttrs: { lang: locale.value === 'zh' ? 'zh-CN' : 'en-US' },
  link: [
    { rel: 'alternate', hreflang: 'zh-CN', href: `${requestURL.origin}/zh/ecommerce-form` },
    { rel: 'alternate', hreflang: 'en-US', href: `${requestURL.origin}/en/ecommerce-form` },
    { rel: 'alternate', hreflang: 'x-default', href: `${requestURL.origin}/zh/ecommerce-form` },
    { rel: 'canonical', href: `${requestURL.origin}${localePath('/ecommerce-form')}` }
  ]
}))

/* ---------------- 预览数据 ---------------- */
/* 占位示例图：拼接 baseURL，兼容 GitHub Pages 子路径部署 */
const base = useRuntimeConfig().app.baseURL || '/'
const apiBase = useRuntimeConfig().public.apiBase
/** 示例图 key 走站点静态资源；后端结果 URL 走 apiBase（真实部署可指向后端域名） */
const previewUrl = (key: string) => {
  if (!key) return ''
  if (/^https?:\/\//.test(key)) return key
  if (key.startsWith('/profile/') || key.startsWith('/images/')) {
    return apiBase.startsWith('http') ? apiBase.replace(/\/$/, '') + key : key
  }
  return `${base}images/preview/${key}.jpg`
}

/* 右侧示意图随左侧功能同步切换：9 张静态封面 + 3 段示例视频 */
const preview = computed(() => {
  const s = getSchema(active.value)
  const p = s.preview
  return {
    kind: p.kind as 'image' | 'video',
    src: `${base}images/preview/${p.file}`,
    poster: p.poster ? `${base}images/preview/${p.poster}` : '',
    alt: t(`nav.items.${s.key}`)
  }
})

const sample = () => [
  'hero', 'shoe1', 'shoe2', 'pair1', 'pair2'
].map(previewUrl)

const generating = ref(false)
const progress = ref(0)
const result = ref<string[] | null>(null)
const records = ref<any[]>([])
const api = useWimoApi()
const { refreshBalance, showToast } = useAuth()

const displayItems = computed(() => result.value ?? sample())

async function openRecords() {
  recordsOpen.value = true
  try {
    const list = await api.listTasks(10)
    records.value = list.map(r => ({
      id: r.taskId,
      time: r.createTime,
      summary: (r.sellingPoints || r.productName || '').slice(0, 44),
      images: (r.results || '').split(',').filter(Boolean).slice(0, 4).map(previewUrl)
    }))
  } catch {
    // 后端不可用时保留内存记录
  }
}

/* 从 payload 提取一句可读摘要 */
function summarize(payload: any): string {
  const v = payload.values || {}
  const candidates = [v.sellingPoints, v.retouchNote, v.posterTheme, v.promo, v.storeName]
  const text = candidates.find((s: any) => s && String(s).trim()) || ''
  const firstLine = String(text).split('\n')[0] || ''
  return firstLine.slice(0, 100)
}

function localMock(payload: any) {
  records.value.unshift({
    id: Date.now(),
    time: new Date().toLocaleString(locale.value === 'zh' ? 'zh-CN' : 'en-US'),
    summary: summarize(payload),
    images: sample().slice(0, 4)
  })
}

/* 生成设置字段：从表单 payload 顶层汇总为后端 schema 所需的 common 对象 */
const COMMON_KEYS = [
  'ratio', 'platform', 'region', 'language', 'resolution', 'quality', 'model',
  'videoResolution', 'videoScene', 'cameraStyle', 'duration', 'videoModel', 'resultCount'
]

/** 素材是否来自后端（/profile 或完整 URL）；含 dataURL 说明上传未连上后端 */
function isBackendAsset(url: string): boolean {
  return /^https?:\/\//.test(url) || url.startsWith('/profile/') || url.startsWith('/images/')
}

async function onSubmit(payload: any) {
  generating.value = true
  progress.value = 0
  result.value = null
  recordsOpen.value = false

  try {
    const uploads: Record<string, string[]> = payload.uploads || {}
    const localOnly = Object.values(uploads)
      .some(list => (list || []).some(u => !isBackendAsset(String(u))))
    if (localOnly) throw new Error('素材未上传至后端，走本地演示')

    const common: Record<string, any> = {}
    for (const k of COMMON_KEYS) {
      if (payload[k] !== undefined && payload[k] !== '') common[k] = payload[k]
    }

    const task = await api.createTask({
      taskType: payload.taskType,
      title: summarize(payload),
      uploads,
      values: payload.values || {},
      common,
      resultMode: payload.resultMode || 'default'
    })
    // 创建成功即预扣钻石：立刻刷新顶栏余额
    await refreshBalance()
    if ((task as any).costDiamonds) {
      showToast(t('form.costCharged', { n: (task as any).costDiamonds }))
    }

    const startedAt = Date.now()
    while (Date.now() - startedAt < 90000) {
      await new Promise(r => setTimeout(r, 400))
      const rec = await api.getTask(task.taskId)
      // 后端进度 0-100 → 预览骨架 0-8 格
      progress.value = Math.min(8, Math.round((rec.progress ?? 0) / 100 * 8))
      if (rec.status === '2') {
        const keys = (rec.results || '').split(',').filter(Boolean)
        result.value = keys.length ? keys.map(previewUrl) : sample()
        break
      }
      if (rec.status === '3' || rec.status === '4') {
        throw new Error(rec.errorMsg || 'generation failed')
      }
    }
    if (!result.value) result.value = sample()
    // 生成结束（成功扣减 / 失败退还）后再刷新一次余额
    await refreshBalance()
    await openRecords()
  } catch (e: any) {
    const msg = e?.message || ''
    // 余额不足 / 校验失败等业务错误：不再降级绕过计费，直接提示用户
    if (msg.includes('钻石') || msg.includes('余额') || msg.includes('智能体') || msg.includes('工作流')) {
      showToast(msg)
      await refreshBalance()
      generating.value = false
      return
    }
    // 后端未启动 / 生成失败时降级为本地模拟，保证页面可演示
    for (let i = 1; i <= 8; i++) {
      await new Promise(r => setTimeout(r, 240))
      progress.value = i
    }
    result.value = sample()
    localMock(payload)
  } finally {
    generating.value = false
  }
}
</script>
