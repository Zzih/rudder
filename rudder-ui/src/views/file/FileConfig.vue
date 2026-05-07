<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import { getFileProviderDefinitions, getFileConfig, saveFileConfig } from '@/api/file-config'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="fileStorage"
    enable-label-key="fileStorage.enableFileStorage"
    :get-provider-definitions="getFileProviderDefinitions"
    :get-config="getFileConfig"
    :save-config="saveFileConfig"
  />
</template>
