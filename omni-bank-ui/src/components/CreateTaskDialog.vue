<template>
  <el-dialog
    :model-value="modelValue"
    title="新建尽调任务"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-divider content-position="left">企业主体</el-divider>
      <el-form-item label="统一信用代码" prop="creditCode">
        <el-input v-model="form.creditCode" maxlength="32" placeholder="必填" />
      </el-form-item>
      <el-form-item label="企业名称">
        <el-input v-model="form.enterpriseName" maxlength="256" placeholder="主体首次入库时填写" />
      </el-form-item>
      <el-form-item label="客户编号">
        <el-input v-model="form.customerId" maxlength="64" placeholder="选填，行内客户号" />
      </el-form-item>

      <el-divider content-position="left">授信申请</el-divider>
      <el-form-item label="产品名称">
        <el-input v-model="form.productName" maxlength="128" placeholder="如：流动资金贷款" />
      </el-form-item>
      <el-form-item label="申请金额" prop="applyAmount">
        <el-input-number v-model="form.applyAmount" :min="0.01" :precision="2" :step="100" controls-position="right" />
      </el-form-item>
      <el-form-item label="申请期限" prop="applyTerm">
        <el-input-number v-model="form.applyTerm" :min="1" :step="1" controls-position="right" />
        <el-select v-model="form.termUnit" style="width: 100px; margin-left: 8px">
          <el-option label="月" value="MONTH" />
          <el-option label="天" value="DAY" />
          <el-option label="年" value="YEAR" />
        </el-select>
      </el-form-item>
      <el-form-item label="贷款用途">
        <el-input v-model="form.loanPurpose" maxlength="1000" placeholder="选填" />
      </el-form-item>
      <el-form-item label="担保方式">
        <el-input v-model="form.guaranteeType" maxlength="64" placeholder="如：抵押 / 保证" />
      </el-form-item>
      <el-form-item label="任务名称">
        <el-input v-model="form.taskName" maxlength="200" placeholder="缺省按企业名称生成" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useTaskStore } from '../stores/task'
import type { Id } from '../types/dd'

defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'created', taskId: Id): void
}>()

const store = useTaskStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  creditCode: '',
  enterpriseName: '',
  customerId: '',
  productName: '',
  applyAmount: 100,
  applyTerm: 12,
  termUnit: 'MONTH',
  loanPurpose: '',
  guaranteeType: '',
  taskName: ''
})

const rules: FormRules = {
  creditCode: [{ required: true, message: '请输入统一社会信用代码', trigger: 'blur' }],
  applyAmount: [{ required: true, message: '请输入申请金额', trigger: 'blur' }],
  applyTerm: [{ required: true, message: '请输入申请期限', trigger: 'blur' }]
}

async function submit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const created = await store.createTask({
      enterprise: {
        creditCode: form.creditCode,
        enterpriseName: form.enterpriseName || undefined,
        customerId: form.customerId || undefined
      },
      application: {
        productName: form.productName || undefined,
        applyAmount: form.applyAmount,
        applyTerm: form.applyTerm,
        termUnit: form.termUnit,
        loanPurpose: form.loanPurpose || undefined,
        guaranteeType: form.guaranteeType || undefined
      },
      taskName: form.taskName || undefined
    })
    emit('created', created.taskId)
    emit('update:modelValue', false)
    formRef.value?.resetFields()
  } catch {
    // 错误提示由 http 层统一处理
  } finally {
    submitting.value = false
  }
}
</script>
