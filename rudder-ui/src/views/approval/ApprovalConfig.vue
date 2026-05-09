<script setup lang="ts">
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import {
  getApprovalProviderDefinitions,
  getApprovalConfig,
  listApprovalConfigs,
  saveApprovalConfig,
} from '@/api/approval'
</script>

<template>
  <SpiConfigPage
    i18n-prefix="approval"
    enable-label-key="approval.enableApproval"
    :get-provider-definitions="getApprovalProviderDefinitions"
    :get-config="getApprovalConfig"
    :list-configs="listApprovalConfigs"
    :save-config="saveApprovalConfig"
  />
</template>
