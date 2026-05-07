<template>
  <div class="redaction-admin">
    <header class="redaction-admin__head">
      <h3>{{ t('admin.redactionConfig') }}</h3>
      <span class="redaction-admin__hint">{{ t('redaction.hint') }}</span>
    </header>
    <el-tabs v-model="activeTab" class="redaction-admin__tabs">
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
.redaction-admin {
  padding: 20px 24px;
  height: 100%;
  overflow-y: auto;
  &__head {
    display: flex; align-items: baseline; gap: 12px; margin-bottom: 8px;
    h3 { margin: 0; font-size: 16px; font-weight: 700; color: var(--r-text-primary); letter-spacing: -0.02em; }
  }
  &__hint {
    font-size: 12px; color: var(--r-text-muted);
  }
  &__tabs {
    margin-top: 8px;
    :deep(.el-tabs__item) { font-size: 13px; }
  }
}
</style>
