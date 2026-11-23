<template>
  <AppSidebar :active="active" @select="onSelect" />

  <div v-if="active === 'productSet'" class="main">
    <TaskForm :generating="generating" @submit="onSubmit" />
    <PreviewPanel
      :items="displayItems"
      :generating="generating"
      :progress="progress"
      :has-result="!!result"
      @records="openRecords"
    />
  </div>

  <div v-else class="empty-page">
    <AppIcon name="grid" :size="26" />
    <p>{{ t('common.comingSoon') }}</p>
  </div>

  <RecordsDrawer :open="recordsOpen" :records="records" @close="recordsOpen = false" />
</template>

<script setup lang="ts">
const { t, locale } = useI18n()

const active = ref('productSet')
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
const SAMPLE = [
  '/images/preview/hero.jpg',
  '/images/preview/shoe1.jpg',
  '/images/preview/shoe2.jpg',
  '/images/preview/shoe3.jpg',
  '/images/preview/shoe4.jpg',
  '/images/preview/shoe5.jpg',
  '/images/preview/shoe6.jpg'
]

const generating = ref(false)
const progress = ref(0)
const result = ref<string[] | null>(null)
const records = ref<any[]>([])
const api = useWimoApi()

const displayItems = computed(() => result.value ?? SAMPLE)

const previewUrl = (key: string) => `/images/preview/${key}.jpg`

function onSelect(key: string) {
  active.value = key
}

async function openRecords() {
  recordsOpen.value = true
  try {
    const list = await api.listTasks(10)
    records.value = list.map(r => ({
      id: r.taskId,
      time: r.createTime,
      summary: (r.sellingPoints || '').slice(0, 44),
      images: (r.results || '').split(',').filter(Boolean).slice(0, 4).map(previewUrl)
    }))
  } catch {
    // 后端不可用时保留内存记录
  }
}

function localMock(payload: any) {
  records.value.unshift({
    id: Date.now(),
    time: new Date().toLocaleString(locale.value === 'zh' ? 'zh-CN' : 'en-US'),
    summary: payload.text.slice(0, 44),
    images: SAMPLE.slice(0, 4)
  })
}

async function onSubmit(payload: any) {
  generating.value = true
  progress.value = 0
  result.value = null
  recordsOpen.value = false

  try {
    const firstLine = String(payload.text || '').split('\n')[0] || ''
    const task = await api.createTask({
      productName: firstLine.replace(/^1[.、\s]*/, '').slice(0, 100),
      sellingPoints: payload.text,
      ratio: payload.ratio,
      platform: payload.platform,
      region: payload.region,
      lang: payload.language,
      resolution: payload.resolution,
      quality: payload.quality,
      model: payload.model,
      resultMode: payload.resultMode,
      images: payload.images
    })

    const startedAt = Date.now()
    while (Date.now() - startedAt < 60000) {
      await new Promise(r => setTimeout(r, 350))
      const rec = await api.getTask(task.taskId)
      progress.value = rec.progress ?? 0
      if (rec.status === '2') {
        const keys = (rec.results || '').split(',').filter(Boolean)
        result.value = keys.length ? keys.map(previewUrl) : [...SAMPLE]
        break
      }
      if (rec.status === '3') throw new Error(rec.errorMsg || 'generation failed')
    }
    if (!result.value) result.value = [...SAMPLE]
  } catch {
    // 后端未启动时降级为本地模拟，保证页面可演示
    for (let i = 1; i <= 8; i++) {
      await new Promise(r => setTimeout(r, 240))
      progress.value = i
    }
    result.value = [...SAMPLE]
    localMock(payload)
  } finally {
    generating.value = false
  }
}
</script>
