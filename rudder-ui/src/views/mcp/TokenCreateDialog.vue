<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  CopyDocument,
  Check,
  View,
  Hide,
  Reading,
  WarningFilled,
  Plus,
} from '@element-plus/icons-vue'
import {
  createToken,
  listAvailableScopes,
  type CapabilityItem,
} from '@/api/mcp'
import { listWorkspaces } from '@/api/workspace'

interface WorkspaceItem {
  id: number
  name: string
}

const { t } = useI18n()

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'created': []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

type Step = 'basic' | 'scopes' | 'result'
const step = ref<Step>('basic')

const form = ref({
  name: '',
  description: '',
  workspaceId: null as number | null,
  expiresInDays: 90,
  capabilities: [] as string[],
})

const workspaces = ref<WorkspaceItem[]>([])
const availableCaps = ref<CapabilityItem[]>([])
const currentRole = ref<string>('')
const submitting = ref(false)
const plainToken = ref<string>('')
const ackSaved = ref(false)
const tokenRevealed = ref(false)

const expiryOptions = [
  { value: 7,   label: '7d' },
  { value: 30,  label: '30d' },
  { value: 90,  label: '90d' },
  { value: 180, label: '180d' },
  { value: 365, label: '1y' },
]

watch(visible, (v) => {
  if (v) {
    reset()
    loadWorkspaces()
  }
})

watch(() => form.value.workspaceId, (wsId) => {
  if (wsId) loadScopes(wsId)
})

function reset() {
  step.value = 'basic'
  form.value = { name: '', description: '', workspaceId: null, expiresInDays: 90, capabilities: [] }
  plainToken.value = ''
  ackSaved.value = false
  tokenRevealed.value = false
  availableCaps.value = []
  currentRole.value = ''
}

async function loadWorkspaces() {
  try {
    const res = await listWorkspaces({ pageNum: 1, pageSize: 1000 }) as { data?: WorkspaceItem[] }
    workspaces.value = res.data ?? []
  } catch { /* ignore */ }
}

async function loadScopes(wsId: number) {
  try {
    const { data } = await listAvailableScopes(wsId)
    availableCaps.value = data?.capabilities ?? []
    currentRole.value = data?.role ?? ''
    form.value.capabilities = availableCaps.value
      .filter(c => c.rwClass === 'READ')
      .map(c => c.id)
  } catch { /* ignore */ }
}

const readCaps  = computed(() => availableCaps.value.filter(c => c.rwClass === 'READ'))
const writeCaps = computed(() => availableCaps.value.filter(c => c.rwClass === 'WRITE'))
const writeSelected = computed(() =>
  form.value.capabilities.some(id => writeCaps.value.some(c => c.id === id)))

const readSelectedCount = computed(() =>
  readCaps.value.filter(c => form.value.capabilities.includes(c.id)).length)
const writeSelectedCount = computed(() =>
  writeCaps.value.filter(c => form.value.capabilities.includes(c.id)).length)

const stepIndex = computed(() => step.value === 'basic' ? 0 : step.value === 'scopes' ? 1 : 2)

function toggleAllRead(checked: boolean) {
  const ids = readCaps.value.map(c => c.id)
  if (checked) {
    form.value.capabilities = Array.from(new Set([...form.value.capabilities, ...ids]))
  } else {
    form.value.capabilities = form.value.capabilities.filter(id => !ids.includes(id))
  }
}

function nextToScopes() {
  if (!form.value.name) return ElMessage.warning(t('mcpPage.nameRequired'))
  if (!form.value.workspaceId) return ElMessage.warning(t('mcpPage.workspaceRequired'))
  step.value = 'scopes'
}

async function submit() {
  if (form.value.capabilities.length === 0) {
    ElMessage.warning(t('mcpPage.capabilityRequired'))
    return
  }
  submitting.value = true
  try {
    const { data } = await createToken({
      name: form.value.name,
      description: form.value.description,
      workspaceId: form.value.workspaceId!,
      expiresInDays: form.value.expiresInDays,
      capabilities: form.value.capabilities,
    })
    plainToken.value = data?.plainToken ?? ''
    step.value = 'result'
  } catch { /* ignore */ } finally {
    submitting.value = false
  }
}

async function copyToken() {
  await navigator.clipboard.writeText(plainToken.value)
  ElMessage.success(t('mcpPage.copied'))
}

function close() {
  if (step.value === 'result' && !ackSaved.value) {
    ElMessage.warning(t('mcpPage.ackBeforeClose'))
    return
  }
  visible.value = false
  if (step.value === 'result') emit('created')
}

const maskedToken = computed(() => {
  if (!plainToken.value) return ''
  if (tokenRevealed.value) return plainToken.value
  const head = plainToken.value.slice(0, 8)
  const tail = plainToken.value.slice(-4)
  return `${head}${'•'.repeat(Math.max(plainToken.value.length - 12, 8))}${tail}`
})
</script>

<template>
  <el-dialog
    v-model="visible"
    width="640px"
    :close-on-click-modal="false"
    :show-close="false"
    :before-close="(_done) => close()"
    class="mcp-create-dialog"
    @submit.prevent
  >
    <!-- Custom header -->
    <template #header>
      <div class="dlg-header">
        <div class="dlg-header__icon">
          <el-icon><Plus /></el-icon>
        </div>
        <div class="dlg-header__title">
          <h3>{{ t('mcpPage.create') }}</h3>
          <p>
            <span v-if="step === 'basic'">{{ t('mcpPage.createStep1') }}</span>
            <span v-else-if="step === 'scopes'">{{ t('mcpPage.createStep2') }}</span>
            <span v-else>{{ t('mcpPage.createDone') }}</span>
          </p>
        </div>
      </div>

      <!-- Custom step bar -->
      <div class="step-bar">
        <div class="step-bar__item" :class="{ 'is-active': stepIndex >= 0, 'is-done': stepIndex > 0 }">
          <span class="step-bar__circle">
            <el-icon v-if="stepIndex > 0"><Check /></el-icon>
            <span v-else>1</span>
          </span>
          <span class="step-bar__label">{{ t('mcpPage.createStep1') }}</span>
        </div>
        <div class="step-bar__line" :class="{ 'is-active': stepIndex >= 1 }" />
        <div class="step-bar__item" :class="{ 'is-active': stepIndex >= 1, 'is-done': stepIndex > 1 }">
          <span class="step-bar__circle">
            <el-icon v-if="stepIndex > 1"><Check /></el-icon>
            <span v-else>2</span>
          </span>
          <span class="step-bar__label">{{ t('mcpPage.createStep2') }}</span>
        </div>
        <div class="step-bar__line" :class="{ 'is-active': stepIndex >= 2 }" />
        <div class="step-bar__item" :class="{ 'is-active': stepIndex >= 2, 'is-done': stepIndex >= 2 }">
          <span class="step-bar__circle">
            <el-icon v-if="stepIndex >= 2"><Check /></el-icon>
            <span v-else>3</span>
          </span>
          <span class="step-bar__label">{{ t('mcpPage.createDone') }}</span>
        </div>
      </div>
    </template>

    <!-- ========== Step 1 ========== -->
    <el-form
      v-if="step === 'basic'"
      class="step-pane"
      label-position="top"
      @submit.prevent
    >
      <el-form-item required>
        <template #label>
          <span class="form-label">{{ t('mcpPage.name') }}</span>
        </template>
        <el-input
          v-model="form.name"
          :placeholder="t('mcpPage.namePlaceholder')"
          maxlength="64"
          show-word-limit
        />
      </el-form-item>

      <el-form-item>
        <template #label>
          <span class="form-label">{{ t('mcpPage.description') }}</span>
        </template>
        <el-input
          v-model="form.description"
          :placeholder="t('mcpPage.descPlaceholder')"
          type="textarea"
          :rows="2"
          maxlength="512"
          resize="none"
        />
      </el-form-item>

      <el-form-item required>
        <template #label>
          <span class="form-label">{{ t('mcpPage.workspace') }}</span>
        </template>
        <el-select
          v-model="form.workspaceId"
          :placeholder="t('mcpPage.selectWorkspace')"
          style="width: 100%"
        >
          <el-option v-for="ws in workspaces" :key="ws.id" :label="ws.name" :value="ws.id" />
        </el-select>
        <div class="form-hint">{{ t('mcpPage.workspaceHint') }}</div>
      </el-form-item>

      <el-form-item required>
        <template #label>
          <span class="form-label">{{ t('mcpPage.expiresIn') }}</span>
        </template>
        <div class="seg-group">
          <button
            v-for="opt in expiryOptions"
            :key="opt.value"
            type="button"
            class="seg"
            :class="{ 'is-active': form.expiresInDays === opt.value }"
            @click="form.expiresInDays = opt.value"
          >
            {{ opt.label }}
          </button>
        </div>
      </el-form-item>
    </el-form>

    <!-- ========== Step 2 ========== -->
    <div v-else-if="step === 'scopes'" class="step-pane">
      <div class="role-banner">
        <el-icon class="role-banner__icon"><Reading /></el-icon>
        <div class="role-banner__text">
          <span class="role-banner__label">{{ t('mcpPage.currentRole') }}</span>
          <strong>{{ currentRole || '—' }}</strong>
        </div>
        <span class="role-banner__hint">{{ t('mcpPage.capabilityRoleHint') }}</span>
      </div>

      <!-- Read capabilities -->
      <section class="cap-group">
        <header class="cap-group__head">
          <span class="cap-group__title">
            <span class="cap-group__dot" data-tone="read" />
            {{ t('mcpPage.readSection') }}
          </span>
          <span class="cap-group__count">{{ readSelectedCount }}/{{ readCaps.length }}</span>
          <button
            v-if="readCaps.length > 1"
            type="button"
            class="cap-group__toggle"
            @click="toggleAllRead(readSelectedCount !== readCaps.length)"
          >
            {{ readSelectedCount === readCaps.length ? t('mcpPage.deselectAll') : t('mcpPage.selectAll') }}
          </button>
        </header>
        <el-checkbox-group v-model="form.capabilities" class="cap-list">
          <el-checkbox
            v-for="c in readCaps"
            :key="c.id"
            :value="c.id"
            class="cap-item"
          >
            <div class="cap-item__body">
              <code class="cap-item__id">{{ c.id }}</code>
              <span class="cap-item__desc">{{ c.description }}</span>
            </div>
          </el-checkbox>
        </el-checkbox-group>
      </section>

      <!-- Write capabilities -->
      <section v-if="writeCaps.length > 0" class="cap-group cap-group--write">
        <header class="cap-group__head">
          <span class="cap-group__title">
            <span class="cap-group__dot" data-tone="write" />
            {{ t('mcpPage.writeSection') }}
          </span>
          <span class="cap-group__count">{{ writeSelectedCount }}/{{ writeCaps.length }}</span>
        </header>
        <el-checkbox-group v-model="form.capabilities" class="cap-list">
          <el-checkbox
            v-for="c in writeCaps"
            :key="c.id"
            :value="c.id"
            class="cap-item cap-item--write"
          >
            <div class="cap-item__body">
              <code class="cap-item__id">{{ c.id }}</code>
              <span class="cap-item__desc">{{ c.description }}</span>
            </div>
          </el-checkbox>
        </el-checkbox-group>
      </section>

      <div v-if="writeSelected" class="warn-banner">
        <el-icon class="warn-banner__icon"><WarningFilled /></el-icon>
        <span>{{ t('mcpPage.writeAlert') }}</span>
      </div>
    </div>

    <!-- ========== Step 3 ========== -->
    <div v-else class="step-pane">
      <div class="success-mark">
        <span class="success-mark__circle">
          <el-icon><Check /></el-icon>
        </span>
        <h4>{{ t('mcpPage.createSuccess') }}</h4>
        <p v-html="t('mcpPage.saveHint')" />
      </div>

      <div class="token-vault">
        <div class="token-vault__head">
          <span class="token-vault__label">access token</span>
          <button
            type="button"
            class="token-vault__eye"
            :title="tokenRevealed ? 'hide' : 'reveal'"
            @click="tokenRevealed = !tokenRevealed"
          >
            <el-icon><component :is="tokenRevealed ? Hide : View" /></el-icon>
          </button>
        </div>
        <code class="token-vault__value">{{ maskedToken }}</code>
        <button type="button" class="token-vault__copy" @click="copyToken">
          <el-icon><CopyDocument /></el-icon>
          <span>{{ t('mcpPage.copy') }}</span>
        </button>
      </div>

      <label class="ack">
        <el-checkbox v-model="ackSaved" />
        <span>{{ t('mcpPage.ack') }}</span>
      </label>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <template v-if="step === 'basic'">
          <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" @click="nextToScopes">{{ t('mcpPage.next') }}</el-button>
        </template>
        <template v-else-if="step === 'scopes'">
          <el-button @click="step = 'basic'">{{ t('mcpPage.prev') }}</el-button>
          <el-button type="primary" :loading="submitting" @click="submit">{{ t('mcpPage.create') }}</el-button>
        </template>
        <template v-else>
          <el-button type="primary" :disabled="!ackSaved" @click="close">{{ t('mcpPage.close') }}</el-button>
        </template>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
/* ========== HEADER ========== */
.dlg-header {
  display: flex;
  align-items: center;
  gap: var(--r-space-3);

  &__icon {
    width: 36px;
    height: 36px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-radius-lg);
    background: var(--r-accent-bg);
    color: var(--r-accent);
    border: 1px solid var(--r-accent-border);

    .el-icon { font-size: 18px; }
  }

  &__title {
    h3 {
      margin: 0;
      font-size: var(--r-font-lg);
      font-weight: var(--r-weight-bold);
      color: var(--r-text-primary);
      letter-spacing: -0.02em;
      line-height: 1.2;
    }
    p {
      margin: 3px 0 0;
      font-size: var(--r-font-xs);
      color: var(--r-text-muted);
      font-family: var(--r-font-mono);
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }
  }
}

/* ========== STEP BAR ========== */
.step-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: var(--r-space-4);
  padding: var(--r-space-3) var(--r-space-4);
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);

  &__item {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;

    .step-bar__circle {
      width: 22px;
      height: 22px;
      flex-shrink: 0;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
      background: var(--r-bg-card);
      border: 1px solid var(--r-border);
      font-size: 11px;
      font-weight: var(--r-weight-bold);
      color: var(--r-text-muted);
      font-family: var(--r-font-mono);

      .el-icon { font-size: 12px; }
    }

    .step-bar__label {
      font-size: var(--r-font-xs);
      color: var(--r-text-muted);
      font-weight: var(--r-weight-medium);
      white-space: nowrap;
    }

    &.is-active {
      .step-bar__circle {
        background: var(--r-accent);
        border-color: var(--r-accent);
        color: #fff;
        box-shadow: 0 0 0 3px var(--r-accent-bg);
      }
      .step-bar__label {
        color: var(--r-text-primary);
        font-weight: var(--r-weight-semibold);
      }
    }

    &.is-done {
      .step-bar__circle {
        background: var(--r-success);
        border-color: var(--r-success);
        color: #fff;
        box-shadow: none;
      }
    }
  }

  &__line {
    flex: 1;
    height: 1px;
    background: var(--r-border);
    transition: background 0.2s;

    &.is-active {
      background: var(--r-accent);
    }
  }
}

/* ========== STEP PANE ========== */
.step-pane {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-3);
  padding-top: var(--r-space-4);
}

/* ========== FORM ========== */
.form-label {
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  letter-spacing: -0.005em;
}

.form-hint {
  margin-top: 4px;
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  line-height: var(--r-leading-snug);
}

.step-pane :deep(.el-form-item) {
  margin-bottom: var(--r-space-3);
}

.step-pane :deep(.el-form-item__label) {
  padding-bottom: 6px;
  line-height: 1.4;
}

/* ========== SEGMENTED GROUP ========== */
.seg-group {
  display: inline-flex;
  align-items: stretch;
  padding: 3px;
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  gap: 2px;
}

.seg {
  all: unset;
  cursor: pointer;
  padding: 6px 14px;
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-tertiary);
  border-radius: 5px;
  transition: background 0.15s, color 0.15s, box-shadow 0.15s;

  &:hover:not(.is-active) {
    color: var(--r-text-primary);
  }

  &.is-active {
    background: var(--r-bg-card);
    color: var(--r-accent);
    box-shadow:
      0 1px 2px rgb(0 0 0 / 0.06),
      0 0 0 1px var(--r-border);
  }
}

/* ========== ROLE BANNER ========== */
.role-banner {
  display: flex;
  align-items: center;
  gap: var(--r-space-3);
  padding: 10px 14px;
  background: var(--r-accent-bg);
  border: 1px solid var(--r-accent-border);
  border-radius: var(--r-radius-md);

  &__icon {
    color: var(--r-accent);
    font-size: 16px;
    flex-shrink: 0;
  }

  &__text {
    display: inline-flex;
    align-items: baseline;
    gap: 6px;
    flex-shrink: 0;
  }

  &__label {
    font-family: var(--r-font-mono);
    font-size: 10px;
    text-transform: uppercase;
    letter-spacing: 0.1em;
    color: var(--r-text-muted);
  }

  strong {
    color: var(--r-accent-hover);
    font-weight: var(--r-weight-bold);
    font-size: var(--r-font-sm);
  }

  &__hint {
    font-size: var(--r-font-xs);
    color: var(--r-text-tertiary);
    line-height: var(--r-leading-snug);
  }
}

/* ========== CAPABILITY GROUP ========== */
.cap-group {
  display: flex;
  flex-direction: column;
  gap: 6px;

  &__head {
    display: flex;
    align-items: center;
    gap: var(--r-space-2);
    padding: 0 4px;
  }

  &__title {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: var(--r-font-sm);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-primary);
  }

  &__dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;

    &[data-tone="read"] {
      background: var(--r-accent);
      box-shadow: 0 0 0 3px var(--r-accent-bg);
    }
    &[data-tone="write"] {
      background: var(--r-warning);
      box-shadow: 0 0 0 3px var(--r-warning-bg);
    }
  }

  &__count {
    font-family: var(--r-font-mono);
    font-size: var(--r-font-xs);
    color: var(--r-text-muted);
    font-weight: var(--r-weight-semibold);
  }

  &__toggle {
    all: unset;
    margin-left: auto;
    cursor: pointer;
    font-size: var(--r-font-xs);
    color: var(--r-text-tertiary);
    text-transform: lowercase;
    padding: 2px 8px;
    border-radius: var(--r-radius-sm);
    transition: color 0.15s, background 0.15s;

    &:hover {
      color: var(--r-accent);
      background: var(--r-accent-bg);
    }
  }
}

.cap-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.cap-item {
  display: flex !important;
  align-items: flex-start !important;
  width: 100%;
  height: auto !important;
  padding: 10px 12px;
  margin: 0 !important;
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  background: var(--r-bg-card);
  transition: background 0.15s, border-color 0.15s;

  &:hover {
    border-color: var(--r-border);
    background: var(--r-bg-panel);
  }

  :deep(.el-checkbox__input) {
    align-self: flex-start;
    margin-top: 2px;
  }

  :deep(.el-checkbox__label) {
    flex: 1;
    min-width: 0;
    padding-left: 10px;
    line-height: var(--r-leading-snug);
  }

  &.is-checked {
    background: var(--r-accent-bg);
    border-color: var(--r-accent-border);
  }

  &--write.is-checked {
    background: var(--r-warning-bg);
    border-color: var(--r-warning-border);
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: 3px;
    min-width: 0;
  }

  &__id {
    font-family: var(--r-font-mono);
    font-size: var(--r-font-sm);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-primary);
    background: none;
    padding: 0;
  }

  &__desc {
    font-size: var(--r-font-xs);
    color: var(--r-text-tertiary);
    line-height: var(--r-leading-snug);
    white-space: normal;
  }
}

/* ========== WARN BANNER ========== */
.warn-banner {
  display: flex;
  align-items: flex-start;
  gap: var(--r-space-2);
  padding: 10px 12px;
  background: var(--r-warning-bg);
  border: 1px solid var(--r-warning-border);
  border-radius: var(--r-radius-md);
  font-size: var(--r-font-xs);
  color: var(--r-text-secondary);
  line-height: var(--r-leading-snug);

  &__icon {
    color: var(--r-warning);
    font-size: 16px;
    flex-shrink: 0;
    margin-top: 1px;
  }
}

/* ========== STEP 3: SUCCESS + VAULT ========== */
.success-mark {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 6px;
  padding: 8px 0 var(--r-space-3);

  &__circle {
    width: 48px;
    height: 48px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: var(--r-success-bg);
    border: 1px solid var(--r-success-border);
    color: var(--r-success);
    margin-bottom: 6px;
    animation: pop-in 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);

    .el-icon { font-size: 22px; }
  }

  h4 {
    margin: 0;
    font-size: var(--r-font-md);
    font-weight: var(--r-weight-bold);
    color: var(--r-text-primary);
  }

  p {
    margin: 0;
    font-size: var(--r-font-xs);
    color: var(--r-warning);
    line-height: var(--r-leading-snug);
    max-width: 460px;

    :deep(strong) {
      color: var(--r-warning);
    }
  }
}

@keyframes pop-in {
  0% { transform: scale(0.6); opacity: 0; }
  60% { transform: scale(1.1); opacity: 1; }
  100% { transform: scale(1); }
}

.token-vault {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--r-space-3);
  padding: 12px 14px;
  background: var(--r-bg-code);
  border: 1px dashed var(--r-warning-border);
  border-radius: var(--r-radius-md);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__label {
    font-family: var(--r-font-mono);
    font-size: 10px;
    font-weight: var(--r-weight-semibold);
    text-transform: uppercase;
    letter-spacing: 0.14em;
    color: var(--r-text-muted);
  }

  &__eye {
    all: unset;
    cursor: pointer;
    padding: 4px;
    border-radius: var(--r-radius-sm);
    color: var(--r-text-muted);
    transition: color 0.15s, background 0.15s;

    &:hover {
      color: var(--r-accent);
      background: var(--r-bg-card);
    }

    .el-icon { font-size: 14px; }
  }

  &__value {
    font-family: var(--r-font-mono);
    font-size: var(--r-font-sm);
    color: var(--r-text-primary);
    word-break: break-all;
    line-height: var(--r-leading-normal);
    background: none;
    padding: 0;
    border: none;
  }

  &__copy {
    all: unset;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 6px 12px;
    background: var(--r-accent);
    color: #fff;
    border-radius: var(--r-radius-md);
    font-size: var(--r-font-sm);
    font-weight: var(--r-weight-semibold);
    cursor: pointer;
    transition: background 0.15s, transform 0.1s;
    width: max-content;
    justify-self: end;

    &:hover { background: var(--r-accent-hover); }
    &:active { transform: translateY(1px); }

    .el-icon { font-size: 13px; }
  }
}

.ack {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  cursor: pointer;
  font-size: var(--r-font-sm);
  color: var(--r-text-secondary);

  :deep(.el-checkbox) {
    height: auto;
    margin: 0;
  }
}

/* ========== FOOTER ========== */
.dlg-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--r-space-2);
}

/* ========== DIALOG BASE OVERRIDES ========== */
:deep(.mcp-create-dialog .el-dialog__header) {
  padding: 20px 24px 0;
  margin-right: 0;
  border-bottom: none;
}
:deep(.mcp-create-dialog .el-dialog__body) {
  padding: 8px 24px 4px;
}
:deep(.mcp-create-dialog .el-dialog__footer) {
  padding: 16px 24px 20px;
  border-top: 1px solid var(--r-border-light);
}
</style>
