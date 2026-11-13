<template>
  <div class="report-layout">
    <div class="panel report-main">
      <div class="toolbar">
        <el-select
          v-model="currentVersion"
          placeholder="选择报告版本"
          style="width: 260px"
          :disabled="versions.length === 0"
        >
          <el-option
            v-for="item in versions"
            :key="item.id"
            :label="`v${item.reportVersion} · ${reportStatusMeta(item.reportStatus).label} · ${formatDateTime(item.generatedAt)}`"
            :value="item.reportVersion as number"
          />
        </el-select>
        <template v-if="detail">
          <el-tag :type="reportStatusMeta(detail.reportStatus).type" effect="light">
            {{ reportStatusMeta(detail.reportStatus).label }}
          </el-tag>
          <el-tag :type="reportReviewStatusMeta(detail.reviewStatus).type" effect="plain">
            {{ reportReviewStatusMeta(detail.reviewStatus).label }}
          </el-tag>
          <span class="muted">证据 {{ detail.evidenceCount ?? 0 }} · 数据缺口 {{ detail.dataGapCount ?? 0 }} · 补充 {{ detail.supplementCount ?? 0 }}</span>
        </template>
        <span class="spacer" />
        <el-button size="small" :icon="Refresh" circle title="刷新" @click="reload" />
      </div>

      <template v-if="detail">
        <div class="report-title">
          {{ detail.title || '尽调报告' }}
          <span class="muted">v{{ detail.reportVersion }} · {{ detail.reportNo }}</span>
        </div>

        <div v-if="detail.chapters?.length" class="chapters">
          <section v-for="(chapter, index) in detail.chapters" :key="chapter.chapterCode || index" class="chapter">
            <div class="chapter-title">
              {{ chapter.chapterNo ?? index + 1 }}、{{ chapter.title || chapter.chapterCode }}
              <el-tag v-if="chapter.status" size="small" effect="plain">{{ chapter.status }}</el-tag>
            </div>
            <div class="chapter-content">{{ chapter.content || '（本章节暂无内容）' }}</div>
            <div v-if="chapter.dataGaps?.length" class="chapter-gaps">
              数据缺口：{{ chapter.dataGaps.join('；') }}
            </div>
            <div v-if="chapter.limitation" class="chapter-limitation">局限说明：{{ chapter.limitation }}</div>
            <div v-if="chapter.evidenceIds?.length" class="chapter-evidence">
              <span class="muted">引用证据：</span>
              <el-button
                v-for="evidenceId in chapter.evidenceIds"
                :key="evidenceId"
                link
                type="primary"
                size="small"
                @click="emit('open-evidence', evidenceId)"
              >
                {{ shortId(evidenceId) }}
              </el-button>
            </div>
          </section>
        </div>
        <el-empty v-else :image-size="60" description="报告正文为空" />
      </template>
      <el-empty v-else :image-size="60" description="尚无报告版本" />
    </div>

    <div class="report-side">
      <div class="panel">
        <div class="panel-title">人工复核</div>
        <el-form label-position="top" size="small" :disabled="!detail">
          <el-form-item label="复核动作">
            <el-select v-model="reviewForm.actionType">
              <el-option
                v-for="(label, value) in REPORT_ACTION_LABELS"
                :key="value"
                :label="label"
                :value="value"
              />
            </el-select>
          </el-form-item>
          <template v-if="reviewForm.actionType === 'AMEND'">
            <el-form-item label="修正章节">
              <el-select v-model="reviewForm.fieldKey" placeholder="选择章节">
                <el-option
                  v-for="chapter in chapterOptions"
                  :key="chapter.value"
                  :label="chapter.label"
                  :value="chapter.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="修正后内容">
              <el-input v-model="reviewForm.amendedValue" type="textarea" :rows="4" />
            </el-form-item>
          </template>
          <el-form-item v-if="reasonRequired" label="原因（必填）">
            <el-input v-model="reviewForm.reason" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="复核意见">
            <el-input v-model="reviewForm.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
        <el-button
          type="primary"
          size="small"
          :loading="submitting"
          :disabled="!detail"
          @click="submitReview"
        >
          提交复核动作
        </el-button>
      </div>

      <div class="panel">
        <div class="panel-title">复核留痕</div>
        <el-timeline v-if="reviews.length">
          <el-timeline-item
            v-for="review in reviews"
            :key="review.id"
            :timestamp="formatDateTime(review.createTime)"
            placement="top"
          >
            <div class="review-line">
              <strong>{{ REPORT_ACTION_LABELS[review.actionType ?? ''] || review.actionType }}</strong>
              <span class="muted">{{ review.reviewer || '-' }}</span>
              <span v-if="review.fromStatus || review.toStatus" class="muted">
                {{ review.fromStatus || '-' }} → {{ review.toStatus || '-' }}
              </span>
            </div>
            <div v-if="review.fieldKey" class="review-text">章节：{{ review.fieldKey }}</div>
            <div v-if="review.amendedValue" class="review-text">修正为：{{ review.amendedValue }}</div>
            <div v-if="review.reason" class="review-text">原因：{{ review.reason }}</div>
            <div v-if="review.remark" class="review-text">意见：{{ review.remark }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else :image-size="50" description="暂无复核记录" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { ddApi } from '../api/dd'
import type {
  Id,
  ReportDetailVO,
  ReportReviewAction,
  ReportReviewVO,
  ReportVersionVO
} from '../types/dd'
import {
  REPORT_ACTION_LABELS,
  REPORT_ACTION_REQUIRES_REASON,
  formatDateTime,
  reportReviewStatusMeta,
  reportStatusMeta,
  shortId
} from '../utils/dd'

const props = defineProps<{
  taskId: Id
  tick: number
}>()

const emit = defineEmits<{
  (event: 'open-evidence', evidenceId: Id): void
  (event: 'changed'): void
}>()

const versions = ref<ReportVersionVO[]>([])
const currentVersion = ref<number | null>(null)
const detail = ref<ReportDetailVO | null>(null)
const reviews = ref<ReportReviewVO[]>([])
const submitting = ref(false)

const reviewForm = reactive<{
  actionType: ReportReviewAction
  fieldKey: string
  amendedValue: string
  reason: string
  remark: string
}>({
  actionType: 'SUBMIT',
  fieldKey: '',
  amendedValue: '',
  reason: '',
  remark: ''
})

const reasonRequired = computed(() => REPORT_ACTION_REQUIRES_REASON.has(reviewForm.actionType))

const chapterOptions = computed(() =>
  (detail.value?.chapters ?? []).map((chapter, index) => ({
    value: chapter.chapterCode || String(chapter.chapterNo ?? index + 1),
    label: `${chapter.chapterNo ?? index + 1}、${chapter.title || chapter.chapterCode || '未命名章节'}`
  }))
)

async function loadVersions(selectLatest: boolean) {
  try {
    const page = await ddApi.pageReports(props.taskId, { current: 1, size: 50 })
    versions.value = [...(page.records ?? [])].sort(
      (a, b) => (b.reportVersion ?? 0) - (a.reportVersion ?? 0)
    )
    if (selectLatest || currentVersion.value === null) {
      currentVersion.value = versions.value[0]?.reportVersion ?? null
    } else if (!versions.value.some((item) => item.reportVersion === currentVersion.value)) {
      currentVersion.value = versions.value[0]?.reportVersion ?? null
    }
  } catch {
    versions.value = []
  }
}

async function loadDetail() {
  if (currentVersion.value === null) {
    detail.value = null
    reviews.value = []
    return
  }
  try {
    detail.value = await ddApi.reportDetail(props.taskId, currentVersion.value)
  } catch {
    detail.value = null
  }
}

async function loadReviews() {
  if (currentVersion.value === null) {
    reviews.value = []
    return
  }
  try {
    reviews.value = await ddApi.reportReviews(props.taskId, currentVersion.value)
  } catch {
    reviews.value = []
  }
}

function reload() {
  void loadVersions(false)
  void loadDetail()
  void loadReviews()
}

async function submitReview() {
  if (currentVersion.value === null) {
    return
  }
  if (reviewForm.actionType === 'AMEND' && (!reviewForm.fieldKey || !reviewForm.amendedValue)) {
    ElMessage.warning('修正章节需选择章节并填写修正后内容')
    return
  }
  if (reasonRequired.value && !reviewForm.reason.trim()) {
    ElMessage.warning('该复核动作必须填写原因')
    return
  }
  submitting.value = true
  try {
    detail.value = await ddApi.reviewReport(props.taskId, currentVersion.value, {
      actionType: reviewForm.actionType,
      fieldKey: reviewForm.actionType === 'AMEND' ? reviewForm.fieldKey : undefined,
      amendedValue: reviewForm.actionType === 'AMEND' ? reviewForm.amendedValue : undefined,
      reason: reviewForm.reason || undefined,
      remark: reviewForm.remark || undefined
    })
    ElMessage.success('复核动作已提交')
    reviewForm.reason = ''
    reviewForm.remark = ''
    reviewForm.amendedValue = ''
    await Promise.all([loadVersions(false), loadReviews()])
    emit('changed')
  } catch {
    // 业务错误提示由 http 层处理
  } finally {
    submitting.value = false
  }
}

watch(
  () => props.taskId,
  async () => {
    versions.value = []
    detail.value = null
    reviews.value = []
    currentVersion.value = null
    await loadVersions(true)
  },
  { immediate: true }
)

watch(currentVersion, () => {
  void loadDetail()
  void loadReviews()
})

watch(() => props.tick, () => {
  void loadVersions(false)
  void loadDetail()
})
</script>

<style scoped>
.report-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 12px;
  align-items: start;
}

.report-title {
  font-size: 14px;
  font-weight: 600;
  margin: 4px 0 12px;
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.chapters {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.chapter-title {
  font-weight: 600;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.chapter-content {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #303133;
}

.chapter-gaps {
  margin-top: 6px;
  color: #e6a23c;
  font-size: 12px;
}

.chapter-limitation {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.chapter-evidence {
  margin-top: 6px;
}

.report-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-line {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  font-size: 12px;
}

.review-text {
  font-size: 12px;
  color: #606266;
  margin-top: 2px;
}
</style>
