<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePagination } from '@/composables/usePagination'
import { useGrantView } from '@/composables/useGrantView'
import type { DataPermPermissionItem } from '@/api/data-perm'

const props = defineProps<{
  /** 分页拉取一页权限项(已拍平);由各页传入对应后端接口(我的数据权限 / 总览)。 */
  loadFn: (params: { pageNum: number; pageSize: number }) => Promise<unknown>
}>()

const { t } = useI18n()
const { fmtTime, resourcePath, serviceLabel, permLabels } = useGrantView()

const { data, loading, pageNum, pageSize, total, fetch, handlePageChange } =
  usePagination<DataPermPermissionItem>({
    fetchApi: params => props.loadFn(params as { pageNum: number; pageSize: number }),
    extractData: (res: any) => (res?.data ?? []) as DataPermPermissionItem[],
    defaultPageSize: 10,
  })

onMounted(fetch)

const isExpired = (exp?: string) => !!exp && new Date(exp).getTime() <= Date.now()
</script>

<template>
  <div v-loading="loading" class="gil">
    <div v-if="!data.length && !loading" class="placeholder-line">{{ t('dataPerm.empty') }}</div>

    <template v-else>
      <div class="items-head">
        <span>{{ t('dataPerm.colService') }}</span>
        <span>{{ t('dataPerm.colResource') }}</span>
        <span>{{ t('dataPerm.colAccesses') }}</span>
        <span>{{ t('dataPerm.effectiveTime') }}</span>
        <span>{{ t('dataPerm.expirationTime') }}</span>
      </div>

      <div v-for="(it, i) in data" :key="i" class="items-row">
        <span class="ds-chip" :title="serviceLabel(it)">{{ serviceLabel(it) }}</span>
        <code class="resource">{{ resourcePath(it) }}</code>
        <div class="access-chips">
          <span v-for="(lab, li) in permLabels(it)" :key="li" class="access-chip">{{ lab }}</span>
        </div>
        <span class="time-cell">{{ fmtTime(it.effectiveTime) }}</span>
        <span class="time-cell" :class="{ 'is-expired': isExpired(it.expirationTime) }">
          {{ fmtTime(it.expirationTime) }}
        </span>
      </div>

      <el-pagination v-if="total > pageSize" class="items-pagination"
        small layout="total, prev, pager, next"
        :current-page="pageNum" :page-size="pageSize" :total="total"
        @current-change="handlePageChange" />
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/data-perm-grant-card.scss' as *;

.gil { min-height: 28px; }

// 资源列 1fr 占满左侧(尽量大,长路径有空间),操作分组 / 生效 / 到期为固定宽的右侧一组。
// 全部固定 / fr → 跨独立 grid 仍对齐;权限包与直接授权列序一致。
.items-head, .items-row {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr) 180px 150px 150px;
  gap: 16px;
  align-items: start;
  padding: 6px 8px;
}
.items-head {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.1em;
}
.items-row {
  border-top: 1px dashed var(--r-border-light);
  font-size: var(--r-font-sm);
}
.time-cell {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  color: var(--r-text-secondary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;

  &.is-expired { color: var(--r-text-disabled); text-decoration: line-through; }
}

.items-pagination {
  margin-top: var(--r-space-3);
  display: flex;
  justify-content: flex-end;
}
</style>
