<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import { getRuntimeProviderDefinitions, getRuntimeConfig, saveRuntimeConfig } from '@/api/runtime-config'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="runtime"
    enable-label-key="runtime.enableRuntime"
    :get-provider-definitions="getRuntimeProviderDefinitions"
    :get-config="getRuntimeConfig"
    :save-config="saveRuntimeConfig"
  />
</template>
