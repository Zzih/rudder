<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import FileSelectDialog from '@/components/FileSelectDialog.vue'

const { t } = useI18n()

const showFilePicker = ref(false)

function onFileSelected(path: string) {
  form.value.jarPath = path
  emitChange()
}

interface ResourceConfig {
  driverCores: number
  driverMemory: string
  executorCores: number
  executorMemory: string
  executorInstances: number
  parallelism: number
  jobManagerMemory: string
  taskManagerMemory: string
}

interface JarParams {
  mainClass: string
  jarPath: string
  args: string
  master: string
  deployMode: string
  appName: string
  queue: string
  resource: ResourceConfig
  engineParams: Record<string, string>
}

const props = defineProps<{
  modelValue: string
  taskType: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const isSpark = computed(() => props.taskType === 'SPARK_JAR')

function defaultParams(): JarParams {
  return {
    mainClass: '',
    jarPath: '',
    args: '',
    master: 'yarn',
    deployMode: isSpark.value ? 'cluster' : 'yarn-application',
    appName: '',
    queue: 'default',
    resource: {
      driverCores: 1,
      driverMemory: '1g',
      executorCores: 2,
      executorMemory: '2g',
      executorInstances: 2,
      parallelism: 2,
      jobManagerMemory: '1g',
      taskManagerMemory: '2g',
    },
    engineParams: {},
  }
}

const form = ref<JarParams>(defaultParams())

// custom engine params as key-value rows
const engineParamRows = ref<{ key: string; value: string }[]>([])

onMounted(() => {
  parseFromJson(props.modelValue)
})

watch(() => props.modelValue, (val) => {
  parseFromJson(val)
}, { flush: 'post' })

function parseFromJson(json: string) {
  if (!json || !json.trim().startsWith('{')) {
    form.value = defaultParams()
    engineParamRows.value = []
    return
  }
  try {
    const parsed = JSON.parse(json)
    form.value = { ...defaultParams(), ...parsed, resource: { ...defaultParams().resource, ...(parsed.resource || {}) } }
    engineParamRows.value = Object.entries(form.value.engineParams || {}).map(([key, value]) => ({ key, value: String(value) }))
  } catch {
    form.value = defaultParams()
    engineParamRows.value = []
  }
}

function emitChange() {
  // sync engine params from rows
  const ep: Record<string, string> = {}
  for (const row of engineParamRows.value) {
    if (row.key.trim()) ep[row.key.trim()] = row.value
  }
  form.value.engineParams = ep
  emit('update:modelValue', JSON.stringify(form.value, null, 2))
}

function addEngineParam() {
  engineParamRows.value.push({ key: '', value: '' })
}

function removeEngineParam(idx: number) {
  engineParamRows.value.splice(idx, 1)
  emitChange()
}
</script>

<template>
  <div class="jar-editor">
    <el-form label-position="top" size="default" @change="emitChange">
      <!-- Program -->
      <div class="section-title">{{ t('jar.program') }}</div>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item :label="t('jar.mainClass')" required>
            <el-input v-model="form.mainClass" placeholder="com.example.MainApp" @change="emitChange" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="t('jar.jarPath')" required>
            <el-input v-model="form.jarPath" placeholder="/jars/my-app.jar" readonly @change="emitChange">
              <template #append>
                <el-button @click="showFilePicker = true">{{ t('common.select') }}</el-button>
              </template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item :label="t('jar.args')">
        <el-input v-model="form.args" type="textarea" :rows="2" placeholder="--input /data/input --output /data/output" @change="emitChange" />
      </el-form-item>

      <!-- Deploy -->
      <div class="section-title">{{ t('jar.deploy') }}</div>

      <template v-if="isSpark">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="Master">
              <el-select v-model="form.master" @change="emitChange">
                <el-option label="YARN" value="yarn" />
                <el-option label="Local" value="local[*]" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="t('jar.deployMode')">
              <el-select v-model="form.deployMode" @change="emitChange">
                <el-option label="Cluster" value="cluster" />
                <el-option label="Client" value="client" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="t('jar.appName')">
              <el-input v-model="form.appName" placeholder="my-spark-app" @change="emitChange" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="t('jar.queue')">
              <el-input v-model="form.queue" placeholder="default" @change="emitChange" />
            </el-form-item>
          </el-col>
        </el-row>
      </template>
      <template v-else>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item :label="t('jar.deployMode')">
              <el-select v-model="form.deployMode" @change="emitChange">
                <el-option label="YARN Application" value="yarn-application" />
                <el-option label="YARN Session" value="yarn-session" />
                <el-option label="Local" value="local" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('jar.appName')">
              <el-input v-model="form.appName" placeholder="my-flink-app" @change="emitChange" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('jar.queue')">
              <el-input v-model="form.queue" placeholder="default" @change="emitChange" />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <!-- Resources -->
      <div class="section-title">{{ t('jar.resources') }}</div>

      <template v-if="isSpark">
        <!-- Spark: Driver + Executor -->
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item :label="t('jar.driverCores')">
              <el-input-number v-model="form.resource.driverCores" :min="1" :max="32" @change="emitChange" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('jar.driverMemory')">
              <el-input v-model="form.resource.driverMemory" placeholder="1g" @change="emitChange" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('jar.executorInstances')">
              <el-input-number v-model="form.resource.executorInstances" :min="1" :max="500" @change="emitChange" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item :label="t('jar.executorCores')">
              <el-input-number v-model="form.resource.executorCores" :min="1" :max="32" @change="emitChange" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('jar.executorMemory')">
              <el-input v-model="form.resource.executorMemory" placeholder="2g" @change="emitChange" />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <template v-else>
        <!-- Flink: JobManager + TaskManager -->
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item :label="t('jar.jobManagerMemory')">
              <el-input v-model="form.resource.jobManagerMemory" placeholder="1g" @change="emitChange" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('jar.taskManagerMemory')">
              <el-input v-model="form.resource.taskManagerMemory" placeholder="2g" @change="emitChange" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('jar.parallelism')">
              <el-input-number v-model="form.resource.parallelism" :min="1" :max="1000" @change="emitChange" />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <!-- Engine Params -->
      <div class="section-title">
        {{ t('jar.engineParams') }}
        <el-button text size="small" type="primary" @click="addEngineParam" style="margin-left: 8px">+ {{ t('common.create') }}</el-button>
      </div>

      <div v-for="(row, idx) in engineParamRows" :key="idx" class="param-row">
        <el-input v-model="row.key" placeholder="spark.sql.shuffle.partitions" class="param-input" @change="emitChange" />
        <el-input v-model="row.value" placeholder="200" class="param-input" @change="emitChange" />
        <el-button text type="danger" size="small" @click="removeEngineParam(idx)">{{ t('common.delete') }}</el-button>
      </div>
    </el-form>

    <FileSelectDialog
      v-model="showFilePicker"
      :current-path="form.jarPath"
      :extensions="['.jar']"
      @select="onFileSelected"
    />
  </div>
</template>

<style scoped lang="scss">
.jar-editor {
  padding: 16px 20px;
  height: 100%;
  overflow-y: auto;
  background: var(--r-bg-card);
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--r-text-primary);
  margin: 16px 0 12px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--r-border);
  display: flex;
  align-items: center;

  &:first-child {
    margin-top: 0;
  }
}

.param-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.param-input {
  flex: 1;
}
</style>
