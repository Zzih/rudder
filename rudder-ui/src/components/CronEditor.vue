<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const { t } = useI18n()

// --- Field config ---
interface FieldState {
  mode: 'every' | 'interval' | 'specific' | 'range'
  intervalStart: number
  intervalStep: number
  rangeStart: number
  rangeEnd: number
  specificValues: number[]
}

const tabs = ['second', 'minute', 'hour', 'day', 'month', 'week'] as const
type TabKey = typeof tabs[number]
const activeTab = ref<TabKey>('minute')

const fieldConfigs: Record<TabKey, { min: number; max: number }> = {
  second: { min: 0, max: 59 },
  minute: { min: 0, max: 59 },
  hour:   { min: 0, max: 23 },
  day:    { min: 1, max: 31 },
  month:  { min: 1, max: 12 },
  week:   { min: 1, max: 7 },
}

// Week: 1=MON ... 7=SUN (ISO style, not Quartz)
const weekNames: Record<number, string> = { 1: 'MON', 2: 'TUE', 3: 'WED', 4: 'THU', 5: 'FRI', 6: 'SAT', 7: 'SUN' }
const weekNameToNum: Record<string, number> = { MON: 1, TUE: 2, WED: 3, THU: 4, FRI: 5, SAT: 6, SUN: 7 }

function weekNumToName(n: number): string { return weekNames[n] || String(n) }

function createDefault(tab: TabKey): FieldState {
  const cfg = fieldConfigs[tab]
  return {
    mode: 'every',
    intervalStart: cfg.min,
    intervalStep: tab === 'minute' ? 5 : 1,
    rangeStart: cfg.min,
    rangeEnd: cfg.max,
    specificValues: [],
  }
}

const states = ref<Record<TabKey, FieldState>>({
  second: { ...createDefault('second'), mode: 'specific', specificValues: [0] },
  minute: createDefault('minute'),
  hour:   createDefault('hour'),
  day:    createDefault('day'),
  month:  createDefault('month'),
  week:   createDefault('week'),
})

// --- Build expression ---
function buildField(tab: TabKey): string {
  const s = states.value[tab]
  const dayMode = states.value.day.mode
  const weekMode = states.value.week.mode
  // Day/Week mutual exclusion
  if (tab === 'day' && weekMode !== 'every') return '?'
  if (tab === 'week' && dayMode !== 'every') return '?'

  const isWeek = tab === 'week'
  switch (s.mode) {
    case 'every': return '*'
    case 'interval': {
      const start = isWeek ? weekNumToName(s.intervalStart) : s.intervalStart
      return `${start}/${s.intervalStep}`
    }
    case 'range': {
      const a = isWeek ? weekNumToName(s.rangeStart) : s.rangeStart
      const b = isWeek ? weekNumToName(s.rangeEnd) : s.rangeEnd
      return `${a}-${b}`
    }
    case 'specific': {
      if (!s.specificValues.length) return '*'
      const sorted = [...s.specificValues].sort((a, b) => a - b)
      return isWeek ? sorted.map(weekNumToName).join(',') : sorted.join(',')
    }
    default: return '*'
  }
}

const cronExpr = computed(() =>
  [buildField('second'), buildField('minute'), buildField('hour'),
   buildField('day'), buildField('month'), buildField('week')].join(' ')
)

// --- Parse expression ---
function parseWeekToken(token: string): number {
  return weekNameToNum[token.toUpperCase()] ?? (parseInt(token) || 1)
}

function parseField(expr: string, tab: TabKey) {
  const s = states.value[tab]
  const isWeek = tab === 'week'

  if (expr === '*' || expr === '?') {
    s.mode = 'every'
  } else if (expr.includes('/')) {
    const [start, step] = expr.split('/')
    s.mode = 'interval'
    s.intervalStart = isWeek ? parseWeekToken(start === '*' ? 'MON' : start) : (start === '*' ? 0 : parseInt(start) || 0)
    s.intervalStep = parseInt(step) || 1
  } else if (expr.includes('-') && !expr.includes(',')) {
    const [a, b] = expr.split('-')
    s.mode = 'range'
    s.rangeStart = isWeek ? parseWeekToken(a) : (parseInt(a) || 0)
    s.rangeEnd = isWeek ? parseWeekToken(b) : (parseInt(b) || 0)
  } else {
    s.mode = 'specific'
    s.specificValues = expr.split(',').map(v => isWeek ? parseWeekToken(v.trim()) : Number(v)).filter(n => !isNaN(n))
  }
}

let syncing = false

function parseCron(expr: string) {
  if (!expr) return
  const parts = expr.trim().split(/\s+/)
  if (parts.length >= 6) {
    syncing = true
    parseField(parts[0], 'second')
    parseField(parts[1], 'minute')
    parseField(parts[2], 'hour')
    parseField(parts[3], 'day')
    parseField(parts[4], 'month')
    parseField(parts[5], 'week')
    nextTick(() => { syncing = false })
  }
}

watch(() => props.modelValue, (v) => parseCron(v), { immediate: true })
watch(cronExpr, (v) => { if (!syncing && v !== props.modelValue) emit('update:modelValue', v) })

// Day/Week mutual exclusion (only when user changes, not during parse)
watch(() => states.value.day.mode, (m) => {
  if (!syncing && m !== 'every') states.value.week.mode = 'every'
})
watch(() => states.value.week.mode, (m) => {
  if (!syncing && m !== 'every') states.value.day.mode = 'every'
})

// --- Specific value chip options ---
function getChipOptions(tab: TabKey) {
  const cfg = fieldConfigs[tab]
  if (tab === 'week') {
    return [
      { label: 'MON', value: 1 }, { label: 'TUE', value: 2 }, { label: 'WED', value: 3 },
      { label: 'THU', value: 4 }, { label: 'FRI', value: 5 }, { label: 'SAT', value: 6 },
      { label: 'SUN', value: 7 },
    ]
  }
  return Array.from({ length: cfg.max - cfg.min + 1 }, (_, i) => {
    const v = cfg.min + i
    return { label: String(v).padStart(2, '0'), value: v }
  })
}

function toggleChip(value: number) {
  const s = states.value[activeTab.value]
  s.mode = 'specific'
  const idx = s.specificValues.indexOf(value)
  if (idx >= 0) s.specificValues.splice(idx, 1)
  else s.specificValues.push(value)
}

function focusInterval() { states.value[activeTab.value].mode = 'interval' }
function focusRange() { states.value[activeTab.value].mode = 'range' }

function fieldDisplay(tab: TabKey): string { return buildField(tab) }

// Presets
const presets = computed(() => [
  { label: t('workflow.cronPresetDaily'), cron: '0 0 2 * * ?' },
  { label: t('workflow.cronPresetHourly'), cron: '0 0 * * * ?' },
  { label: t('workflow.cronPreset30m'), cron: '0 */30 * * * ?' },
  { label: t('workflow.cronPresetWeekday'), cron: '0 0 8 ? * MON-FRI' },
  { label: t('workflow.cronPresetSunday'), cron: '0 0 0 ? * SUN' },
])

function applyPreset(cron: string) {
  parseCron(cron)
  emit('update:modelValue', cron)
}

const tabLabels: Record<TabKey, string> = {
  second: 'cronSecond', minute: 'cronMinute', hour: 'cronHour',
  day: 'cronDay', month: 'cronMonth', week: 'cronWeek',
}
</script>

<template>
  <div class="cron-editor">
    <!-- Presets -->
    <div class="ce-presets">
      <span
        v-for="p in presets" :key="p.cron"
        class="ce-preset" :class="{ active: cronExpr === p.cron }"
        @click="applyPreset(p.cron)"
      >{{ p.label }}</span>
    </div>

    <!-- Tabs -->
    <div class="ce-tabs">
      <div
        v-for="tab in tabs" :key="tab"
        class="ce-tab" :class="{ active: activeTab === tab }"
        @click="activeTab = tab"
      >
        <span class="ce-tab__label">{{ t(`workflow.${tabLabels[tab]}`) }}</span>
        <span class="ce-tab__value">{{ fieldDisplay(tab) }}</span>
      </div>
    </div>

    <!-- Tab body -->
    <div class="ce-body">
      <!-- Every -->
      <div class="ce-row" @click="states[activeTab].mode = 'every'">
        <el-radio v-model="states[activeTab].mode" value="every" />
        <span class="ce-row__text">{{ t('workflow.cronEvery') }}</span>
      </div>

      <!-- Interval -->
      <div class="ce-row" @click="states[activeTab].mode = 'interval'">
        <el-radio v-model="states[activeTab].mode" value="interval" />
        <span class="ce-row__text">{{ t('workflow.cronFrom') }}</span>
        <el-input-number
          v-model="states[activeTab].intervalStart"
          :min="fieldConfigs[activeTab].min" :max="fieldConfigs[activeTab].max"
          size="small" controls-position="right" class="ce-num"
          @focus="focusInterval"
        />
        <span class="ce-row__text">{{ t('workflow.cronEveryN') }}</span>
        <el-input-number
          v-model="states[activeTab].intervalStep"
          :min="1" :max="fieldConfigs[activeTab].max"
          size="small" controls-position="right" class="ce-num"
          @focus="focusInterval"
        />
        <span class="ce-row__text ce-row__unit">{{ t('workflow.cronExecute') }}</span>
      </div>

      <!-- Range -->
      <div class="ce-row" @click="states[activeTab].mode = 'range'">
        <el-radio v-model="states[activeTab].mode" value="range" />
        <span class="ce-row__text">{{ t('workflow.cronRange') }}</span>
        <el-input-number
          v-model="states[activeTab].rangeStart"
          :min="fieldConfigs[activeTab].min" :max="fieldConfigs[activeTab].max"
          size="small" controls-position="right" class="ce-num"
          @focus="focusRange"
        />
        <span class="ce-row__text">{{ t('workflow.cronTo') }}</span>
        <el-input-number
          v-model="states[activeTab].rangeEnd"
          :min="fieldConfigs[activeTab].min" :max="fieldConfigs[activeTab].max"
          size="small" controls-position="right" class="ce-num"
          @focus="focusRange"
        />
      </div>

      <!-- Specific -->
      <div class="ce-row ce-row--col">
        <div class="ce-row__head" @click="states[activeTab].mode = 'specific'">
          <el-radio v-model="states[activeTab].mode" value="specific" />
          <span class="ce-row__text">{{ t('workflow.cronSpecific') }}</span>
        </div>
        <div v-if="states[activeTab].mode === 'specific'" class="ce-chips">
          <span
            v-for="opt in getChipOptions(activeTab)" :key="opt.value"
            class="ce-chip" :class="{ active: states[activeTab].specificValues.includes(opt.value), wide: activeTab === 'week' }"
            @click.stop="toggleChip(opt.value)"
          >{{ opt.label }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.cron-editor {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-3);
}

/* ===== Presets ===== */
.ce-presets {
  display: flex;
  flex-wrap: wrap;
  gap: var(--r-space-2);
}
.ce-preset {
  padding: var(--r-space-1) var(--r-space-3);
  border-radius: 999px;
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-medium);
  color: var(--r-text-tertiary);
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  cursor: pointer;
  user-select: none;
  transition: color 0.15s ease, background 0.15s ease, border-color 0.15s ease;

  &:hover {
    color: var(--r-text-primary);
    border-color: var(--r-border-dark);
  }
  &.active {
    color: var(--r-accent);
    background: var(--r-accent-bg);
    border-color: var(--r-accent-border);
  }
}

/* ===== Tabs: segmented control ===== */
.ce-tabs {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: var(--r-space-1);
  padding: var(--r-space-1);
  background: var(--r-bg-panel);
  border-radius: var(--r-radius-md);
}
.ce-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: var(--r-space-2) var(--r-space-1);
  cursor: pointer;
  user-select: none;
  border-radius: var(--r-radius-sm);
  transition: background 0.15s ease, box-shadow 0.15s ease;

  &:hover { background: var(--r-bg-hover); }
  &.active {
    background: var(--r-bg-card);
    box-shadow: var(--r-shadow-sm);
  }
}
.ce-tab__label {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.ce-tab__value {
  font-size: var(--r-font-sm);
  font-family: var(--r-font-mono);
  color: var(--r-text-primary);
  font-weight: var(--r-weight-medium);
}

/* ===== Body rows ===== */
.ce-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ce-row {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  padding: var(--r-space-2) var(--r-space-3);
  border-radius: var(--r-radius-sm);
  cursor: pointer;
  transition: background 0.12s ease;

  &:hover { background: var(--r-bg-panel); }
  &--col { flex-direction: column; align-items: stretch; cursor: default; padding-top: var(--r-space-2); }
  &__head { display: flex; align-items: center; gap: var(--r-space-2); cursor: pointer; }
  &__text { font-size: var(--r-font-base); color: var(--r-text-secondary); white-space: nowrap; }
  &__unit { color: var(--r-text-muted); }
}

.ce-num {
  width: 92px;
  :deep(.el-input__inner) {
    text-align: center;
    font-family: var(--r-font-mono);
  }
}

/* ===== Specific-value chip grid ===== */
.ce-chips {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(40px, 1fr));
  gap: var(--r-space-1);
  margin-top: var(--r-space-2);
  padding-left: 30px;
}
.ce-chip {
  min-width: 0;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 var(--r-space-1);
  font-size: var(--r-font-sm);
  font-family: var(--r-font-mono);
  border-radius: var(--r-radius-sm);
  border: 1px solid var(--r-border);
  color: var(--r-text-secondary);
  background: var(--r-bg-card);
  cursor: pointer;
  user-select: none;
  transition: all 0.12s ease;

  &:hover {
    border-color: var(--r-accent-border);
    color: var(--r-accent);
  }
  &.active {
    background: var(--r-accent);
    color: #fff;
    border-color: transparent;
  }
  &.wide {
    font-size: var(--r-font-xs);
  }
}
</style>
