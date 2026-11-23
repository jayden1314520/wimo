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
        {{ t(`form.upload.${slot.labelKey}`) }}<span class="req">*</span>
        <span class="hint">{{ t('form.uploadLimit', { n: slot.max }) }}</span>
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

      <div v-else-if="f.type === 'number'" class="select-wrap">
        <input
          v-model.number="values[f.key]" type="number" min="1" max="20"
          class="number-input" :placeholder="t('form.ph.count')"
        />
      </div>

      <div v-else-if="f.type === 'datetime'" class="select-wrap">
        <input v-model="values[f.key]" type="datetime-local" class="datetime-input" />
      </div>

      <div v-else class="select-wrap">
        <select v-model="values[f.key]">
          <option v-for="o in f.options" :key="o" :value="o">
            {{ t(`options.${f.optionsNs}.${o}`) }}
          </option>
        </select>
        <AppIcon name="pencil" :size="12" class="select-wrap__edit" :title="t('form.edit')" />
      </div>
    </div>

    <!-- 生成设置 -->
    <div class="field">
      <div class="field__label">
        {{ t('form.settingsLabel') }}<span class="req">*</span>
      </div>

      <div class="grid2">
        <div v-for="f in commonFields" :key="f.key" class="field" style="margin-bottom: 0">
          <div class="field__label" style="font-weight: 400; font-size: 12px; margin-bottom: 6px">
            {{ t(`form.fields.${f.key}`) }}
          </div>
          <div class="select-wrap">
            <select v-model="model[f.key]" :aria-label="t(`form.fields.${f.key}`)">
              <option v-for="o in f.options" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
            <AppIcon name="pencil" :size="12" class="select-wrap__edit" :title="t('form.edit')" />
          </div>
        </div>
      </div>

      <div class="field" style="margin: 13px 0 0">
        <div class="field__label" style="font-weight: 400; font-size: 12px; margin-bottom: 6px">
          {{ t('form.fields.model') }}
        </div>
        <div class="select-wrap">
          <select v-model="model.model" :aria-label="t('form.fields.model')">
            <option v-for="o in modelOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
          <AppIcon name="pencil" :size="12" class="select-wrap__edit" />
        </div>
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
import type { TaskSchema, FieldDef } from '~/config/tasks'
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

/* 共用生成设置（按 schema.common 过滤） */
const ALL_COMMON: Record<string, () => { value: string; label: string }[]> = {
  ratio: () => ['3:4', '1:1', '9:16', '16:9', '4:3'].map(v => ({ value: v, label: v })),
  platform: () => ['taobao', 'jd', 'pdd', 'douyin'].map(v => ({ value: v, label: t(`options.platform.${v}`) })),
  region: () => ['cn', 'us', 'jp', 'eu'].map(v => ({ value: v, label: t(`options.region.${v}`) })),
  language: () => ['zh', 'en', 'ja'].map(v => ({ value: v, label: t(`options.language.${v}`) })),
  resolution: () => ['2k', '4k'].map(v => ({ value: v, label: t(`options.resolution.${v}`) })),
  quality: () => ['low', 'mid', 'high'].map(v => ({ value: v, label: t(`options.quality.${v}`) }))
}

const commonFields = computed(() =>
  schema.value.common.map(key => ({ key, options: ALL_COMMON[key]() }))
)

const model = reactive({
  ratio: '3:4',
  platform: 'taobao',
  region: 'cn',
  language: 'zh',
  resolution: '2k',
  quality: 'mid',
  model: 'wimo2'
})

const modelOptions = computed(() =>
  ['wimo2', 'wimo2pro'].map(v => ({ value: v, label: t(`options.model.${v}`) }))
)

/* 必填校验：首个上传槽位至少 1 张 + required 字段非空 */
const canSubmit = computed(() => {
  const s = schema.value
  const first = s.uploads[0]
  if (first && uploads[first.key]?.length === 0) return false
  return s.fields.every(f => !f.required || String(values[f.key] ?? '').trim() !== '')
})

function setFileInput(key: string, el: any) {
  fileInputs[key] = el as HTMLInputElement
}

function pickFile(key: string) {
  fileInputs[key]?.click()
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
