<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import { getAiVectorProviderDefinitions, getAiVectorConfig, saveAiVectorConfig, listAiVectorConfigs, testAiVectorConfig } from '@/api/ai-vector-config'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="aiAdmin.vector"
    enable-label-key="aiAdmin.vector.enable"
    :get-provider-definitions="getAiVectorProviderDefinitions"
    :get-config="getAiVectorConfig"
    :list-configs="listAiVectorConfigs"
    :save-config="saveAiVectorConfig"
    :test-config="testAiVectorConfig"
  />
</template>
