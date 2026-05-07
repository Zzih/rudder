<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { renderSafeMarkdown } from '@/utils/safeMarkdown'
import { colorMeta } from '@/utils/colorMeta'
import {
  getApprovalChannelDefinitions,
  getApprovalConfig,
  saveApprovalConfig,
  type ChannelDefinition,
} from '@/api/approval'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)

const channelDefs = ref<Record<string, ChannelDefinition>>({})
const availableChannels = computed(() => Object.keys(channelDefs.value))
const currentChannelParams = computed(() => channelDefs.value[form.value.channel]?.params ?? [])
const currentGuide = computed(() => {
  const md = channelDefs.value[form.value.channel]?.guide
  return md ? renderSafeMarkdown(md) : ''
})

const form = ref({
  channel: 'LOCAL',
  channelParams: {} as Record<string, string>,
  enabled: true,
})

const channelMeta: Record<string, { icon: string; color: string; bg: string; border: string }> = {
  LOCAL: { icon: '&#9881;', ...colorMeta('#10b981') },
  LARK: { icon: '&#9992;', ...colorMeta('#3b82f6') },
  KISSFLOW: { icon: '&#9889;', ...colorMeta('#8b5cf6') },
}

function onChannelChange() {
  form.value.channelParams = {}
}

async function loadData() {
  loading.value = true
  try {
    const [defRes, cfgRes] = await Promise.all([
      getApprovalChannelDefinitions(),
      getApprovalConfig(),
    ])
    channelDefs.value = (defRes as any).data ?? {}
    const cfg = (cfgRes as any).data
    if (cfg) {
      form.value.channel = cfg.channel || 'LOCAL'
      form.value.enabled = cfg.enabled !== false
      try {
        form.value.channelParams = cfg.channelParams ? JSON.parse(cfg.channelParams) : {}
      } catch {
        form.value.channelParams = {}
      }
    }
  } catch { /* interceptor */ }
  finally { loading.value = false }
}

async function handleSave() {
  saving.value = true
  try {
    await saveApprovalConfig({
      channel: form.value.channel,
      channelParams: JSON.stringify(form.value.channelParams),
      enabled: form.value.enabled,
    })
    ElMessage.success(t('common.success'))
  } catch { /* interceptor */ }
  finally { saving.value = false }
}

onMounted(loadData)
</script>

<template>
  <div class="approval-config-page" v-loading="loading">
    <div class="config-split">
      <!-- Left: Configuration -->
      <div class="config-panel">
        <div class="panel-scroll">
          <div class="panel-inner">
            <div class="section-header">
              <h3>{{ t('approval.configTitle') }}</h3>
              <p class="section-sub">{{ t('approval.channel') }}</p>
            </div>

            <!-- Channel cards -->
            <div class="channel-cards">
              <div
                v-for="ch in availableChannels" :key="ch"
                class="channel-card"
                :class="{ active: form.channel === ch }"
                @click="form.channel = ch; onChannelChange()"
              >
                <div class="card-indicator" :style="{ background: channelMeta[ch]?.color }" />
                <div class="card-body">
                  <div class="card-top">
                    <span class="card-tag"
                          :style="{ color: channelMeta[ch]?.color, background: channelMeta[ch]?.bg, borderColor: channelMeta[ch]?.border }">
                      {{ ch }}
                    </span>
                    <span v-if="form.channel === ch" class="card-check">
                      <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M13.3 4.3a1 1 0 0 1 0 1.4l-6 6a1 1 0 0 1-1.4 0l-2.6-2.6a1 1 0 1 1 1.4-1.4L6.6 9.6l5.3-5.3a1 1 0 0 1 1.4 0Z" fill="currentColor"/></svg>
                    </span>
                  </div>
                  <span class="card-desc">
                    {{ ch === 'LOCAL' ? t('approval.channelLocalDesc') : ch === 'LARK' ? t('approval.channelLarkDesc') : t('approval.channelKissflowDesc') }}
                  </span>
                </div>
              </div>
            </div>

            <!-- Dynamic params -->
            <Transition name="params-fade">
              <div v-if="currentChannelParams.length > 0" class="params-section">
                <div class="section-divider" />
                <h4 class="section-label">{{ t('approval.channelConfig') }}</h4>
                <div class="params-list">
                  <div v-for="param in currentChannelParams" :key="param.name" class="param-row">
                    <label class="param-label">
                      {{ param.label }}
                      <span v-if="param.required" class="req">*</span>
                    </label>
                    <el-input
                      v-model="form.channelParams[param.name]"
                      :type="param.type === 'password' ? 'password' : 'text'"
                      :show-password="param.type === 'password'"
                      :placeholder="param.placeholder"
                      size="default"
                    />
                  </div>
                </div>
              </div>
            </Transition>

            <!-- Enable switch -->
            <div class="section-divider" />
            <div class="enable-row">
              <div class="enable-info">
                <h4 class="section-label" style="margin-bottom: 2px">{{ t('approval.enableApproval') }}</h4>
                <span class="enable-desc">{{ form.enabled ? t('approval.enabledHint') : t('approval.disabledHint') }}</span>
              </div>
              <el-switch v-model="form.enabled" />
            </div>

            <!-- Save -->
            <div class="save-bar">
              <el-button type="primary" :loading="saving" @click="handleSave">
                {{ t('common.save') }}
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Guide -->
      <div class="guide-panel" :class="{ empty: !currentGuide }">
        <div class="guide-scroll" v-if="currentGuide">
          <div class="guide-badge">
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none"><path d="M8 1a7 7 0 1 0 0 14A7 7 0 0 0 8 1Zm-.75 3.5a.75.75 0 0 1 1.5 0v4a.75.75 0 0 1-1.5 0v-4Zm.75 7.25a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5Z" fill="currentColor"/></svg>
            {{ t('approval.setupGuide') }}
          </div>
          <div class="guide-prose" v-html="currentGuide" />
        </div>
        <div v-else class="guide-empty">
          <div class="empty-icon">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9 12h6m-3-3v6m-7 4h14a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2Z"/></svg>
          </div>
          <span>{{ t('approval.channelLocalDesc') }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.approval-config-page {
  height: 100%;
  padding: 24px 28px;
  display: flex;
  flex-direction: column;
}

.config-split {
  flex: 1;
  display: flex;
  gap: 0;
  min-height: 0;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: 10px;
  overflow: hidden;
}

// ── Left panel ──
.config-panel {
  flex: 0 0 480px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.panel-scroll {
  flex: 1;
  overflow-y: auto;
}

.panel-inner {
  padding: 28px 32px;
}

.section-header {
  margin-bottom: 24px;

  h3 {
    margin: 0 0 4px;
    font-size: 16px;
    font-weight: 700;
    color: var(--r-text-primary);
    letter-spacing: -0.02em;
  }
}

.section-sub {
  margin: 0;
  font-size: 13px;
  color: var(--r-text-muted);
}

// ── Channel cards ──
.channel-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.channel-card {
  display: flex;
  align-items: stretch;
  border: 1.5px solid var(--r-border);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    border-color: var(--r-border-dark);
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
  }

  &.active {
    border-color: var(--r-accent);
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);

    .card-indicator {
      opacity: 1;
    }

    .card-check {
      opacity: 1;
    }
  }
}

.card-indicator {
  width: 3px;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.card-body {
  flex: 1;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-tag {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  padding: 1px 8px;
  border-radius: 4px;
  border: 1px solid;
  letter-spacing: 0.04em;
}

.card-check {
  color: var(--r-accent);
  opacity: 0;
  transition: opacity 0.15s ease;
  display: flex;
  align-items: center;
}

.card-desc {
  font-size: 12px;
  color: var(--r-text-muted);
  line-height: 1.4;
}

// ── Section divider ──
.section-divider {
  height: 1px;
  background: var(--r-bg-hover);
  margin: 24px 0;
}

.section-label {
  margin: 0 0 14px;
  font-size: 13px;
  font-weight: 600;
  color: var(--r-text-primary);
  letter-spacing: -0.01em;
}

// ── Params ──
.params-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.param-row {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.param-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--r-text-secondary);
}

.req {
  color: var(--r-danger);
  margin-left: 2px;
  font-weight: 600;
}

// ── Enable row ──
.enable-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.enable-info {
  display: flex;
  flex-direction: column;
}

.enable-desc {
  font-size: 12px;
  color: var(--r-text-muted);
}

// ── Save ──
.save-bar {
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid var(--r-bg-hover);
}

// ── Right panel: Guide ──
.guide-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--r-bg-panel);
  border-left: 1px solid var(--r-border);

  &.empty {
    align-items: center;
    justify-content: center;
  }
}

.guide-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 28px 32px;

  &::-webkit-scrollbar {
    width: 5px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--r-border-dark);
    border-radius: 3px;

    &:hover {
      background: var(--r-text-muted);
    }
  }
}

.guide-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--r-text-muted);
  margin-bottom: 20px;

  svg { opacity: 0.6; }
}

.guide-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--r-text-muted);
  font-size: 13px;

  .empty-icon {
    opacity: 0.3;
  }
}

// ── Guide markdown prose ──
.guide-prose {
  font-size: 13px;
  line-height: 1.75;
  color: var(--r-text-primary);

  :deep(h2) {
    font-size: 14px;
    font-weight: 700;
    color: var(--r-text-primary);
    margin: 0 0 12px;
    letter-spacing: -0.01em;
  }

  :deep(h3) {
    font-size: 14px;
    font-weight: 600;
    color: var(--r-text-primary);
    margin: 22px 0 8px;
    padding-bottom: 6px;
    border-bottom: 1px solid var(--r-border);
  }

  :deep(ol),
  :deep(ul) {
    padding-left: 18px;
    margin: 6px 0;
  }

  :deep(li) {
    margin: 4px 0;
    padding-left: 2px;
  }

  :deep(code) {
    background: var(--r-border);
    color: var(--r-text-primary);
    padding: 1px 5px;
    border-radius: 3px;
    font-size: 12px;
    font-family: var(--r-font-mono);
  }

  :deep(a) {
    color: var(--r-accent);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }

  :deep(blockquote) {
    margin: 12px 0;
    padding: 10px 14px;
    border-left: 3px solid var(--r-accent);
    background: var(--r-accent-bg);
    border-radius: 0 6px 6px 0;
    color: var(--r-accent-hover);
    font-size: 12px;

    p { margin: 0; }
  }

  :deep(strong) {
    color: var(--r-text-primary);
    font-weight: 600;
  }
}

// ── Transition ──
.params-fade-enter-active,
.params-fade-leave-active {
  transition: all 0.2s ease;
}

.params-fade-enter-from,
.params-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
