<script setup lang="ts">
import { computed } from 'vue'
import { getTaskIconUrl } from '@/utils/taskIconUrl'

const props = defineProps<{ type: string; size?: number }>()

const iconUrl = computed(() => getTaskIconUrl(props.type))
const iconSize = computed(() => (props.size || 20) + 'px')
</script>

<template>
  <img
    v-if="iconUrl"
    :src="iconUrl"
    :width="iconSize"
    :height="iconSize"
    :alt="type"
    class="task-icon"
  >
  <div v-else class="task-icon-fallback" :style="{ width: iconSize, height: iconSize }">
    {{ type.charAt(0) }}
  </div>
</template>

<style scoped>
.task-icon {
  display: inline-block;
  flex-shrink: 0;
  object-fit: contain;
}

.task-icon-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--r-border);
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--r-text-muted);
}
</style>
