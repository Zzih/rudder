<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  oldSql: string
  newSql: string
}>()

interface DiffLine {
  type: 'equal' | 'added' | 'removed'
  oldLineNo: number | null
  newLineNo: number | null
  content: string
}

// Myers diff algorithm (simplified LCS-based)
function computeDiff(oldText: string, newText: string): DiffLine[] {
  const oldLines = oldText.split('\n')
  const newLines = newText.split('\n')

  const m = oldLines.length
  const n = newLines.length

  // Guard: avoid O(n*m) memory explosion on large files
  const MAX_LINES = 2000
  if (m > MAX_LINES || n > MAX_LINES) {
    return [{ type: 'equal' as const, oldLineNo: 1, newLineNo: 1, content: `(File too large to diff inline: ${m} vs ${n} lines)` }]
  }

  // Build LCS table
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0))

  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      if (oldLines[i - 1] === newLines[j - 1]) {
        dp[i][j] = dp[i - 1][j - 1] + 1
      } else {
        dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1])
      }
    }
  }

  // Backtrack to produce diff
  const result: DiffLine[] = []
  let i = m, j = n
  const stack: DiffLine[] = []

  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && oldLines[i - 1] === newLines[j - 1]) {
      stack.push({ type: 'equal', oldLineNo: i, newLineNo: j, content: oldLines[i - 1] })
      i--; j--
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      stack.push({ type: 'added', oldLineNo: null, newLineNo: j, content: newLines[j - 1] })
      j--
    } else {
      stack.push({ type: 'removed', oldLineNo: i, newLineNo: null, content: oldLines[i - 1] })
      i--
    }
  }

  // Reverse since we built it backwards
  while (stack.length) result.push(stack.pop()!)
  return result
}

const diffLines = computed(() => computeDiff(props.oldSql ?? '', props.newSql ?? ''))

const stats = computed(() => {
  let added = 0, removed = 0
  for (const line of diffLines.value) {
    if (line.type === 'added') added++
    if (line.type === 'removed') removed++
  }
  return { added, removed }
})
</script>

<template>
  <div class="diff-viewer">
    <div class="diff-stats">
      <span class="diff-stats__item diff-stats__added">+{{ stats.added }}</span>
      <span class="diff-stats__item diff-stats__removed">-{{ stats.removed }}</span>
    </div>
    <div class="diff-table-wrap">
      <table class="diff-table">
        <tbody>
          <tr v-for="(line, idx) in diffLines" :key="idx" :class="'diff-row--' + line.type">
            <td class="diff-ln diff-ln--old">{{ line.oldLineNo ?? '' }}</td>
            <td class="diff-ln diff-ln--new">{{ line.newLineNo ?? '' }}</td>
            <td class="diff-sign">
              <span v-if="line.type === 'added'">+</span>
              <span v-else-if="line.type === 'removed'">-</span>
              <span v-else>&nbsp;</span>
            </td>
            <td class="diff-content">
              <code>{{ line.content }}</code>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.diff-viewer {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: 6px;
  overflow: hidden;
}

.diff-stats {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 14px;
  background: var(--r-bg-panel);
  border-bottom: 1px solid var(--r-border);
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.diff-stats__added { color: var(--r-success); }
.diff-stats__removed { color: var(--r-danger); }

.diff-table-wrap {
  flex: 1;
  overflow: auto;
}

.diff-table {
  width: 100%;
  border-collapse: collapse;
  font-family: var(--r-font-mono);
  font-size: 12px;
  line-height: 20px;
}

.diff-table tr { border: none; }

.diff-ln {
  width: 40px;
  min-width: 40px;
  padding: 0 8px;
  text-align: right;
  color: var(--r-text-muted);
  user-select: none;
  vertical-align: top;
  border-right: 1px solid var(--r-border);
}

.diff-sign {
  width: 20px;
  min-width: 20px;
  padding: 0 4px;
  text-align: center;
  font-weight: 700;
  user-select: none;
  vertical-align: top;
}

.diff-content {
  padding: 0 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
.diff-content code {
  font-family: inherit;
  background: none;
}

/* Row colors */
.diff-row--equal { background: var(--r-bg-card); }
.diff-row--equal .diff-sign { color: var(--r-text-muted); }

.diff-row--added { background: var(--r-success-bg); }
.diff-row--added .diff-ln { background: var(--r-success-bg); color: var(--r-success); }
.diff-row--added .diff-sign { color: var(--r-success); }
.diff-row--added .diff-content { color: var(--r-success); }

.diff-row--removed { background: var(--r-danger-bg); }
.diff-row--removed .diff-ln { background: var(--r-danger-bg); color: var(--r-danger); }
.diff-row--removed .diff-sign { color: var(--r-danger); }
.diff-row--removed .diff-content { color: var(--r-danger); }
</style>
