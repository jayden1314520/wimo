<template>
  <section class="preview-col">
    <div class="preview-bar">
      <button class="btn-ghost" type="button" @click="emit('records')">
        <AppIcon name="clock" :size="13" />
        {{ t('preview.records') }}
      </button>
    </div>

    <div class="preview-card">
      <div class="preview-card__head">
        <span class="preview-card__title">{{ t('preview.title') }}</span>
        <span v-if="!hasResult" class="badge">{{ t('preview.placeholderBadge') }}</span>
        <span v-else-if="generating" class="badge">{{ t('preview.generating', { n: progress }) }}</span>
        <span v-else class="badge">{{ t('records.count', { n: items.length }) }}</span>
      </div>

      <!-- 生成中骨架 -->
      <div v-if="generating" class="preview-grid">
        <div
          v-for="n in 8" :key="n"
          class="preview-grid__item skeleton"
          :class="{ 'preview-grid__item--hero': n === 1 }"
          :style="n > progress ? 'opacity:.45' : ''"
        ></div>
      </div>

      <!-- 结果 / 示例 -->
      <div v-else class="preview-grid">
        <div
          v-for="(src, i) in items" :key="i"
          class="preview-grid__item"
          :class="{ 'preview-grid__item--hero': i === 0 }"
        >
          <img :src="src" :alt="`preview-${i + 1}`" loading="lazy" />
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
const props = defineProps<{
  items: string[]
  generating: boolean
  progress: number
  hasResult: boolean
}>()
const emit = defineEmits<{ records: [] }>()
const { t } = useI18n()
</script>
