<template>
  <div class="app-container">
    <!-- 顶部：统计卡片 -->
    <el-row :gutter="14" class="stat-row">
      <el-col :xs="12" :sm="8" :md="4" v-for="c in cards" :key="c.label">
        <el-card shadow="never" class="stat-card">
          <div class="stat-card__label">{{ c.label }}</div>
          <div class="stat-card__value" :style="{ color: c.color }">{{ c.value }}</div>
          <div class="stat-card__sub">{{ c.sub }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 钻石消耗概览 -->
    <el-row :gutter="14" class="stat-row">
      <el-col :xs="12" :sm="8" :md="6" v-for="c in diamondCards" :key="c.label">
        <el-card shadow="never" class="stat-card">
          <div class="stat-card__label">{{ c.label }}</div>
          <div class="stat-card__value" :style="{ color: c.color }">{{ c.value }}</div>
          <div class="stat-card__sub">{{ c.sub }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 适配器状态 + 趋势天数 -->
    <el-card shadow="never" class="mb14">
      <div class="provider-bar">
        <div class="provider-bar__title">当前服务适配器</div>
        <el-tag type="success" effect="plain">生成器：{{ providers.generator || '-' }}</el-tag>
        <el-tag type="success" effect="plain">邮件：{{ providers.mail || '-' }}</el-tag>
        <el-tag type="success" effect="plain">存储：{{ providers.storage || '-' }}</el-tag>
        <el-tag type="warning" effect="plain">模型：{{ diamond.models ?? '-' }}</el-tag>
        <el-tag type="warning" effect="plain">智能体：{{ diamond.workflows ?? '-' }}</el-tag>
        <span class="provider-bar__hint">默认均为本地模拟，在 application.yml 配置 wimo.* 即可切换真实服务</span>
        <div class="provider-bar__right">
          <el-radio-group v-model="days" size="small" @change="loadData">
            <el-radio-button :value="7">近 7 天</el-radio-button>
            <el-radio-button :value="14">近 14 天</el-radio-button>
            <el-radio-button :value="30">近 30 天</el-radio-button>
          </el-radio-group>
          <el-button size="small" icon="Refresh" @click="loadData">刷新</el-button>
        </div>
      </div>
    </el-card>

    <!-- 图表区 -->
    <el-row :gutter="14" class="mb14">
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>任务状态分布</template>
          <div ref="statusChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>功能使用分布</template>
          <div ref="typeChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>任务创建趋势</template>
          <div ref="trendChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近任务 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-head"><span>最近任务</span><span class="card-head__sub">按创建时间倒序，最多 10 条</span></div>
      </template>
      <el-table v-loading="loading" :data="recent" size="small">
        <el-table-column label="ID" align="center" prop="taskId" width="70" />
        <el-table-column label="功能" align="center" width="120">
          <template #default="scope">{{ typeMap[scope.row.taskType] || scope.row.taskType }}</template>
        </el-table-column>
        <el-table-column label="标题" prop="title" show-overflow-tooltip min-width="180" />
        <el-table-column label="状态" align="center" width="95">
          <template #default="scope"><el-tag size="small" :type="statusTag(scope.row.status)">{{ statusName(scope.row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="结果" align="center" width="80">
          <template #default="scope">{{ scope.row.resultCount || 0 }} 个</template>
        </el-table-column>
        <el-table-column label="消耗钻石" align="center" width="95">
          <template #default="scope"><span class="cost-cell">{{ scope.row.costDiamonds || 0 }}</span></template>
        </el-table-column>
        <el-table-column label="耗时" align="center" width="90">
          <template #default="scope">{{ formatCost(scope.row.costMs) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="165" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup name="WimoDashboard">
import * as echarts from 'echarts'
import { getDashboard } from '@/api/wimo/dashboard'
import { listSchema } from '@/api/wimo/schema'

const loading = ref(false)
const days = ref(7)
const overview = ref({})
const providers = ref({})
const diamond = ref({})
const recent = ref([])
const typeMap = ref({})
const statusChartRef = ref(null)
const typeChartRef = ref(null)
const trendChartRef = ref(null)
let charts = []

const STATUS_NAME = { '0': '待生成', '1': '生成中', '2': '已完成', '3': '失败', '4': '已取消' }
const STATUS_COLOR = { '0': '#909399', '1': '#e6a23c', '2': '#67c23a', '3': '#f56c6c', '4': '#c0c4cc' }

const cards = computed(() => {
  const o = overview.value || {}
  const total = Number(o.total || 0)
  const done = Number(o.done || 0)
  return [
    { label: '任务总数', value: total, sub: '累计生成任务', color: '#409eff' },
    { label: '今日新增', value: Number(o.today || 0), sub: '今天创建的任务', color: '#67c23a' },
    { label: '已完成', value: done, sub: '成功率 ' + (total ? Math.round(done / total * 100) : 0) + '%', color: '#67c23a' },
    { label: '失败 / 生成中', value: Number(o.failed || 0) + ' / ' + Number(o.running || 0), sub: '需关注的任务', color: '#f56c6c' },
    { label: '注册会员', value: Number(o.users || 0), sub: '平均耗时 ' + (o.avgCost ? (o.avgCost / 1000).toFixed(1) + 's' : '-'), color: '#909399' },
    { label: '模板 / 邮件', value: Number(o.schemas || 0) + ' / ' + Number(o.mailLogs || 0), sub: '功能模板 / 验证码日志', color: '#909399' }
  ]
})

const diamondCards = computed(() => {
  const d = diamond.value || {}
  return [
    { label: '累计消耗钻石', value: Number(d.used || 0), sub: '用户实际生成扣减', color: '#d97706' },
    { label: '累计发放钻石', value: Number(d.total || 0), sub: '注册赠送 + 充值', color: '#16a34a' },
    { label: '冻结中钻石', value: Number(d.frozen || 0), sub: '生成中的任务预扣', color: '#0284c7' },
    { label: '流水记录', value: Number(d.logCount || 0), sub: '全部钻石变动次数', color: '#909399' }
  ]
})

function statusName(v) { return STATUS_NAME[v] || v || '-' }
function statusTag(v) { return { '0': 'info', '1': 'warning', '2': 'success', '3': 'danger', '4': 'info' }[v] || 'info' }
function formatCost(ms) { const n = Number(ms || 0); return !n ? '-' : (n < 1000 ? n + ' ms' : (n / 1000).toFixed(1) + ' s') }

function renderCharts(byType, trend) {
  charts.forEach(c => c.dispose())
  charts = []

  // 状态分布
  const sm = overview.value.statusMap || {}
  if (statusChartRef.value) {
    const c = echarts.init(statusChartRef.value)
    c.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
      series: [{
        type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        data: Object.keys(STATUS_NAME).map(k => ({
          name: STATUS_NAME[k], value: Number(sm[k] || 0),
          itemStyle: { color: STATUS_COLOR[k] }
        }))
      }]
    })
    charts.push(c)
  }

  // 功能分布
  if (typeChartRef.value) {
    const rows = [...(byType || [])].reverse()
    const c = echarts.init(typeChartRef.value)
    c.setOption({
      grid: { left: 8, right: 24, top: 10, bottom: 10, containLabel: true },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { type: 'dashed' } } },
      yAxis: {
        type: 'category',
        data: rows.map(r => typeMap.value[r.task_type] || r.task_type),
        axisLabel: { fontSize: 11 }
      },
      series: [{
        type: 'bar', data: rows.map(r => Number(r.cnt || 0)), barWidth: 12,
        itemStyle: { color: '#409eff', borderRadius: [0, 6, 6, 0] },
        label: { show: true, position: 'right', fontSize: 11 }
      }]
    })
    charts.push(c)
  }

  // 趋势
  if (trendChartRef.value) {
    const c = echarts.init(trendChartRef.value)
    c.setOption({
      grid: { left: 8, right: 18, top: 18, bottom: 8, containLabel: true },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', boundaryGap: false, data: (trend || []).map(r => String(r.d).slice(5)), axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { type: 'dashed' } } },
      series: [{
        type: 'line', smooth: true, data: (trend || []).map(r => Number(r.cnt || 0)),
        areaStyle: { color: 'rgba(64,158,255,.18)' }, itemStyle: { color: '#409eff' }, lineStyle: { width: 2 }
      }]
    })
    charts.push(c)
  }
}

function loadTypeMap() {
  listSchema().then(res => {
    const m = {}
    ;(res.data || []).forEach(s => { if (s.taskKey) m[s.taskKey] = s.nameZh || s.taskKey })
    typeMap.value = m
  }).catch(() => {})
}

async function loadData() {
  loading.value = true
  try {
    const res = await getDashboard(days.value)
    const d = res.data || {}
    overview.value = d.overview || {}
    providers.value = d.providers || {}
    diamond.value = d.diamond || {}
    recent.value = d.recent || []
    await nextTick()
    renderCharts(d.byType || [], d.trend || [])
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadTypeMap()
  await loadData()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach(c => c.dispose())
  charts = []
})
function onResize() { charts.forEach(c => c.resize()) }
</script>

<style scoped>
.mb14 { margin-bottom: 14px; }
.stat-row { margin-bottom: 14px; }
.stat-card { border-radius: 8px; }
.stat-card__label { font-size: 12px; color: #909399; }
.stat-card__value { font-size: 26px; font-weight: 600; line-height: 1.5; }
.stat-card__sub { font-size: 11px; color: #a8abb2; }
.provider-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.provider-bar__title { font-weight: 600; margin-right: 6px; }
.provider-bar__hint { font-size: 12px; color: #909399; }
.provider-bar__right { margin-left: auto; display: flex; gap: 8px; align-items: center; }
.chart-box { height: 260px; width: 100%; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.card-head__sub { font-size: 12px; color: #909399; font-weight: normal; }
.cost-cell { color: #d97706; font-weight: 700; font-variant-numeric: tabular-nums; }
</style>
