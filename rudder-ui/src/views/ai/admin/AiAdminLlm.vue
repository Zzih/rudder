<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import { getAiLlmProviderDefinitions, getAiLlmConfig, saveAiLlmConfig, listAiLlmConfigs, testAiLlmConfig } from '@/api/ai-llm-config'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="aiAdmin.llm"
    enable-label-key="aiAdmin.llm.enable"
    :get-provider-definitions="getAiLlmProviderDefinitions"
    :get-config="getAiLlmConfig"
    :list-configs="listAiLlmConfigs"
    :save-config="saveAiLlmConfig"
    :test-config="testAiLlmConfig"
  />
</template>
