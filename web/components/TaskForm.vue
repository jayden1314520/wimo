<template>
  <form class="form-col" @submit.prevent="submit">
    <div class="form-head">
      <h1 class="form-head__title">{{ t('form.title') }}</h1>
      <button type="button" class="btn-ghost" @click="reset">
        <AppIcon name="plus" :size="13" />
        {{ t('form.newTask') }}
      </button>
    </div>

    <!-- 上传商品图 -->
    <div class="field">
      <div class="field__label">
        {{ t('form.uploadLabel') }}<span class="req">*</span>
        <span class="hint">{{ t('form.uploadLimit') }}</span>
      </div>
      <div class="uploader">
        <div v-for="(asset, i) in images" :key="i" class="upload-box">
          <img :src="asset.preview" :alt="`asset-${i + 1}`" />
          <span v-if="asset.uploading" class="upload-box__loading"></span>
          <button type="button" class="upload-box__del" @click="images.splice(i, 1)">✕</button>
        </div>
        <button
          v-if="images.length < 6" type="button"
          class="upload-box" :title="t('form.uploadHint')" @click="pickFile"
        >
          <AppIcon name="plus" :size="20" />
        </button>
        <input ref="fileInput" type="file" accept="image/*" multiple hidden @change="onFile" />
      </div>
    </div>

    <!-- 卖点 & 要求 -->
    <div class="field">
      <div class="field__label">
        {{ t('form.sellingLabel') }}<span class="req">*</span>
      </div>
      <div class="textarea-wrap">
        <textarea
          v-model="text"
          class="textarea"
          :placeholder="t('form.sellingPlaceholder')"
        ></textarea>
        <button type="button" class="ai-btn" :disabled="writing" @click="aiWrite">
          <AppIcon name="spark" :size="13" />
          {{ writing ? t('form.aiWriting') : t('form.aiWrite') }}
        </button>
      </div>
    </div>

    <!-- 生成设置 -->
    <div class="field">
      <div class="field__label">
        {{ t('form.settingsLabel') }}<span class="req">*</span>
      </div>

      <div class="grid2">
        <div v-for="f in halfFields" :key="f.key" class="field" style="margin-bottom: 0">
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
    <div class="field">
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
const props = defineProps<{ generating: boolean }>()
const emit = defineEmits<{ submit: [payload: any] }>()
const { t } = useI18n()
const api = useWimoApi()

interface Asset {
  preview: string
  url: string
  uploading: boolean
}

const images = ref<Asset[]>([])
const text = ref('')
const writing = ref(false)
const resultMode = ref<'default' | 'custom'>('default')
const fileInput = ref<HTMLInputElement>()

const model = reactive({
  ratio: '3:4',
  platform: 'taobao',
  region: 'cn',
  language: 'zh',
  resolution: '2k',
  quality: 'mid',
  model: 'wimo2'
})

const halfFields = computed(() => [
  {
    key: 'ratio',
    options: ['3:4', '1:1', '9:16', '16:9', '4:3'].map(v => ({ value: v, label: v }))
  },
  {
    key: 'platform',
    options: ['taobao', 'jd', 'pdd', 'douyin'].map(v => ({ value: v, label: t(`options.platform.${v}`) }))
  },
  {
    key: 'region',
    options: ['cn', 'us', 'jp', 'eu'].map(v => ({ value: v, label: t(`options.region.${v}`) }))
  },
  {
    key: 'language',
    options: ['zh', 'en', 'ja'].map(v => ({ value: v, label: t(`options.language.${v}`) }))
  },
  {
    key: 'resolution',
    options: ['2k', '4k'].map(v => ({ value: v, label: t(`options.resolution.${v}`) }))
  },
  {
    key: 'quality',
    options: ['low', 'mid', 'high'].map(v => ({ value: v, label: t(`options.quality.${v}`) }))
  }
])

const modelOptions = computed(() =>
  ['wimo2', 'wimo2pro'].map(v => ({ value: v, label: t(`options.model.${v}`) }))
)

const canSubmit = computed(() => images.value.length > 0 && text.value.trim().length > 0)

function pickFile() {
  fileInput.value?.click()
}

function readAsDataURL(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

async function onFile(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || []).slice(0, 6 - images.value.length)
  input.value = ''

  for (const file of files) {
    const asset = reactive<Asset>({
      preview: await readAsDataURL(file),
      url: '',
      uploading: true
    })
    images.value.push(asset)
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

async function aiWrite() {
  writing.value = true
  await new Promise(r => setTimeout(r, 900))
  text.value = t('form.aiExample')
  writing.value = false
}

function reset() {
  images.value = []
  text.value = ''
  resultMode.value = 'default'
}

function submit() {
  if (!canSubmit.value || props.generating) return
  emit('submit', {
    images: images.value.map(a => a.url || a.preview),
    text: text.value,
    ...model,
    resultMode: resultMode.value
  })
}
</script>
