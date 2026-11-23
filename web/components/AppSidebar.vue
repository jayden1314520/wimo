<template>
  <aside class="sidebar">
    <div v-for="group in groups" :key="group.label" class="sidebar__group">
      <div class="sidebar__label">{{ t(group.label) }}</div>
      <!-- 真实路由链接：水合失败时退化为整页导航，依然可用；且每个功能可被独立收录 -->
      <NuxtLink
        v-for="item in group.items" :key="item.key"
        class="sidebar__item" :class="{ 'is-active': active === item.key }"
        :to="{ query: { task: item.key } }"
        @click="$emit('select', item.key)"
      >
        <AppIcon :name="item.icon" :size="16" />
        <span>{{ t(item.label) }}</span>
      </NuxtLink>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { sidebarGroups } from '~/config/menu'

defineProps<{ active: string }>()
defineEmits<{ select: [key: string] }>()
const { t } = useI18n()
const groups = sidebarGroups
</script>
