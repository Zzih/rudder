<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import { getMetadataProviderDefinitions, getMetadataConfig, saveMetadataConfig, listMetadataConfigs } from '@/api/metadata-config'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="metadata"
    enable-label-key="metadata.enableMetadata"
    :get-provider-definitions="getMetadataProviderDefinitions"
    :get-config="getMetadataConfig"
    :list-configs="listMetadataConfigs"
    :save-config="saveMetadataConfig"
  />
</template>
