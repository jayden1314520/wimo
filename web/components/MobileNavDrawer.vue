<template>
  <Transition name="navdrawer">
    <div v-if="mobileNavOpen" class="nav-mask" @click.self="closeNav">
      <aside class="nav-drawer" role="dialog" aria-modal="true" :aria-label="t('nav.menu')">
        <div class="nav-drawer__head">
          <div class="logo">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
              <path d="M12 1.6l2.35 6.75 7.05 2.05-7.05 2.05L12 19.2l-2.35-6.75L2.6 10.4l7.05-2.05L12 1.6z" />
              <circle cx="19.4" cy="19.2" r="2.7" />
            </svg>
            <span class="logo__text">{{ t('brand.name') }}</span>
          </div>
          <button class="icon-btn" :aria-label="t('auth.close')" @click="closeNav">
            <AppIcon name="close" :size="18" />
          </button>
        </div>

        <div class="nav-drawer__body">
          <div v-for="group in groups" :key="group.label" class="sidebar__group">
            <div class="sidebar__label">{{ t(group.label) }}</div>
            <NuxtLink
              v-for="item in group.items" :key="item.key"
              class="sidebar__item" :class="{ 'is-active': active === item.key }"
              :to="{ query: { task: item.key } }"
              @click="closeNav"
            >
              <AppIcon :name="item.icon" :size="16" />
              <span>{{ t(item.label) }}</span>
            </NuxtLink>
          </div>
        </div>
      </aside>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { sidebarGroups } from '~/config/menu'

const { t } = useI18n()
const { mobileNavOpen, closeNav } = useAuth()
const route = useRoute()
const active = computed(() => (route.query.task as string) || 'productSet')
const groups = sidebarGroups

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape' && mobileNavOpen.value) closeNav()
}
onMounted(() => document.addEventListener('keydown', onKey))
onBeforeUnmount(() => document.removeEventListener('keydown', onKey))
</script>
