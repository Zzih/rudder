<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  content: string
}>()

const MAX_LINES = 5000
const logContainer = ref<HTMLDivElement>()
const showAll = ref(false)

/** Count newlines without allocating a full split array */
function countLines(s: string): number {
  if (!s) return 0
  let count = 1
  let pos = 0
  while ((pos = s.indexOf('\n', pos)) !== -1) { count++; pos++ }
  return count
}

const lineCount = computed(() => countLines(props.content))
const truncated = computed(() => !showAll.value && lineCount.value > MAX_LINES)
const displayContent = computed(() => {
  if (!props.content) return t('ide.noLogs')
  if (!truncated.value) return props.content
  // Only split when we need to truncate
  return props.content.split('\n').slice(-MAX_LINES).join('\n')
})

watch(() => props.content, async () => {
  await nextTick()
  const el = logContainer.value
  if (!el) return
  // Only auto-scroll if user is near the bottom (within 80px)
  const isNearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80
  if (isNearBottom) el.scrollTop = el.scrollHeight
})
</script>

<template>
  <div ref="logContainer" class="log-viewer">
    <div v-if="truncated" class="log-viewer__truncated" @click="showAll = true">
      {{ t('ide.logTruncated', { total: lineCount, shown: MAX_LINES }) }}
    </div>
    <pre class="log-content">{{ displayContent }}</pre>
  </div>
</template>

<style scoped>
.log-viewer {
  height: 100%;
  overflow-y: auto;
  background: var(--r-bg-panel);
  padding: 10px 14px;
  font-family: var(--r-font-mono);
}

.log-content {
  color: var(--r-text-secondary);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

.log-viewer__truncated {
  padding: 6px 12px;
  margin-bottom: 8px;
  background: var(--r-warning-bg);
  color: var(--r-warning);
  border: 1px solid var(--r-warning-border);
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  text-align: center;
}
</style>
