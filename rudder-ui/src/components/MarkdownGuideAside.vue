<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Marked } from 'marked'
import { renderSafeMarkdown } from '@/utils/safeMarkdown'
import { highlightCode } from '@/utils/markdownHljs'

const marked = new Marked({
  gfm: true,
  breaks: false,
  renderer: {
    code({ text, lang }) {
      const { language, html: code } = highlightCode(text, lang)
      return `<pre class="md-guide__pre"><code class="hljs language-${language}">${code}</code></pre>`
    },
  },
})

const props = defineProps<{
  /** 直接传 markdown 字符串(父组件自己管数据)。 */
  markdown?: string | null
  /** 自拉模式:组件挂载时调一次,locale 变化重拉。返回 markdown 正文字符串。 */
  loader?: () => Promise<string | null | undefined>
  kicker?: string
  label?: string
  emptyText?: string
}>()

const { t, locale } = useI18n()

const loaded = ref<string>('')
let fetchSeq = 0
async function fetchGuide() {
  if (!props.loader) return
  const my = ++fetchSeq
  try {
    const md = (await props.loader()) ?? ''
    // 过期请求(更晚发起的已开始)丢弃,避免旧响应覆盖新结果
    if (my === fetchSeq) loaded.value = md
  } catch {
    // 暂时失败保留上次成功的内容,避免抖动时 guide 闪烁清空
  }
}

onMounted(fetchGuide)
watch(locale, fetchGuide)

const html = computed(() => {
  const md = props.markdown ?? loaded.value
  return md ? renderSafeMarkdown(md, marked) : ''
})
</script>

<template>
  <aside class="md-guide" :class="{ 'is-empty': !html }">
    <div v-if="html" class="md-guide__scroll">
      <div class="md-guide__head">
        <span class="md-guide__kicker">{{ kicker ?? t('common.setupGuide') }}</span>
        <span v-if="label" class="md-guide__label">{{ label }}</span>
      </div>
      <div class="md-guide__prose" v-html="html" />
    </div>
    <div v-else class="md-guide__empty">
      <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path d="M9 12h6m-3-3v6m-7 4h14a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2Z"/>
      </svg>
      <span>{{ emptyText ?? t('common.emptyTip') }}</span>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.md-guide {
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

.md-guide__scroll {
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

.md-guide__head {
  display: flex; align-items: baseline; justify-content: space-between;
  padding-bottom: 10px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--r-border);
}

.md-guide__kicker {
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.md-guide__label {
  font-size: var(--r-font-xs);
  color: var(--r-text-secondary);
}

.md-guide__empty {
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  color: var(--r-text-muted);
  font-size: var(--r-font-sm);
  opacity: 0.7;
}

.md-guide__prose {
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

  :deep(pre.md-guide__pre) {
    margin: var(--r-space-3) 0;
    padding: var(--r-space-3) var(--r-space-4);
    background: var(--r-bg-card);
    border: 1px solid var(--r-border-light);
    border-radius: var(--r-radius-md);
    overflow-x: auto;
    font-size: var(--r-font-sm);
    line-height: var(--r-leading-snug);

    code {
      background: transparent;
      padding: 0;
      font-family: var(--r-font-mono);
      color: inherit;
      border-radius: 0;
      font-size: inherit;
    }

    &::-webkit-scrollbar { height: 6px; }
    &::-webkit-scrollbar-track { background: transparent; }
    &::-webkit-scrollbar-thumb {
      background: var(--r-border-dark);
      border-radius: 3px;
    }
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
</style>
