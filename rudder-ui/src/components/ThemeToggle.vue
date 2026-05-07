<script setup lang="ts">
import { computed } from 'vue'
import { useThemeStore } from '@/stores/theme'
import type { ThemeMode } from '@/stores/theme'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const themeStore = useThemeStore()

const modes: { value: ThemeMode; icon: string; label: string }[] = [
  { value: 'light',  icon: 'Sunny',   label: 'theme.light' },
  { value: 'dark',   icon: 'Moon',    label: 'theme.dark' },
  { value: 'system', icon: 'Monitor', label: 'theme.system' },
]

const currentIcon = computed(() => modes.find(m => m.value === themeStore.mode)?.icon ?? 'Monitor')
</script>

<template>
  <el-dropdown trigger="click" @command="(m: ThemeMode) => themeStore.setMode(m)">
    <div class="theme-btn">
      <el-icon class="theme-icon"><component :is="currentIcon" /></el-icon>
    </div>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item v-for="m in modes" :key="m.value"
                          :command="m.value" :disabled="themeStore.mode === m.value">
          <span class="theme-menu-item">
            <el-icon class="theme-menu-icon"><component :is="m.icon" /></el-icon>
            {{ t(m.label) }}
          </span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.theme-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 5px 8px;
  border-radius: 5px;
  color: var(--r-text-tertiary);
  transition: all 0.12s;
}
.theme-btn:hover {
  background: var(--r-bg-hover);
  color: var(--r-text-primary);
}
.theme-icon {
  font-size: 16px;
}
.theme-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.theme-menu-icon {
  font-size: 14px;
}
</style>
