<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Delete, Plus } from '@element-plus/icons-vue'
import {
  listAccessGroups,
  emptyResourcePath,
  type DataPermAdapter,
  type DataPermScopeAccessGroup,
  type DataPermScope,
  type StatementDraft,
  type ResourcePathDraft,
} from '@/api/data-perm'
import ResourcePathEditor from './ResourcePathEditor.vue'

const props = defineProps<{
  modelValue: StatementDraft
  /** 平台登记的数据权限域列表(来自 DataPermConfig.scopes)。 */
  scopes: DataPermScope[]
  /** pluginType → adapter(resourceLevels + accessTypes) 索引。 */
  adaptersByPluginType: Record<string, DataPermAdapter>
  workspaceId?: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: StatementDraft): void
  (e: 'remove'): void
}>()

const { t } = useI18n()

const scope = computed<DataPermScope | null>(() =>
  props.scopes.find(s => s.code === props.modelValue.scopeCode) ?? null)

const adapter = computed<DataPermAdapter | null>(() =>
  scope.value ? props.adaptersByPluginType[scope.value.pluginType] ?? null : null)

const accessGroups = ref<DataPermScopeAccessGroup[]>([])
const groupsLoading = ref(false)

async function loadAccessGroups() {
  accessGroups.value = []
  const code = props.modelValue.scopeCode
  if (!code) return
  groupsLoading.value = true
  try {
    const res: any = await listAccessGroups(code)
    accessGroups.value = (res?.data as DataPermScopeAccessGroup[]) ?? []
  } catch {
    accessGroups.value = []
  } finally {
    groupsLoading.value = false
  }
}

watch(() => props.modelValue.scopeCode, loadAccessGroups)
onMounted(loadAccessGroups)

function update(patch: Partial<StatementDraft>) {
  emit('update:modelValue', { ...props.modelValue, ...patch })
}
function onScopeChange(code: number) {
  // 换作用域 → 分组 / 库表全部重来(分组、可用层级都随 scope 变)
  update({ scopeCode: code, groupIds: [], resources: [emptyResourcePath()] })
}
function isGroupOn(id: number): boolean {
  return (props.modelValue.groupIds ?? []).includes(id)
}
function toggleGroup(id: number) {
  const current = props.modelValue.groupIds ?? []
  update({ groupIds: current.includes(id) ? current.filter(x => x !== id) : [...current, id] })
}

function addResource() {
  update({ resources: [...props.modelValue.resources, emptyResourcePath()] })
}
function removeResource(idx: number) {
  const next = props.modelValue.resources.slice()
  next.splice(idx, 1)
  update({ resources: next })
}
function updateResource(idx: number, v: ResourcePathDraft) {
  const next = props.modelValue.resources.slice()
  next.splice(idx, 1, v)
  update({ resources: next })
}
</script>

<template>
  <div class="perm-card" :class="{ 'is-empty': !modelValue.scopeCode }">
    <!-- Section 1: Scope -->
    <section class="perm-card__section">
      <div class="perm-card__field">
        <label class="perm-card__field-label">
          {{ t('dataPerm.apply.serviceLabel') }}
          <span class="perm-card__required">*</span>
        </label>
        <div class="perm-card__field-row">
          <el-select
            :model-value="modelValue.scopeCode || undefined"
            :placeholder="t('dataPerm.apply.servicePlaceholder')"
            filterable class="perm-card__svc"
            @update:model-value="v => onScopeChange(v as number)"
          >
            <el-option v-for="s in scopes" :key="s.code" :value="s.code!" :label="s.name">
              <span class="svc-opt">
                <span class="svc-opt__name">{{ s.name }}</span>
                <el-tag size="small" type="info" effect="plain" round>{{ s.pluginType }}</el-tag>
              </span>
            </el-option>
          </el-select>
          <el-tag v-if="scope" size="small" type="info" effect="plain" round class="perm-card__svc-type">
            {{ scope.pluginType }}
          </el-tag>
          <button type="button" class="perm-card__close"
            :title="t('common.delete')" @click="emit('remove')">
            <el-icon><Delete /></el-icon>
          </button>
        </div>
      </div>
    </section>

    <!-- empty hint -->
    <section v-if="!modelValue.scopeCode" class="perm-card__empty">
      {{ t('dataPerm.apply.directNoServiceHint') }}
    </section>

    <!-- Section 2: Operation groups -->
    <section v-if="modelValue.scopeCode" class="perm-card__section">
      <div class="perm-card__field">
        <label class="perm-card__field-label">
          {{ t('dataPerm.apply.groupsLabel') }}
          <span class="perm-card__required">*</span>
        </label>
        <div v-if="groupsLoading" class="perm-card__pick-hint">{{ t('common.loading') }}</div>
        <div v-else-if="!accessGroups.length" class="perm-card__empty">
          {{ t('dataPerm.apply.noGroups') }}
        </div>
        <div v-else class="perm-card__access">
          <button
            v-for="g in accessGroups" :key="g.id"
            type="button"
            class="access-chip"
            :class="{ 'is-on': isGroupOn(g.id!) }"
            :title="g.description || ''"
            @click="toggleGroup(g.id!)"
          >{{ g.name }}</button>
        </div>
      </div>
    </section>

    <!-- Section 3: Resource paths (库表选择,可多条) -->
    <section v-if="modelValue.scopeCode" class="perm-card__section">
      <div class="perm-card__field">
        <div class="perm-card__field-label">
          {{ t('dataPerm.apply.resourcePathSection') }}
          <span class="perm-card__required">*</span>
          <span class="perm-card__field-hint">{{ t('dataPerm.apply.resourceMultiHint') }}</span>
          <button type="button" class="perm-card__add" @click="addResource">
            <el-icon><Plus /></el-icon>{{ t('dataPerm.apply.addResource') }}
          </button>
        </div>
        <div class="perm-card__resources">
          <ResourcePathEditor
            v-for="(r, ri) in modelValue.resources" :key="ri"
            :model-value="r"
            :scope="scope!"
            :adapter="adapter"
            :workspace-id="workspaceId"
            :removable="modelValue.resources.length > 1"
            @update:model-value="v => updateResource(ri, v)"
            @remove="removeResource(ri)"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.perm-card {
  display: flex;
  flex-direction: column;
  background: var(--r-bg-card);

  &.is-empty { background: var(--r-bg-panel); }

  &__section {
    padding: var(--r-space-3) var(--r-space-4);
    & + & { border-top: 1px solid var(--r-border-light); }
  }

  &__field {
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-width: 0;
  }

  &__field-label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: var(--r-font-sm);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-primary);
  }
  &__field-hint {
    font-size: var(--r-font-xs);
    font-weight: var(--r-weight-normal);
    color: var(--r-text-muted);
  }
  &__field-row {
    display: flex;
    align-items: center;
    gap: var(--r-space-2);
  }
  &__required { color: var(--r-danger); }

  &__svc { flex: 1; min-width: 0; }
  &__svc-type { flex-shrink: 0; }

  &__close {
    all: unset;
    flex-shrink: 0;
    width: 28px;
    height: 28px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-radius-sm);
    color: var(--r-text-muted);
    cursor: pointer;
    transition: color 0.12s, background 0.12s;

    &:hover { color: var(--r-danger); background: var(--r-danger-bg); }
    .el-icon { font-size: var(--r-font-sm); }
  }

  &__add {
    all: unset;
    margin-left: auto;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 3px 9px;
    border-radius: var(--r-radius-sm);
    background: var(--r-bg-panel);
    border: 1px solid var(--r-border-light);
    font-size: var(--r-font-xs);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-secondary);
    transition: all 0.12s;

    &:hover {
      color: var(--r-accent);
      background: var(--r-accent-bg);
      border-color: var(--r-accent-border);
    }
    .el-icon { font-size: 12px; }
  }

  &__resources {
    display: flex;
    flex-direction: column;
    gap: var(--r-space-3);
  }

  &__pick-hint {
    font-size: var(--r-font-xs);
    color: var(--r-text-muted);
  }

  &__access {
    display: flex;
    flex-wrap: wrap;
    gap: var(--r-space-2);
  }

  &__empty {
    padding: var(--r-space-4);
    text-align: center;
    font-size: var(--r-font-sm);
    color: var(--r-text-muted);
  }
}

.svc-opt {
  display: inline-flex;
  align-items: center;
  gap: var(--r-space-2);
}
.svc-opt__name {
  font-weight: var(--r-weight-medium);
  color: var(--r-text-primary);
}

.access-chip {
  all: unset;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 var(--r-space-3);
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-sm);
  font-size: var(--r-font-sm);
  color: var(--r-text-secondary);
  transition: all 0.12s;

  &:hover:not(.is-on) {
    border-color: var(--r-border);
    color: var(--r-text-primary);
    background: var(--r-bg-panel);
  }
  &.is-on {
    background: var(--r-accent-bg);
    border-color: var(--r-accent);
    color: var(--r-accent);
    font-weight: var(--r-weight-semibold);
  }
}
</style>
