<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import { getAiVectorProviderDefinitions, getAiVectorConfig, saveAiVectorConfig, testAiVectorConfig } from '@/api/ai-vector-config'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="aiAdmin.vector"
    enable-label-key="aiAdmin.vector.enable"
    :get-provider-definitions="getAiVectorProviderDefinitions"
    :get-config="getAiVectorConfig"
    :save-config="saveAiVectorConfig"
    :test-config="testAiVectorConfig"
  />
</template>
