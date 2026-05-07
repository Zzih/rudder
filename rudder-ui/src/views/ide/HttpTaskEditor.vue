<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Delete, Link, Document, Timer, Check, Refresh } from '@element-plus/icons-vue'
import { defaultHttpParams, type HttpParams } from './httpParams'

const { t } = useI18n()

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
type SectionKey = 'headers' | 'body' | 'validation' | 'reliability'

const props = defineProps<{
  modelValue: string
  embedded?: boolean
}>()
const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()

const METHODS: readonly Method[] = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
const BODY_METHODS = new Set<Method>(['POST', 'PUT', 'PATCH'])

const CONTENT_TYPE_PRESETS = [
  { label: 'JSON', value: 'application/json' },
  { label: 'Form', value: 'application/x-www-form-urlencoded' },
  { label: 'Text', value: 'text/plain' },
  { label: 'XML',  value: 'application/xml' },
]

const form = ref<HttpParams>(defaultHttpParams())
const headerRows = ref<{ key: string; value: string }[]>([])
const codeDraft = ref('')
const activeSection = ref<SectionKey>('headers')
const codeInput = ref<HTMLInputElement | null>(null)
// Sentinel to suppress self-reparse when our own emit bounces back via props.
let lastEmitted = ''

const hasBody = computed(() => BODY_METHODS.has(form.value.method as Method))
const methodTone = computed(() => form.value.method.toLowerCase())

onMounted(() => parseFromJson(props.modelValue))
watch(() => props.modelValue, (val) => {
  if (val === lastEmitted) return
  parseFromJson(val)
}, { flush: 'post' })
watch(hasBody, (v) => { if (!v && activeSection.value === 'body') activeSection.value = 'headers' })

function parseFromJson(json: string) {
  if (!json || !json.trim().startsWith('{')) {
    Object.assign(form.value, defaultHttpParams())
    headerRows.value = []
    return
  }
  try {
    const parsed = JSON.parse(json)
    Object.assign(form.value, defaultHttpParams(), parsed, { headers: parsed.headers || {} })
    if (!Array.isArray(form.value.successCodes) || !form.value.successCodes.length) {
      form.value.successCodes = [200]
    }
    headerRows.value = Object.entries(form.value.headers).map(([key, value]) => ({ key, value: String(value) }))
  } catch {
    Object.assign(form.value, defaultHttpParams())
    headerRows.value = []
  }
}

function emitChange() {
  const h: Record<string, string> = {}
  for (const row of headerRows.value) {
    if (row.key.trim()) h[row.key.trim()] = row.value
  }
  form.value.headers = h
  if (!form.value.successCodes.length) form.value.successCodes = [200]
  const json = JSON.stringify(form.value, null, 2)
  lastEmitted = json
  emit('update:modelValue', json)
}

function addHeader() { headerRows.value.push({ key: '', value: '' }) }
function removeHeader(idx: number) { headerRows.value.splice(idx, 1); emitChange() }

function onCodeKey(e: KeyboardEvent) {
  if (e.key === 'Enter' || e.key === ',' || e.key === ' ') {
    e.preventDefault()
    commitCode()
  } else if (e.key === 'Backspace' && !codeDraft.value && form.value.successCodes.length) {
    form.value.successCodes.pop()
    emitChange()
  }
}
function commitCode() {
  const raw = codeDraft.value.trim()
  if (!raw) return
  for (const seg of raw.split(/[,\s]+/)) {
    const n = parseInt(seg, 10)
    if (Number.isFinite(n) && n >= 100 && n < 600 && !form.value.successCodes.includes(n)) {
      form.value.successCodes.push(n)
    }
  }
  form.value.successCodes.sort((a, b) => a - b)
  codeDraft.value = ''
  emitChange()
}
function removeCode(code: number) {
  form.value.successCodes = form.value.successCodes.filter(c => c !== code)
  emitChange()
}

function setContentType(v: string) {
  form.value.contentType = v
  emitChange()
}

function codeClass(n: number) {
  return `r-${Math.max(1, Math.min(5, Math.floor(n / 100)))}xx`
}

const sections = computed<{ key: SectionKey; label: string; badge: number }[]>(() => {
  const base: { key: SectionKey; label: string; badge: number }[] = [
    { key: 'headers',     label: t('http.headers'),       badge: Object.keys(form.value.headers).length },
    { key: 'validation',  label: t('http.validation'),    badge: form.value.successCodes.length },
    { key: 'reliability', label: t('http.timeoutsRetry'), badge: form.value.retries },
  ]
  if (hasBody.value) base.splice(1, 0, { key: 'body', label: t('http.body'), badge: form.value.body ? 1 : 0 })
  return base
})
</script>

<template>
  <div class="http-editor" :class="{ 'http-editor--embedded': embedded }" :data-tone="methodTone">
    <div class="request-bar">
      <el-select
        v-model="form.method"
        class="method-select"
        :class="`tone-${methodTone}`"
        @change="emitChange"
      >
        <template #prefix>
          <span class="method-dot" :class="`tone-${methodTone}`" />
        </template>
        <el-option v-for="m in METHODS" :key="m" :label="m" :value="m">
          <span class="opt-row">
            <span class="method-dot" :class="`tone-${m.toLowerCase()}`" />
            <span class="opt-label">{{ m }}</span>
          </span>
        </el-option>
      </el-select>
      <div class="url-wrap" :class="`tone-${methodTone}`">
        <el-icon class="url-icon"><Link /></el-icon>
        <input
          v-model="form.url"
          class="url-input"
          placeholder="https://api.example.com/webhook"
          spellcheck="false"
          @change="emitChange"
        />
      </div>
    </div>

    <nav class="section-nav" role="tablist">
      <button
        v-for="s in sections"
        :key="s.key"
        type="button"
        class="section-tab"
        :class="{ active: activeSection === s.key }"
        :aria-selected="activeSection === s.key"
        @click="activeSection = s.key"
      >
        <span>{{ s.label }}</span>
        <span v-if="s.badge" class="section-badge">{{ s.badge }}</span>
      </button>
      <div class="section-nav-rail" />
    </nav>

    <div class="panel">
      <section v-show="activeSection === 'headers'">
        <div v-if="headerRows.length" class="kv-table">
          <div class="kv-head">
            <span>Key</span><span>Value</span><span />
          </div>
          <div v-for="(row, idx) in headerRows" :key="idx" class="kv-row">
            <el-input v-model="row.key"   placeholder="Authorization" @change="emitChange" />
            <el-input v-model="row.value" placeholder="Bearer ..."     @change="emitChange" />
            <button type="button" class="icon-btn danger" :title="t('common.delete')" @click="removeHeader(idx)">
              <el-icon><Delete /></el-icon>
            </button>
          </div>
        </div>
        <div v-else class="empty-state">
          <el-icon class="empty-icon"><Document /></el-icon>
          <div class="empty-title">{{ t('http.headers') }}</div>
          <div class="empty-hint">Authorization · Content-Type · X-Request-Id ...</div>
        </div>
        <el-button class="add-btn" :icon="Plus" @click="addHeader">
          {{ t('common.create') }}
        </el-button>
      </section>

      <section v-show="activeSection === 'body' && hasBody">
        <div class="field-row">
          <label class="field-label">{{ t('http.contentType') }}</label>
          <div class="ct-row">
            <el-input v-model="form.contentType" placeholder="application/json" class="ct-input" @change="emitChange" />
            <div class="ct-presets">
              <button
                v-for="p in CONTENT_TYPE_PRESETS"
                :key="p.value"
                type="button"
                class="preset-chip"
                :class="{ active: form.contentType === p.value }"
                @click="setContentType(p.value)"
              >{{ p.label }}</button>
            </div>
          </div>
        </div>
        <div class="field-row">
          <label class="field-label">Payload</label>
          <el-input
            v-model="form.body"
            type="textarea"
            :rows="12"
            class="body-area"
            :placeholder="'{&quot;key&quot;: &quot;value&quot;}'"
            @change="emitChange"
          />
        </div>
      </section>

      <section v-show="activeSection === 'validation'">
        <div class="field-row">
          <label class="field-label">{{ t('http.successCodes') }}</label>
          <div class="chip-input" @click="codeInput?.focus()">
            <span v-for="code in form.successCodes" :key="code" class="code-chip" :class="codeClass(code)">
              <el-icon class="code-chip-icon"><Check /></el-icon>
              {{ code }}
              <button type="button" class="code-chip-x" @click.stop="removeCode(code)">×</button>
            </span>
            <input
              ref="codeInput"
              v-model="codeDraft"
              class="chip-raw"
              placeholder="200, 201 ⏎"
              inputmode="numeric"
              @keydown="onCodeKey"
              @blur="commitCode"
            />
          </div>
          <div class="hint">{{ t('http.successCodesHint') }}</div>
        </div>
        <div class="field-row">
          <label class="field-label">{{ t('http.expectedBodyContains') }}</label>
          <el-input
            v-model="form.expectedBodyContains"
            :placeholder="t('http.expectedBodyContainsHint')"
            @change="emitChange"
          />
          <div class="hint">{{ t('http.expectedBodyContainsHint') }}</div>
        </div>
      </section>

      <section v-show="activeSection === 'reliability'">
        <div class="reliability-grid">
          <div class="stat-card">
            <div class="stat-head">
              <el-icon><Timer /></el-icon>
              <span>{{ t('http.connectTimeout') }}</span>
            </div>
            <div class="stat-body">
              <el-input-number
                v-model="form.connectTimeoutMs"
                :min="100" :max="300_000" :step="1000"
                controls-position="right"
                @change="emitChange"
              />
              <span class="unit">ms</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-head">
              <el-icon><Timer /></el-icon>
              <span>{{ t('http.readTimeout') }}</span>
            </div>
            <div class="stat-body">
              <el-input-number
                v-model="form.readTimeoutMs"
                :min="100" :max="600_000" :step="1000"
                controls-position="right"
                @change="emitChange"
              />
              <span class="unit">ms</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-head">
              <el-icon><Refresh /></el-icon>
              <span>{{ t('http.retries') }}</span>
            </div>
            <div class="stat-body">
              <el-input-number
                v-model="form.retries"
                :min="0" :max="10"
                controls-position="right"
                @change="emitChange"
              />
              <span class="unit">×</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-head">
              <el-icon><Timer /></el-icon>
              <span>{{ t('http.retryDelay') }}</span>
            </div>
            <div class="stat-body">
              <el-input-number
                v-model="form.retryDelayMs"
                :min="0" :max="60_000" :step="500"
                controls-position="right"
                @change="emitChange"
              />
              <span class="unit">ms</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.http-editor {
  padding: var(--r-space-5) var(--r-space-5) var(--r-space-6);
  height: 100%;
  overflow-y: auto;
  background: var(--r-bg-card);
  color: var(--r-text-primary);

  // Method tones reuse semantic tokens so dark mode / theme swaps carry through.
  --tone-get:    var(--r-info);
  --tone-post:   var(--r-success);
  --tone-put:    var(--r-warning);
  --tone-patch:  var(--r-purple);
  --tone-delete: var(--r-danger);

  &--embedded {
    padding: 0;
    height: auto;
    overflow: visible;
    background: transparent;
  }
}

/* ─── Request bar ─────────────────────────── */
.request-bar {
  display: grid;
  grid-template-columns: 132px 1fr;
  gap: var(--r-space-2);
  align-items: stretch;
  margin-bottom: var(--r-space-5);
}
.method-select {
  :deep(.el-select__wrapper) {
    height: 38px;
    padding-left: var(--r-space-3);
    font-weight: var(--r-weight-semibold);
    font-family: var(--r-font-mono);
    letter-spacing: 0.04em;
    border-radius: var(--r-radius-md);
  }
}
.method-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  display: inline-block;
  margin-right: var(--r-space-2);

  &.tone-get    { background: var(--tone-get); }
  &.tone-post   { background: var(--tone-post); }
  &.tone-put    { background: var(--tone-put); }
  &.tone-patch  { background: var(--tone-patch); }
  &.tone-delete { background: var(--tone-delete); }
}
.opt-row { display: flex; align-items: center; }
.opt-label { font-family: var(--r-font-mono); font-weight: var(--r-weight-semibold); }

.url-wrap {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  padding: 0 var(--r-space-3);
  height: 38px;
  border: 1px solid var(--r-border);
  border-left-width: 3px;
  border-radius: var(--r-radius-md);
  background: var(--r-bg-input);
  transition: border-color .15s, box-shadow .15s;

  &:focus-within {
    border-color: var(--r-accent);
    box-shadow: 0 0 0 3px var(--r-accent-bg);
  }
  &.tone-get    { border-left-color: var(--tone-get); }
  &.tone-post   { border-left-color: var(--tone-post); }
  &.tone-put    { border-left-color: var(--tone-put); }
  &.tone-patch  { border-left-color: var(--tone-patch); }
  &.tone-delete { border-left-color: var(--tone-delete); }
}
.url-icon {
  color: var(--r-text-muted);
  font-size: var(--r-font-md);
}
.url-input {
  flex: 1;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--r-text-primary);
  font-family: var(--r-font-mono);
  font-size: var(--r-font-base);
  letter-spacing: 0.01em;

  &::placeholder { color: var(--r-text-muted); font-family: var(--r-font-ui); }
}

/* ─── Section nav ─────────────────────────── */
.section-nav {
  position: relative;
  display: flex;
  gap: var(--r-space-1);
  margin-bottom: var(--r-space-4);
  border-bottom: 1px solid var(--r-border);
}
.section-nav-rail {
  position: absolute;
  left: 0; right: 0; bottom: -1px;
  height: 1px;
  background: transparent;
  pointer-events: none;
}
.section-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: var(--r-space-2);
  padding: var(--r-space-2) var(--r-space-4);
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-medium);
  color: var(--r-text-secondary);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: color .15s, border-color .15s;

  &:hover { color: var(--r-text-primary); }
  &.active {
    color: var(--r-text-primary);
    font-weight: var(--r-weight-semibold);
    border-bottom-color: var(--r-accent);
  }
}
.section-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 var(--r-space-1);
  border-radius: var(--r-radius-sm);
  background: var(--r-bg-hover);
  color: var(--r-text-secondary);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  font-family: var(--r-font-mono);
  display: inline-flex;
  align-items: center;
  justify-content: center;

  .section-tab.active & {
    background: var(--r-accent-bg);
    color: var(--r-accent);
  }
}

/* ─── Panels ──────────────────────────────── */
.panel { min-height: 280px; }

/* Headers table */
.kv-table {
  border: 1px solid var(--r-border);
  border-radius: var(--r-radius-md);
  overflow: hidden;
  margin-bottom: var(--r-space-3);
}
.kv-head, .kv-row {
  display: grid;
  grid-template-columns: 1fr 1.5fr 40px;
  gap: var(--r-space-2);
  align-items: center;
}
.kv-head {
  padding: var(--r-space-2) var(--r-space-3);
  background: var(--r-bg-panel);
  border-bottom: 1px solid var(--r-border);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-family: var(--r-font-mono);
}
.kv-row {
  padding: var(--r-space-2) var(--r-space-3);
  border-bottom: 1px solid var(--r-border-light);

  &:last-child { border-bottom: 0; }
  &:hover { background: var(--r-bg-hover); }
}
.icon-btn {
  width: 28px; height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 0;
  border-radius: var(--r-radius-sm);
  color: var(--r-text-muted);
  cursor: pointer;

  &:hover.danger { background: var(--r-danger-bg); color: var(--r-danger); }
}
.empty-state {
  border: 1px dashed var(--r-border);
  border-radius: var(--r-radius-md);
  padding: var(--r-space-6) var(--r-space-4);
  text-align: center;
  background: var(--r-bg-panel);
  margin-bottom: var(--r-space-3);
}
.empty-icon { font-size: 28px; color: var(--r-text-muted); margin-bottom: var(--r-space-2); }
.empty-title {
  font-size: var(--r-font-md);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-secondary);
  margin-bottom: var(--r-space-1);
}
.empty-hint {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  font-family: var(--r-font-mono);
}
.add-btn {
  width: 100%;
  border-style: dashed !important;
  color: var(--r-text-secondary);
}

/* Body */
.field-row {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-2);
  margin-bottom: var(--r-space-4);
}
.field-label {
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-family: var(--r-font-mono);
}
.ct-row {
  display: flex;
  gap: var(--r-space-2);
  align-items: center;
  flex-wrap: wrap;
}
.ct-input { flex: 1; min-width: 260px; }
.ct-presets { display: inline-flex; gap: var(--r-space-1); }
.preset-chip {
  padding: 4px var(--r-space-3);
  border-radius: var(--r-radius-sm);
  border: 1px solid var(--r-border);
  background: var(--r-bg-input);
  color: var(--r-text-secondary);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-medium);
  font-family: var(--r-font-mono);
  cursor: pointer;
  transition: all .12s;

  &:hover { border-color: var(--r-border-dark); color: var(--r-text-primary); }
  &.active {
    background: var(--r-accent-bg);
    border-color: var(--r-accent-border);
    color: var(--r-accent);
  }
}
.body-area :deep(.el-textarea__inner) {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-base);
  line-height: var(--r-leading-normal);
  background: var(--r-bg-code);
}

/* Chip input (success codes) */
.chip-input {
  display: flex;
  flex-wrap: wrap;
  gap: var(--r-space-1);
  padding: 6px var(--r-space-2);
  min-height: 38px;
  border: 1px solid var(--r-border);
  border-radius: var(--r-radius-md);
  background: var(--r-bg-input);
  cursor: text;

  &:focus-within {
    border-color: var(--r-accent);
    box-shadow: 0 0 0 3px var(--r-accent-bg);
  }
}
.code-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px var(--r-space-2);
  border-radius: var(--r-radius-sm);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  font-family: var(--r-font-mono);
  border: 1px solid;

  &.r-2xx { background: var(--r-success-bg); border-color: var(--r-success-border); color: var(--r-success); }
  &.r-3xx { background: var(--r-info-bg);    border-color: var(--r-info-border);    color: var(--r-info); }
  &.r-4xx { background: var(--r-warning-bg); border-color: var(--r-warning-border); color: var(--r-warning); }
  &.r-5xx { background: var(--r-danger-bg);  border-color: var(--r-danger-border);  color: var(--r-danger); }
  &.r-1xx { background: var(--r-purple-bg);  border-color: var(--r-purple-border);  color: var(--r-purple); }
}
.code-chip-icon { font-size: 11px; }
.code-chip-x {
  margin-left: 2px;
  width: 14px; height: 14px;
  display: inline-flex; align-items: center; justify-content: center;
  border: 0;
  background: transparent;
  color: inherit;
  opacity: 0.55;
  cursor: pointer;
  border-radius: 50%;
  font-size: 14px;
  line-height: 1;

  &:hover { opacity: 1; background: rgba(0,0,0,0.06); }
}
.chip-raw {
  flex: 1;
  min-width: 90px;
  border: 0;
  outline: none;
  background: transparent;
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  color: var(--r-text-primary);

  &::placeholder { color: var(--r-text-muted); }
}
.hint {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  line-height: var(--r-leading-snug);
}

/* Reliability */
.reliability-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--r-space-3);
}
.stat-card {
  border: 1px solid var(--r-border);
  border-radius: var(--r-radius-md);
  padding: var(--r-space-3) var(--r-space-4);
  background: var(--r-bg-panel);
  transition: border-color .15s;

  &:hover { border-color: var(--r-border-dark); }
}
.stat-head {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: var(--r-space-2);

  .el-icon { font-size: 14px; }
}
.stat-body {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
}
.stat-body :deep(.el-input-number) { flex: 1; }
.unit {
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  font-family: var(--r-font-mono);
  color: var(--r-text-muted);
  padding: 2px 6px;
  background: var(--r-bg-hover);
  border-radius: var(--r-radius-sm);
}
</style>
