<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search, Reading, EditPen, WarningFilled } from '@element-plus/icons-vue'
import { listAllCapabilities, type CapabilityItem } from '@/api/mcp'

const { t } = useI18n()

const items = ref<CapabilityItem[]>([])
const loading = ref(false)
const search = ref('')
const rwFilter = ref<'all' | 'READ' | 'WRITE'>('all')

async function load() {
  loading.value = true
  try {
    const { data } = await listAllCapabilities()
    items.value = data ?? []
  } catch {
    /* ignore */
  } finally {
    loading.value = false
  }
}

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  return items.value.filter((c) => {
    if (rwFilter.value !== 'all' && c.rwClass !== rwFilter.value) return false
    if (!q) return true
    return (
      c.id.toLowerCase().includes(q)
      || c.description.toLowerCase().includes(q)
      || c.domain.toLowerCase().includes(q)
    )
  })
})

const grouped = computed(() => {
  const map = new Map<string, CapabilityItem[]>()
  for (const c of filtered.value) {
    if (!map.has(c.domain)) map.set(c.domain, [])
    map.get(c.domain)!.push(c)
  }
  return Array.from(map, ([domain, list]) => ({ domain, list }))
})

const stats = computed(() => {
  const acc = { total: 0, read: 0, write: 0, high: 0 }
  for (const c of items.value) {
    acc.total++
    if (c.rwClass === 'READ') acc.read++
    else if (c.rwClass === 'WRITE') acc.write++
    if (c.sensitivity === 'HIGH') acc.high++
  }
  return acc
})

const roleColor: Record<string, string> = {
  SUPER_ADMIN: 'pink',
  WORKSPACE_OWNER: 'purple',
  DEVELOPER: 'cyan',
  VIEWER: 'teal',
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="catalog-page">
    <!-- Hero -->
    <section class="hero">
      <div class="hero__text">
        <h4>{{ t('mcpPage.catalog.title') }}</h4>
        <p>{{ t('mcpPage.catalog.subtitle') }}</p>
      </div>
      <div class="hero__stats">
        <div class="stat">
          <span class="stat__value">{{ stats.total }}</span>
          <span class="stat__label">total</span>
        </div>
        <div class="stat-divider" />
        <div class="stat">
          <span class="stat__value">{{ stats.read }}</span>
          <span class="stat__label" data-tone="read">read</span>
        </div>
        <div class="stat">
          <span class="stat__value">{{ stats.write }}</span>
          <span class="stat__label" data-tone="write">write</span>
        </div>
        <div class="stat">
          <span class="stat__value">{{ stats.high }}</span>
          <span class="stat__label" data-tone="high">high</span>
        </div>
      </div>
    </section>

    <!-- Toolbar -->
    <div class="toolbar">
      <div class="toolbar__search">
        <el-icon class="toolbar__search-icon"><Search /></el-icon>
        <input
          v-model="search"
          type="text"
          class="toolbar__search-input"
          :placeholder="t('mcpPage.catalog.searchPlaceholder')"
        />
      </div>
      <div class="seg-group">
        <button
          class="seg" :class="{ 'is-active': rwFilter === 'all' }"
          @click="rwFilter = 'all'"
        >
          {{ t('mcpPage.catalog.filterAll') }}
        </button>
        <button
          class="seg" :class="{ 'is-active': rwFilter === 'READ' }"
          @click="rwFilter = 'READ'"
        >
          <el-icon><Reading /></el-icon>
          {{ t('mcpPage.catalog.filterRead') }}
        </button>
        <button
          class="seg" :class="{ 'is-active': rwFilter === 'WRITE' }"
          @click="rwFilter = 'WRITE'"
        >
          <el-icon><EditPen /></el-icon>
          {{ t('mcpPage.catalog.filterWrite') }}
        </button>
      </div>
    </div>

    <!-- Domain groups -->
    <section v-for="g in grouped" :key="g.domain" class="domain-block">
      <header class="domain-header">
        <span class="domain-header__dot" />
        <h5 class="domain-header__name">{{ g.domain }}</h5>
        <span class="domain-header__count">{{ t('mcpPage.catalog.domainCount', { n: g.list.length }) }}</span>
      </header>

      <div class="cap-grid">
        <article
          v-for="c in g.list" :key="c.id"
          class="cap-card"
          :data-rw="c.rwClass"
        >
          <header class="cap-card__head">
            <span class="cap-card__rw" :data-rw="c.rwClass">
              <el-icon><component :is="c.rwClass === 'READ' ? Reading : EditPen" /></el-icon>
              {{ c.rwClass === 'READ' ? t('mcpPage.catalog.read') : t('mcpPage.catalog.write') }}
            </span>
            <span
              v-if="c.sensitivity === 'HIGH'"
              class="cap-card__sens"
              :title="t('mcpPage.catalog.sensitivityHigh')"
            >
              <el-icon><WarningFilled /></el-icon>
              HIGH
            </span>
          </header>

          <code class="cap-card__id">{{ c.id }}</code>
          <p class="cap-card__desc">{{ c.description }}</p>

          <footer class="cap-card__roles">
            <span class="cap-card__roles-label">{{ t('mcpPage.catalog.requiredRoles') }}</span>
            <div class="cap-card__roles-list">
              <span
                v-for="r in c.requiredRoles"
                :key="r"
                class="role-chip"
                :data-tone="roleColor[r] ?? 'gray'"
              >{{ r }}</span>
            </div>
          </footer>
        </article>
      </div>
    </section>

    <div v-if="!loading && grouped.length === 0" class="empty">
      {{ t('mcpPage.catalog.empty') }}
    </div>
  </div>
</template>

<style scoped lang="scss">
.catalog-page {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-5);
}

/* ============ HERO ============ */
.hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--r-space-5);
  padding: 16px 18px;
  background: linear-gradient(180deg, var(--r-bg-card) 0%, var(--r-bg-panel) 100%);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-lg);
  flex-wrap: wrap;

  &__text {
    flex: 1;
    min-width: 280px;

    h4 {
      margin: 0 0 4px;
      font-size: var(--r-font-md);
      font-weight: var(--r-weight-bold);
      color: var(--r-text-primary);
      letter-spacing: -0.01em;
    }
    p {
      margin: 0;
      font-size: var(--r-font-sm);
      color: var(--r-text-tertiary);
      line-height: var(--r-leading-snug);
      max-width: 640px;
    }
  }

  &__stats {
    display: flex;
    align-items: center;
    gap: 18px;
    padding-top: 4px;
  }
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;

  &__value {
    font-family: var(--r-font-mono);
    font-size: 22px;
    font-weight: var(--r-weight-bold);
    color: var(--r-text-primary);
    line-height: 1;
    font-variant-numeric: tabular-nums;
  }

  &__label {
    font-family: var(--r-font-mono);
    font-size: 10px;
    font-weight: var(--r-weight-semibold);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    color: var(--r-text-muted);

    &[data-tone="read"]  { color: var(--r-accent); }
    &[data-tone="write"] { color: var(--r-warning); }
    &[data-tone="high"]  { color: var(--r-danger); }
  }
}

.stat-divider {
  width: 1px;
  height: 28px;
  background: var(--r-border);
}

/* ============ TOOLBAR ============ */
.toolbar {
  display: flex;
  align-items: center;
  gap: var(--r-space-3);
  padding: 12px 14px;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-lg);
  box-shadow: 0 1px 2px rgb(0 0 0 / 0.03);

  &__search {
    position: relative;
    flex: 1;
    max-width: 380px;
    display: flex;
    align-items: center;
    height: 34px;
    padding: 0 12px;
    background: var(--r-bg-panel);
    border: 1px solid var(--r-border-light);
    border-radius: var(--r-radius-md);
    transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;

    &:focus-within {
      background: var(--r-bg-card);
      border-color: var(--r-accent);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--r-accent) 12%, transparent);
    }

    &-icon {
      color: var(--r-text-muted);
      font-size: 14px;
      flex-shrink: 0;
    }

    &-input {
      flex: 1;
      min-width: 0;
      margin: 0 8px;
      padding: 0;
      border: none;
      outline: none;
      background: transparent;
      font-family: inherit;
      font-size: var(--r-font-md);
      color: var(--r-text-primary);

      &::placeholder { color: var(--r-text-muted); }
    }
  }
}

/* segmented filter */
.seg-group {
  display: inline-flex;
  align-items: stretch;
  padding: 3px;
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  gap: 2px;
  margin-left: auto;
}

.seg {
  all: unset;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-tertiary);
  border-radius: 5px;
  transition: background 0.15s, color 0.15s, box-shadow 0.15s;

  .el-icon { font-size: 13px; }

  &:hover:not(.is-active) { color: var(--r-text-primary); }

  &.is-active {
    background: var(--r-bg-card);
    color: var(--r-accent);
    box-shadow: 0 1px 2px rgb(0 0 0 / 0.06), 0 0 0 1px var(--r-border);
  }
}

/* ============ DOMAIN BLOCK (each is a card) ============ */
.domain-block {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-3);
  padding: 16px 18px 18px;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-lg);
  box-shadow: 0 1px 2px rgb(0 0 0 / 0.03);
}

.domain-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 0 12px;
  margin: 0 -2px;
  border-bottom: 1px solid var(--r-border-light);

  &__dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--r-accent);
    box-shadow: 0 0 0 4px var(--r-accent-bg);
  }

  &__name {
    margin: 0;
    font-family: var(--r-font-mono);
    font-size: var(--r-font-md);
    font-weight: var(--r-weight-bold);
    color: var(--r-text-primary);
    text-transform: uppercase;
    letter-spacing: 0.06em;
  }

  &__count {
    margin-left: auto;
    font-family: var(--r-font-mono);
    font-size: 10px;
    color: var(--r-text-muted);
    font-weight: var(--r-weight-semibold);
    text-transform: uppercase;
    letter-spacing: 0.08em;
    padding: 3px 8px;
    background: var(--r-bg-panel);
    border: 1px solid var(--r-border-light);
    border-radius: 999px;
  }
}

/* ============ CAP CARDS ============ */
.cap-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--r-space-3);
}

.cap-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-lg);
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;

  &:hover {
    border-color: var(--r-border);
    box-shadow: 0 2px 8px -4px rgb(0 0 0 / 0.06);
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 6px;
  }

  &__rw {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 10px;
    font-weight: var(--r-weight-bold);
    text-transform: uppercase;
    letter-spacing: 0.08em;
    font-family: var(--r-font-mono);

    .el-icon { font-size: 11px; }

    &[data-rw="READ"] {
      color: var(--r-accent);
      background: var(--r-accent-bg);
      border: 1px solid var(--r-accent-border);
    }
    &[data-rw="WRITE"] {
      color: var(--r-warning);
      background: var(--r-warning-bg);
      border: 1px solid var(--r-warning-border);
    }
  }

  &__sens {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 10px;
    font-weight: var(--r-weight-bold);
    letter-spacing: 0.08em;
    font-family: var(--r-font-mono);
    color: var(--r-danger);
    background: var(--r-danger-bg);
    border: 1px solid var(--r-danger-border);

    .el-icon { font-size: 11px; }
  }

  &__id {
    font-family: var(--r-font-mono);
    font-size: var(--r-font-md);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-primary);
    background: none;
    padding: 0;
    border: none;
    word-break: break-all;
  }

  &__desc {
    margin: 0;
    font-size: var(--r-font-sm);
    color: var(--r-text-secondary);
    line-height: var(--r-leading-snug);
  }

  &__roles {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-top: 4px;
    padding-top: 10px;
    border-top: 1px dashed var(--r-border-light);
  }

  &__roles-label {
    font-family: var(--r-font-mono);
    font-size: 10px;
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }

  &__roles-list {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }
}

.role-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 7px;
  border-radius: var(--r-radius-sm);
  font-family: var(--r-font-mono);
  font-size: 10px;
  font-weight: var(--r-weight-semibold);
  letter-spacing: 0.02em;

  &[data-tone="pink"]   { color: var(--r-pink);   background: var(--r-pink-bg);   border: 1px solid var(--r-pink-border); }
  &[data-tone="purple"] { color: var(--r-purple); background: var(--r-purple-bg); border: 1px solid var(--r-purple-border); }
  &[data-tone="cyan"]   { color: var(--r-cyan);   background: var(--r-cyan-bg);   border: 1px solid var(--r-cyan-border); }
  &[data-tone="teal"]   { color: var(--r-teal);   background: var(--r-teal-bg);   border: 1px solid var(--r-teal-border); }
  &[data-tone="gray"]   { color: var(--r-text-tertiary); background: var(--r-bg-panel); border: 1px solid var(--r-border); }
}

/* ============ EMPTY ============ */
.empty {
  padding: 48px 24px;
  text-align: center;
  font-size: var(--r-font-sm);
  color: var(--r-text-muted);
  background: var(--r-bg-card);
  border: 1px dashed var(--r-border);
  border-radius: var(--r-radius-lg);
}
</style>
