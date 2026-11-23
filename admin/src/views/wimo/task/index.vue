<template>
  <div class="app-container">
    <!-- 查询区 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="任务标题" prop="title">
        <el-input v-model="queryParams.title" placeholder="请输入任务标题" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="功能类型" prop="taskType">
        <el-select v-model="queryParams.taskType" placeholder="全部功能" clearable style="width: 170px">
          <el-option v-for="(label, key) in typeMap" :key="key" :label="label" :value="key" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 操作区 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['wimo:task:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button plain icon="Refresh" @click="getList">刷新</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="taskList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" align="center" prop="taskId" width="70" />
      <el-table-column label="用户" align="center" prop="userId" width="70" />
      <el-table-column label="功能" align="center" width="120">
        <template #default="scope">{{ typeMap[scope.row.taskType] || scope.row.taskType || '-' }}</template>
      </el-table-column>
      <el-table-column label="任务标题" align="center" prop="title" show-overflow-tooltip min-width="170" />
      <el-table-column label="状态" align="center" width="95">
        <template #default="scope">
          <el-tag :type="statusTag(scope.row.status)" disable-transitions>{{ statusName(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" align="center" width="130">
        <template #default="scope">
          <el-progress :percentage="Number(scope.row.progress || 0)" :stroke-width="8" />
        </template>
      </el-table-column>
      <el-table-column label="结果" align="center" width="80">
        <template #default="scope">{{ scope.row.resultCount || 0 }} 个</template>
      </el-table-column>
      <el-table-column label="重试" align="center" prop="retryCount" width="65" />
      <el-table-column label="耗时" align="center" width="90">
        <template #default="scope">{{ formatCost(scope.row.costMs) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="165" />
      <el-table-column label="操作" align="center" width="215" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['wimo:task:query']">详情</el-button>
          <el-button link type="warning" icon="RefreshRight" @click="handleRetry(scope.row)" v-hasPermi="['wimo:task:retry']">重试</el-button>
          <el-button v-if="['0', '1'].includes(scope.row.status)" link type="info" icon="CircleClose" @click="handleCancel(scope.row)" v-hasPermi="['wimo:task:retry']">取消</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wimo:task:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 详情弹窗 -->
    <el-dialog title="任务详情" v-model="detailOpen" width="860px" append-to-body>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="任务ID">{{ detail.taskId }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ detail.userId }}</el-descriptions-item>
        <el-descriptions-item label="功能">{{ typeMap[detail.taskType] || detail.taskType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusName(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="进度">{{ detail.progress || 0 }}%</el-descriptions-item>
        <el-descriptions-item label="结果数">{{ detail.resultCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="重试次数">{{ detail.retryCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ formatCost(detail.costMs) }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ detail.finishTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="消耗钻石"><b style="color:#d97706">{{ detail.costDiamonds || 0 }}</b></el-descriptions-item>
        <el-descriptions-item label="计费状态">{{ diamondStateName(detail.diamondState) }}</el-descriptions-item>
        <el-descriptions-item label="模型">{{ detail.modelKey || detail.model || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="3">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务标题" :span="3">{{ detail.title || detail.productName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="卖点/要求" :span="3">
          <div style="white-space: pre-wrap">{{ detail.sellingPoints || '-' }}</div>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMsg" label="失败原因" :span="3">
          <span style="color: #f56c6c">{{ detail.errorMsg }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">上传素材（{{ splitImages(detail.images).length }}）</el-divider>
      <div class="img-row">
        <el-image v-for="(img, i) in splitImages(detail.images)" :key="'m' + i" :src="assetUrl(img)" :preview-src-list="splitImages(detail.images).map(assetUrl)" :initial-index="i" fit="cover" class="img-item" preview-teleported>
          <template #error><div class="img-miss">素材</div></template>
        </el-image>
        <span v-if="!splitImages(detail.images).length" class="img-empty">未上传素材</span>
      </div>

      <el-divider content-position="left">生成结果（{{ splitImages(detail.results).length }}）</el-divider>
      <div class="img-row">
        <el-image v-for="(img, i) in splitImages(detail.results)" :key="'r' + i" :src="assetUrl(img)" :preview-src-list="splitImages(detail.results).map(assetUrl)" :initial-index="i" fit="cover" class="img-item" preview-teleported />
        <span v-if="!splitImages(detail.results).length" class="img-empty">{{ detail.status === '3' ? '生成失败' : detail.status === '4' ? '已取消' : '暂无结果' }}</span>
      </div>

      <el-divider content-position="left">计费快照（下单时的规则明细，规则改动不影响历史账单）</el-divider>
      <pre class="payload-box">{{ prettyPayload(detail.billingSnapshot) }}</pre>

      <el-divider content-position="left">提交参数</el-divider>
      <pre class="payload-box">{{ prettyPayload(detail.payload) }}</pre>

      <template #footer>
        <el-button @click="detailOpen = false">关 闭</el-button>
        <el-button type="warning" @click="handleRetry(detail)" v-hasPermi="['wimo:task:retry']">重新生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="WimoTask">
import { listTask, getTask, delTask, retryTask, cancelTask } from '@/api/wimo/task'
import { listSchema } from '@/api/wimo/schema'

const { proxy } = getCurrentInstance()

const taskList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const multiple = ref(true)
const total = ref(0)
const detailOpen = ref(false)
const detail = ref({})
const typeMap = ref({
  productSet: '商品套图', productDetail: '商品详情页', hotReplica: '爆款图复刻',
  scene: '商品场景图', retouch: '商品图精修', poster: '电商海报', flatLay: '服饰平铺图',
  modelTryon: '模特试衣', anythingWear: '万物穿戴', swapModel: 'AI 换模特',
  productVideo: '商品展示短片', salesVideo: '带货视频', storeVisit: '探店视频'
})

const statusOptions = [
  { value: '0', label: '待生成' },
  { value: '1', label: '生成中' },
  { value: '2', label: '已完成' },
  { value: '3', label: '失败' },
  { value: '4', label: '已取消' }
]

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    title: undefined,
    taskType: undefined,
    status: undefined
  }
})
const { queryParams } = toRefs(data)

function statusName(v) { return (statusOptions.find(o => o.value === v) || {}).label || v || '-' }
function statusTag(v) { return { '0': 'info', '1': 'warning', '2': 'success', '3': 'danger', '4': 'info' }[v] || 'info' }
function formatCost(ms) {
  const n = Number(ms || 0)
  if (!n) return '-'
  return n < 1000 ? n + ' ms' : (n / 1000).toFixed(1) + ' s'
}
function splitImages(v) { return (v || '').split(',').filter(Boolean) }
function assetUrl(v) {
  const s = String(v || '')
  if (/^https?:\/\//.test(s)) return s
  if (s.startsWith('/profile/')) return import.meta.env.VITE_APP_BASE_API + s
  return import.meta.env.VITE_APP_BASE_API + '/images/preview/' + s + '.jpg'
}
function prettyPayload(v) {
  if (!v) return '-'
  try { return JSON.stringify(JSON.parse(v), null, 2) } catch { return v }
}
function diamondStateName(v) {
  return { '0': '已冻结（待结算）', '1': '已扣减', '2': '已退还', '9': '不计费（匿名演示）' }[v] || v || '-'
}

// 从后端模板配置动态补全功能名，保证新增模板后管理端也能正确显示
function loadTypeMap() {
  listSchema().then(res => {
    const rows = res.data || []
    rows.forEach(s => { if (s.taskKey) typeMap.value[s.taskKey] = s.nameZh || s.taskKey })
  }).catch(() => {})
}

function getList() {
  loading.value = true
  listTask(queryParams.value).then(res => {
    taskList.value = res.rows || []
    total.value = res.total || 0
    loading.value = false
  }).catch(() => { loading.value = false })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.taskId)
  multiple.value = !selection.length
}

function handleDetail(row) {
  getTask(row.taskId).then(res => {
    detail.value = res.data || {}
    detailOpen.value = true
  })
}

function handleRetry(row) {
  proxy.$modal.confirm('确认重新生成任务「' + row.taskId + '」？将清空已有结果并重新执行生成流程。').then(() => {
    return retryTask(row.taskId)
  }).then(() => {
    proxy.$modal.msgSuccess('已提交重新生成')
    detailOpen.value = false
    getList()
  }).catch(() => {})
}

function handleCancel(row) {
  proxy.$modal.confirm('确认取消任务「' + row.taskId + '」？').then(() => {
    return cancelTask(row.taskId)
  }).then(() => {
    proxy.$modal.msgSuccess('任务已取消')
    detailOpen.value = false
    getList()
  }).catch(() => {})
}

function handleDelete(row) {
  const taskIds = row && row.taskId ? [row.taskId] : ids.value
  if (!taskIds.length) return
  proxy.$modal.confirm('是否确认删除任务编号为「' + taskIds.join(', ') + '」的数据项？').then(() => {
    return delTask(taskIds.join(','))
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

loadTypeMap()
getList()
</script>

<style scoped>
.img-row { display: flex; flex-wrap: wrap; gap: 10px; }
.img-item { width: 96px; height: 96px; border-radius: 8px; background: #f5f5f5; }
.img-miss, .img-empty { color: #999; font-size: 12px; }
.payload-box { max-height: 220px; overflow: auto; background: #f7f8fa; border: 1px solid #e4e7ed; border-radius: 6px; padding: 12px; font-size: 12px; line-height: 1.6; margin: 0; }
</style>
