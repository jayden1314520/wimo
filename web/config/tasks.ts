/**
 * 任务类型 Schema —— 配置驱动表单
 *
 * 原站（wimogen）所有功能共用同一表单骨架，
 * 每个功能 = 上传区组合 + 特有字段 + 共用生成设置。
 * 新增功能只需在此追加一条 schema，无需改组件。
 */

export interface UploadSlot {
  key: string
  labelKey: string      // i18n: form.upload.<key>
  max: number
}

export interface FieldDef {
  key: string
  type: 'textarea' | 'number' | 'datetime' | 'select'
  required?: boolean
  options?: string[]        // select 值列表（配合 optionsNs 翻译）
  optionsNs?: string        // i18n 命名空间: options.<ns>.<value>
  placeholderKey?: string   // i18n: form.ph.<key>
  ai?: boolean              // 显示 AI 帮写按钮
  half?: boolean            // 半宽（与下一字段并排）
}

export interface TaskSchema {
  key: string
  uploads: UploadSlot[]
  fields: FieldDef[]
  common: string[]          // 生成设置里的下拉: ratio/platform/region/language/resolution/quality
  resultMode: boolean       // 是否显示「系统默认/自定义张数」分段
}

const REGION = ['cn', 'us', 'jp', 'eu']

export const TASK_SCHEMAS: TaskSchema[] = [
  {
    key: 'productSet',
    uploads: [{ key: 'product', labelKey: 'product', max: 6 }],
    fields: [
      { key: 'sellingPoints', type: 'textarea', required: true, placeholderKey: 'sellingPoints', ai: true }
    ],
    common: ['ratio', 'platform', 'region', 'language', 'resolution', 'quality'],
    resultMode: true
  },
  {
    key: 'productDetail',
    uploads: [{ key: 'product', labelKey: 'product', max: 6 }],
    fields: [
      { key: 'sellingPoints', type: 'textarea', required: true, placeholderKey: 'sellingPoints', ai: true },
      { key: 'pageStyle', type: 'select', options: ['minimal', 'premium', 'marketing', 'editorial'], optionsNs: 'pageStyle' }
    ],
    common: ['ratio', 'platform', 'region', 'language', 'resolution', 'quality'],
    resultMode: true
  },
  {
    key: 'hotReplica',
    uploads: [
      { key: 'product', labelKey: 'product', max: 1 },
      { key: 'reference', labelKey: 'reference', max: 1 }
    ],
    fields: [
      { key: 'replicaType', type: 'select', required: true, options: ['layout', 'style', 'full'], optionsNs: 'replicaType' },
      { key: 'replicaLevel', type: 'select', required: true, options: ['loose', 'balanced', 'strict'], optionsNs: 'replicaLevel' }
    ],
    common: ['ratio', 'platform', 'resolution', 'quality'],
    resultMode: true
  },
  {
    key: 'scene',
    uploads: [
      { key: 'product', labelKey: 'product', max: 1 },
      { key: 'sceneRef', labelKey: 'sceneRef', max: 1 }
    ],
    fields: [
      { key: 'count', type: 'number', required: true }
    ],
    common: ['ratio', 'platform', 'resolution', 'quality'],
    resultMode: true
  },
  {
    key: 'retouch',
    uploads: [{ key: 'product', labelKey: 'product', max: 1 }],
    fields: [
      { key: 'retouchNote', type: 'textarea', required: true, placeholderKey: 'retouchNote' }
    ],
    common: ['ratio', 'platform', 'resolution', 'quality'],
    resultMode: false
  },
  {
    key: 'poster',
    uploads: [{ key: 'product', labelKey: 'product', max: 1 }],
    fields: [
      { key: 'posterTheme', type: 'textarea', required: true, placeholderKey: 'posterTheme' },
      { key: 'promo', type: 'textarea', required: true, placeholderKey: 'promo' },
      { key: 'activityStart', type: 'datetime', half: true },
      { key: 'activityEnd', type: 'datetime', half: true }
    ],
    common: ['ratio', 'platform', 'resolution', 'quality'],
    resultMode: false
  },
  {
    key: 'flatLay',
    uploads: [
      { key: 'apparel', labelKey: 'apparel', max: 1 },
      { key: 'sceneRef', labelKey: 'sceneRef', max: 1 }
    ],
    fields: [
      { key: 'count', type: 'number', required: true }
    ],
    common: ['ratio', 'platform', 'resolution', 'quality'],
    resultMode: false
  },
  {
    key: 'modelTryon',
    uploads: [
      { key: 'apparel', labelKey: 'apparel', max: 1 },
      { key: 'modelRef', labelKey: 'modelRef', max: 1 },
      { key: 'sceneRef', labelKey: 'sceneRef', max: 1 }
    ],
    fields: [
      { key: 'count', type: 'number', required: true }
    ],
    common: ['ratio', 'platform', 'resolution', 'quality'],
    resultMode: false
  },
  {
    key: 'anythingWear',
    uploads: [
      { key: 'apparel', labelKey: 'apparel', max: 1 },
      { key: 'modelRef', labelKey: 'modelRef', max: 1 },
      { key: 'sceneRef', labelKey: 'sceneRef', max: 1 }
    ],
    fields: [
      { key: 'count', type: 'number', required: true }
    ],
    common: ['ratio', 'platform', 'region', 'resolution', 'quality'],
    resultMode: false
  },
  {
    key: 'swapModel',
    uploads: [
      { key: 'model', labelKey: 'model', max: 1 },
      { key: 'target', labelKey: 'target', max: 1 }
    ],
    fields: [
      { key: 'count', type: 'number', required: true }
    ],
    common: ['ratio', 'platform', 'resolution', 'quality'],
    resultMode: false
  }
]

export function getSchema(key: string): TaskSchema {
  return TASK_SCHEMAS.find(s => s.key === key) || TASK_SCHEMAS[0]
}
