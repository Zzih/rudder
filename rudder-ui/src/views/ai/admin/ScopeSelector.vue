<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  label: string
  desc: string
  allLabel: string
  selected: string[]
  options: string[]
  loading?: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{ 'update:selected': [value: string[]] }>()

type Mode = 'all' | 'pick'

// mode 独立于 selected.length 存储:光靠 selected 推导会导致"点指定但还没选值时
// getter 立刻把它算回 all",radio 切不动。selected 非空时强制 pick;为空时尊重用户选择。
const mode = ref<Mode>(props.selected.length > 0 ? 'pick' : 'all')

watch(() => props.selected, (v) => {
  if (v.length > 0) mode.value = 'pick'
})

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
      <el-select
        v-if="mode === 'pick'"
        :model-value="selected"
        multiple
        filterable
        collapse-tags
        collapse-tags-tooltip
        :loading="loading"
        :disabled="disabled"
        :placeholder="$t('aiAdmin.metaSync.scopePickPlaceholder')"
        class="scope-selector__picker"
        @update:model-value="onPickChange"
      >
        <el-option v-for="o in options" :key="o" :label="o" :value="o" />
      </el-select>
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

.scope-selector__picker {
  flex: 1;
  min-width: 240px;
}
</style>
