<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon class="mb14"
      title="钻石账户管理"
      description="用户注册赠送 100 钻石。生成任务采用两阶段结算：提交时预扣（freeze），成功扣减（consume），失败/取消自动退还（refund）。所有余额变动均为原子操作并留痕，可在此审计与人工调整。" />

    <el-row :gutter="14" class="mb14">
      <el-col :span="6" v-for="c in cards" :key="c.label">
        <el-card shadow="never" class="stat-card">
          <div class="stat-card__label">{{ c.label }}</div>
          <div class="stat-card__value" :style="{ color: c.color }">{{ c.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="mb14">
      <template #header><div class="card-head"><span>会员钱包</span><el-button size="small" icon="Refresh" @click="loadAll" /></div></template>
      <el-table :data="users" size="small" max-height="260">
        <el-table-column label="ID" align="center" prop="userId" width="70" />
        <el-table-column label="邮箱" prop="email" min-width="200" />
        <el-table-column label="可用余额" align="center" width="110"><template #default="s"><b class="num">{{ s.row.diamondBalance ?? 0 }}</b></template></el-table-column>
        <el-table-column label="冻结中" align="center" width="100"><template #default="s"><span class="frozen">{{ s.row.diamondFrozen ?? 0 }}</span></template></el-table-column>
        <el-table-column label="累计获得" align="center" prop="diamondTotal" width="100" />
        <el-table-column label="累计消耗" align="center" prop="diamondUsed" width="100" />
        <el-table-column label="状态" align="center" width="80"><template #default="s"><el-tag size="small" :type="s.row.status === '0' ? 'success' : 'danger'">{{ s.row.status === '0' ? '正常' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" align="center" width="90">
          <template #default="s"><el-button link type="primary" icon="Edit" @click="openAdjust(s.row)" v-hasPermi="['wimo:diamond:edit']">调整</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>钻石流水</span>
          <div>
            <el-select v-model="query.changeType" placeholder="全部类型" size="small" clearable style="width: 130px; margin-right: 8px" @change="handleFilter">
              <el-option v-for="(v, k) in TYPE_NAME" :key="k" :label="v" :value="k" />
            </el-select>
            <el-input v-model="query.userId" placeholder="会员ID" size="small" clearable style="width: 110px; margin-right: 8px" @keyup.enter="handleFilter" />
            <el-button size="small" icon="Search" @click="handleFilter" />
          </div>
        </div>
      </template>
      <el-table v-loading="logLoading" :data="logs" size="small">
        <el-table-column label="ID" align="center" prop="id" width="80" />
        <el-table-column label="会员ID" align="center" prop="userId" width="90" />
        <el-table-column label="类型" align="center" width="105">
          <template #default="s"><el-tag size="small" :type="TYPE_TAG[s.row.changeType] || 'info'">{{ TYPE_NAME[s.row.changeType] || s.row.changeType }}</el-tag></template>
        </el-table-column>
        <el-table-column label="变动" align="center" width="100">
          <template #default="s"><span :class="s.row.amount >= 0 ? 'plus' : 'minus'">{{ s.row.amount >= 0 ? '+' : '' }}{{ s.row.amount }}</span></template>
        </el-table-column>
        <el-table-column label="余额(前→后)" align="center" width="130">
          <template #default="s"><span class="mono">{{ s.row.balanceBefore }} → {{ s.row.balanceAfter }}</span></template>
        </el-table-column>
        <el-table-column label="冻结(前→后)" align="center" width="130">
          <template #default="s"><span class="mono">{{ s.row.frozenBefore }} → {{ s.row.frozenAfter }}</span></template>
        </el-table-column>
        <el-table-column label="关联任务" align="center" prop="taskId" width="95" />
        <el-table-column label="智能体" prop="workflowKey" width="120" />
        <el-table-column label="备注" prop="remark" min-width="150" />
        <el-table-column label="时间" align="center" prop="createTime" width="165" />
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="getLogs" />
    </el-card>

    <el-dialog title="调整钻石余额" v-model="adjustOpen" width="460px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="会员"><el-input :model-value="adjustForm.email" disabled /></el-form-item>
        <el-form-item label="当前余额"><el-input :model-value="String(adjustForm.balance)" disabled /></el-form-item>
        <el-form-item label="变动数量">
          <el-input-number v-model="adjustForm.delta" :min="-999999" :max="999999" style="width: 100%" />
          <div class="tip">正数赠送 / 充值，负数扣减；不允许扣成负数</div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="adjustForm.remark" placeholder="如：活动赠送 50 钻石" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustOpen = false">取 消</el-button>
        <el-button type="primary" @click="submitAdjust">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="WimoDiamond">
import { diamondOverview, diamondUsers, listDiamondLog, adjustDiamond } from '@/api/wimo/diamond'

const { proxy } = getCurrentInstance()
const TYPE_NAME = { gift: '注册赠送', recharge: '充值', freeze: '生成预扣', consume: '生成扣减', refund: '失败退还', adjust: '人工调整' }
const TYPE_TAG = { gift: 'success', recharge: 'success', freeze: 'info', consume: 'danger', refund: 'warning', adjust: 'primary' }

const overview = ref({})
const users = ref([])
const logs = ref([])
const total = ref(0)
const logLoading = ref(false)
const query = ref({ pageNum: 1, pageSize: 10, userId: '', changeType: '' })
const adjustOpen = ref(false)
const adjustForm = ref({})

const cards = computed(() => [
  { label: '流通中钻石（可用余额）', value: overview.value.balance ?? 0, color: '#b45309' },
  { label: '冻结中钻石（生成中）', value: overview.value.frozen ?? 0, color: '#0284c7' },
  { label: '累计消耗钻石', value: overview.value.used ?? 0, color: '#dc2626' },
  { label: '累计发放钻石', value: overview.value.total ?? 0, color: '#16a34a' }
])

function loadOverview() {
  diamondOverview().then(res => { overview.value = res.data || {} }).catch(() => {})
}
function loadUsers() {
  diamondUsers().then(res => { users.value = res.data || [] }).catch(() => {})
}
function getLogs() {
  logLoading.value = true
  const p = { ...query.value }
  if (p.userId === '') p.userId = undefined
  if (p.changeType === '') p.changeType = undefined
  listDiamondLog(p).then(res => {
    const d = res.data || {}
    logs.value = d.list || []
    total.value = d.total || 0
    logLoading.value = false
  }).catch(() => { logLoading.value = false })
}
function handleFilter() { query.value.pageNum = 1; getLogs() }
function loadAll() { loadOverview(); loadUsers(); getLogs() }

function openAdjust(row) {
  adjustForm.value = { userId: row.userId, email: row.email, balance: row.diamondBalance ?? 0, delta: 10, remark: '' }
  adjustOpen.value = true
}
function submitAdjust() {
  if (!adjustForm.value.delta) { proxy.$modal.msgError('变动数量不能为 0'); return }
  adjustDiamond({ userId: adjustForm.value.userId, delta: adjustForm.value.delta, remark: adjustForm.value.remark })
    .then(() => { proxy.$modal.msgSuccess('调整成功'); adjustOpen.value = false; loadAll() })
}

onMounted(loadAll)
</script>

<style scoped>
.card-head { display: flex; align-items: center; justify-content: space-between; }
.mb14 { margin-bottom: 14px; }
.stat-card__label { font-size: 12px; color: #71717a; }
.stat-card__value { font-size: 26px; font-weight: 800; margin-top: 6px; font-variant-numeric: tabular-nums; }
.num { color: #b45309; font-size: 14px; }
.frozen { color: #0284c7; }
.plus { color: #16a34a; font-weight: 700; }
.minus { color: #dc2626; font-weight: 700; }
.mono { font-size: 12px; font-variant-numeric: tabular-nums; color: #52525b; }
.tip { font-size: 11px; color: #a1a1aa; line-height: 1.5; }
</style>
