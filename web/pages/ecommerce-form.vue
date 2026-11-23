<template>
  <AppSidebar :active="active" />

  <div class="main">
    <template v-if="isVideo">
      <div class="empty-page">
        <AppIcon :name="active === 'productVideo' ? 'video' : 'cart'" :size="26" />
        <p>{{ t('common.comingSoon') }}</p>
      </div>
    </template>

    <template v-else>
      <TaskForm
        :key="active"
        :generating="generating"
        :schema-key="active"
        @submit="onSubmit"
      />
      <PreviewPanel
        :items="displayItems"
        :generating="generating"
        :progress="progress"
        :has-result="!!result"
        @records="openRecords"
      />
    </template>
  </div>

  <RecordsDrawer :open="recordsOpen" :records="records" @close="recordsOpen = false" />
</template>

<script setup lang="ts">
const { t, locale } = useI18n()

const VIDEO_KEYS = ['productVideo', 'salesVideo']

/* 功能选择由 URL query 驱动（?task=xxx），侧栏是真实路由链接 */
const route = useRoute()
const active = computed(() => (route.query.task as string) || 'productSet')

const isVideo = computed(() => VIDEO_KEYS.includes(active.value))

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
const previewUrl = (key: string) => `${base}images/preview/${key}.jpg`

const sample = () => [
  'hero', 'shoe1', 'shoe2', 'pair1', 'pair2'
].map(previewUrl)

const generating = ref(false)
const progress = ref(0)
const result = ref<string[] | null>(null)
const records = ref<any[]>([])
const api = useWimoApi()

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
  const candidates = [v.sellingPoints, v.retouchNote, v.posterTheme, v.promo]
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

async function onSubmit(payload: any) {
  generating.value = true
  progress.value = 0
  result.value = null
  recordsOpen.value = false

  try {
    const task = await api.createTask({
      taskType: payload.taskType,
      productName: summarize(payload),
      sellingPoints: summarize(payload),
      ratio: payload.ratio,
      platform: payload.platform,
      region: payload.region,
      lang: payload.language,
      resolution: payload.resolution,
      quality: payload.quality,
      model: payload.model,
      resultMode: payload.resultMode,
      images: (payload.uploads.product || payload.uploads.apparel || payload.uploads.model || []).join(','),
      extra: JSON.stringify({ uploads: payload.uploads, values: payload.values })
    })

    const startedAt = Date.now()
    while (Date.now() - startedAt < 60000) {
      await new Promise(r => setTimeout(r, 350))
      const rec = await api.getTask(task.taskId)
      progress.value = rec.progress ?? 0
      if (rec.status === '2') {
        const keys = (rec.results || '').split(',').filter(Boolean)
        result.value = keys.length ? keys.map(previewUrl) : sample()
        break
      }
      if (rec.status === '3') throw new Error(rec.errorMsg || 'generation failed')
    }
    if (!result.value) result.value = sample()
  } catch {
    // 后端未启动时降级为本地模拟，保证页面可演示
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
