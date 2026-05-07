<template>
  <div class="tab-pane">
    <div class="tab-bar">
      <el-button type="primary" size="small" @click="openEdit()"><el-icon><Plus /></el-icon>{{ t('common.create') }}</el-button>
      <el-button size="small" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>{{ t('common.refresh') }}</el-button>
      <span class="bar-hint">{{ t('aiAdmin.skillHint') }}</span>
    </div>
    <el-table :data="rows" v-loading="loading" size="small" stripe>
      <el-table-column prop="name" :label="t('aiAdmin.col.name')" width="180" />
      <el-table-column prop="displayName" :label="t('aiAdmin.col.displayName')" width="160" />
      <el-table-column :label="t('aiAdmin.col.category')" width="140">
        <template #default="{ row }">
          <el-tag size="small" :color="categoryColor(row.category)" effect="light" style="border: none; color: #fff">
            {{ row.category || 'OTHER' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="t('aiAdmin.col.description')" min-width="220" show-overflow-tooltip />
      <el-table-column :label="t('common.status')" width="80">
        <template #default="{ row }"><el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? 'ON' : 'OFF' }}</el-tag></template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="140">
        <template #default="{ row }">
          <el-button link size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button link size="small" type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" small background layout="total, prev, pager, next"
      :total="total" :page-size="pageSize" :current-page="pageNum" @current-change="(n: number) => { pageNum = n; load() }" />

    <el-dialog v-model="editing" :title="form.id ? t('common.edit') : t('common.create')" width="640">
      <el-form label-position="top">
        <el-form-item :label="t('aiAdmin.col.name')" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="t('aiAdmin.col.displayName')" required><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item :label="t('aiAdmin.col.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('aiAdmin.col.category')">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('aiAdmin.col.definition')" required>
          <el-input v-model="form.definition" type="textarea" :rows="10" :placeholder="defPlaceholder" />
        </el-form-item>
        <el-form-item :label="t('aiAdmin.col.requiredTools')">
          <el-select v-model="requiredToolsList" multiple filterable collapse-tags collapse-tags-tooltip
            style="width: 100%" :placeholder="t('aiAdmin.col.requiredToolsHint')">
            <el-option-group v-for="group in groupedTools" :key="group.source" :label="group.source">
              <el-option v-for="t in group.tools" :key="t.name" :label="t.name" :value="t.name">
                <div class="tool-option">
                  <el-tag size="small" :color="toolSourceColor(t.source)" effect="light"
                    style="border: none; color: #fff; margin-right: 6px">{{ t.source }}</el-tag>
                  <code class="tool-option__name">{{ t.name }}</code>
                  <span class="tool-option__desc">{{ t.description }}</span>
                </div>
              </el-option>
            </el-option-group>
          </el-select>
          <div class="hint">{{ t('aiAdmin.col.requiredToolsHint') }}</div>
        </el-form-item>
        <el-form-item><el-switch v-model="form.enabled" :active-text="t('aiAdmin.col.enabled')" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editing = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminSkill, listTools, type AiSkillAdminVO, type ToolViewVO } from '@/api/ai'

const { t } = useI18n()

// Skill 分类:后端枚举是真相源。前端直接展示枚举值,不做美化也不硬编码翻译。
// color 由名字 hash 产生保证同名同色;新增 / 改名枚举后前端零改动。
const categories = ref<string[]>([])
function categoryColor(cat: string | null | undefined): string {
  const key = cat || 'OTHER'
  const hues = [211, 358, 39, 263, 187, 160, 172, 239, 215]
  let h = 0
  for (let i = 0; i < key.length; i++) {
    h = (h * 31 + key.charCodeAt(i)) >>> 0
  }
  return `hsl(${hues[h % hues.length]}, 70%, 55%)`
}
const rows = ref<AiSkillAdminVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const editing = ref(false)
const saving = ref(false)
const form = reactive<AiSkillAdminVO>(empty())

// 可供 skill 依赖的 tool 列表(后端已经 excludeSkill=true 防递归)
const availableTools = ref<ToolViewVO[]>([])
const groupedTools = computed(() => {
  const g: Record<string, ToolViewVO[]> = { NATIVE: [], MCP: [] }
  for (const t of availableTools.value) {
    (g[t.source] ||= []).push(t)
  }
  return Object.entries(g)
    .filter(([, tools]) => tools.length > 0)
    .map(([source, tools]) => ({ source, tools }))
})
const TOOL_SOURCE_COLORS: Record<string, string> = { NATIVE: '#3b82f6', MCP: '#8b5cf6', SKILL: '#f59e0b' }
function toolSourceColor(source: string): string {
  return TOOL_SOURCE_COLORS[source] ?? '#94a3b8'
}

// requiredTools 在 DB 存 JSON 数组字符串,在表单里用 string[] 方便 el-select multi
const requiredToolsList = computed<string[]>({
  get: () => {
    const raw = form.requiredTools
    if (!raw) return []
    try {
      const arr = JSON.parse(raw)
      if (Array.isArray(arr)) return arr.map(String)
    } catch { /* not JSON */ }
    return raw.split(',').map(s => s.trim()).filter(Boolean)
  },
  set: (v: string[]) => {
    form.requiredTools = v && v.length ? JSON.stringify(v) : ''
  },
})

const defPlaceholder = `You are an expert …

1. Step one
2. Step two
…`

function empty(): AiSkillAdminVO {
  return { name: '', displayName: '', description: '', category: 'CODE_GEN', definition: '', requiredTools: '', enabled: true }
}

async function load() {
  loading.value = true
  try {
    const { data } = await adminSkill.list({ pageNum: pageNum.value, pageSize: pageSize.value })
    rows.value = data?.records ?? []
    total.value = data?.total ?? 0
  } finally { loading.value = false }
}

function openEdit(row?: AiSkillAdminVO) {
  Object.assign(form, row ? { ...row } : empty())
  editing.value = true
}

async function save() {
  if (!form.name || !form.displayName || !form.definition) { ElMessage.warning(t('common.required')); return }
  saving.value = true
  // MySQL JSON 列不接受空串,可选字段空值发 null
  const payload: AiSkillAdminVO = {
    ...form,
    inputSchema: form.inputSchema?.trim() || null,
    requiredTools: form.requiredTools?.trim() || null,
  }
  try {
    if (payload.id) await adminSkill.update(payload.id, payload)
    else await adminSkill.create(payload)
    editing.value = false
    await load()
    ElMessage.success(t('common.success'))
  } catch { ElMessage.error(t('common.failed')) } finally { saving.value = false }
}

async function remove(row: AiSkillAdminVO) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' })
    await adminSkill.remove(row.id); await load()
  } catch { /* cancel */ }
}

async function loadCategories() {
  try {
    const { data } = await adminSkill.listCategories()
    categories.value = data ?? []
  } catch {
    categories.value = ['CODE_GEN', 'DEBUG', 'OPTIMIZE', 'EXPLAIN', 'REVIEW', 'ANALYZE', 'DATA_DISCOVERY', 'OPS', 'OTHER']
  }
}

async function loadAvailableTools() {
  try {
    const { data } = await listTools({ excludeSkill: true })  // ← 过滤 SKILL,UI 层防递归
    availableTools.value = data ?? []
  } catch { availableTools.value = [] }
}

onMounted(() => Promise.all([load(), loadCategories(), loadAvailableTools()]))
</script>

<style scoped lang="scss">
.tool-option {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  line-height: var(--r-leading-snug);

  &__name {
    font-family: var(--r-font-mono);
    font-size: var(--r-font-sm);
    color: var(--r-text-primary);
  }
  &__desc {
    font-size: var(--r-font-xs);
    color: var(--r-text-muted);
    margin-left: auto;
    max-width: 280px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
