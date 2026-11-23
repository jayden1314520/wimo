<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon class="mb14"
      title="AI 模型管理"
      description="配置文生图 / 图生图 / 图生视频 / 文生视频四类模型。用户端表单的模型下拉由这里动态驱动；costMultiplier 会作为钻石计费的模型倍率。provider=remote 时可填写模型网关 endpoint，密钥用 credentialRef 引用环境变量，不明文落库。" />

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>模型列表（{{ rows.length }}）</span>
          <div>
            <el-select v-model="filterCap" placeholder="全部能力" size="small" clearable style="width: 150px; margin-right: 8px">
              <el-option v-for="(v, k) in CAP_NAME" :key="k" :label="v" :value="k" />
            </el-select>
            <el-button size="small" icon="Plus" type="primary" @click="handleAdd" v-hasPermi="['wimo:model:edit']">新增模型</el-button>
            <el-button size="small" icon="Refresh" @click="getList" />
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="filtered" size="small" row-key="modelId">
        <el-table-column label="ID" align="center" prop="modelId" width="55" />
        <el-table-column label="模型标识" prop="modelKey" width="150" />
        <el-table-column label="中文名称" prop="nameZh" min-width="150" />
        <el-table-column label="英文名称" prop="nameEn" min-width="160" />
        <el-table-column label="能力" align="center" width="120">
          <template #default="scope">
            <el-tag size="small" :type="CAP_TAG[scope.row.capability] || 'info'">{{ CAP_NAME[scope.row.capability] || scope.row.capability }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="供应商" align="center" prop="provider" width="95" />
        <el-table-column label="计费倍率" align="center" width="100">
          <template #default="scope"><span class="mult">×{{ Number(scope.row.costMultiplier ?? 1).toFixed(2) }}</span></template>
        </el-table-column>
        <el-table-column label="排序" align="center" prop="sortNo" width="65" />
        <el-table-column label="启用" align="center" width="85">
          <template #default="scope">
            <el-switch v-model="scope.row.enabled" active-value="1" inactive-value="0"
              @change="val => handleToggle(scope.row, val)" v-hasPermi="['wimo:model:edit']" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="130" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['wimo:model:edit']">编辑</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wimo:model:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="form.modelId ? '编辑模型' : '新增模型'" v-model="editOpen" width="720px" append-to-body>
      <el-form ref="editRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模型标识" prop="modelKey">
              <el-input v-model="form.modelKey" :disabled="!!form.modelId" placeholder="如 seedance25pro" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="能力" prop="capability">
              <el-select v-model="form.capability" style="width: 100%">
                <el-option v-for="(v, k) in CAP_NAME" :key="k" :label="v" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="中文名称" prop="nameZh"><el-input v-model="form.nameZh" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="英文名称"><el-input v-model="form.nameEn" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="供应商"><el-input v-model="form.provider" placeholder="mock / remote" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计费倍率"><el-input-number v-model="form.costMultiplier" :min="0.1" :max="20" :step="0.1" :precision="2" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" :max="999" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="启用"><el-switch v-model="form.enabled" active-value="1" inactive-value="0" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="Endpoint"><el-input v-model="form.endpoint" placeholder="模型网关地址（provider=remote 时生效）" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="密钥引用"><el-input v-model="form.credentialRef" placeholder="环境变量名，如 MODEL_API_KEY" /></el-form-item></el-col>
          <el-col :span="24">
            <el-form-item label="默认参数">
              <el-input v-model="form.configJson" type="textarea" :rows="4" placeholder='{"maxDuration":60,"resolutions":["720p","1080p"]}' />
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editOpen = false">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="WimoModel">
import { listModel, addModel, updateModel, delModel } from '@/api/wimo/model'

const { proxy } = getCurrentInstance()
const CAP_NAME = { text2img: '文生图', img2img: '图生图', img2video: '图生视频', text2video: '文生视频' }
const CAP_TAG = { text2img: 'primary', img2img: 'success', img2video: 'warning', text2video: 'danger' }

const rows = ref([])
const loading = ref(false)
const editOpen = ref(false)
const filterCap = ref('')
const form = ref({})

const rules = {
  modelKey: [{ required: true, message: '模型标识不能为空', trigger: 'blur' }],
  capability: [{ required: true, message: '请选择能力', trigger: 'change' }],
  nameZh: [{ required: true, message: '中文名称不能为空', trigger: 'blur' }]
}

const filtered = computed(() => filterCap.value ? rows.value.filter(r => r.capability === filterCap.value) : rows.value)

function getList() {
  loading.value = true
  listModel().then(res => {
    rows.value = (res.data || []).map(r => ({ ...r, enabled: String(r.enabled ?? '1') }))
    loading.value = false
  }).catch(() => { loading.value = false })
}

function handleAdd() {
  form.value = { modelKey: '', nameZh: '', nameEn: '', capability: 'img2img', provider: 'mock',
    costMultiplier: 1, sortNo: rows.value.length + 1, enabled: '1', endpoint: '', credentialRef: '',
    configJson: '{}', remark: '' }
  editOpen.value = true
  nextTick(() => proxy.$refs.editRef && proxy.$refs.editRef.clearValidate())
}

function handleEdit(row) {
  let pretty = row.configJson || '{}'
  try { pretty = JSON.stringify(JSON.parse(pretty), null, 2) } catch {}
  form.value = { ...row, configJson: pretty }
  editOpen.value = true
  nextTick(() => proxy.$refs.editRef && proxy.$refs.editRef.clearValidate())
}

function handleToggle(row, val) {
  updateModel({ ...row, enabled: String(val) }).then(() => {
    proxy.$modal.msgSuccess((val === '1' ? '已启用「' : '已停用「') + row.nameZh + '」')
  }).catch(() => { row.enabled = val === '1' ? '0' : '1' })
}

function handleDelete(row) {
  proxy.$modal.confirm('确认删除模型「' + row.nameZh + '」？该模型将从用户端下拉中移除。').then(() => {
    return delModel(row.modelId)
  }).then(() => { proxy.$modal.msgSuccess('删除成功'); getList() }).catch(() => {})
}

function submitForm() {
  proxy.$refs.editRef.validate(valid => {
    if (!valid) return
    let compact = form.value.configJson
    try { compact = JSON.stringify(JSON.parse(compact || '{}')) }
    catch (e) { proxy.$modal.msgError('默认参数 JSON 格式错误：' + e.message); return }
    const data = { ...form.value, configJson: compact }
    const req = data.modelId ? updateModel(data) : addModel(data)
    req.then(() => { proxy.$modal.msgSuccess('保存成功'); editOpen.value = false; getList() })
  })
}

onMounted(getList)
</script>

<style scoped>
.card-head { display: flex; align-items: center; justify-content: space-between; }
.mb14 { margin-bottom: 14px; }
.mult { color: #b45309; font-weight: 700; font-variant-numeric: tabular-nums; }
</style>
