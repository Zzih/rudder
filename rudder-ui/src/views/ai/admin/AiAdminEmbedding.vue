<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import { getAiEmbeddingProviderDefinitions, getAiEmbeddingConfig, saveAiEmbeddingConfig, testAiEmbeddingConfig } from '@/api/ai-embedding-config'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="aiAdmin.embedding"
    enable-label-key="aiAdmin.embedding.enable"
    :get-provider-definitions="getAiEmbeddingProviderDefinitions"
    :get-config="getAiEmbeddingConfig"
    :save-config="saveAiEmbeddingConfig"
    :test-config="testAiEmbeddingConfig"
  />
</template>
