<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'
import { renderSafeMarkdown } from '@/utils/safeMarkdown'
import { listMcpClients, type McpClientGuide } from '@/api/mcp'

const { t, locale } = useI18n()

const baseUrl = `${window.location.origin}/mcp`
const clients = ref<McpClientGuide[]>([])
const activeId = ref<string>('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const lang = locale.value?.startsWith('zh') ? 'zh' : 'en'
    const { data } = await listMcpClients(lang)
    clients.value = data ?? []
    if (clients.value.length && !clients.value.find((c) => c.id === activeId.value)) {
      activeId.value = clients.value[0].id
    }
  } finally {
    loading.value = false
  }
}

const activeClient = computed(() => clients.value.find((c) => c.id === activeId.value) ?? null)

const renderedGuide = computed(() => {
  const md = activeClient.value?.guide
  if (!md) return ''
  return renderSafeMarkdown(md.replace(/\{\{baseUrl\}\}/g, baseUrl))
})

async function copyBaseUrl() {
  try {
    await navigator.clipboard.writeText(baseUrl)
    ElMessage.success(t('mcpPage.copied'))
  } catch { /* ignore */ }
}

onMounted(load)
</script>

<template>
  <div class="mcp-guide-page" v-loading="loading">
    <div class="mcp-guide-split">
      <!-- LEFT: client cards -->
      <aside class="mcp-guide-clients">
        <div class="clients-head">
          <span class="clients-head__title">{{ t('mcpPage.clientChoose') }}</span>
          <span v-if="clients.length" class="clients-head__count">{{ clients.length }}</span>
        </div>
        <div class="clients-list">
          <button
            v-for="c in clients" :key="c.id"
            class="client-card"
            :class="[`is-${c.color}`, { 'is-active': c.id === activeId }]"
            @click="activeId = c.id"
          >
            <span class="client-card__dot" />
            <span class="client-card__body">
              <span class="client-card__name">{{ c.label }}</span>
              <span v-if="c.description" class="client-card__desc">{{ c.description }}</span>
            </span>
            <span class="client-card__check" aria-hidden="true">
              <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
                <path d="M13.3 4.3a1 1 0 0 1 0 1.4l-6 6a1 1 0 0 1-1.4 0l-2.6-2.6a1 1 0 1 1 1.4-1.4L6.6 9.6l5.3-5.3a1 1 0 0 1 1.4 0Z" fill="currentColor"/>
              </svg>
            </span>
          </button>
        </div>
      </aside>

      <!-- RIGHT: guide markdown -->
      <section class="mcp-guide-doc" :class="{ 'is-empty': !renderedGuide }">
        <div v-if="renderedGuide" class="mcp-guide-doc__scroll">
          <div class="mcp-guide-doc__head">
            <div class="mcp-guide-doc__head-left">
              <span class="mcp-guide-doc__kicker">{{ t('mcpPage.setupGuide') }}</span>
              <h4 v-if="activeClient" class="mcp-guide-doc__title">{{ activeClient.label }}</h4>
            </div>
            <button class="mcp-guide-doc__url" @click="copyBaseUrl" :title="t('mcpPage.copy')">
              <span class="mcp-guide-doc__url-text">{{ baseUrl }}</span>
              <el-icon class="mcp-guide-doc__url-icon"><CopyDocument /></el-icon>
            </button>
          </div>
          <div class="mcp-guide-doc__prose" v-html="renderedGuide" />
        </div>
        <div v-else class="mcp-guide-doc__empty">{{ t('mcpPage.emptyHint') }}</div>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.mcp-guide-page {
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: var(--r-radius-lg);
  overflow: hidden;
  box-shadow: var(--r-shadow-sm);
}

.mcp-guide-split {
  display: flex;
  min-height: 520px;
}

/* ===== LEFT ===== */
.mcp-guide-clients {
  flex: 0 0 280px;
  border-right: 1px solid var(--r-border-light);
  display: flex;
  flex-direction: column;
  background: var(--r-bg-panel);
}

.clients-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--r-space-4) var(--r-space-4) var(--r-space-3);

  &__title {
    font-size: var(--r-font-xs);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }

  &__count {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 20px;
    height: 18px;
    padding: 0 6px;
    background: var(--r-bg-card);
    border: 1px solid var(--r-border);
    border-radius: 999px;
    font-family: var(--r-font-mono);
    font-size: 10px;
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-tertiary);
  }
}

.clients-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0 var(--r-space-3) var(--r-space-3);
}

.client-card {
  all: unset;
  display: grid;
  grid-template-columns: 12px 1fr 14px;
  align-items: center;
  gap: var(--r-space-3);
  padding: 10px var(--r-space-3);
  border: 1px solid transparent;
  border-radius: var(--r-radius-md);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, box-shadow 0.18s;

  &:hover {
    background: var(--r-bg-hover);
  }

  &.is-active {
    background: var(--r-bg-card);
    border-color: var(--c-color);
    box-shadow:
      0 0 0 3px color-mix(in srgb, var(--c-color) 10%, transparent),
      0 1px 2px rgb(0 0 0 / 0.04);

    .client-card__check { opacity: 1; }
    .client-card__name { color: var(--c-color); }
  }

  &.is-orange { --c-color: var(--r-orange); --c-bg: var(--r-orange-bg); }
  &.is-purple { --c-color: var(--r-purple); --c-bg: var(--r-purple-bg); }
  &.is-teal   { --c-color: var(--r-teal);   --c-bg: var(--r-teal-bg); }
  &.is-cyan   { --c-color: var(--r-cyan);   --c-bg: var(--r-cyan-bg); }
  &.is-pink   { --c-color: var(--r-pink);   --c-bg: var(--r-pink-bg); }
}

.client-card__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--c-color);
  box-shadow: 0 0 0 3px var(--c-bg);
}

.client-card__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.client-card__name {
  font-size: var(--r-font-base);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  transition: color 0.15s;
}

.client-card__desc {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  line-height: var(--r-leading-snug);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.client-card__check {
  color: var(--c-color);
  opacity: 0;
  transition: opacity 0.15s;
  display: inline-flex;
}

/* ===== RIGHT ===== */
.mcp-guide-doc {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--r-bg-card);

  &.is-empty {
    align-items: center;
    justify-content: center;
  }
}

.mcp-guide-doc__scroll {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px 32px;
}

.mcp-guide-doc__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--r-space-4);
  padding-bottom: var(--r-space-3);
  margin-bottom: var(--r-space-4);
  border-bottom: 1px solid var(--r-border-light);
}

.mcp-guide-doc__head-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.mcp-guide-doc__kicker {
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.mcp-guide-doc__title {
  margin: 0;
  font-size: var(--r-font-lg);
  font-weight: var(--r-weight-bold);
  color: var(--r-text-primary);
  letter-spacing: -0.02em;
}

.mcp-guide-doc__url {
  all: unset;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px 4px 10px;
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-sm);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
  flex-shrink: 0;

  &:hover {
    border-color: var(--r-accent-border);
    background: var(--r-bg-card);

    .mcp-guide-doc__url-icon { color: var(--r-accent); }
  }

  &-text {
    font-family: var(--r-font-mono);
    font-size: var(--r-font-xs);
    color: var(--r-text-secondary);
    font-weight: var(--r-weight-medium);
  }

  &-icon {
    color: var(--r-text-muted);
    font-size: 12px;
    transition: color 0.15s;
  }
}

.mcp-guide-doc__empty {
  color: var(--r-text-muted);
  font-size: var(--r-font-sm);
}

.mcp-guide-doc__prose {
  font-size: var(--r-font-base);
  line-height: var(--r-leading-loose);
  color: var(--r-text-secondary);

  :deep(h2) {
    font-size: var(--r-font-lg);
    font-weight: var(--r-weight-bold);
    color: var(--r-text-primary);
    margin: 0 0 var(--r-space-4);
    padding-bottom: var(--r-space-3);
    border-bottom: 2px solid var(--r-border);
    letter-spacing: -0.01em;
  }

  :deep(h3) {
    position: relative;
    font-size: var(--r-font-md);
    font-weight: var(--r-weight-bold);
    color: var(--r-text-primary);
    margin: var(--r-space-6) 0 var(--r-space-3);
    padding-left: 12px;
    padding-bottom: var(--r-space-2);
    border-bottom: 1px solid var(--r-border-light);

    &::before {
      content: "";
      position: absolute;
      left: 0;
      top: 4px;
      bottom: 8px;
      width: 3px;
      background: var(--r-accent);
      border-radius: 2px;
    }
  }

  :deep(p) {
    margin: var(--r-space-2) 0;
  }

  :deep(ol),
  :deep(ul) {
    padding-left: var(--r-space-5);
    margin: var(--r-space-2) 0;
  }

  :deep(li) {
    margin: var(--r-space-1) 0;
  }

  :deep(li::marker) {
    color: var(--r-text-muted);
  }

  :deep(a) {
    color: var(--r-accent);
    text-decoration: none;
    border-bottom: 1px solid color-mix(in srgb, var(--r-accent) 30%, transparent);
    transition: border-color 0.15s;

    &:hover {
      border-bottom-color: var(--r-accent);
    }
  }

  :deep(code) {
    background: var(--r-bg-code);
    color: var(--r-text-primary);
    padding: 1px 5px;
    border: 1px solid var(--r-border-light);
    border-radius: 3px;
    font-size: var(--r-font-sm);
    font-family: var(--r-font-mono);
  }

  :deep(pre) {
    margin: var(--r-space-3) 0;
    padding: var(--r-space-3) var(--r-space-4);
    background: var(--r-bg-code);
    border: 1px solid var(--r-border-light);
    border-radius: var(--r-radius-md);
    overflow-x: auto;
    font-size: var(--r-font-sm);
    line-height: var(--r-leading-normal);

    code {
      background: none;
      border: none;
      padding: 0;
      color: var(--r-text-primary);
      font-size: inherit;
    }
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

  :deep(table) {
    width: 100%;
    border-collapse: collapse;
    margin: var(--r-space-3) 0;
    font-size: var(--r-font-sm);
    border: 1px solid var(--r-border-light);
    border-radius: var(--r-radius-md);
    overflow: hidden;
  }

  :deep(th),
  :deep(td) {
    padding: var(--r-space-2) var(--r-space-3);
    border-bottom: 1px solid var(--r-border-light);
    text-align: left;
  }

  :deep(tr:last-child td) {
    border-bottom: none;
  }

  :deep(th) {
    background: var(--r-bg-panel);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-primary);
    font-size: var(--r-font-xs);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  :deep(strong) {
    color: var(--r-text-primary);
    font-weight: var(--r-weight-semibold);
  }

  :deep(hr) {
    border: none;
    border-top: 1px solid var(--r-border-light);
    margin: var(--r-space-5) 0;
  }
}

@media (max-width: 720px) {
  .mcp-guide-split {
    flex-direction: column;
  }
  .mcp-guide-clients {
    flex: 0 0 auto;
    border-right: none;
    border-bottom: 1px solid var(--r-border-light);
  }
  .mcp-guide-doc__url-text {
    max-width: 160px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
