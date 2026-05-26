<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import MarkdownGuideAside from '@/components/MarkdownGuideAside.vue'
import { colorMeta, providerColor } from '@/utils/colorMeta'
import type { PluginProviderDefinition } from '@/api/spi-config'

const props = defineProps<{
  i18nPrefix: string
  enableLabelKey: string
  getProviderDefinitions: () => Promise<any>
  getConfig: () => Promise<any>
  listConfigs: () => Promise<any>
  saveConfig: (data: { provider: string; providerParams?: string; enabled?: boolean }) => Promise<any>
  /** 不传则不显示"测试连接"按钮。 */
  testConfig?: (data: { provider: string; providerParams: string }) => Promise<any>
}>()

const { t, te, locale } = useI18n()

function tt(key: string, fallback: string): string {
  return te(key) ? t(key) : t(fallback)
}

const loading = ref(false)
const saving = ref(false)

const providerDefs = ref<Record<string, PluginProviderDefinition>>({})
const availableProviders = computed(() => Object.keys(providerDefs.value))
const currentProviderParams = computed(() => providerDefs.value[form.value.provider]?.params ?? [])
const currentGuide = computed(() => providerDefs.value[form.value.provider]?.guide ?? '')

const form = ref({
  provider: '',
  providerParams: {} as Record<string, any>,
  enabled: true,
})

function providerDesc(p: string): string {
  if (!p) return ''
  return providerDefs.value[p]?.metadata?.description ?? ''
}

function providerMeta(p: string) {
  return colorMeta(providerColor(p))
}

function providerMonogram(p: string): string {
  if (!p) return '··'
  const str = String(p)
  if (str.length <= 3) return str
  return str.slice(0, 2)
}

const configsByProvider = ref<Record<string, any>>({})

/** 按 param.type 把 defaultValue 从 string coerce 成 number/boolean,避免 el-input-number 等组件类型不匹配。 */
function coerceDefault(p: { type: string; defaultValue?: string }): any {
  if (p.defaultValue == null) return undefined
  if (p.type === 'number') return Number(p.defaultValue)
  if (p.type === 'boolean') return p.defaultValue === 'true'
  return p.defaultValue
}

function defaultsForProvider(provider: string): Record<string, any> {
  const defs = providerDefs.value[provider]?.params ?? []
  const out: Record<string, any> = {}
  for (const p of defs) {
    const v = coerceDefault(p)
    if (v !== undefined) out[p.name] = v
  }
  return out
}

function onProviderChange() {
  const saved = configsByProvider.value[form.value.provider]
  if (saved) {
    // 旧 row 可能没存全新字段,merge defaults 兜底
    form.value.providerParams = { ...defaultsForProvider(form.value.provider), ...(saved.providerParams ?? {}) }
    form.value.enabled = saved.enabled !== false
    return
  }
  form.value.providerParams = defaultsForProvider(form.value.provider)
  form.value.enabled = true
}

async function loadData() {
  loading.value = true
  try {
    const [defRes, cfgRes, listRes] = await Promise.all([
      props.getProviderDefinitions(),
      props.getConfig(),
      props.listConfigs(),
    ])
    providerDefs.value = (defRes as any).data ?? {}
    const all = ((listRes as any).data ?? []) as any[]
    const map: Record<string, any> = {}
    for (const row of all) {
      if (row?.provider) map[row.provider] = row
    }
    configsByProvider.value = map

    const cfg = (cfgRes as any).data
    const firstProvider = Object.keys(providerDefs.value)[0] ?? ''
    if (cfg) {
      form.value.provider = cfg.provider || firstProvider
      form.value.enabled = cfg.enabled !== false
      // 旧 row(本次以前持久化的)可能没存某些新字段,merge defaults 防止 UI 显示空白
      form.value.providerParams = { ...defaultsForProvider(form.value.provider), ...(cfg.providerParams ?? {}) }
    } else {
      form.value.provider = firstProvider
      onProviderChange()
    }
  } catch { /* interceptor */ }
  finally { loading.value = false }
}

function validateRequired(): boolean {
  for (const param of currentProviderParams.value) {
    const v = form.value.providerParams[param.name]
    if (param.required && (v == null || String(v).trim() === '')) {
      ElMessage.warning(`${param.label} is required`)
      return false
    }
  }
  return true
}

async function handleSave() {
  if (!validateRequired()) return
  saving.value = true
  try {
    await props.saveConfig({
      provider: form.value.provider,
      providerParams: JSON.stringify(form.value.providerParams),
      enabled: form.value.enabled,
    })
    // 与后端 disableOthers 同步本地 cache 的 enabled 状态。
    const next: Record<string, any> = {}
    for (const [p, row] of Object.entries(configsByProvider.value)) {
      next[p] = { ...row, enabled: false }
    }
    next[form.value.provider] = {
      provider: form.value.provider,
      providerParams: { ...form.value.providerParams },
      enabled: form.value.enabled,
    }
    configsByProvider.value = next
    ElMessage.success(t('common.success'))
  } catch { /* interceptor */ }
  finally { saving.value = false }
}

const testing = ref(false)

async function handleTest() {
  if (!props.testConfig) return
  if (!validateRequired()) return
  testing.value = true
  try {
    const res = (await props.testConfig({
      provider: form.value.provider,
      providerParams: JSON.stringify(form.value.providerParams),
    })) as any
    const r = res?.data
    if (r?.success) {
      ElMessage.success(`${t('common.success')}${r.latencyMs != null ? ` · ${r.latencyMs}ms` : ''}`)
    } else {
      ElMessage.error(`${t('common.failed')}: ${r?.message ?? t('common.unknown')}`)
    }
  } catch { /* interceptor handled */ }
  finally { testing.value = false }
}

onMounted(loadData)

// providerDefs 按 Accept-Language 头从后端按 locale 加载,切语言时重拉。
watch(locale, async () => {
  try {
    const defRes = await props.getProviderDefinitions()
    providerDefs.value = (defRes as any).data ?? {}
  } catch { /* interceptor */ }
})
</script>

<template>
  <div class="spi-page" v-loading="loading">
    <div class="spi-split">
      <!-- ============== LEFT: Configuration ============== -->
      <div class="spi-config">
        <div class="spi-config__scroll">
          <div class="spi-config__inner">
            <div class="spi-head">
              <h3 class="spi-head__title">{{ t(i18nPrefix + '.configTitle') }}</h3>
              <span class="spi-head__sub">{{ t(i18nPrefix + '.provider') }}</span>
            </div>

            <!-- Provider cards -->
            <div class="spi-providers">
              <button
                v-for="p in availableProviders" :key="p"
                class="spi-provider"
                :class="{ 'is-active': form.provider === p }"
                :style="{
                  '--p-color': providerMeta(p).color,
                  '--p-bg': providerMeta(p).bg,
                  '--p-border': providerMeta(p).border,
                }"
                @click="form.provider = p; onProviderChange()"
              >
                <span class="spi-provider__mark">{{ providerMonogram(p) }}</span>
                <span class="spi-provider__body">
                  <span class="spi-provider__head">
                    <span class="spi-provider__name">{{ p }}</span>
                    <span class="spi-provider__check" aria-hidden="true">
                      <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
                        <path d="M13.3 4.3a1 1 0 0 1 0 1.4l-6 6a1 1 0 0 1-1.4 0l-2.6-2.6a1 1 0 1 1 1.4-1.4L6.6 9.6l5.3-5.3a1 1 0 0 1 1.4 0Z" fill="currentColor"/>
                      </svg>
                    </span>
                  </span>
                  <span v-if="providerDesc(p)" class="spi-provider__desc">{{ providerDesc(p) }}</span>
                </span>
              </button>
            </div>

            <!-- Dynamic params -->
            <Transition name="spi-fade">
              <section v-if="currentProviderParams.length > 0" class="spi-section">
                <h4 class="spi-section__title">{{ t(i18nPrefix + '.providerConfig') }}</h4>
                <div class="spi-params">
                  <div v-for="param in currentProviderParams" :key="param.name" class="spi-param">
                    <label class="spi-param__label">
                      {{ param.label }}
                      <span v-if="param.required" class="spi-param__req">*</span>
                    </label>
                    <el-input
                      v-if="param.type === 'textarea'"
                      v-model="form.providerParams[param.name]"
                      type="textarea"
                      :autosize="{ minRows: 5, maxRows: 16 }"
                      :placeholder="param.placeholder"
                    />
                    <el-switch
                      v-else-if="param.type === 'boolean'"
                      v-model="form.providerParams[param.name]"
                    />
                    <el-input-number
                      v-else-if="param.type === 'number'"
                      v-model="form.providerParams[param.name]"
                      :min="param.min ?? 1"
                      :max="param.max ?? 2147483647"
                      :step="param.step ?? 1"
                      controls-position="right"
                      style="width: 100%"
                    />
                    <el-input
                      v-else
                      v-model="form.providerParams[param.name]"
                      :type="param.type === 'password' ? 'password' : 'text'"
                      :show-password="param.type === 'password'"
                      :placeholder="param.placeholder"
                      size="default"
                    />
                    <span
                      v-if="param.placeholder && param.type === 'number'"
                      class="spi-param__hint"
                    >{{ param.placeholder }}</span>
                  </div>
                </div>
              </section>
            </Transition>

            <!-- Enable switch -->
            <section class="spi-section">
              <h4 class="spi-section__title">{{ t(enableLabelKey) }}</h4>
              <div class="spi-enable">
                <span class="spi-enable__desc">
                  {{ form.enabled ? t(i18nPrefix + '.enabledHint') : t(i18nPrefix + '.disabledHint') }}
                </span>
                <el-switch v-model="form.enabled" />
              </div>
            </section>
          </div>
        </div>

        <!-- Sticky action bar -->
        <div class="spi-actions">
          <span class="spi-actions__meta">
            <span class="spi-actions__dot" :class="{ on: form.enabled }" />
            <span>{{ form.provider || '—' }}</span>
          </span>
          <div class="spi-actions__buttons">
            <el-button v-if="testConfig" :loading="testing" :disabled="!form.provider" @click="handleTest">
              {{ t('common.testConnection') }}
            </el-button>
            <el-button type="primary" :loading="saving" :disabled="!form.provider" @click="handleSave">
              {{ t('common.save') }}
            </el-button>
          </div>
        </div>
      </div>

      <!-- ============== RIGHT: Guide ============== -->
      <MarkdownGuideAside
        :markdown="currentGuide"
        :kicker="tt(i18nPrefix + '.setupGuide', 'common.setupGuide')"
        :label="form.provider"
        :empty-text="tt(i18nPrefix + '.emptyTip', 'common.emptyTip')"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.spi-page {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.spi-split {
  flex: 1;
  display: flex;
  min-height: 0;
  background: var(--r-bg-card);
}

/* ========== LEFT ========== */
.spi-config {
  flex: 0 0 480px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid var(--r-border);
}

.spi-config__scroll {
  flex: 1;
  overflow-y: auto;
}

.spi-config__inner {
  padding: 22px 24px 18px;
}

/* --- Header --- */
.spi-head {
  margin-bottom: 18px;

  &__title {
    margin: 0 0 4px;
    font-size: var(--r-font-md);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-primary);
    letter-spacing: -0.005em;
  }
  &__sub {
    font-size: var(--r-font-sm);
    color: var(--r-text-muted);
  }
}

/* --- Provider cards --- */
.spi-providers {
  display: flex; flex-direction: column; gap: 8px;
}

.spi-provider {
  all: unset;
  display: grid;
  grid-template-columns: 36px 1fr;
  gap: var(--r-space-3);
  align-items: center;
  padding: var(--r-space-2) var(--r-space-3);
  border: 1px solid var(--r-border);
  border-radius: var(--r-radius-md);
  cursor: pointer;
  background: var(--r-bg-card);
  transition: border-color 0.15s ease, background-color 0.15s ease, box-shadow 0.15s ease;

  &:hover {
    border-color: var(--r-border-dark);
    background: var(--r-bg-hover);
  }

  &.is-active {
    border-color: var(--p-color);
    background: color-mix(in srgb, var(--p-color) 5%, var(--r-bg-card));
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--p-color) 12%, transparent);

    .spi-provider__mark {
      background: var(--p-color);
      color: #fff;
      border-color: transparent;
    }
    .spi-provider__check { opacity: 1; }
  }
}

.spi-provider__mark {
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  border-radius: var(--r-radius-sm);
  background: var(--p-bg);
  color: var(--p-color);
  border: 1px solid var(--p-border);
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-bold);
  letter-spacing: 0.02em;
  text-transform: uppercase;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.spi-provider__body {
  display: flex; flex-direction: column; gap: 2px;
  min-width: 0;
}

.spi-provider__head {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
}

.spi-provider__name {
  font-size: var(--r-font-base);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
}

.spi-provider__check {
  color: var(--p-color);
  opacity: 0;
  transition: opacity 0.15s ease;
  display: inline-flex;
}

.spi-provider__desc {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  line-height: var(--r-leading-snug);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* --- Section --- */
.spi-section {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid var(--r-border-light);
}

.spi-section__title {
  margin: 0 0 12px;
  font-size: var(--r-font-base);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
}

.spi-params {
  display: flex; flex-direction: column; gap: 12px;
}

.spi-param {
  display: flex; flex-direction: column; gap: 5px;
}

.spi-param__label {
  font-size: var(--r-font-sm);
  color: var(--r-text-secondary);
  font-weight: var(--r-weight-medium);
}

.spi-param__req {
  color: var(--r-danger);
  margin-left: 2px;
}

.spi-param__hint {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  line-height: var(--r-leading-snug);
}

.spi-enable {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  padding: 10px 12px;
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-sm);
}

.spi-enable__desc {
  font-size: var(--r-font-sm);
  color: var(--r-text-secondary);
}

/* --- Sticky action bar --- */
.spi-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--r-space-3) var(--r-space-5);
  background: var(--r-bg-card);
  border-top: 1px solid var(--r-border);
}

.spi-actions__meta {
  display: inline-flex;
  align-items: center;
  gap: var(--r-space-2);
  font-size: var(--r-font-sm);
  color: var(--r-text-muted);
}

.spi-actions__buttons {
  display: inline-flex;
  align-items: center;
  gap: var(--r-space-2);
}

.spi-actions__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--r-text-disabled);
  transition: background-color 0.2s ease, box-shadow 0.2s ease;

  &.on {
    background: var(--r-success);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--r-success) 18%, transparent);
  }
}

.spi-fade-enter-active,
.spi-fade-leave-active {
  transition: opacity 0.2s ease;
}
.spi-fade-enter-from,
.spi-fade-leave-to {
  opacity: 0;
}
</style>
