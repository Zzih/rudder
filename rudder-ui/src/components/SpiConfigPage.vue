<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { renderSafeMarkdown } from '@/utils/safeMarkdown'
import { colorMeta, providerColor } from '@/utils/colorMeta'
import type { PluginProviderDefinition } from '@/api/spi-config'

const props = defineProps<{
  i18nPrefix: string
  enableLabelKey: string
  getProviderDefinitions: () => Promise<any>
  getConfig: () => Promise<any>
  listConfigs: () => Promise<any>
  saveConfig: (data: { provider: string; providerParams?: string; enabled?: boolean; [key: string]: any }) => Promise<any>
  extraFields?: string[]
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
const currentGuide = computed(() => {
  const md = providerDefs.value[form.value.provider]?.guide
  return md ? renderSafeMarkdown(md) : ''
})

const form = ref({
  provider: '',
  providerParams: {} as Record<string, string>,
  enabled: true,
  extra: {} as Record<string, any>,
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

function onProviderChange() {
  const saved = configsByProvider.value[form.value.provider]
  if (saved) {
    form.value.providerParams = (saved.providerParams ?? {}) as Record<string, string>
    form.value.enabled = saved.enabled !== false
    return
  }
  const defs = providerDefs.value[form.value.provider]?.params ?? []
  const next: Record<string, string> = {}
  for (const p of defs) {
    if (p.defaultValue) next[p.name] = p.defaultValue
  }
  form.value.providerParams = next
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
      form.value.providerParams = (cfg.providerParams ?? {}) as Record<string, string>
      if (props.extraFields) {
        for (const key of props.extraFields) {
          if (cfg[key] !== undefined) {
            form.value.extra[key] = cfg[key]
          }
        }
      }
    } else {
      form.value.provider = firstProvider
      onProviderChange()
    }
  } catch { /* interceptor */ }
  finally { loading.value = false }
}

function validateRequired(): boolean {
  for (const param of currentProviderParams.value) {
    if (param.required && !form.value.providerParams[param.name]?.trim()) {
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
      ...form.value.extra,
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
                    <el-input
                      v-else
                      v-model="form.providerParams[param.name]"
                      :type="param.type === 'password' ? 'password' : 'text'"
                      :show-password="param.type === 'password'"
                      :placeholder="param.placeholder"
                      size="default"
                    />
                  </div>
                </div>
              </section>
            </Transition>

            <!-- Extra settings (slot) -->
            <slot name="extra-settings" :form="form" />

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
      <aside class="spi-guide" :class="{ 'is-empty': !currentGuide }">
        <div v-if="currentGuide" class="spi-guide__scroll">
          <div class="spi-guide__head">
            <span class="spi-guide__kicker">{{ tt(i18nPrefix + '.setupGuide', 'common.setupGuide') }}</span>
            <span v-if="form.provider" class="spi-guide__issue">{{ form.provider }}</span>
          </div>
          <div class="spi-guide__prose" v-html="currentGuide" />
        </div>
        <div v-else class="spi-guide__empty">
          <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M9 12h6m-3-3v6m-7 4h14a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2Z"/>
          </svg>
          <span>{{ tt(i18nPrefix + '.emptyTip', 'common.emptyTip') }}</span>
        </div>
      </aside>
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

/* ========== RIGHT — guide ========== */
.spi-guide {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--r-bg-panel);

  &.is-empty {
    align-items: center;
    justify-content: center;
  }
}

.spi-guide__scroll {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px;

  &::-webkit-scrollbar { width: 5px; }
  &::-webkit-scrollbar-track { background: transparent; }
  &::-webkit-scrollbar-thumb {
    background: var(--r-border-dark);
    border-radius: 3px;
    &:hover { background: var(--r-text-muted); }
  }
}

.spi-guide__head {
  display: flex; align-items: baseline; justify-content: space-between;
  padding-bottom: 10px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--r-border);
}

.spi-guide__kicker {
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.spi-guide__issue {
  font-size: var(--r-font-xs);
  color: var(--r-text-secondary);
}

/* empty */
.spi-guide__empty {
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  color: var(--r-text-muted);
  font-size: var(--r-font-sm);
  opacity: 0.7;
}

/* --- Prose --- */
.spi-guide__prose {
  font-size: var(--r-font-base);
  line-height: var(--r-leading-loose);
  color: var(--r-text-secondary);

  :deep(h2) {
    font-size: var(--r-font-md);
    font-weight: var(--r-weight-bold);
    color: var(--r-text-primary);
    margin: 0 0 var(--r-space-3);
    letter-spacing: -0.01em;
  }

  :deep(h3) {
    font-size: var(--r-font-md);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-primary);
    margin: var(--r-space-5) 0 var(--r-space-2);
    padding-bottom: var(--r-space-2);
    border-bottom: 1px solid var(--r-border);
  }

  :deep(ol),
  :deep(ul) {
    padding-left: var(--r-space-5);
    margin: var(--r-space-2) 0;
  }

  :deep(li) {
    margin: var(--r-space-1) 0;
    padding-left: 2px;
  }

  :deep(code) {
    background: var(--r-border);
    color: var(--r-text-primary);
    padding: 1px var(--r-space-1);
    border-radius: 3px;
    font-size: var(--r-font-sm);
    font-family: var(--r-font-mono);
  }

  :deep(a) {
    color: var(--r-accent);
    text-decoration: none;
    &:hover { text-decoration: underline; }
  }

  :deep(blockquote) {
    margin: var(--r-space-3) 0;
    padding: var(--r-space-2) var(--r-space-4);
    border-left: 3px solid var(--r-accent);
    background: var(--r-accent-bg);
    border-radius: 0 var(--r-radius-md) var(--r-radius-md) 0;
    color: var(--r-accent-hover);
    font-size: var(--r-font-sm);

    p { margin: 0; }
  }

  :deep(strong) {
    color: var(--r-text-primary);
    font-weight: var(--r-weight-semibold);
  }

  :deep(table) {
    width: 100%;
    border-collapse: collapse;
    margin: var(--r-space-3) 0;
    font-size: var(--r-font-sm);
  }

  :deep(th),
  :deep(td) {
    padding: var(--r-space-2) var(--r-space-3);
    border: 1px solid var(--r-border);
    text-align: left;
  }

  :deep(th) {
    background: var(--r-bg-hover);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-primary);
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
