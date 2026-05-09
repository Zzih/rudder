<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import { getAiEmbeddingProviderDefinitions, getAiEmbeddingConfig, saveAiEmbeddingConfig, listAiEmbeddingConfigs, testAiEmbeddingConfig } from '@/api/ai-embedding-config'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="aiAdmin.embedding"
    enable-label-key="aiAdmin.embedding.enable"
    :get-provider-definitions="getAiEmbeddingProviderDefinitions"
    :get-config="getAiEmbeddingConfig"
    :list-configs="listAiEmbeddingConfigs"
    :save-config="saveAiEmbeddingConfig"
    :test-config="testAiEmbeddingConfig"
  />
</template>
