<template>
  <AppSidebar :active="active" @select="onSelect" />

  <div v-if="active === 'productSet'" class="main">
    <TaskForm :generating="generating" @submit="onSubmit" />
    <PreviewPanel
      :items="displayItems"
      :generating="generating"
      :progress="progress"
      :has-result="!!result"
      @records="recordsOpen = true"
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

const displayItems = computed(() => result.value ?? SAMPLE)

function onSelect(key: string) {
  active.value = key
}

async function onSubmit(payload: any) {
  generating.value = true
  progress.value = 0
  result.value = null
  recordsOpen.value = false

  // TODO: 接入若依后端 POST /prod-api/api/v1/product-set/task 后改为轮询任务状态
  for (let i = 1; i <= 8; i++) {
    await new Promise(r => setTimeout(r, 240))
    progress.value = i
  }

  generating.value = false
  result.value = [...SAMPLE]

  records.value.unshift({
    id: Date.now(),
    time: new Date().toLocaleString(locale.value === 'zh' ? 'zh-CN' : 'en-US'),
    summary: payload.text.slice(0, 40),
    images: result.value.slice(0, 4)
  })
}
</script>
