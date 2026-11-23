<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon class="mb14"
      title="功能模板配置"
      description="这里维护用户端 13 个功能的上传槽位、字段与生成设置。修改后用户端 /api/wimo/schema/list 立即生效，任务提交会按新模板做后端校验。默认使用本地模拟适配器，配置 wimo.* 可切换真实服务。" />

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>模板列表（{{ rows.length }}）</span>
          <el-button size="small" icon="Refresh" @click="getList">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" size="small" row-key="id">
        <el-table-column label="ID" align="center" prop="id" width="60" />
        <el-table-column label="功能标识" prop="taskKey" width="140" />
        <el-table-column label="中文名称" prop="nameZh" width="130" />
        <el-table-column label="英文名称" prop="nameEn" width="140" />
        <el-table-column label="分组" align="center" width="100">
          <template #default="scope"><el-tag size="small" effect="plain">{{ groupName(scope.row.groupKey) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="排序" align="center" prop="sortNo" width="70" />
        <el-table-column label="启用" align="center" width="90">
          <template #default="scope">
            <el-switch v-model="scope.row.enabled" active-value="1" inactive-value="0"
              @change="val => handleToggle(scope.row, val)" v-hasPermi="['wimo:schema:edit']" />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" align="center" prop="updateTime" width="165" />
        <el-table-column label="操作" align="center" width="100" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['wimo:schema:edit']">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog title="编辑功能模板" v-model="editOpen" width="820px" append-to-body>
      <el-form ref="editRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="功能标识">
              <el-input v-model="form.taskKey" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分组">
              <el-select v-model="form.groupKey" style="width: 100%">
                <el-option label="电商图片" value="ecommerce" />
                <el-option label="模特/穿戴" value="model" />
                <el-option label="视频" value="video" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="中文名称" prop="nameZh">
              <el-input v-model="form.nameZh" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="英文名称" prop="nameEn">
              <el-input v-model="form.nameEn" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="图标">
              <el-input v-model="form.icon" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序号">
              <el-input-number v-model="form.sortNo" :min="0" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否启用">
              <el-switch v-model="form.enabled" active-value="1" inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="模板 JSON" prop="schemaJson">
          <el-input v-model="form.schemaJson" type="textarea" :rows="14" spellcheck="false" class="json-input" />
          <div class="json-tip">
            <span>需为合法 JSON。包含 uploads / fields / common / preview 等节点；保存后后端立即按此校验任务提交。</span>
            <el-button link type="primary" size="small" @click="formatJson">格式化</el-button>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editOpen = false">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="WimoSchema">
import { listSchema, updateSchema, schemaProviders } from '@/api/wimo/schema'

const { proxy } = getCurrentInstance()

const rows = ref([])
const loading = ref(false)
const editOpen = ref(false)
const form = ref({})
const providerHint = ref('')

const rules = {
  nameZh: [{ required: true, message: '中文名称不能为空', trigger: 'blur' }],
  nameEn: [{ required: true, message: '英文名称不能为空', trigger: 'blur' }],
  schemaJson: [{ validator: (rule, value, callback) => {
    try { JSON.parse(value); callback() } catch (e) { callback(new Error('JSON 格式错误：' + e.message)) }
  }, trigger: 'blur' }]
}

const GROUP_NAME = { ecommerce: '电商图片', model: '模特/穿戴', video: '视频' }
function groupName(v) { return GROUP_NAME[v] || v || '-' }

function getList() {
  loading.value = true
  listSchema().then(res => {
    rows.value = (res.data || []).map(r => ({ ...r, enabled: String(r.enabled ?? '1') }))
    loading.value = false
  }).catch(() => { loading.value = false })
}

function handleToggle(row, val) {
  updateSchema({ ...row, enabled: String(val) }).then(() => {
    proxy.$modal.msgSuccess((val === '1' ? '已启用「' : '已停用「') + row.nameZh + '」')
  }).catch(() => {
    row.enabled = val === '1' ? '0' : '1'
  })
}

function handleEdit(row) {
  let pretty = row.schemaJson || '{}'
  try { pretty = JSON.stringify(JSON.parse(pretty), null, 2) } catch {}
  form.value = { ...row, schemaJson: pretty }
  editOpen.value = true
  nextTick(() => proxy.$refs.editRef && proxy.$refs.editRef.clearValidate())
}

function formatJson() {
  try { form.value.schemaJson = JSON.stringify(JSON.parse(form.value.schemaJson), null, 2) }
  catch (e) { proxy.$modal.msgError('JSON 格式错误：' + e.message) }
}

function submitForm() {
  proxy.$refs.editRef.validate(valid => {
    if (!valid) return
    let compact
    try { compact = JSON.stringify(JSON.parse(form.value.schemaJson)) }
    catch (e) { proxy.$modal.msgError('JSON 格式错误：' + e.message); return }
    updateSchema({ ...form.value, schemaJson: compact }).then(() => {
      proxy.$modal.msgSuccess('保存成功')
      editOpen.value = false
      getList()
    })
  })
}

onMounted(() => {
  getList()
  schemaProviders().then(res => { providerHint.value = (res.data || {}).hint || '' }).catch(() => {})
})
</script>

<style scoped>
.mb14 { margin-bottom: 14px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.json-input :deep(textarea) { font-family: 'SFMono-Regular', Consolas, monospace; font-size: 12px; line-height: 1.55; }
.json-tip { display: flex; justify-content: space-between; align-items: center; font-size: 12px; color: #909399; width: 100%; }
</style>
