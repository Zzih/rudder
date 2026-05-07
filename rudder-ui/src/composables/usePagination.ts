import { ref, nextTick } from 'vue'

export interface PaginationOptions<T> {
  /** API call that returns paginated data. Receives { pageNum, pageSize, ...extra params } */
  fetchApi: (params: Record<string, unknown>) => Promise<unknown>
  /** Extract items array from API response. Default: (res) => res.data ?? [] */
  extractData?: (res: unknown) => T[]
  /** Extract total count from API response. Default: (res) => res.total ?? 0 */
  extractTotal?: (res: unknown) => number
  /** Default page size. Default: 20 */
  defaultPageSize?: number
  /** Enable appear animation flag. Default: false */
  animated?: boolean
}

/**
 * Composable for paginated list views.
 * Manages pageNum, pageSize, total, loading, data list, and optional appear animation.
 */
export function usePagination<T = unknown>(options: PaginationOptions<T>) {
  const {
    fetchApi,
    extractData = (res: any) => res.data ?? [],
    extractTotal = (res: any) => res.total ?? 0,
    defaultPageSize = 20,
    animated = false,
  } = options

  const data = ref<T[]>([]) as import('vue').Ref<T[]>
  const loading = ref(false)
  const pageNum = ref(1)
  const pageSize = ref(defaultPageSize)
  const total = ref(0)
  const appeared = ref(!animated)

  async function fetch(extraParams?: Record<string, unknown>) {
    loading.value = true
    if (animated) appeared.value = false
    try {
      const res = await fetchApi({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...extraParams,
      })
      data.value = extractData(res)
      total.value = extractTotal(res)
      if (animated) {
        await nextTick()
        appeared.value = true
      }
    } finally {
      loading.value = false
    }
  }

  function handlePageChange(page: number) {
    pageNum.value = page
    fetch()
  }

  function handleSizeChange(size: number) {
    pageSize.value = size
    pageNum.value = 1
    fetch()
  }

  /** Reset to page 1 and re-fetch. Typically called after search/filter changes. */
  function resetAndFetch(extraParams?: Record<string, unknown>) {
    pageNum.value = 1
    fetch(extraParams)
  }

  return {
    data,
    loading,
    pageNum,
    pageSize,
    total,
    appeared,
    fetch,
    handlePageChange,
    handleSizeChange,
    resetAndFetch,
  }
}
