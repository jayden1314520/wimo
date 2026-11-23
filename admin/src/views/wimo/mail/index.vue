<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon class="mb14"
      title="验证码邮件日志"
      description="记录每一次验证码发送（注册 / 登录 / 找回密码）。默认使用 mock 适配器：不真实发信，验证码直接写入日志并回显给前端；切换 wimo.mail.provider=http 或 smtp 后由真实服务发送。" />

    <el-card shadow="never">
      <!-- 查询区 -->
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="68px">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="queryParams.email" placeholder="请输入邮箱" clearable style="width: 220px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="场景" prop="scene">
          <el-select v-model="queryParams.scene" placeholder="全部场景" clearable style="width: 150px">
            <el-option v-for="(v, k) in SCENE_NAME" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" size="small">
        <el-table-column label="ID" align="center" prop="id" width="70" />
        <el-table-column label="邮箱" prop="email" min-width="200" show-overflow-tooltip />
        <el-table-column label="场景" align="center" width="110">
          <template #default="scope"><el-tag size="small" effect="plain">{{ SCENE_NAME[scope.row.scene] || scope.row.scene }}</el-tag></template>
        </el-table-column>
        <el-table-column label="验证码" align="center" width="110">
          <template #default="scope"><span class="code-cell">{{ scope.row.code || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="通道" align="center" prop="provider" width="100" />
        <el-table-column label="结果" align="center" width="90">
          <template #default="scope">
            <el-tag size="small" :type="String(scope.row.success) === '1' ? 'success' : 'danger'">
              {{ String(scope.row.success) === '1' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
        <el-table-column label="发送时间" align="center" prop="createTime" width="165" />
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup name="WimoMail">
import { listMail } from '@/api/wimo/mail'

const { proxy } = getCurrentInstance()

const rows = ref([])
const loading = ref(false)
const total = ref(0)
const SCENE_NAME = { register: '注册', login: '验证码登录', reset: '找回密码' }

const data = reactive({
  queryParams: { pageNum: 1, pageSize: 10, email: undefined, scene: undefined }
})
const { queryParams } = toRefs(data)

function getList() {
  loading.value = true
  listMail(queryParams.value).then(res => {
    const d = res.data || {}
    rows.value = d.rows || []
    total.value = Number(d.total || 0)
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

getList()
</script>

<style scoped>
.mb14 { margin-bottom: 14px; }
.code-cell { font-family: 'SFMono-Regular', Consolas, monospace; letter-spacing: 1px; font-weight: 600; color: #409eff; }
</style>
