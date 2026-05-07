<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import { getVersionProviderDefinitions, getVersionConfig, saveVersionConfig } from '@/api/version-config'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="version"
    enable-label-key="version.enableVersion"
    :get-provider-definitions="getVersionProviderDefinitions"
    :get-config="getVersionConfig"
    :save-config="saveVersionConfig"
  />
</template>
