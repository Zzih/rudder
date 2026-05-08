<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import {
  getNotificationProviderDefinitions,
  getNotificationConfig,
  saveNotificationConfig,
} from '@/api/notification'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="notification"
    enable-label-key="notification.enableNotification"
    :get-provider-definitions="getNotificationProviderDefinitions"
    :get-config="getNotificationConfig"
    :save-config="saveNotificationConfig"
  />
</template>
