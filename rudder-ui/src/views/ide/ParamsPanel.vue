<template>
  <div class="pp">
    <div class="pp-head">
      <div class="pp-head__left">
        <span class="pp-head__title">{{ t('ide.params') }}</span>
        <span v-if="rows.length" class="pp-head__badge">{{ rows.length }}</span>
      </div>
      <button class="pp-head__add" @click="addRow">
        <el-icon :size="11"><Plus /></el-icon>
      </button>
    </div>

    <div v-if="!rows.length" class="pp-empty" @click="addRow">
      <div class="pp-empty__icon">
        <el-icon :size="18"><Setting /></el-icon>
      </div>
      <span>{{ t('ide.paramHint') }}</span>
    </div>

    <div v-else class="pp-list">
      <div v-for="(row, idx) in rows" :key="row.uid" class="pp-row">
        <div class="pp-field">
          <span class="pp-field__badge pp-field__badge--key">K</span>
          <input
            :ref="(el: any) => { if (el) rowRefs[idx] = el }"
            v-model="row.key"
            class="pp-field__input"
            placeholder="key"
            spellcheck="false"
            @input="emitRecord"
          />
        </div>
        <div class="pp-field pp-field--val">
          <span class="pp-field__badge pp-field__badge--val">V</span>
          <input
            v-model="row.val"
            class="pp-field__input"
            placeholder="value"
            spellcheck="false"
            @input="emitRecord"
          />
        </div>
        <button class="pp-row__del" @click="removeRow(idx)">
          <el-icon :size="11"><Close /></el-icon>
        </button>
      </div>
    </div>

    <div class="pp-ref">
      <button class="pp-ref__toggle" @click="refOpen = !refOpen">
        <el-icon :size="9" :class="{ 'is-open': refOpen }"><ArrowRight /></el-icon>
        <span>{{ t('ide.paramTimeRef') }}</span>
      </button>
      <div v-show="refOpen" class="pp-ref__grid">
        <div v-for="r in timeRefs" :key="r.code" class="pp-ref__chip" @click="copyRef(r.code)">
          <code>{{ r.code }}</code>
          <span class="pp-ref__hint">{{ r.desc }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Plus, Close, Setting, ArrowRight } from '@element-plus/icons-vue'

const { t } = useI18n()

let nextUid = 0
interface Row { uid: number; key: string; val: string }

const props = defineProps<{ modelValue: Record<string, string> }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: Record<string, string>): void }>()

const rows = ref<Row[]>(toRows(props.modelValue))
const refOpen = ref(false)
const rowRefs = ref<HTMLInputElement[]>([])

const timeRefs = computed(() => [
  { code: '$[yyyyMMdd]', desc: t('ide.paramTimeToday') },
  { code: '$[yyyyMMdd-1]', desc: t('ide.paramTimeYesterday') },
  { code: '$[yyyyMMddHHmmss]', desc: t('ide.paramTimeNow') },
  { code: '$[yyyy-MM-dd]', desc: t('ide.paramTimeDash') },
  { code: '$[add_months(yyyyMMdd,-1)]', desc: t('ide.paramAddMonths') },
  { code: '$[month_begin(yyyyMMdd,0)]', desc: t('ide.paramMonthBegin') },
  { code: '$[month_end(yyyyMMdd,0)]', desc: t('ide.paramMonthEnd') },
  { code: '$[week_begin(yyyy-MM-dd,0)]', desc: t('ide.paramWeekBegin') },
  { code: '$[week_end(yyyy-MM-dd,0)]', desc: t('ide.paramWeekEnd') },
  { code: '$[last_day(yyyy-MM-dd)]', desc: t('ide.paramLastDay') },
])

function toRows(rec: Record<string, string>): Row[] {
  return Object.entries(rec).map(([k, v]) => ({ uid: nextUid++, key: k, val: v }))
}

let selfEmitting = false
watch(() => props.modelValue, (nv) => {
  if (selfEmitting) return
  rows.value = toRows(nv)
}, { deep: true })

function emitRecord() {
  const obj: Record<string, string> = {}
  for (const r of rows.value) { const k = r.key.trim(); if (k) obj[k] = r.val }
  selfEmitting = true
  emit('update:modelValue', obj)
  Promise.resolve().then(() => { selfEmitting = false })
}

function addRow() {
  rows.value.push({ uid: nextUid++, key: '', val: '' })
  nextTick(() => {
    const last = rowRefs.value[rowRefs.value.length - 1]
    last?.focus()
  })
}

function removeRow(idx: number) {
  rows.value.splice(idx, 1)
  emitRecord()
}

function copyRef(code: string) {
  navigator.clipboard.writeText(code)
  ElMessage({ message: 'Copied', type: 'success', duration: 1000, offset: 60, grouping: true })
}
</script>

<style scoped lang="scss">
@use '@/styles/ide.scss' as *;

.pp {
  width: 100%;
}

// ---- Header ----
.pp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid $ide-border;
}

.pp-head__left {
  display: flex;
  align-items: center;
  gap: 7px;
}

.pp-head__title {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: $ide-text-muted;
}

.pp-head__badge {
  font-size: 11px;
  font-weight: 700;
  color: $ide-accent;
  background: $ide-accent-bg;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  border-radius: 5px;
  padding: 0 5px;
  font-family: var(--r-font-mono);
}

.pp-head__add {
  width: 26px;
  height: 26px;
  border: 1px solid $ide-border;
  border-radius: 6px;
  background: transparent;
  color: $ide-text-muted;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    border-color: $ide-accent;
    color: $ide-accent;
    background: $ide-accent-bg;
  }
}

// ---- Empty state ----
.pp-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 28px 14px;
  cursor: pointer;

  &:hover {
    .pp-empty__icon { background: $ide-accent-bg; color: $ide-accent; }
    span { color: $ide-text-secondary; }
  }

  span {
    font-size: 12px;
    color: $ide-text-disabled;
    transition: color 0.15s;
  }
}

.pp-empty__icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: $ide-hover-bg;
  color: $ide-text-muted;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

// ---- Parameter rows ----
.pp-list {
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.pp-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px;
  border-radius: 6px;
  transition: background 0.1s;

  &:hover {
    background: $ide-hover-bg;
    .pp-row__del { opacity: 1; }
  }
}

.pp-field {
  flex: 4;
  display: flex;
  align-items: center;
  gap: 6px;
  padding-left: 5px;
  height: 30px;
  background: $ide-hover-bg;
  border: 1px solid transparent;
  border-radius: 5px;
  overflow: hidden;
  transition: all 0.12s;

  &--val { flex: 6; }

  &:focus-within {
    border-color: $ide-accent;
    background: $ide-bg;
    box-shadow: 0 0 0 2px rgba($ide-accent, 0.1);

    .pp-field__badge--key { background: $ide-accent-bg; color: $ide-accent; }
    .pp-field__badge--val { background: $ide-spark-soft; color: $ide-spark; }
  }
}

// K/V 徽章 — 用独立背景, 不依赖父级 .pp-field 背景, 聚焦换肤时也不会丢字
.pp-field__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
  font-family: var(--r-font-mono);
  letter-spacing: 0;
  user-select: none;
  transition: background 0.12s, color 0.12s;
}

.pp-field__badge--key {
  background: rgba($ide-spark, 0.12);
  color: $ide-text-muted;
}

.pp-field__badge--val {
  background: rgba($ide-spark, 0.06);
  color: $ide-text-muted;
}

.pp-field__input {
  flex: 1;
  height: 100%;
  min-width: 0;
  padding: 0 8px 0 0;
  border: none;
  background: transparent;
  font-size: 12px;
  font-family: var(--r-font-mono);
  color: $ide-text;
  outline: none;

  &::placeholder { color: $ide-text-disabled; }
}

.pp-row__del {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  border: none;
  background: transparent;
  border-radius: 5px;
  color: $ide-text-disabled;
  cursor: pointer;
  opacity: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.1s;

  &:hover { color: var(--r-danger); background: var(--r-danger-bg); }
}

// ---- Time reference ----
.pp-ref {
  border-top: 1px solid $ide-border;
}

.pp-ref__toggle {
  display: flex;
  align-items: center;
  gap: 5px;
  width: 100%;
  padding: 8px 14px;
  border: none;
  background: none;
  font-size: 11px;
  font-weight: 600;
  color: $ide-text-muted;
  cursor: pointer;
  transition: color 0.12s;

  &:hover { color: $ide-text-secondary; }

  .el-icon {
    transition: transform 0.2s;
    &.is-open { transform: rotate(90deg); }
  }
}

.pp-ref__grid {
  padding: 0 8px 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.pp-ref__chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 5px;
  background: $ide-hover-bg;
  cursor: pointer;
  transition: all 0.12s;
  max-width: 100%;

  &:hover {
    background: $ide-border-light;
    code { color: var(--r-warning); }
  }

  code {
    font-family: var(--r-font-mono);
    font-size: 11px;
    color: var(--r-warning);
    white-space: nowrap;
    transition: color 0.12s;
  }
}

.pp-ref__hint {
  font-size: 11px;
  color: $ide-text-muted;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
