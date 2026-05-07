<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { editor as monacoEditor } from 'monaco-editor'
import type * as MonacoTypes from 'monaco-editor'
import { useMonacoTheme } from '@/composables/useMonacoTheme'
import { registerCompletionProviders, setSyntaxMap, setCurrentDialect, getMonacoLanguage } from '@/utils/sqlCompletion'
import { getAllSyntax } from '@/api/config'

const props = defineProps<{
  modelValue: string
  language?: string       // Override language (sql, python, shell)
  taskType?: string       // TaskType enum value, auto-detects language
  height?: string         // CSS height, default 240px
  readOnly?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const monacoTheme = useMonacoTheme()
const containerRef = ref<HTMLDivElement>()
let editor: MonacoTypes.editor.IStandaloneCodeEditor | null = null
let syntaxLoaded = false

async function loadSyntax() {
  if (syntaxLoaded) return
  try {
    const { data } = await getAllSyntax()
    if (data) setSyntaxMap(data)
    registerCompletionProviders()
    syntaxLoaded = true
  } catch { /* fallback */ }
}

function getLang(): string {
  if (props.language) return props.language
  if (props.taskType) return getMonacoLanguage(props.taskType)
  return 'sql'
}

onMounted(async () => {
  await loadSyntax()
  await nextTick()
  if (!containerRef.value) return

  const lang = getLang()
  if (props.taskType) setCurrentDialect(props.taskType)

  editor = monacoEditor.create(containerRef.value, {
    value: props.modelValue ?? '',
    language: lang,
    theme: monacoTheme,
    minimap: { enabled: false },
    fontSize: 13,
    lineNumbers: 'on',
    scrollBeyondLastLine: false,
    automaticLayout: true,
    tabSize: 2,
    wordWrap: 'on',
    renderLineHighlight: 'line',
    lineDecorationsWidth: 8,
    padding: { top: 8 },
    readOnly: props.readOnly,
    suggestOnTriggerCharacters: true,
    quickSuggestions: true,
  })

  editor.onDidChangeModelContent(() => {
    emit('update:modelValue', editor!.getValue())
  })
})

// Sync external value changes
watch(() => props.modelValue, (val) => {
  if (editor && val !== editor.getValue()) {
    editor.setValue(val ?? '')
  }
})

// Switch language when taskType changes
watch(() => props.taskType, (tt) => {
  if (!editor || !tt) return
  const lang = getMonacoLanguage(tt)
  const model = editor.getModel()
  if (model && model.getLanguageId() !== lang) {
    monacoEditor.setModelLanguage(model, lang)
  }
  setCurrentDialect(tt)
})

function getSelection(): string {
  if (!editor) return ''
  const selection = editor.getSelection()
  if (!selection || selection.isEmpty()) return ''
  return editor.getModel()?.getValueInRange(selection) ?? ''
}

defineExpose({ getSelection })

onBeforeUnmount(() => {
  editor?.dispose()
  editor = null
})
</script>

<template>
  <div
    ref="containerRef"
    class="monaco-input"
    :style="{ height: height || '240px' }"
  />
</template>

<style scoped>
.monaco-input {
  width: 100%;
  border: 1px solid var(--r-border);
  border-radius: 4px;
  overflow: hidden;
}

.monaco-input:focus-within {
  border-color: var(--r-accent);
}
</style>
