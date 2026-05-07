<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const visible = defineModel<boolean>({ default: false })
const { t } = useI18n()
</script>

<template>
  <el-dialog v-model="visible" :title="t('about.title')" width="460px" destroy-on-close>
    <div class="about-dialog">
      <div class="about-brand">
        <div class="about-logo">R</div>
        <div class="about-name">Rudder</div>
        <div class="about-subtitle">{{ t('about.subtitle') }}</div>
      </div>
      <p class="about-desc">{{ t('about.description') }}</p>
      <div class="about-info">
        <div class="about-row">
          <span class="about-label">{{ t('about.version') }}</span>
          <span class="about-value">1.0.0</span>
        </div>
        <div class="about-row">
          <span class="about-label">{{ t('about.author') }}</span>
          <span class="about-value">Zzih</span>
        </div>
        <div class="about-row">
          <span class="about-label">{{ t('about.license') }}</span>
          <span class="about-value">Apache-2.0</span>
        </div>
        <div class="about-row">
          <span class="about-label">{{ t('about.github') }}</span>
          <a class="about-link" href="https://github.com/zzih/rudder" target="_blank" rel="noopener noreferrer">github.com/zzih/rudder</a>
        </div>
      </div>
      <div class="about-copyright">{{ t('footer.copyright') }}</div>
    </div>
  </el-dialog>
</template>

<style scoped>
.about-dialog {
  text-align: center;
}

.about-brand {
  margin-bottom: 16px;
}

.about-logo {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, var(--r-accent), var(--r-accent-hover));
  border-radius: 14px;
  font-size: 24px;
  font-weight: 700;
  color: #fff;
  line-height: 56px;
  margin: 0 auto 12px;
}

.about-name {
  font-size: 24px;
  font-weight: 700;
  color: var(--r-text-primary);
  letter-spacing: 0.5px;
}

.about-subtitle {
  font-size: 13px;
  color: var(--r-text-muted);
  margin-top: 4px;
}

.about-desc {
  font-size: 13px;
  color: var(--r-text-secondary);
  line-height: 1.6;
  margin: 0 0 20px;
  padding: 0 12px;
}

.about-info {
  text-align: left;
  background: var(--r-bg-panel);
  border-radius: 8px;
  padding: 12px 20px;
  margin-bottom: 16px;
}

.about-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.about-row + .about-row {
  border-top: 1px solid var(--r-border);
}

.about-label {
  font-size: 13px;
  color: var(--r-text-muted);
}

.about-value {
  font-size: 13px;
  color: var(--r-text-primary);
  font-weight: 500;
}

.about-link {
  font-size: 13px;
  color: var(--r-accent);
  text-decoration: none;
}

.about-link:hover {
  text-decoration: underline;
}

.about-copyright {
  font-size: 12px;
  color: var(--r-text-disabled);
  padding-bottom: 4px;
}
</style>
