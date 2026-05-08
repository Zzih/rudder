<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import { getPublishProviderDefinitions, getPublishConfig, savePublishConfig } from '@/api/publish-config'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="publish"
    enable-label-key="publish.enablePublish"
    :get-provider-definitions="getPublishProviderDefinitions"
    :get-config="getPublishConfig"
    :save-config="savePublishConfig"
  />
</template>
