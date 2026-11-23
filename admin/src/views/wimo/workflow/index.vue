<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon class="mb14"
      title="智能体工作流 · 一个菜单就是一个工作流"
      description="所有任务共用同一个引擎，但每个菜单（智能体）有独立的：能力 / 模型池 / 基础价 / 计费单位 / 维度倍率 / 执行步骤。最终消耗 = clamp(ceil((基础价 + 单位单价×单位数) × Π维度倍率 × 模型倍率), 最低, 最高)。视频按秒+清晰度+模型计费，图片按张+清晰度+画质+模型计费。" />

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>工作流列表（{{ rows.length }}）· 共 {{ models.length }} 个模型可绑定</span>
          <el-button size="small" icon="Refresh" @click="getList" />
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" size="small" row-key="workflowId">
        <el-table-column label="菜单 / 智能体" min-width="150">
          <template #default="scope">
            <div class="wf-name">{{ scope.row.nameZh }}</div>
            <div class="wf-key">{{ scope.row.workflowKey }}</div>
          </template>
        </el-table-column>
        <el-table-column label="能力" align="center" width="110">
          <template #default="scope"><el-tag size="small" effect="plain">{{ CAP_NAME[scope.row.capability] || scope.row.capability }}</el-tag></template>
        </el-table-column>
        <el-table-column label="基础价" align="center" width="80">
          <template #default="scope"><b class="num">{{ scope.row.baseCost }}</b></template>
        </el-table-column>
        <el-table-column label="计费单位" align="center" width="120">
          <template #default="scope">{{ UNIT_NAME[scope.row.unit] || scope.row.unit }} <span class="unit-cost">×{{ Number(scope.row.unitCost ?? 0).toFixed(2) }}</span></template>
        </el-table-column>
        <el-table-column label="默认模型" prop="defaultModel" min-width="130" />
        <el-table-column label="价格区间" align="center" width="105">
          <template #default="scope"><span class="range">{{ scope.row.minCost }}~{{ scope.row.maxCost }}</span></template>
        </el-table-column>
        <el-table-column label="启用" align="center" width="80">
          <template #default="scope">
            <el-switch v-model="scope.row.enabled" active-value="1" inactive-value="0"
              @change="val => handleToggle(scope.row, val)" v-hasPermi="['wimo:workflow:edit']" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="90" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['wimo:workflow:edit']">配置</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog title="配置智能体与计费规则" v-model="editOpen" width="860px" append-to-body>
      <el-form ref="editRef" :model="form" :rules="rules" label-width="120px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="菜单标识"><el-input v-model="form.workflowKey" disabled /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="中文名称"><el-input v-model="form.nameZh" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="英文名称"><el-input v-model="form.nameEn" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="能力（模型池）">
              <el-select v-model="form.capability" style="width: 100%">
                <el-option v-for="(v, k) in CAP_NAME" :key="k" :label="v" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认模型">
              <el-select v-model="form.defaultModel" style="width: 100%" clearable filterable>
                <el-option v-for="m in capModels" :key="m.modelKey" :label="m.nameZh + ' (' + m.modelKey + ')'" :value="m.modelKey" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="24">
            <el-form-item label="允许的模型">
              <el-select v-model="allowedArr" multiple filterable style="width: 100%" placeholder="留空 = 该能力下全部启用模型">
                <el-option v-for="m in capModels" :key="m.modelKey" :label="m.nameZh" :value="m.modelKey" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">计费规则</el-divider>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="基础价(钻石)"><el-input-number v-model="form.baseCost" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8">
            <el-form-item label="计费单位">
              <el-select v-model="form.unit" style="width: 100%">
                <el-option v-for="(v, k) in UNIT_NAME" :key="k" :label="v" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="单位单价"><el-input-number v-model="form.unitCost" :min="0" :step="0.1" :precision="2" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="单位数字段"><el-input v-model="form.unitField" placeholder="duration / resultCount" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="最低消耗"><el-input-number v-model="form.minCost" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="最高消耗"><el-input-number v-model="form.maxCost" :min="1" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="24">
            <el-form-item label="维度倍率">
              <el-input v-model="form.multipliers" type="textarea" :rows="4" />
              <div class="tip">JSON：键为表单字段名，值为「选项 → 倍率」。如 {"videoResolution":{"720p":1.4,"1080p":1.8}}。视频建议用 videoResolution，图片用 resolution / quality。</div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="执行步骤">
              <el-input v-model="form.stepsJson" type="textarea" :rows="3" />
              <div class="tip">JSON 数组，决定生成进度的推进步数，也是该智能体的工作流编排说明。</div>
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="editOpen = false">取 消</el-button>
        <el-button type="primary" @click="submitForm">保 存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="WimoWorkflow">
import { listWorkflow, updateWorkflow } from '@/api/wimo/workflow'

const { proxy } = getCurrentInstance()
const CAP_NAME = { text2img: '文生图', img2img: '图生图', img2video: '图生视频', text2video: '文生视频' }
const UNIT_NAME = { flat: '固定', count: '按张', second: '按秒' }

const rows = ref([])
const models = ref([])
const loading = ref(false)
const editOpen = ref(false)
const form = ref({})
const allowedArr = ref([])

const rules = {}

const capModels = computed(() => models.value.filter(m => m.capability === form.value.capability && String(m.enabled) === '1'))

function getList() {
  loading.value = true
  listWorkflow().then(res => {
    const d = res.data || {}
    rows.value = (d.rows || []).map(r => ({ ...r, enabled: String(r.enabled ?? '1') }))
    models.value = d.models || []
    loading.value = false
  }).catch(() => { loading.value = false })
}

function handleEdit(row) {
  form.value = { ...row }
  try { form.value.multipliers = JSON.stringify(JSON.parse(row.multipliers || '{}'), null, 2) } catch {}
  try { form.value.stepsJson = JSON.stringify(JSON.parse(row.stepsJson || '[]'), null, 2) } catch {}
  allowedArr.value = (row.allowedModels || '').split(',').map(s => s.trim()).filter(Boolean)
  editOpen.value = true
  nextTick(() => proxy.$refs.editRef && proxy.$refs.editRef.clearValidate())
}

function handleToggle(row, val) {
  updateWorkflow({ ...row, enabled: String(val) }).then(() => {
    proxy.$modal.msgSuccess((val === '1' ? '已启用「' : '已停用「') + row.nameZh + '」')
  }).catch(() => { row.enabled = val === '1' ? '0' : '1' })
}

function submitForm() {
  let multipliers, steps
  try { multipliers = JSON.stringify(JSON.parse(form.value.multipliers || '{}')) }
  catch (e) { proxy.$modal.msgError('维度倍率 JSON 错误：' + e.message); return }
  try { steps = JSON.stringify(JSON.parse(form.value.stepsJson || '[]')) }
  catch (e) { proxy.$modal.msgError('执行步骤 JSON 错误：' + e.message); return }
  const data = { ...form.value, multipliers, stepsJson: steps, allowedModels: allowedArr.value.join(',') }
  updateWorkflow(data).then(() => { proxy.$modal.msgSuccess('计费规则已保存'); editOpen.value = false; getList() })
}

onMounted(getList)
</script>

<style scoped>
.card-head { display: flex; align-items: center; justify-content: space-between; }
.mb14 { margin-bottom: 14px; }
.wf-name { font-weight: 600; }
.wf-key { font-size: 11px; color: #a1a1aa; }
.num { color: #b45309; font-size: 14px; font-variant-numeric: tabular-nums; }
.unit-cost { color: #a1a1aa; font-size: 11px; }
.range { color: #71717a; font-size: 12px; font-variant-numeric: tabular-nums; }
.tip { font-size: 11px; color: #a1a1aa; line-height: 1.5; margin-top: 2px; }
</style>
