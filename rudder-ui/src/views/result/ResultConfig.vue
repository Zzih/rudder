<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import { getResultProviderDefinitions, getResultConfig, saveResultConfig } from '@/api/result-config'

const { t } = useI18n()
</script>

<template>
  <SpiConfigPage
    i18n-prefix="resultFormat"
    enable-label-key="resultFormat.enableResultFormat"
    :get-provider-definitions="getResultProviderDefinitions"
    :get-config="getResultConfig"
    :save-config="saveResultConfig"
    :extra-fields="['defaultQueryRows']"
  >
    <template #extra-settings="{ form }">
      <section class="spi-section">
        <h4 class="spi-section__title">{{ t('resultFormat.queryLimitTitle') }}</h4>
        <div class="spi-param">
          <label class="spi-param__label">{{ t('resultFormat.defaultQueryRows') }}</label>
          <el-input-number
            v-model="form.extra.defaultQueryRows"
            :min="1"
            :max="10000000"
            :step="1000"
            :placeholder="'1000'"
            controls-position="right"
            style="width: 100%"
          />
          <span class="spi-param__hint">{{ t('resultFormat.defaultQueryRowsHint') }}</span>
        </div>
      </section>
    </template>
  </SpiConfigPage>
</template>

<style scoped lang="scss">
.spi-section {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid var(--r-border-light);
}
.spi-section__title {
  margin: 0 0 12px;
  font-size: var(--r-font-base);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
}
.spi-param {
  display: flex; flex-direction: column; gap: 5px;
}
.spi-param__label {
  font-size: var(--r-font-sm);
  color: var(--r-text-secondary);
  font-weight: var(--r-weight-medium);
}
.spi-param__hint {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  line-height: var(--r-leading-snug);
}
</style>
