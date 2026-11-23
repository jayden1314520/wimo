<template>
  <form class="form-col" :key="schema.key" @submit.prevent="submit">
    <div class="form-head">
      <h1 class="form-head__title">{{ t(`nav.items.${schema.key}`) }}</h1>
      <button type="button" class="btn-ghost" @click="reset">
        <AppIcon name="plus" :size="13" />
        {{ t('form.newTask') }}
      </button>
    </div>

    <!-- 上传区（多槽位） -->
    <div v-for="slot in schema.uploads" :key="slot.key" class="field">
      <div class="field__label">
        {{ t(`form.upload.${slot.labelKey}`) }}
        <span v-if="!slot.optional" class="req">*</span>
        <span v-else class="optional">{{ t('form.uploadOptional') }}</span>
        <span v-if="slot.hintKey" class="hint">
          {{ t(`form.hint.${slot.hintKey}`, { min: slot.min || 1, max: slot.max }) }}
        </span>
        <span v-else-if="slot.max > 1" class="hint">{{ t('form.uploadLimit', { n: slot.max }) }}</span>
        <span v-if="slot.max > 1" class="hint hint--count">
          {{ uploads[slot.key].length }}/{{ slot.max }}
        </span>
      </div>
      <div class="uploader">
        <div v-for="(asset, i) in uploads[slot.key]" :key="i" class="upload-box">
          <img :src="asset.preview" :alt="`${slot.key}-${i + 1}`" />
          <span v-if="asset.uploading" class="upload-box__loading"></span>
          <button type="button" class="upload-box__del" @click="uploads[slot.key].splice(i, 1)">✕</button>
        </div>
        <button
          v-if="uploads[slot.key].length < slot.max" type="button"
          class="upload-box" :title="t('form.uploadHint')" @click="pickFile(slot.key)"
        >
          <AppIcon name="plus" :size="20" />
        </button>
        <input
          :ref="el => setFileInput(slot.key, el)" type="file" accept="image/*"
          :multiple="slot.max > 1" hidden @change="e => onFile(slot.key, e)"
        />
      </div>
    </div>

    <!-- 特有字段 -->
    <div
      v-for="f in schema.fields" :key="f.key"
      class="field" :class="{ 'field--half-row': f.half }"
    >
      <div class="field__label">
        {{ t(`form.field.${f.key}`) }}<span v-if="f.required" class="req">*</span>
      </div>

      <div v-if="f.type === 'textarea'" class="textarea-wrap">
        <textarea
          v-model="values[f.key]" class="textarea"
          :placeholder="f.placeholderKey ? t(`form.ph.${f.placeholderKey}`) : ''"
        ></textarea>
        <button v-if="f.ai" type="button" class="ai-btn" :disabled="writing" @click="aiWrite(f.key)">
          <AppIcon name="spark" :size="13" />
          {{ writing ? t('form.aiWriting') : t('form.aiWrite') }}
        </button>
      </div>

      <!-- 单行文本：与下拉 / 日期统一的白底细边框控件 -->
      <div v-else-if="f.type === 'text'" class="control">
        <input
          v-model="values[f.key]" type="text" class="control__input"
          :placeholder="f.placeholderKey ? t(`form.ph.${f.placeholderKey}`) : ''"
          :aria-label="t(`form.field.${f.key}`)"
        />
      </div>

      <!-- 数字输入：与下拉统一的白底细边框 + 步进按钮 -->
      <div v-else-if="f.type === 'number'" class="stepper">
        <button type="button" class="stepper__btn" aria-label="minus" @click="step(f.key, -1)">
          <AppIcon name="minus" :size="13" />
        </button>
        <input
          v-model.number="values[f.key]" type="number" min="1" max="20"
          class="stepper__input" :aria-label="t(`form.field.${f.key}`)"
        />
        <button type="button" class="stepper__btn" aria-label="plus" @click="step(f.key, 1)">
          <AppIcon name="plus" :size="13" />
        </button>
      </div>

      <div v-else-if="f.type === 'datetime'" class="control">
        <input
          v-model="values[f.key]" type="datetime-local" class="control__input"
          :aria-label="t(`form.field.${f.key}`)"
        />
      </div>

      <!-- 自定义下拉：替代浏览器原生蓝色高亮弹层 -->
      <AppSelect
        v-else
        v-model="values[f.key]"
        :options="(f.options || []).map(o => ({ value: o, label: t(`options.${f.optionsNs}.${o}`) }))"
        :label="t(`form.field.${f.key}`)"
      />
    </div>

    <!-- 生成设置 -->
    <div class="field">
      <div class="field__label">
        {{ t('form.settingsLabel') }}<span class="req">*</span>
      </div>

      <div class="grid2">
        <div v-for="f in commonFields" :key="f.key" class="field" style="margin-bottom: 0">
          <div class="field__label field__label--sub">{{ t(`form.fields.${f.key}`) }}</div>
          <!-- 视频时长：数字 + 秒单位 -->
          <div v-if="f.key === 'duration'" class="control control--unit">
            <input
              v-model.number="model.duration" type="number" min="1" max="60"
              class="control__input" :aria-label="t('form.fields.duration')"
            />
            <span class="control__unit">s</span>
          </div>
          <AppSelect
            v-else
            v-model="model[f.key]" :options="f.options"
            :label="t(`form.fields.${f.key}`)"
          />
        </div>
      </div>

      <!-- 视频模型已包含在上方网格中；图片类功能单独一行 -->
      <div v-if="!schema.common.includes('videoModel')" class="field" style="margin: 14px 0 0">
        <div class="field__label field__label--sub">{{ t('form.fields.model') }}</div>
        <AppSelect v-model="model.model" :options="modelOptions" :label="t('form.fields.model')" />
      </div>
    </div>

    <!-- 结果配置 -->
    <div v-if="schema.resultMode" class="field">
      <div class="field__label">{{ t('form.resultLabel') }}</div>
      <div class="seg-line">
        <button
          type="button" class="seg-line__btn" :class="{ 'is-active': resultMode === 'default' }"
          @click="resultMode = 'default'"
        >{{ t('form.resultDefault') }}</button>
        <button
          type="button" class="seg-line__btn" :class="{ 'is-active': resultMode === 'custom' }"
          @click="resultMode = 'custom'"
        >{{ t('form.resultCustom') }}</button>
      </div>
      <p class="result-desc">
        {{ resultMode === 'default' ? t('form.resultDesc') : t('form.resultDescCustom') }}
      </p>
    </div>

    <button class="submit" type="submit" :disabled="!canSubmit || generating">
      <AppIcon name="bolt" :size="14" />
      {{ generating ? t('form.submitting') : t('form.submit') }}
    </button>
  </form>
</template>

<script setup lang="ts">
import type { TaskSchema } from '~/config/tasks'
import { getSchema } from '~/config/tasks'

const props = defineProps<{ generating: boolean; schemaKey: string }>()
const emit = defineEmits<{ submit: [payload: any] }>()
const { t } = useI18n()
const api = useWimoApi()

const schema = computed<TaskSchema>(() => getSchema(props.schemaKey))

interface Asset {
  preview: string
  url: string
  uploading: boolean
}

/* 每个上传槽位独立素材列表 */
const uploads = reactive<Record<string, Asset[]>>({})
const values = reactive<Record<string, any>>({})
const resultMode = ref<'default' | 'custom'>('default')
const writing = ref(false)
const fileInputs = reactive<Record<string, HTMLInputElement | null>>({})

/* schema 切换 / 首次挂载：初始化槽位与字段默认值 */
watchEffect(() => {
  const s = schema.value
  for (const slot of s.uploads) if (!uploads[slot.key]) uploads[slot.key] = []
  for (const f of s.fields) {
    if (values[f.key] === undefined) {
      if (f.type === 'select') values[f.key] = f.options?.[0]
      else if (f.type === 'number') values[f.key] = 4
      else values[f.key] = ''
    }
  }
})

/* 共用生成设置（按 schema.common 过滤）
   —— 图片类与视频类共用大部分字段，视频类另有专属选项（对齐原站） */
const ALL_COMMON: Record<string, () => { value: string; label: string }[]> = {
  ratio: () => ['3:4', '1:1', '9:16', '16:9', '4:3'].map(v => ({ value: v, label: v })),
  platform: () => ['taobao', 'jd', 'pdd', 'douyin'].map(v => ({ value: v, label: t(`options.platform.${v}`) })),
  region: () => ['cn', 'us', 'jp', 'eu'].map(v => ({ value: v, label: t(`options.region.${v}`) })),
  language: () => ['zh', 'en', 'ja'].map(v => ({ value: v, label: t(`options.language.${v}`) })),
  resolution: () => ['2k', '4k'].map(v => ({ value: v, label: t(`options.resolution.${v}`) })),
  quality: () => ['low', 'mid', 'high'].map(v => ({ value: v, label: t(`options.quality.${v}`) })),
  videoResolution: () => ['480p', '720p'].map(v => ({ value: v, label: v })),
  videoScene: () => ['studio', 'desk', 'bath', 'kitchen', 'outdoor', 'tech', 'premium']
    .map(v => ({ value: v, label: t(`options.videoScene.${v}`) })),
  cameraStyle: () => ['closeup', 'slowmo', 'macro', 'orbit', 'static', 'handheld']
    .map(v => ({ value: v, label: t(`options.cameraStyle.${v}`) })),
  videoModel: () => ['seedance2fast', 'seedance25flash', 'seedance25', 'seedance20', 'seedance20mini']
    .map(v => ({ value: v, label: t(`options.videoModel.${v}`) }))
}

/* duration 为数字输入（秒），不是下拉 */
const commonFields = computed(() =>
  schema.value.common.map(key => ({
    key,
    options: ALL_COMMON[key] ? ALL_COMMON[key]() : []
  }))
)

const model = reactive({
  ratio: '3:4',
  platform: 'taobao',
  region: 'cn',
  language: 'zh',
  resolution: '2k',
  quality: 'mid',
  model: 'wimo2',
  videoResolution: '480p',
  videoScene: 'studio',
  cameraStyle: 'closeup',
  videoModel: 'seedance2fast',
  duration: 5
})

const modelOptions = computed(() =>
  ['wimo2', 'wimo2pro'].map(v => ({ value: v, label: t(`options.model.${v}`) }))
)

/* 必填校验：每个非选填上传槽位达到最少数量 + required 字段非空 */
const canSubmit = computed(() => {
  const s = schema.value
  for (const slot of s.uploads) {
    if (slot.optional) continue
    const len = uploads[slot.key]?.length ?? 0
    if (len < (slot.min ?? 1)) return false
  }
  return s.fields.every(f => !f.required || String(values[f.key] ?? '').trim() !== '')
})

function setFileInput(key: string, el: any) {
  fileInputs[key] = el as HTMLInputElement
}

function pickFile(key: string) {
  fileInputs[key]?.click()
}

/* 数字步进，限制 1..20 */
function step(key: string, delta: number) {
  const n = Number(values[key]) || 1
  values[key] = Math.min(20, Math.max(1, n + delta))
}

function readAsDataURL(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

async function onFile(key: string, e: Event) {
  const input = e.target as HTMLInputElement
  const slot = schema.value.uploads.find(u => u.key === key)!
  const list = uploads[key]
  const files = Array.from(input.files || []).slice(0, slot.max - list.length)
  input.value = ''

  for (const file of files) {
    const asset = reactive<Asset>({
      preview: await readAsDataURL(file),
      url: '',
      uploading: true
    })
    list.push(asset)
    try {
      const res = await api.upload(file)
      asset.url = res.url
    } catch {
      // 后端未启动时降级：直接把 dataURL 作为素材标识，保证演示可继续
      asset.url = asset.preview
    } finally {
      asset.uploading = false
    }
  }
}

async function aiWrite(key: string) {
  writing.value = true
  await new Promise(r => setTimeout(r, 900))
  values[key] = t('form.aiExample')
  writing.value = false
}

function reset() {
  for (const slot of schema.value.uploads) uploads[slot.key] = []
  for (const f of schema.value.fields) {
    if (f.type === 'select') values[f.key] = f.options?.[0]
    else if (f.type === 'number') values[f.key] = 4
    else values[f.key] = ''
  }
  resultMode.value = 'default'
}

function submit() {
  if (!canSubmit.value || props.generating) return
  const uploadPayload: Record<string, string[]> = {}
  for (const slot of schema.value.uploads) {
    uploadPayload[slot.key] = uploads[slot.key].map(a => a.url || a.preview)
  }
  emit('submit', {
    taskType: schema.value.key,
    uploads: uploadPayload,
    values: { ...values },
    ...model,
    resultMode: resultMode.value
  })
}
</script>
