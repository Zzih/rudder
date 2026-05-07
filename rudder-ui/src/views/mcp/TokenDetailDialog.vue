<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getToken, type TokenDetail } from '@/api/mcp'

const { t } = useI18n()

const props = defineProps<{ modelValue: boolean; tokenId: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const detail = ref<TokenDetail | null>(null)
const loading = ref(false)

watch(
  () => [props.modelValue, props.tokenId],
  async ([v, id]) => {
    if (!v || !id) {
      detail.value = null
      return
    }
    loading.value = true
    try {
      const { data } = await getToken(id as number)
      detail.value = data ?? null
    } finally {
      loading.value = false
    }
  },
)

const readGrants = computed(() => detail.value?.grants.filter((g) => g.rwClass === 'READ') ?? [])
const writeGrants = computed(() => detail.value?.grants.filter((g) => g.rwClass === 'WRITE') ?? [])
const pendingCount = computed(
  () => detail.value?.grants.filter((g) => g.status === 'PENDING_APPROVAL').length ?? 0,
)
</script>

<template>
  <el-dialog v-model="visible" :title="t('mcpPage.detailTitle')" width="640px" :close-on-click-modal="false">
    <div v-loading="loading">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="t('mcpPage.name')">{{ detail.token.name }}</el-descriptions-item>
          <el-descriptions-item :label="t('mcpPage.status')">
            <span class="status-pill" :data-status="detail.token.status">
              <span class="dot" />{{ detail.token.status }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('mcpPage.prefix')"><code>{{ detail.token.tokenPrefix }}…</code></el-descriptions-item>
          <el-descriptions-item :label="t('mcpPage.workspaceId')">{{ detail.token.workspaceId }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.createdAt')">{{ detail.token.createdAt }}</el-descriptions-item>
          <el-descriptions-item :label="t('mcpPage.expiresAt')">{{ detail.token.expiresAt ?? t('mcpPage.permanent') }}</el-descriptions-item>
          <el-descriptions-item :label="t('mcpPage.lastUsed')" :span="2">
            {{ detail.token.lastUsedAt ?? t('mcpPage.neverUsed') }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.token.description" :label="t('mcpPage.description')" :span="2">
            {{ detail.token.description }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="pendingCount > 0"
          type="warning"
          show-icon
          :closable="false"
          class="mt-md"
          :title="t('mcpPage.pendingApprovalAlert', { count: pendingCount })"
        />

        <h4 class="section-title">{{ t('mcpPage.readCapabilities') }}</h4>
        <div class="grant-grid">
          <div v-for="g in readGrants" :key="g.capability" class="grant-card">
            <code class="cap-id">{{ g.capability }}</code>
            <span class="status-pill" :data-status="g.status">
              <span class="dot" />{{ g.status }}
            </span>
          </div>
        </div>

        <h4 class="section-title">{{ t('mcpPage.writeCapabilities') }}</h4>
        <div v-if="writeGrants.length" class="grant-grid">
          <div v-for="g in writeGrants" :key="g.capability" class="grant-card">
            <code class="cap-id">{{ g.capability }}</code>
            <span class="status-pill" :data-status="g.status">
              <span class="dot" />{{ g.status }}
            </span>
          </div>
        </div>
        <div v-else class="empty-write">{{ t('mcpPage.writeEmpty') }}</div>
      </template>
    </div>

    <template #footer>
      <el-button @click="visible = false">{{ t('mcpPage.close') }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.section-title {
  margin: var(--r-space-5) 0 var(--r-space-3);
  font-size: var(--r-font-md);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
}
.mt-md { margin-top: var(--r-space-3); }

.grant-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--r-space-2);
}

.grant-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--r-space-3);
  padding: 10px var(--r-space-3);
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  transition: border-color 0.15s, background 0.15s;

  &:hover {
    border-color: var(--r-border);
    background: var(--r-bg-panel);
  }

  .cap-id {
    flex: 1;
    font-family: var(--r-font-mono);
    font-size: var(--r-font-sm);
    color: var(--r-text-primary);
    background: none;
    padding: 0;
  }
}

.empty-write {
  padding: var(--r-space-4);
  text-align: center;
  color: var(--r-text-muted);
  font-size: var(--r-font-sm);
  font-style: italic;
  border: 1px dashed var(--r-border);
  border-radius: var(--r-radius-md);
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  letter-spacing: 0.02em;
  flex-shrink: 0;

  .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
  }

  &[data-status="ACTIVE"] {
    color: var(--r-success);
    background: var(--r-success-bg);
    border: 1px solid var(--r-success-border);
  }
  &[data-status="PENDING_APPROVAL"] {
    color: var(--r-warning);
    background: var(--r-warning-bg);
    border: 1px solid var(--r-warning-border);
  }
  &[data-status="REJECTED"] {
    color: var(--r-danger);
    background: var(--r-danger-bg);
    border: 1px solid var(--r-danger-border);
  }
  &[data-status="REVOKED"],
  &[data-status="EXPIRED"],
  &[data-status="WITHDRAWN"] {
    color: var(--r-text-tertiary);
    background: var(--r-bg-panel);
    border: 1px solid var(--r-border);
  }
}
</style>
