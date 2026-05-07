<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useDatasourceStore } from '@/stores/datasource'
import { listDatasources } from '@/api/datasource'

const props = withDefaults(defineProps<{
  modelValue?: number
  datasourceType?: string
}>(), {
  modelValue: undefined,
  datasourceType: '',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | undefined): void
}>()

const dsStore = useDatasourceStore()

const filteredDatasources = computed(() => {
  let list = dsStore.datasources
  if (props.datasourceType) {
    list = list.filter((ds) => ds.datasourceType === props.datasourceType)
  }
  return list
})

function handleChange(val: number | undefined) {
  emit('update:modelValue', val)
}

onMounted(async () => {
  if (dsStore.datasources.length === 0) {
    try {
      const { data } = await listDatasources()
      dsStore.setDatasources(data)
    } catch {
      // Store remains empty; user can retry
    }
  }
})
</script>

<template>
  <el-select
    :model-value="modelValue"
    placeholder="Select datasource"
    filterable
    clearable
    @update:model-value="handleChange"
  >
    <el-option
      v-for="ds in filteredDatasources"
      :key="ds.id"
      :value="ds.id"
      :label="ds.name"
    >
      <span>{{ ds.name }}</span>
    </el-option>
  </el-select>
</template>
