<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import { getResultProviderDefinitions, getResultConfig, saveResultConfig, listResultConfigs } from '@/api/result-config'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="resultFormat"
    enable-label-key="resultFormat.enableResultFormat"
    :get-provider-definitions="getResultProviderDefinitions"
    :get-config="getResultConfig"
    :list-configs="listResultConfigs"
    :save-config="saveResultConfig"
  />
</template>
