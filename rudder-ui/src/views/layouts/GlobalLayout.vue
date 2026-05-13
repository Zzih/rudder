<script setup lang="ts">
import AppHeader from '@/components/AppHeader.vue'
</script>

<template>
  <div class="app-layout">
    <AppHeader />
    <main class="app-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped lang="scss">
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.app-content {
  flex: 1;
  overflow: auto;
  background: var(--r-bg-panel);
}
</style>
