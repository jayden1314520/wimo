<template>
  <header class="topbar">
    <div class="topbar__left" @click.capture="onLogoClick">
      <NuxtLink :to="localePath('/ecommerce-form')" class="logo">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="currentColor">
          <path d="M12 1.6l2.35 6.75 7.05 2.05-7.05 2.05L12 19.2l-2.35-6.75L2.6 10.4l7.05-2.05L12 1.6z" />
          <circle cx="19.4" cy="19.2" r="2.7" />
        </svg>
        <span class="logo__text">{{ t('brand.name') }}</span>
        <!-- 移动端提示：点 logo 打开左侧菜单 -->
        <AppIcon name="menu" :size="16" class="logo__menu-hint" />
      </NuxtLink>
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

      <!-- 未登录：登录 / 注册入口 -->
      <button v-if="!user" class="login-btn" @click="openAuth">{{ t('nav.login') }}</button>

      <!-- 已登录：钻石余额 + 账号胶囊 -->
      <template v-else>
        <div class="diamond" :title="t('nav.diamondHint')">
          <svg viewBox="0 0 24 24" width="15" height="15" fill="currentColor" aria-hidden="true">
            <path d="M6 3h12l4 6-10 12L2 9l4-6zm0 2L3.3 9h5.2L9.5 5H6zm3.9 0-1 4h6.2l-1-4H9.9zM15 5l1 4h5.2L18 5h-3zM3.7 11l4.1 5-2.3-5H3.7zm5.7 0 2.6 6.7L14.6 11H9.4zm7.1 0-2.3 5 4.1-5h-1.8z" />
          </svg>
          <span class="diamond__num">{{ diamonds }}</span>
        </div>
        <div class="account">
          <button class="account__chip" @click.stop="accountOpen = !accountOpen">
            <span class="account__avatar">{{ user.email.charAt(0).toUpperCase() }}</span>
            <span class="account__mail">{{ user.email }}</span>
            <AppIcon name="chevron-down" :size="13" />
          </button>
          <div v-if="accountOpen" class="account__menu" @click.stop>
            <div class="account__menu-mail">{{ user.email }}</div>
            <button class="account__menu-item" @click="doSignOut">
              <AppIcon name="close" :size="14" />
              {{ t('auth.signout') }}
            </button>
          </div>
        </div>
      </template>
    </div>
  </header>

  <!-- 全局轻提示 -->
  <Transition name="toast">
    <div v-if="toast" class="toast">{{ toast }}</div>
  </Transition>
</template>

<script setup lang="ts">
const { t, locale, locales, setLocale } = useI18n()
const localePath = useLocalePath()
const { user, diamonds, openAuth, openNav, restore, signOut, showToast, toast } = useAuth()

const langOpen = ref(false)
const accountOpen = ref(false)
const localeList = computed(() =>
  (locales.value as any[]).map(l => ({ code: l.code, name: l.name }))
)

async function chooseLocale(code: string) {
  langOpen.value = false
  await setLocale(code as 'zh' | 'en')
}

/* 移动端点击 logo 打开左侧菜单抽屉；PC 端保持返回首页的链接 */
function onLogoClick(e: MouseEvent) {
  if (import.meta.client && window.matchMedia('(max-width: 860px)').matches) {
    e.preventDefault()
    openNav()
  }
}

function doSignOut() {
  accountOpen.value = false
  signOut()
  showToast(t('auth.signedOut'))
}

/* 外观模式：light / dark，存 localStorage */
const theme = useState<'light' | 'dark'>('theme', () => 'light')
useHead({ htmlAttrs: { 'data-theme': () => theme.value } })

function toggleTheme() {
  theme.value = theme.value === 'light' ? 'dark' : 'light'
  if (import.meta.client) localStorage.setItem('wimo_theme', theme.value)
}

function closePopovers() {
  langOpen.value = false
  accountOpen.value = false
}

onMounted(() => {
  const saved = localStorage.getItem('wimo_theme')
  if (saved === 'dark') theme.value = 'dark'
  restore()
  document.addEventListener('click', closePopovers)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', closePopovers)
})
</script>
