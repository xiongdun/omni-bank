<template>
  <div class="panel">
    <div class="panel-title">
      补充材料
      <el-button class="extra" type="primary" size="small" @click="createVisible = true">新建请求</el-button>
    </div>

    <div class="toolbar">
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px" @change="search">
        <el-option
          v-for="item in SUPPLEMENT_STATUS_OPTIONS"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <span class="spacer" />
      <span class="muted">共 {{ total }} 条</span>
    </div>

    <el-table v-loading="loading" :data="records" size="small" border>
      <el-table-column label="材料" min-width="170">
        <template #default="{ row }">
          <div class="data-name">{{ row.dataName || row.dataCode }}</div>
          <div class="muted">{{ row.dataCode }}</div>
        </template>
      </el-table-column>
      <el-table-column label="Agent" width="100">
        <template #default="{ row }">{{ agentLabel(row.agentCode) }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
      <el-table-column label="来源" width="90">
        <template #default="{ row }">{{ supplementSourceLabel(row.source) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="supplementStatusMeta(row.status).type" size="small" effect="light">
            {{ supplementStatusMeta(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="完成时间" width="150">
        <template #default="{ row }">{{ formatDateTime(row.completedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" align="center">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'PENDING'"
            link
            type="primary"
            size="small"
            @click="openComplete(row)"
          >
            标记完成
          </el-button>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        size="small"
        layout="prev, pager, next"
        :total="total"
        :page-size="query.size"
        :current-page="query.current"
        @current-change="changePage"
      />
    </div>

    <el-dialog v-model="createVisible" title="新建补充材料请求" width="520px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="96px">
        <el-form-item label="数据编码" prop="dataCode">
          <el-input v-model="createForm.dataCode" maxlength="64" placeholder="如：BANK_STATEMENT_2025" />
        </el-form-item>
        <el-form-item label="材料名称">
          <el-input v-model="createForm.dataName" maxlength="256" />
        </el-form-item>
        <el-form-item label="关联 Agent">
          <el-select v-model="createForm.agentCode" clearable placeholder="不选则按全量重分析">
            <el-option v-for="code in AGENT_CODES" :key="code" :label="agentLabel(code)" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="请求原因">
          <el-input v-model="createForm.reason" type="textarea" :rows="2" maxlength="1000" />
        </el-form-item>
        <el-form-item label="建议补充方式">
          <el-input v-model="createForm.suggestedAction" type="textarea" :rows="2" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="create">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="completeVisible" title="标记补充材料已完成" width="460px">
      <p class="muted">完成后将触发受影响 Agent 的增量重分析。</p>
      <el-input v-model="completeRemark" type="textarea" :rows="3" maxlength="1000" placeholder="完成说明（选填）" />
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" :loading="completing" @click="complete">确认完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { ddApi } from '../api/dd'
import type { Id, SupplementRequestVO } from '../types/dd'
import {
  AGENT_CODES,
  SUPPLEMENT_STATUS_OPTIONS,
  agentLabel,
  formatDateTime,
  supplementSourceLabel,
  supplementStatusMeta
} from '../utils/dd'

const props = defineProps<{
  taskId: Id
  tick: number
}>()

const emit = defineEmits<{
  (event: 'changed'): void
}>()

const loading = ref(false)
const records = ref<SupplementRequestVO[]>([])
const total = ref(0)
const query = reactive({ status: '', current: 1, size: 10 })

const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  dataCode: '',
  dataName: '',
  agentCode: '',
  reason: '',
  suggestedAction: ''
})
const createRules: FormRules = {
  dataCode: [{ required: true, message: '请输入数据编码', trigger: 'blur' }]
}

const completeVisible = ref(false)
const completing = ref(false)
const completeRemark = ref('')
const completingRequest = ref<SupplementRequestVO | null>(null)

async function load() {
  loading.value = true
  try {
    const page = await ddApi.pageSupplements(props.taskId, {
      current: query.current,
      size: query.size,
      status: query.status || undefined
    })
    records.value = page.records ?? []
    total.value = page.total ?? 0
  } catch {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.current = 1
  void load()
}

function changePage(page: number) {
  query.current = page
  void load()
}

async function create() {
  await createFormRef.value?.validate()
  creating.value = true
  try {
    await ddApi.createSupplement(props.taskId, {
      dataCode: createForm.dataCode,
      dataName: createForm.dataName || undefined,
      agentCode: createForm.agentCode || undefined,
      reason: createForm.reason || undefined,
      suggestedAction: createForm.suggestedAction || undefined
    })
    ElMessage.success('补充材料请求已创建')
    createVisible.value = false
    createFormRef.value?.resetFields()
    await load()
    emit('changed')
  } catch {
    // 错误提示由 http 层统一处理
  } finally {
    creating.value = false
  }
}

function openComplete(row: SupplementRequestVO) {
  completingRequest.value = row
  completeRemark.value = ''
  completeVisible.value = true
}

async function complete() {
  if (!completingRequest.value) {
    return
  }
  completing.value = true
  try {
    await ddApi.completeSupplement(props.taskId, completingRequest.value.requestId, {
      remark: completeRemark.value || undefined
    })
    ElMessage.success('已标记完成，正在触发增量重分析')
    completeVisible.value = false
    await load()
    emit('changed')
  } catch {
    // 错误提示由 http 层统一处理
  } finally {
    completing.value = false
  }
}

watch(
  () => props.taskId,
  () => {
    query.status = ''
    query.current = 1
    void load()
  },
  { immediate: true }
)

watch(() => props.tick, load)
</script>

<style scoped>
.data-name {
  font-weight: 500;
}
</style>
