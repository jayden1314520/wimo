<template>
  <section class="preview-col">
    <div class="preview-bar">
      <button class="btn-ghost" type="button" @click="emit('records')">
        <AppIcon name="clock" :size="13" />
        {{ t('preview.records') }}
      </button>
    </div>

    <div class="preview-workspace">
      <!-- 生成中：骨架网格 -->
      <div v-if="generating" class="preview-hero">
        <div class="preview-hero__title">Maison Talon</div>
        <div class="preview-grid">
          <div
            v-for="n in 5" :key="n"
            class="preview-grid__item skeleton"
            :class="{ 'preview-grid__item--hero': n === 1 }"
            :style="n > progress ? 'opacity:.4' : ''"
          ></div>
        </div>
        <div class="gen-badge">{{ t('preview.generating', { n: progress }) }}</div>
      </div>

      <!-- 默认 / 完成：原站风格的成品封面图 -->
      <div v-else class="preview-hero">
        <img
          class="preview-hero__img"
          :src="coverSrc"
          alt="Maison Talon 套图效果"
        />
        <div v-if="hasResult" class="gen-badge gen-badge--done">✓ {{ t('records.count', { n: items.length }) }}</div>
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

const base = useRuntimeConfig().app.baseURL || '/'
const coverSrc = `${base}images/preview/template-cover.jpg`
</script>
