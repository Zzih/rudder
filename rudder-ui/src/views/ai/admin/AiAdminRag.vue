<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import SpiConfigPage from '@/components/SpiConfigPage.vue'
import {
  getAiRagPipelineConfig,
  saveAiRagPipelineConfig,
  type RagPipelineSettings,
  DEFAULT_RAG_PIPELINE,
} from '@/api/ai-rag-config'
import {
  getAiRerankProviderDefinitions,
  getAiRerankConfig,
  saveAiRerankConfig,
  testAiRerankConfig,
} from '@/api/ai-rerank-config'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const settings = ref<RagPipelineSettings>({ ...DEFAULT_RAG_PIPELINE })

async function load() {
  loading.value = true
  try {
    // request 拦截器返回 {code, message, data} 整体,实际配置在 .data
    const res = (await getAiRagPipelineConfig()) as any
    settings.value = { ...DEFAULT_RAG_PIPELINE, ...(res?.data ?? {}) }
  } catch {
    settings.value = { ...DEFAULT_RAG_PIPELINE }
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await saveAiRagPipelineConfig(settings.value)
    ElMessage.success(t('common.success'))
  } catch {
    ElMessage.error(t('common.failed'))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="ai-admin-rag">
    <!-- 上半段:RAG 链路开关 -->
    <section class="ai-admin-rag__panel" v-loading="loading">
      <header class="ai-admin-rag__head">
        <h4>{{ t('aiAdmin.rag.pipeline.title') }}</h4>
        <p class="ai-admin-rag__hint">{{ t('aiAdmin.rag.pipeline.hint') }}</p>
      </header>

      <el-form label-position="top" class="ai-admin-rag__form">
        <!-- Query Rewrite -->
        <el-form-item>
          <div class="ai-admin-rag__row">
            <el-switch v-model="settings.rewriteEnabled" />
            <div class="ai-admin-rag__row-text">
              <div class="ai-admin-rag__row-label">{{ t('aiAdmin.rag.rewrite.label') }}</div>
              <div class="ai-admin-rag__row-desc">{{ t('aiAdmin.rag.rewrite.desc') }}</div>
            </div>
          </div>
        </el-form-item>

        <!-- Multi Query -->
        <el-form-item>
          <div class="ai-admin-rag__row">
            <el-switch v-model="settings.multiQueryEnabled" />
            <div class="ai-admin-rag__row-text">
              <div class="ai-admin-rag__row-label">{{ t('aiAdmin.rag.multiQuery.label') }}</div>
              <div class="ai-admin-rag__row-desc">{{ t('aiAdmin.rag.multiQuery.desc') }}</div>
              <div v-if="settings.multiQueryEnabled" class="ai-admin-rag__row-extra">
                <span>{{ t('aiAdmin.rag.multiQuery.count') }}</span>
                <el-input-number
                  v-model="settings.multiQueryCount"
                  :min="2" :max="5" size="small"
                />
                <el-checkbox v-model="settings.multiQueryIncludeOriginal">
                  {{ t('aiAdmin.rag.multiQuery.includeOriginal') }}
                </el-checkbox>
              </div>
            </div>
          </div>
        </el-form-item>

        <!-- Compression (multi-turn) -->
        <el-form-item>
          <div class="ai-admin-rag__row">
            <el-switch v-model="settings.compressionEnabled" />
            <div class="ai-admin-rag__row-text">
              <div class="ai-admin-rag__row-label">{{ t('aiAdmin.rag.compression.label') }}</div>
              <div class="ai-admin-rag__row-desc">{{ t('aiAdmin.rag.compression.desc') }}</div>
            </div>
          </div>
        </el-form-item>

        <!-- Translation (cross-lingual) -->
        <el-form-item>
          <div class="ai-admin-rag__row">
            <el-switch v-model="settings.translationEnabled" />
            <div class="ai-admin-rag__row-text">
              <div class="ai-admin-rag__row-label">{{ t('aiAdmin.rag.translation.label') }}</div>
              <div class="ai-admin-rag__row-desc">{{ t('aiAdmin.rag.translation.desc') }}</div>
              <div v-if="settings.translationEnabled" class="ai-admin-rag__row-extra">
                <span>{{ t('aiAdmin.rag.translation.targetLanguage') }}</span>
                <el-input
                  v-model="settings.translationTargetLanguage"
                  size="small" style="width: 180px"
                  placeholder="english / chinese / ..."
                />
              </div>
            </div>
          </div>
        </el-form-item>

        <!-- Rerank Stage -->
        <el-form-item>
          <div class="ai-admin-rag__row">
            <el-switch v-model="settings.rerankStageEnabled" />
            <div class="ai-admin-rag__row-text">
              <div class="ai-admin-rag__row-label">{{ t('aiAdmin.rag.rerankStage.label') }}</div>
              <div class="ai-admin-rag__row-desc">{{ t('aiAdmin.rag.rerankStage.desc') }}</div>
              <div v-if="settings.rerankStageEnabled" class="ai-admin-rag__row-extra">
                <span>{{ t('aiAdmin.rag.rerankStage.topN') }}</span>
                <el-input-number
                  v-model="settings.rerankTopN"
                  :min="1" :max="50" size="small"
                />
              </div>
            </div>
          </div>
        </el-form-item>

        <!-- Keyword Enricher -->
        <el-form-item>
          <div class="ai-admin-rag__row">
            <el-switch v-model="settings.keywordEnricherEnabled" />
            <div class="ai-admin-rag__row-text">
              <div class="ai-admin-rag__row-label">{{ t('aiAdmin.rag.keywordEnricher.label') }}</div>
              <div class="ai-admin-rag__row-desc">{{ t('aiAdmin.rag.keywordEnricher.desc') }}</div>
            </div>
          </div>
        </el-form-item>

        <!-- Summary Enricher -->
        <el-form-item>
          <div class="ai-admin-rag__row">
            <el-switch v-model="settings.summaryEnricherEnabled" />
            <div class="ai-admin-rag__row-text">
              <div class="ai-admin-rag__row-label">{{ t('aiAdmin.rag.summaryEnricher.label') }}</div>
              <div class="ai-admin-rag__row-desc">{{ t('aiAdmin.rag.summaryEnricher.desc') }}</div>
            </div>
          </div>
        </el-form-item>

        <!-- Allow Empty Context -->
        <el-form-item>
          <div class="ai-admin-rag__row">
            <el-switch v-model="settings.augmenterAllowEmptyContext" />
            <div class="ai-admin-rag__row-text">
              <div class="ai-admin-rag__row-label">{{ t('aiAdmin.rag.allowEmpty.label') }}</div>
              <div class="ai-admin-rag__row-desc">{{ t('aiAdmin.rag.allowEmpty.desc') }}</div>
            </div>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">
            {{ t('common.save') }}
          </el-button>
        </el-form-item>
      </el-form>
    </section>

    <!-- 下半段:Rerank Provider 配置 -->
    <!-- 仅在 rerank stage 启用时展示。先开启上方"Rerank 精排"开关并保存,这里才出现 provider 配置 -->
    <section v-if="settings.rerankStageEnabled" class="ai-admin-rag__panel">
      <header class="ai-admin-rag__head">
        <h4>{{ t('aiAdmin.rag.rerankProvider.title') }}</h4>
        <p class="ai-admin-rag__hint">{{ t('aiAdmin.rag.rerankProvider.hint') }}</p>
      </header>
      <SpiConfigPage
        i18n-prefix="aiAdmin.rerank"
        enable-label-key="aiAdmin.rerank.enable"
        :get-provider-definitions="getAiRerankProviderDefinitions"
        :get-config="getAiRerankConfig"
        :save-config="saveAiRerankConfig"
        :test-config="testAiRerankConfig"
      />
    </section>
  </div>
</template>

<style scoped>
.ai-admin-rag {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.ai-admin-rag__panel {
  background: var(--r-bg-elevated, #fff);
  border: 1px solid var(--r-border, #e5e7eb);
  border-radius: 8px;
  padding: 20px 24px;
}

.ai-admin-rag__head {
  margin-bottom: 16px;
}

.ai-admin-rag__head h4 {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
}

.ai-admin-rag__hint {
  margin: 0;
  color: var(--r-text-muted, #6b7280);
  font-size: 12px;
}

.ai-admin-rag__form {
  max-width: 720px;
}

.ai-admin-rag__row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  width: 100%;
}

.ai-admin-rag__row-text {
  flex: 1;
}

.ai-admin-rag__row-label {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 2px;
}

.ai-admin-rag__row-desc {
  color: var(--r-text-muted, #6b7280);
  font-size: 12px;
}

.ai-admin-rag__row-extra {
  margin-top: 8px;
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 13px;
}
</style>
