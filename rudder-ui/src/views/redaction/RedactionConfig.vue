<template>
  <div class="page-container redaction-page">
    <div class="page-header">
      <div class="redaction-page__title">
        <h3>{{ t('admin.redactionConfig') }}</h3>
        <span class="redaction-page__hint">{{ t('redaction.hint') }}</span>
      </div>
    </div>
    <el-tabs v-model="activeTab" class="redaction-page__tabs">
      <el-tab-pane :label="t('redaction.rules')" name="rules">
        <RedactionRules />
      </el-tab-pane>
      <el-tab-pane :label="t('redaction.strategies')" name="strategies">
        <RedactionStrategies />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import RedactionRules from './RedactionRules.vue'
import RedactionStrategies from './RedactionStrategies.vue'

const { t } = useI18n()
const activeTab = ref('rules')
</script>

<style scoped lang="scss">
@use '@/styles/admin.scss';

.redaction-page {
  &__title {
    display: flex;
    align-items: baseline;
    gap: var(--r-space-3);
  }

  &__hint {
    font-size: var(--r-font-sm);
    color: var(--r-text-muted);
  }

  &__tabs {
    :deep(.el-tabs__nav-wrap)::after { display: none; }
    :deep(.el-tabs__header) {
      margin-bottom: var(--r-space-4);
      border-bottom: 1px solid var(--r-border-light);
    }
    :deep(.el-tabs__item) {
      font-size: var(--r-font-md);
      font-weight: var(--r-weight-medium);
      color: var(--r-text-secondary);
      height: 44px;
      line-height: 44px;
      &.is-active {
        color: var(--r-accent);
        font-weight: var(--r-weight-semibold);
      }
    }
    :deep(.el-tabs__active-bar) {
      background-color: var(--r-accent);
      height: 2px;
    }
  }
}
</style>
