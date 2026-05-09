<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import {
  getNotificationProviderDefinitions,
  getNotificationConfig,
  saveNotificationConfig,
  listNotificationConfigs,
} from '@/api/notification'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="notification"
    enable-label-key="notification.enableNotification"
    :get-provider-definitions="getNotificationProviderDefinitions"
    :get-config="getNotificationConfig"
    :list-configs="listNotificationConfigs"
    :save-config="saveNotificationConfig"
  />
</template>
