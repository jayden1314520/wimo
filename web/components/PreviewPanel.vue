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

      <!-- 默认 / 完成：有真实结果时展示结果图，否则展示功能示意图 -->
      <div v-else class="preview-hero">
        <!-- 真实生成结果（后端 /profile/result 下的文件） -->
        <div v-if="hasResult && items.length" class="preview-grid">
          <div
            v-for="(src, i) in items" :key="i"
            class="preview-grid__item"
            :class="{ 'preview-grid__item--hero': i === 0 }"
          >
            <img :src="src" :alt="`result-${i + 1}`" />
          </div>
        </div>

        <template v-else>
          <!-- 视频类功能：示例短片播放器 -->
          <div v-if="preview.kind === 'video'" class="preview-video">
            <video
              class="preview-video__media"
              :src="preview.src"
              :poster="preview.poster || undefined"
              autoplay muted loop playsinline preload="metadata"
            ></video>
            <span class="preview-video__tag">
              <AppIcon name="play" :size="11" />
              {{ t('preview.videoTag') }}
            </span>
          </div>

          <!-- 图片类功能：该功能的模板封面示意图 -->
          <img
            v-else
            class="preview-hero__img"
            :src="preview.src"
            :alt="`${preview.alt} · ${t('preview.placeholderBadge')}`"
          />
        </template>

        <div v-if="hasResult" class="gen-badge gen-badge--done">
          ✓ {{ t('records.count', { n: items.length }) }}
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
interface PreviewCfg {
  kind: 'image' | 'video'
  src: string
  poster: string
  alt: string
}

defineProps<{
  items: string[]
  generating: boolean
  progress: number
  hasResult: boolean
  preview: PreviewCfg
}>()
const emit = defineEmits<{ records: [] }>()
const { t } = useI18n()
</script>
