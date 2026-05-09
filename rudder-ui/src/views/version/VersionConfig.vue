<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import { getVersionProviderDefinitions, getVersionConfig, saveVersionConfig, listVersionConfigs } from '@/api/version-config'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="version"
    enable-label-key="version.enableVersion"
    :get-provider-definitions="getVersionProviderDefinitions"
    :get-config="getVersionConfig"
    :list-configs="listVersionConfigs"
    :save-config="saveVersionConfig"
  />
</template>
