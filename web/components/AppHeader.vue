<template>
  <header class="topbar">
    <div class="topbar__left">
      <NuxtLink :to="localePath('/ecommerce-form')" class="logo">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="currentColor">
          <path d="M12 1.6l2.35 6.75 7.05 2.05-7.05 2.05L12 19.2l-2.35-6.75L2.6 10.4l7.05-2.05L12 1.6z" />
          <circle cx="19.4" cy="19.2" r="2.7" />
        </svg>
        <span class="logo__text">{{ t('brand.name') }}</span>
      </NuxtLink>

      <div class="seg" role="tablist">
        <button
          v-for="m in modes" :key="m.value"
          class="seg__btn" :class="{ 'is-active': mode === m.value }"
          role="tab" :aria-selected="mode === m.value"
          @click="mode = m.value"
        >{{ t(m.label) }}</button>
      </div>
    </div>

    <div class="topbar__right">
      <!-- 语言切换 -->
      <div class="lang">
        <button class="icon-btn" :title="t('nav.language')" @click.stop="langOpen = !langOpen">
          <AppIcon name="globe" :size="18" />
        </button>
        <div v-if="langOpen" class="lang__menu" @click.stop>
          <button
            v-for="l in localeList" :key="l.code"
            class="lang__item" :class="{ 'is-active': l.code === locale }"
            @click="chooseLocale(l.code)"
          >
            <span>{{ l.name }}</span>
            <AppIcon v-if="l.code === locale" name="check" :size="13" />
          </button>
        </div>
      </div>

      <button class="icon-btn" :title="t('nav.theme')" @click="toggleTheme">
        <AppIcon name="monitor" :size="18" />
      </button>

      <button class="login-btn">{{ t('nav.login') }}</button>
    </div>
  </header>
</template>

<script setup lang="ts">
const { t, locale, locales, setLocale } = useI18n()
const localePath = useLocalePath()

const modes = [
  { value: 'single', label: 'nav.singleTask' },
  { value: 'batch', label: 'nav.batchTask' }
]
const mode = useState<'single' | 'batch'>('task-mode', () => 'single')

const langOpen = ref(false)
const localeList = computed(() =>
  (locales.value as any[]).map(l => ({ code: l.code, name: l.name }))
)

async function chooseLocale(code: string) {
  langOpen.value = false
  await setLocale(code as 'zh' | 'en')
}

/* 外观模式：light / dark，存 localStorage */
const theme = useState<'light' | 'dark'>('theme', () => 'light')
useHead({ htmlAttrs: { 'data-theme': () => theme.value } })

function toggleTheme() {
  theme.value = theme.value === 'light' ? 'dark' : 'light'
  if (import.meta.client) localStorage.setItem('wimo_theme', theme.value)
}

onMounted(() => {
  const saved = localStorage.getItem('wimo_theme')
  if (saved === 'dark') theme.value = 'dark'
  document.addEventListener('click', () => (langOpen.value = false))
})
onBeforeUnmount(() => {
  document.removeEventListener('click', () => (langOpen.value = false))
})
</script>
