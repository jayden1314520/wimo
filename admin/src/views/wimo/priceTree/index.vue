<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon class="mb14"
      title="钻石计费规则树 · 能力 → 维度档位 / 模型 / 工作流"
      description="每一类能力（文本生成 / 文生图 / 图生图 / 图生视频 / 文生视频）是一棵树根，基础钻石各不相同；其下的清晰度、时长、画质、语言、字数等是可独立调价的「维度档位」，模型带倍率，工作流可继承或覆盖能力层基础价。最终消耗 = clamp(ceil((能力基础价 + 单位单价×单位数) × Π档位倍率 × 模型倍率), 最低, 最高)。改任一节点即时影响该能力下全部工作流的预估价。" />

    <el-row :gutter="14">
      <!-- 左：能力树 -->
      <el-col :span="9">
        <el-card shadow="never" class="tree-card">
          <template #header>
            <div class="card-head">
              <span>计费规则树（{{ caps.length }} 个能力分支）</span>
              <div>
                <el-button size="small" icon="Refresh" @click="getTree" />
                <el-button size="small" icon="RefreshRight" @click="handleReload" v-hasPermi="['wimo:capability:edit']">重载默认</el-button>
              </div>
            </div>
          </template>

          <el-input v-model="filterText" placeholder="搜索能力 / 维度 / 档位" size="small" clearable prefix-icon="Search" class="mb10" />
          <el-tree
            ref="treeRef"
            :data="treeData"
            :props="{ label: 'label', children: 'children' }"
            node-key="id"
            :default-expand-all="true"
            :filter-node-method="filterNode"
            :expand-on-click-node="false"
            @node-click="onNodeClick"
            highlight-current
          >
            <template #default="{ node, data }">
              <span class="tree-node" :class="'tree-node--' + data.kind">
                <el-icon v-if="data.kind === 'capability'"><Collection /></el-icon>
                <el-icon v-else-if="data.kind === 'group'"><FolderOpened /></el-icon>
                <el-icon v-else-if="data.kind === 'model-group'"><Cpu /></el-icon>
                <el-icon v-else-if="data.kind === 'workflow-group'"><Share /></el-icon>
                <span class="tree-node__label">{{ data.label }}</span>
                <span v-if="data.factor !== undefined && data.factor !== null" class="tree-node__factor">×{{ Number(data.factor).toFixed(2) }}</span>
                <span v-if="data.cost !== undefined && data.cost !== null" class="tree-node__cost">{{ data.cost }} 钻</span>
                <el-tag v-if="data.enabled === '0'" size="small" type="info" class="ml6">停用</el-tag>
                <el-tag v-if="data.kind === 'capability' && data.reserved" size="small" type="warning" class="ml6">预留</el-tag>
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <!-- 右：节点编辑 + 即时试算 -->
      <el-col :span="15">
        <el-card shadow="never" v-if="current" class="mb14">
          <template #header>
            <div class="card-head">
              <span>{{ currentTitle }}</span>
              <el-tag size="small" :type="KIND_TAG[current.kind] || 'info'">{{ KIND_NAME[current.kind] || current.kind }}</el-tag>
            </div>
          </template>

          <!-- 能力节点 -->
          <el-form v-if="current.kind === 'capability'" :model="capForm" label-width="120px" size="small">
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="能力标识"><el-input v-model="capForm.capabilityKey" disabled /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="中文名称"><el-input v-model="capForm.nameZh" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="英文名称"><el-input v-model="capForm.nameEn" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="基础钻石"><el-input-number v-model="capForm.baseCost" :min="0" style="width:100%" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="计费单位"><el-input v-model="capForm.unit" disabled /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="单位单价"><el-input-number v-model="capForm.unitCost" :min="0" :step="0.1" :precision="2" style="width:100%" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="最低消耗"><el-input-number v-model="capForm.minCost" :min="0" style="width:100%" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="最高消耗"><el-input-number v-model="capForm.maxCost" :min="1" style="width:100%" /></el-form-item></el-col>
            </el-row>
            <el-form-item>
              <el-button type="primary" icon="Check" @click="saveCapability" v-hasPermi="['wimo:capability:edit']">保存能力价</el-button>
              <span class="form-hint">修改后，所有 costSource=inherit 的工作流立即采用新的基础价</span>
            </el-form-item>
          </el-form>

          <!-- 档位节点 -->
          <el-form v-else-if="current.kind === 'option'" :model="nodeForm" label-width="120px" size="small">
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="所属能力"><el-input v-model="nodeForm.capabilityKey" disabled /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="维度"><el-input v-model="nodeForm.dimension" disabled /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="档位值"><el-input v-model="nodeForm.optionValue" disabled /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="倍率 factor"><el-input-number v-model="nodeForm.factor" :min="0.01" :max="99" :step="0.1" :precision="3" style="width:100%" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="中文标签"><el-input v-model="nodeForm.labelZh" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="英文标签"><el-input v-model="nodeForm.labelEn" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="排序"><el-input-number v-model="nodeForm.sortNo" :min="0" style="width:100%" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="启用">
                <el-switch v-model="nodeForm.enabled" active-value="1" inactive-value="0" />
              </el-form-item></el-col>
            </el-row>
            <el-form-item>
              <el-button type="primary" icon="Check" @click="saveNode" v-hasPermi="['wimo:capability:edit']">保存档位</el-button>
              <el-button type="danger" icon="Delete" plain @click="delNode" v-hasPermi="['wimo:capability:edit']">删除档位</el-button>
              <span class="form-hint">改一个 4K 档即影响该能力下所有工作流的 4K 消耗</span>
            </el-form-item>
          </el-form>

          <!-- 模型 / 工作流 / 分组节点 -->
          <div v-else class="readonly-panel">
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item v-for="(v, k) in currentMeta" :key="k" :label="k">{{ v }}</el-descriptions-item>
            </el-descriptions>
            <el-alert v-if="current.kind === 'model'" type="warning" :closable="false" class="mt10"
              title="模型倍率在「模型管理」页维护" description="这里是计费树的只读投影，改倍率请到模型管理，保存后本树与预估价即时同步。" />
            <el-alert v-if="current.kind === 'workflow'" type="warning" :closable="false" class="mt10"
              title="工作流基础价在「智能体工作流」页维护" description="costSource=override 用工作流自身基础价；=inherit 则继承左侧能力层基础价。" />
            <el-alert v-if="current.kind === 'group'" type="success" :closable="false" class="mt10"
              title="维度分组" description="点击下方任一档位即可编辑倍率。也可在下方「新增档位」里为这个维度加新档位（如 8K）。" />
          </div>
        </el-card>

        <el-card v-else shadow="never" class="mb14">
          <el-empty description="点击左侧能力 / 维度 / 档位节点查看与编辑" :image-size="70" />
        </el-card>

        <!-- 新增档位 -->
        <el-card shadow="never" class="mb14">
          <template #header><span>新增维度档位</span></template>
          <el-form :model="newNode" :inline="true" size="small" label-width="70px">
            <el-form-item label="能力">
              <el-select v-model="newNode.capabilityKey" style="width:130px">
                <el-option v-for="c in caps" :key="c.capabilityKey" :label="c.nameZh" :value="c.capabilityKey" />
              </el-select>
            </el-form-item>
            <el-form-item label="维度">
              <el-select v-model="newNode.dimension" style="width:130px">
                <el-option v-for="(v, k) in DIM_NAME" :key="k" :label="v" :value="k" />
              </el-select>
            </el-form-item>
            <el-form-item label="档位值"><el-input v-model="newNode.optionValue" placeholder="如 8k" style="width:100px" /></el-form-item>
            <el-form-item label="倍率"><el-input-number v-model="newNode.factor" :min="0.01" :step="0.1" :precision="3" style="width:110px" /></el-form-item>
            <el-form-item><el-button type="primary" icon="Plus" @click="addNode" v-hasPermi="['wimo:capability:edit']">新增</el-button></el-form-item>
          </el-form>
        </el-card>

        <!-- 即时试算 -->
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>即时试算（验证改价是否生效）</span>
              <el-button size="small" type="primary" icon="Coin" @click="simulate" :loading="simLoading">试算</el-button>
            </div>
          </template>
          <el-form :inline="true" size="small" label-width="70px" class="mb10">
            <el-form-item label="工作流">
              <el-select v-model="sim.workflowKey" filterable style="width:170px" @change="onSimWorkflowChange">
                <el-option v-for="w in allWorkflows" :key="w.workflowKey" :label="w.nameZh" :value="w.workflowKey" />
              </el-select>
            </el-form-item>
            <el-form-item label="清晰度"><el-input v-model="sim.resolution" style="width:90px" placeholder="2k/720p" /></el-form-item>
            <el-form-item label="视频清晰度"><el-input v-model="sim.videoResolution" style="width:90px" placeholder="720p" /></el-form-item>
            <el-form-item label="画质"><el-input v-model="sim.quality" style="width:80px" placeholder="mid" /></el-form-item>
            <el-form-item label="时长/张数"><el-input-number v-model="sim.units" :min="1" :max="600" style="width:110px" /></el-form-item>
            <el-form-item label="模型"><el-input v-model="sim.model" style="width:150px" placeholder="留空用默认" /></el-form-item>
          </el-form>
          <div v-if="simResult">
            <div class="sim-total">本次预计消耗 <b>{{ simResult.cost }}</b> 钻石 · 模型 {{ simResult.modelKey }}</div>
            <el-table :data="simResult.breakdown" size="small" border class="mt10">
              <el-table-column label="层级" width="110">
                <template #default="s"><el-tag size="small" effect="plain">{{ LAYER_NAME[s.row.layer] || s.row.layer }}</el-tag></template>
              </el-table-column>
              <el-table-column label="项目" min-width="150">
                <template #default="s">
                  {{ s.row.labelZh }}
                  <span v-if="s.row.option" class="dim-opt">{{ s.row.optionLabelZh || s.row.option }}</span>
                </template>
              </el-table-column>
              <el-table-column label="倍率 / 取值" align="center" width="130">
                <template #default="s">
                  <span v-if="s.row.factor !== undefined">×{{ Number(s.row.factor).toFixed(2) }}</span>
                  <span v-else-if="s.row.units !== undefined">{{ s.row.units }} × {{ s.row.unitCost }}</span>
                  <span v-else-if="s.row.amount !== undefined">+{{ s.row.amount }}</span>
                  <span v-else-if="s.row.total !== undefined">{{ s.row.total }}</span>
                </template>
              </el-table-column>
              <el-table-column label="小计" align="center" width="100">
                <template #default="s"><b class="num">{{ s.row.total ?? s.row.subtotal ?? '' }}</b></template>
              </el-table-column>
            </el-table>
          </div>
          <el-empty v-else description="填好参数点「试算」，查看沿树逐层求值明细" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="WimoPriceTree">
import {
  capabilityTree, updateCapability, reloadCapability,
  addPriceNode, updatePriceNode, delPriceNode
} from '@/api/wimo/capability'
import { listWorkflow } from '@/api/wimo/workflow'
import request from '@/utils/request'

const { proxy } = getCurrentInstance()

const DIM_NAME = { resolution: '图片清晰度', videoResolution: '视频清晰度', quality: '画质', duration: '时长', wordCount: '字数', language: '语言' }
const KIND_NAME = { capability: '能力分支', group: '维度分组', option: '档位节点', model: '模型', modelGroup: '模型分组', workflow: '工作流', workflowGroup: '工作流分组' }
const KIND_TAG = { capability: 'danger', group: 'success', option: 'primary', model: 'warning', modelGroup: 'info', workflow: 'warning', workflowGroup: 'info' }
const LAYER_NAME = { capability: '能力基础', unit: '单位计价', dimension: '维度倍率', 'unit-adjust': '单位档位', model: '模型倍率', clamp: '取整限价' }

const caps = ref([])
const treeData = ref([])
const loading = ref(false)
const filterText = ref('')
const treeRef = ref(null)
const current = ref(null)
const currentMeta = ref({})
const currentTitle = ref('')
const capForm = ref({})
const nodeForm = ref({})
const newNode = ref({ capabilityKey: '', dimension: 'resolution', optionValue: '', factor: 1.0 })
const allWorkflows = ref([])
const sim = ref({ workflowKey: '', resolution: '2k', videoResolution: '720p', quality: 'mid', units: 4, model: '' })
const simResult = ref(null)
const simLoading = ref(false)

watch(filterText, val => treeRef.value && treeRef.value.filter(val))
function filterNode(value, data) { return !value || (data.label || '').toLowerCase().includes(value.toLowerCase()) }

/** 把后端能力树拍平成 el-tree 的四级结构 */
function buildTree(rows) {
  const out = []
  for (const c of rows) {
    const capNode = {
      id: 'cap:' + c.capabilityKey, kind: 'capability', label: c.nameZh + ' (' + c.capabilityKey + ')',
      cost: c.baseCost, enabled: c.enabled, reserved: c.capabilityKey === 'text2text', raw: c,
      children: []
    }
    // 维度分组 → 档位
    for (const d of (c.dimensions || [])) {
      const gNode = {
        id: 'grp:' + c.capabilityKey + ':' + d.dimension, kind: 'group',
        label: (d.labelZh || d.dimension) + ' · ' + d.dimension, enabled: d.enabled,
        raw: { ...d, capabilityKey: c.capabilityKey }, children: []
      }
      for (const o of (d.options || [])) {
        gNode.children.push({
          id: 'opt:' + o.nodeKey, kind: 'option',
          label: (o.labelZh || o.optionValue) + ' (' + o.optionValue + ')',
          factor: o.factor, enabled: o.enabled, raw: { ...o, capabilityKey: c.capabilityKey }
        })
      }
      capNode.children.push(gNode)
    }
    // 模型分组
    const mg = { id: 'mgrp:' + c.capabilityKey, kind: 'modelGroup', label: '模型倍率（' + (c.models || []).length + '）', enabled: '1', children: [] }
    for (const m of (c.models || [])) {
      mg.children.push({ id: 'model:' + c.capabilityKey + ':' + m.modelKey, kind: 'model', label: m.nameZh + ' (' + m.modelKey + ')', factor: m.costMultiplier, enabled: m.enabled, raw: m })
    }
    capNode.children.push(mg)
    // 工作流分组
    const wg = { id: 'wgrp:' + c.capabilityKey, kind: 'workflowGroup', label: '工作流 / 智能体（' + (c.workflows || []).length + '）', enabled: '1', children: [] }
    for (const w of (c.workflows || [])) {
      wg.children.push({ id: 'wf:' + c.capabilityKey + ':' + w.workflowKey, kind: 'workflow', label: w.nameZh + ' · ' + (w.costSource === 'inherit' ? '继承' : w.baseCost + ' 钻'), cost: w.costSource === 'inherit' ? null : w.baseCost, enabled: w.enabled, raw: w })
    }
    capNode.children.push(wg)
    out.push(capNode)
  }
  return out
}

function getTree() {
  loading.value = true
  capabilityTree().then(res => {
    const data = res.data || {}
    caps.value = data.rows || []
    treeData.value = buildTree(caps.value)
    if (!newNode.value.capabilityKey && caps.value.length) newNode.value.capabilityKey = caps.value[0].capabilityKey
    loading.value = false
  }).catch(() => { loading.value = false })
}

function onNodeClick(data) {
  current.value = data
  currentTitle.value = data.label
  if (data.kind === 'capability') {
    capForm.value = { ...data.raw }
  } else if (data.kind === 'option') {
    nodeForm.value = { ...data.raw, factor: Number(data.raw.factor ?? 1) }
  } else {
    const r = data.raw || {}
    currentMeta.value = {}
    if (r.capabilityKey) currentMeta.value['所属能力'] = r.capabilityKey
    if (r.dimension) currentMeta.value['维度'] = r.dimension
    if (r.modelKey) { currentMeta.value['模型标识'] = r.modelKey; currentMeta.value['计费倍率'] = r.costMultiplier; currentMeta.value['状态'] = r.enabled === '1' ? '启用' : '停用' }
    if (r.workflowKey) { currentMeta.value['工作流'] = r.workflowKey; currentMeta.value['基础价'] = r.baseCost; currentMeta.value['价格来源'] = r.costSource; currentMeta.value['默认模型'] = r.defaultModel }
    if (data.kind === 'group') currentMeta.value['档位数'] = (r.options || []).length
    if (r.baseCost !== undefined && data.kind === 'capability') currentMeta.value['基础价'] = r.baseCost
  }
}

function saveCapability() {
  updateCapability(capForm.value).then(() => {
    proxy.$modal.msgSuccess('能力基础价已保存，即时生效')
    getTree()
  })
}
function saveNode() {
  updatePriceNode(nodeForm.value).then(() => {
    proxy.$modal.msgSuccess('档位已保存，该能力下全部工作流即时生效')
    getTree()
  })
}
function delNode() {
  proxy.$modal.confirm('确认删除档位「' + nodeForm.value.labelZh + '」？').then(() => {
    return delPriceNode(nodeForm.value.nodeId)
  }).then(() => { proxy.$modal.msgSuccess('删除成功'); current.value = null; getTree() }).catch(() => {})
}
function addNode() {
  const n = newNode.value
  if (!n.capabilityKey || !n.optionValue) { proxy.$modal.msgWarning('请选择能力并填写档位值'); return }
  addPriceNode({ capabilityKey: n.capabilityKey, dimension: n.dimension, optionValue: n.optionValue,
    factor: n.factor, labelZh: n.optionValue, labelEn: n.optionValue, nodeType: 'option', enabled: '1', sortNo: 99 }).then(() => {
    proxy.$modal.msgSuccess('档位已新增')
    n.optionValue = ''
    getTree()
  })
}
function handleReload() {
  reloadCapability().then(() => { proxy.$modal.msgSuccess('已按默认 JSON 重载（不覆盖已有改动）'); getTree() })
}

/** 试算：复用管理端 simulate 接口，走真实计费引擎 */
function loadWorkflows() {
  listWorkflow().then(res => {
    allWorkflows.value = res.data?.rows || res.data || []
    const list = Array.isArray(allWorkflows.value) ? allWorkflows.value : []
    if (list.length && !sim.value.workflowKey) { sim.value.workflowKey = list[0].workflowKey; onSimWorkflowChange() }
  }).catch(() => { allWorkflows.value = [] })
}
function onSimWorkflowChange() {
  const w = (allWorkflows.value || []).find(x => x.workflowKey === sim.value.workflowKey)
  if (!w) return
  const isVideo = w.unit === 'second'
  sim.value.units = isVideo ? 5 : 4
  sim.value.model = w.defaultModel || ''
}
function simulate() {
  if (!sim.value.workflowKey) { proxy.$modal.msgWarning('请先选择工作流'); return }
  const common = {
    resolution: sim.value.resolution, videoResolution: sim.value.videoResolution,
    quality: sim.value.quality, resultCount: sim.value.units, duration: sim.value.units,
    model: sim.value.model, videoModel: sim.value.model, language: 'zh'
  }
  simLoading.value = true
  request({ url: '/wimo/capability/simulate', method: 'post',
    data: { taskType: sim.value.workflowKey, uploads: {}, values: {}, common } }).then(res => {
    simResult.value = res.data || res
    simLoading.value = false
  }).catch(() => { simLoading.value = false })
}

onMounted(() => { getTree(); loadWorkflows() })
</script>

<style scoped>
.card-head { display: flex; align-items: center; justify-content: space-between; }
.mb14 { margin-bottom: 14px; }
.mb10 { margin-bottom: 10px; }
.mt10 { margin-top: 10px; }
.ml6 { margin-left: 6px; }
.tree-card :deep(.el-tree-node__content) { height: 30px; }
.tree-node { display: inline-flex; align-items: center; gap: 4px; font-size: 13px; }
.tree-node__label { margin-left: 2px; }
.tree-node--capability { font-weight: 700; color: #b45309; }
.tree-node--group { font-weight: 600; }
.tree-node__factor { color: #d4380d; font-weight: 700; font-variant-numeric: tabular-nums; }
.tree-node__cost { color: #64748b; font-variant-numeric: tabular-nums; }
.num { color: #b45309; font-variant-numeric: tabular-nums; }
.unit-cost, .range, .form-hint, .dim-opt { color: #94a3b8; font-size: 12px; }
.form-hint { margin-left: 12px; }
.dim-opt { margin-left: 6px; color: #64748b; }
.sim-total { font-size: 14px; }
.sim-total b { color: #d4380d; font-size: 20px; font-variant-numeric: tabular-nums; }
.readonly-panel { min-height: 60px; }
</style>
