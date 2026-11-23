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
defineProps<{ active: string }>()
defineEmits<{ select: [key: string] }>()
const { t } = useI18n()

const groups = [
  {
    label: 'nav.groups.ecommerce',
    items: [
      { key: 'productSet', icon: 'grid', label: 'nav.items.productSet' },
      { key: 'productDetail', icon: 'detail', label: 'nav.items.productDetail' },
      { key: 'hotReplica', icon: 'copy', label: 'nav.items.hotReplica' },
      { key: 'scene', icon: 'image', label: 'nav.items.scene' },
      { key: 'retouch', icon: 'retouch', label: 'nav.items.retouch' },
      { key: 'poster', icon: 'poster', label: 'nav.items.poster' },
      { key: 'flatLay', icon: 'shirt', label: 'nav.items.flatLay' }
    ]
  },
  {
    label: 'nav.groups.model',
    items: [
      { key: 'modelTryon', icon: 'user', label: 'nav.items.modelTryon' },
      { key: 'anythingWear', icon: 'wear', label: 'nav.items.anythingWear' },
      { key: 'swapModel', icon: 'swap', label: 'nav.items.swapModel' }
    ]
  },
  {
    label: 'nav.groups.video',
    items: [
      { key: 'productVideo', icon: 'video', label: 'nav.items.productVideo' },
      { key: 'salesVideo', icon: 'cart', label: 'nav.items.salesVideo' }
    ]
  }
]
</script>
