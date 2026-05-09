<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import { getFileProviderDefinitions, getFileConfig, saveFileConfig, listFileConfigs } from '@/api/file-config'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="fileStorage"
    enable-label-key="fileStorage.enableFileStorage"
    :get-provider-definitions="getFileProviderDefinitions"
    :get-config="getFileConfig"
    :list-configs="listFileConfigs"
    :save-config="saveFileConfig"
  />
</template>
