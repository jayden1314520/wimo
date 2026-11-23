<template>
  <div class="app-container">
    <!-- 查询区 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="商品名称" prop="productName">
        <el-input v-model="queryParams.productName" placeholder="请输入商品名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="任务状态" clearable style="width: 160px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="平台" prop="platform">
        <el-select v-model="queryParams.platform" placeholder="投放平台" clearable style="width: 160px">
          <el-option label="淘宝" value="taobao" />
          <el-option label="京东" value="jd" />
          <el-option label="拼多多" value="pdd" />
          <el-option label="亚马逊" value="amazon" />
          <el-option label="Shopify" value="shopify" />
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
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['wimo:task:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="taskList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="任务ID" align="center" prop="taskId" width="80" />
      <el-table-column label="商品名称" align="center" prop="productName" show-overflow-tooltip />
      <el-table-column label="卖点与要求" align="center" prop="sellingPoints" show-overflow-tooltip min-width="180" />
      <el-table-column label="平台" align="center" prop="platform" width="90">
        <template #default="scope">{{ platformName(scope.row.platform) }}</template>
      </el-table-column>
      <el-table-column label="比例" align="center" prop="ratio" width="70" />
      <el-table-column label="清晰度" align="center" width="90">
        <template #default="scope">{{ dictLabel('resolution', scope.row.resolution) }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="scope">
          <el-tag :type="statusTag(scope.row.status)" disable-transitions>{{ statusName(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" align="center" width="120">
        <template #default="scope">
          <el-progress :percentage="Math.round((scope.row.progress || 0) / 8 * 100)" :stroke-width="8" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="170" />
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['wimo:task:query']">详情</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wimo:task:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 详情弹窗 -->
    <el-dialog title="任务详情" v-model="detailOpen" width="720px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="任务ID">{{ detail.taskId }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="商品名称">{{ detail.productName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="平台">{{ platformName(detail.platform) }}</el-descriptions-item>
        <el-descriptions-item label="画面比例">{{ detail.ratio }}</el-descriptions-item>
        <el-descriptions-item label="清晰度">{{ dictLabel('resolution', detail.resolution) }} / {{ dictLabel('quality', detail.quality) }}</el-descriptions-item>
        <el-descriptions-item label="模型">{{ detail.model }}</el-descriptions-item>
        <el-descriptions-item label="结果配置">{{ detail.resultMode === 'custom' ? '自定义张数' : '系统默认' }}</el-descriptions-item>
        <el-descriptions-item label="卖点与要求" :span="2">
          <div style="white-space: pre-wrap">{{ detail.sellingPoints }}</div>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMsg" label="失败原因" :span="2">
          <span style="color: #f56c6c">{{ detail.errorMsg }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">商品素材</el-divider>
      <div class="img-row">
        <el-image v-for="(img, i) in splitImages(detail.images)" :key="'m' + i" :src="profileUrl(img)" :preview-src-list="[profileUrl(img)]" fit="cover" class="img-item" preview-teleported>
          <template #error><div class="img-miss">素材</div></template>
        </el-image>
        <span v-if="!splitImages(detail.images).length" class="img-empty">未上传素材</span>
      </div>

      <el-divider content-position="left">生成结果（{{ splitImages(detail.results).length }}/8）</el-divider>
      <div class="img-row">
        <el-image v-for="(img, i) in splitImages(detail.results)" :key="'r' + i" :src="resultUrl(img)" :preview-src-list="[resultUrl(img)]" fit="cover" class="img-item" preview-teleported />
        <span v-if="!splitImages(detail.results).length" class="img-empty">{{ detail.status === '3' ? '生成失败' : '生成中…' }}</span>
      </div>
    </el-dialog>
  </div>
</template>

<script setup name="WimoTask">
import { listTask, getTask, delTask } from '@/api/wimo/task'

const { proxy } = getCurrentInstance()

const taskList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const multiple = ref(true)
const total = ref(0)
const detailOpen = ref(false)
const detail = ref({})

const statusOptions = [
  { value: '0', label: '待生成' },
  { value: '1', label: '生成中' },
  { value: '2', label: '已完成' },
  { value: '3', label: '失败' }
]

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    productName: undefined,
    status: undefined,
    platform: undefined
  }
})
const { queryParams } = toRefs(data)

const platformMap = { taobao: '淘宝', jd: '京东', pdd: '拼多多', amazon: '亚马逊', shopify: 'Shopify' }
const resolutionMap = { '2k': '2K', '4k': '4K' }
const qualityMap = { mid: '标准', high: '高清' }

function platformName(v) { return platformMap[v] || v || '-' }
function statusName(v) { return (statusOptions.find(o => o.value === v) || {}).label || v }
function statusTag(v) { return { '0': 'info', '1': 'warning', '2': 'success', '3': 'danger' }[v] || 'info' }
function dictLabel(map, v) { return ({ resolution: resolutionMap, quality: qualityMap }[map] || {})[v] || v || '-' }
function splitImages(v) { return (v || '').split(',').filter(Boolean) }
function profileUrl(v) { return import.meta.env.VITE_APP_BASE_API + v }
function resultUrl(v) {
  // 模拟结果标识 → 前端示例图；接入真实模型后返回图片 URL 直接展示
  if (/^https?:|^\/profile/.test(v)) return import.meta.env.VITE_APP_BASE_API + v
  return '/images/preview/' + v + '.jpg'
}

function getList() {
  loading.value = true
  listTask(queryParams.value).then(res => {
    taskList.value = res.rows
    total.value = res.total
    loading.value = false
  })
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

function handleDelete(row) {
  const taskIds = row.taskId ? [row.taskId] : ids.value
  proxy.$modal.confirm('是否确认删除任务编号为「' + taskIds.join(', ') + '」的数据项？').then(() => {
    return delTask(taskIds.join(','))
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

getList()
</script>

<style scoped>
.img-row { display: flex; flex-wrap: wrap; gap: 10px; }
.img-item { width: 96px; height: 96px; border-radius: 8px; background: #f5f5f5; }
.img-miss, .img-empty { color: #999; font-size: 12px; }
</style>
