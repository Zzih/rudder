<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  label: string
  desc: string
  allLabel: string
  selected: string[]
  options: string[]
  /** 仅控 hint 区"加载中"文案;el-select 输入框不再显示 spinner(防 dropdown 内 loading 占位切换抖动) */
  loading?: boolean
  disabled?: boolean
  /** 传入则启用 remote search:el-select 切 :remote=true 模式,典型用于 10K+ catalog 场景。 */
  remoteMethod?: (keyword: string) => void
  /** filter 后总数;> options.length 时显示底部 hint 引导输入关键字或滚动加载。 */
  total?: number
  /** 传入则启用滚动加载下一页;client-side merge 模式不传,只走 keyword search */
  loadMore?: () => void
}>()

const emit = defineEmits<{ 'update:selected': [value: string[]] }>()
const { t } = useI18n()

type Mode = 'all' | 'pick'

// mode 独立于 selected.length 存储:光靠 selected 推导会导致"点指定但还没选值时
// getter 立刻把它算回 all",radio 切不动。selected 非空时强制 pick;为空时尊重用户选择。
const mode = ref<Mode>(props.selected.length > 0 ? 'pick' : 'all')

watch(() => props.selected, (v) => {
  if (v.length > 0) mode.value = 'pick'
})

// remote search 模式下,已选项可能不在当前 search 结果里 — 合并进 dropdown options,
// 否则 dropdown 关掉再开,user 看到 selected tag 但 dropdown 里没对应 option 不能点删。
// 已选项放最前面,便于快速点删;后跟当前 search 结果(剔除已选项,避免重复)
const displayOptions = computed(() => {
  if (!props.remoteMethod) return props.options
  const selectedSet = new Set(props.selected)
  const out = [...props.selected]
  for (const o of props.options) {
    if (!selectedSet.has(o)) out.push(o)
  }
  return out
})

// 仅 remote 模式 + 还有未加载项时显示;有 loadMore 走 load-hint,无走 search-hint(引导搜索)
const showHint = computed(() => {
  if (!props.remoteMethod) return false
  return (props.total ?? 0) > props.options.length
})
const hintKey = computed(() => props.loadMore ? 'common.dropdownLoadHint' : 'common.dropdownSearchHint')

function onModeChange(v: string | number | boolean | undefined) {
  const m = v === 'pick' ? 'pick' : 'all'
  mode.value = m
  if (m === 'all' && props.selected.length > 0) {
    emit('update:selected', [])
  }
}

function onPickChange(v: string[]) {
  emit('update:selected', v ?? [])
}

// 实例隔离 popperClass,避免多 ScopeSelector 同时打开时 querySelector 命中错误 wrap
const dropdownClass = 'scope-dd-' + Math.random().toString(36).slice(2, 8)
let scrollEl: HTMLElement | null = null
function onScroll() {
  const el = scrollEl
  if (!el || props.loading || !props.loadMore) return
  if (el.scrollTop + el.clientHeight < el.scrollHeight - 24) return
  if ((props.total ?? 0) <= props.options.length) return
  props.loadMore()
}
function detachScroll() {
  scrollEl?.removeEventListener('scroll', onScroll)
  scrollEl = null
}
function onDropdownVisible(visible: boolean) {
  if (!visible) { detachScroll(); return }
  if (!props.loadMore) return
  // popper teleport 时序不保证一个 nextTick 内挂载完成,重试几次避免 scroll 监听静默失败
  let tries = 0
  const tryAttach = () => {
    const wrap = document.querySelector(`.${dropdownClass} .el-select-dropdown__wrap`) as HTMLElement | null
    if (wrap) {
      detachScroll()
      scrollEl = wrap
      wrap.addEventListener('scroll', onScroll)
      return
    }
    if (++tries < 5) setTimeout(tryAttach, 50)
  }
  nextTick(tryAttach)
}
onUnmounted(detachScroll)
</script>

<template>
  <div class="scope-selector" :class="{ 'is-disabled': disabled }">
    <div class="scope-selector__head">
      <span class="scope-selector__label">{{ label }}</span>
      <span class="scope-selector__desc">{{ desc }}</span>
    </div>
    <div class="scope-selector__body">
      <el-radio-group :model-value="mode" size="small" :disabled="disabled" @change="onModeChange">
        <el-radio-button value="all">{{ allLabel }}</el-radio-button>
        <el-radio-button value="pick">{{ $t('aiAdmin.metaSync.scopePick') }}</el-radio-button>
      </el-radio-group>
      <div v-if="mode === 'pick'" class="scope-selector__picker-wrap">
        <el-select
          :model-value="selected"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          :remote="!!remoteMethod"
          :remote-method="remoteMethod"
          reserve-keyword
          :disabled="disabled"
          :placeholder="$t('aiAdmin.metaSync.scopePickPlaceholder')"
          :popper-class="`r-stable-dropdown ${dropdownClass}`"
          class="scope-selector__picker"
          @update:model-value="onPickChange"
          @visible-change="onDropdownVisible"
        >
          <el-option v-for="o in displayOptions" :key="o" :label="o" :value="o" />
        </el-select>
        <div class="scope-selector__hint">
          <template v-if="loading">{{ t('common.loading') }}</template>
          <template v-else-if="showHint">{{ t(hintKey, { total, shown: options.length }) }}</template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.scope-selector {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-2);

  &.is-disabled {
    opacity: 0.55;
    pointer-events: none;
  }
}

.scope-selector__head {
  display: flex;
  align-items: baseline;
  gap: var(--r-space-2);
}

.scope-selector__label {
  font-size: var(--r-font-base);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
}

.scope-selector__desc {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  line-height: var(--r-leading-snug);
}

.scope-selector__body {
  display: flex;
  gap: var(--r-space-3);
  align-items: center;
  flex-wrap: wrap;
}

.scope-selector__picker-wrap {
  flex: 1;
  min-width: 240px;
  display: flex;
  flex-direction: column;
}

.scope-selector__picker {
  width: 100%;
}

.scope-selector__hint {
  margin-top: 4px;
  min-height: calc(var(--r-font-xs) * 1.3);
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  line-height: 1.3;
}
</style>
