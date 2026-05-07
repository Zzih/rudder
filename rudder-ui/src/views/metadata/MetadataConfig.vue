<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import { getMetadataProviderDefinitions, getMetadataConfig, saveMetadataConfig } from '@/api/metadata-config'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="metadata"
    enable-label-key="metadata.enableMetadata"
    :get-provider-definitions="getMetadataProviderDefinitions"
    :get-config="getMetadataConfig"
    :save-config="saveMetadataConfig"
  />
</template>
