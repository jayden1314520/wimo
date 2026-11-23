<template>
  <Teleport to="body">
    <div v-if="open" class="drawer-mask" @click.self="emit('close')">
      <aside class="drawer">
        <div class="drawer__head">
          <span class="drawer__title">{{ t('records.title') }}</span>
          <button class="icon-btn" :title="t('records.close')" @click="emit('close')">
            <AppIcon name="close" :size="16" />
          </button>
        </div>

        <p v-if="!records.length" class="result-desc">{{ t('records.empty') }}</p>

        <div v-for="r in records" :key="r.id" class="record">
          <div class="record__top">
            <span class="record__meta">{{ r.time }}</span>
            <span class="record__status">{{ t('records.status') }}</span>
          </div>
          <p class="record__meta">{{ r.summary }}</p>
          <div class="record__thumbs">
            <img v-for="(src, i) in r.images" :key="i" :src="src" :alt="`thumb-${i}`" />
          </div>
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
defineProps<{ open: boolean; records: any[] }>()
const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()
</script>
