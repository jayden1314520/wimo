/**
 * 任务类型 Schema —— 配置驱动表单
 *
 * 原站（wimogen）所有功能共用同一表单骨架，
 * 每个功能 = 上传区组合 + 特有字段 + 共用生成设置 + 右侧独立的示意图。
 * 新增功能只需在此追加一条 schema，无需改组件。
 */

export interface UploadSlot {
  key: string
  labelKey: string      // i18n: form.upload.<key>
  max: number
  min?: number          // 最少上传数（默认 1，参与必填校验）
  optional?: boolean    // 选填：不显示 *、不参与校验
  hintKey?: string      // 自定义数量提示 i18n key: form.hint.<hintKey>
}

export interface FieldDef {
  key: string
  type: 'textarea' | 'number' | 'datetime' | 'select' | 'text'
  required?: boolean
  optional?: boolean        // 选填：不显示 *、显示「选填」、不参与校验
  options?: string[]        // select 值列表（配合 optionsNs 翻译）
  optionsNs?: string        // i18n 命名空间: options.<ns>.<value>
  placeholderKey?: string   // i18n: form.ph.<key>
  ai?: boolean              // 显示 AI 帮写按钮
  half?: boolean            // 半宽（与下一字段并排）
}

/** 右侧预览示意图：图片功能用静态封面，视频功能用示例 mp4 */
export type PreviewKind = 'image' | 'video'

export interface PreviewDef {
  kind: PreviewKind
  file: string              // public/images/preview/ 下的文件名
  poster?: string           // 视频首帧（加载前的占位图）
}

export interface TaskSchema {
  key: string
  uploads: UploadSlot[]
  fields: FieldDef[]
  common: string[]          // 生成设置里的下拉: ratio/platform/.../duration/videoScene...
  resultMode: boolean       // 是否显示「系统默认/自定义张数」分段
  preview: PreviewDef       // 左侧切换时右侧同步切换的示意图
}

export const TASK_SCHEMAS: TaskSchema[] = [
  {
    key: 'productSet',
    uploads: [{ key: 'product', labelKey: 'product', max: 6 }],
    fields: [
      { key: 'sellingPoints', type: 'textarea', required: true, placeholderKey: 'sellingPoints', ai: true }
    ],
    common: ['ratio', 'platform', 'region', 'language', 'resolution', 'quality'],
    resultMode: true,
    preview: { kind: 'image', file: 'cover-productSet.jpg' }
  },
  {
    key: 'productDetail',
    uploads: [{ key: 'product', labelKey: 'product', max: 6 }],
    fields: [
      { key: 'sellingPoints', type: 'textarea', required: true, placeholderKey: 'sellingPoints', ai: true },
      { key: 'pageStyle', type: 'select', options: ['minimal', 'premium', 'marketing', 'editorial'], optionsNs: 'pageStyle' }
    ],
    common: ['ratio', 'platform', 'region', 'language', 'resolution', 'quality'],
    resultMode: true,
    preview: { kind: 'image', file: 'cover-productDetail.jpg' }
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
    resultMode: true,
    preview: { kind: 'image', file: 'cover-hotReplica.jpg' }
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
    resultMode: true,
    preview: { kind: 'image', file: 'cover-scene.jpg' }
  },
  {
    key: 'retouch',
    uploads: [{ key: 'product', labelKey: 'product', max: 1 }],
    fields: [
      { key: 'retouchNote', type: 'textarea', required: true, placeholderKey: 'retouchNote' }
    ],
    common: ['ratio', 'platform', 'resolution', 'quality'],
    resultMode: false,
    preview: { kind: 'image', file: 'cover-retouch.jpg' }
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
    resultMode: false,
    preview: { kind: 'image', file: 'cover-poster.jpg' }
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
    resultMode: false,
    preview: { kind: 'image', file: 'cover-flatLay.jpg' }
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
    resultMode: false,
    preview: { kind: 'image', file: 'cover-modelTryon.jpg' }
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
    resultMode: false,
    preview: { kind: 'image', file: 'cover-anythingWear.jpg' }
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
    resultMode: false,
    // 原站该功能的示意内容为动态视频
    preview: { kind: 'video', file: 'clip-swapModel.mp4', poster: 'poster-swapModel.jpg' }
  },
  {
    /* 视频 · 商品展示短片：原站字段为 商品图 + 视频背景/镜头/清晰度/时长/视频模型 */
    key: 'productVideo',
    uploads: [{ key: 'product', labelKey: 'product', max: 1 }],
    fields: [],
    common: ['ratio', 'videoScene', 'cameraStyle', 'videoResolution', 'duration', 'videoModel'],
    resultMode: false,
    preview: { kind: 'video', file: 'clip-productVideo.mp4', poster: 'poster-productVideo.jpg' }
  },
  {
    /* 视频 · 带货视频：比展示短片多 卖点、模特图来源、平台/地区/语言 */
    key: 'salesVideo',
    uploads: [
      { key: 'product', labelKey: 'product', max: 1 },
      { key: 'modelRef', labelKey: 'modelRef', max: 1 }
    ],
    fields: [
      { key: 'sellingPoints', type: 'textarea', required: true, placeholderKey: 'sellingPoints', ai: true }
    ],
    common: ['ratio', 'platform', 'region', 'language', 'videoResolution', 'duration', 'videoModel'],
    resultMode: false,
    preview: { kind: 'video', file: 'clip-salesVideo.mp4', poster: 'poster-salesVideo.jpg' }
  },
  {
    /* 视频 · 探店：门头照 + 环境照 + 菜品照(4-8) + 博主照(必填) + 店铺名/位置(选填) + 视频配置 */
    key: 'storeVisit',
    uploads: [
      { key: 'storefront', labelKey: 'storefront', max: 1 },
      { key: 'storeEnv', labelKey: 'storeEnv', max: 6 },
      { key: 'dishes', labelKey: 'dishes', max: 8, min: 4, hintKey: 'dishes' },
      { key: 'blogger', labelKey: 'blogger', max: 1 }
    ],
    fields: [
      { key: 'storeName', type: 'text', optional: true, placeholderKey: 'storeName' },
      { key: 'location', type: 'text', optional: true, placeholderKey: 'location' }
    ],
    common: ['ratio', 'language', 'videoResolution', 'duration', 'videoModel'],
    resultMode: false,
    preview: { kind: 'image', file: 'cover-storeVisit.jpg' }
  }
]

export function getSchema(key: string): TaskSchema {
  return TASK_SCHEMAS.find(s => s.key === key) || TASK_SCHEMAS[0]
}
