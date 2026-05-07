import { languages } from 'monaco-editor'
import type { LanguageSyntax } from '@/api/config'

// ============ State ============

let syntaxMap: Record<string, LanguageSyntax> = {}
let currentDialect = ''
let registered = false

/**
 * Load syntax definitions from backend. Call once on app init.
 */
export function setSyntaxMap(map: Record<string, LanguageSyntax>) {
  syntaxMap = map
}

/**
 * Set the active dialect (task type). Called when switching tabs.
 */
export function setCurrentDialect(taskType: string) {
  currentDialect = taskType?.toUpperCase() ?? ''
}

/**
 * Map task type to Monaco language ID.
 */
export function getMonacoLanguage(taskType: string): string {
  const syntax = syntaxMap[taskType?.toUpperCase()]
  if (syntax) return syntax.language
  const upper = taskType?.toUpperCase() ?? ''
  if (upper.endsWith('_SQL')) return 'sql'
  if (upper === 'PYTHON') return 'python'
  if (upper === 'SHELL') return 'shell'
  return 'sql'
}

/**
 * Register completion providers for all languages. Call once.
 */
export function registerCompletionProviders() {
  if (registered) return
  registered = true

  // Register for each language type
  for (const lang of ['sql', 'python', 'shell']) {
    languages.registerCompletionItemProvider(lang, {
      triggerCharacters: lang === 'sql' ? ['.', ' '] : ['.', ' ', '('],
      provideCompletionItems(model, position) {
        const word = model.getWordUntilPosition(position)
        const range = {
          startLineNumber: position.lineNumber,
          endLineNumber: position.lineNumber,
          startColumn: word.startColumn,
          endColumn: word.endColumn,
        }

        // Get syntax for current dialect
        const syntax = syntaxMap[currentDialect]
        if (!syntax) return { suggestions: [] }

        const suggestions: languages.CompletionItem[] = []

        // Keywords
        for (const kw of syntax.keywords ?? []) {
          suggestions.push({
            label: kw,
            kind: languages.CompletionItemKind.Keyword,
            insertText: kw,
            range,
          })
        }

        // Functions
        for (const fn of syntax.functions ?? []) {
          suggestions.push({
            label: fn,
            kind: languages.CompletionItemKind.Function,
            insertText: fn + '(${1})',
            insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet,
            range,
            detail: 'Function',
          })
        }

        // Snippets
        for (const snip of syntax.snippets ?? []) {
          suggestions.push({
            label: snip.label,
            kind: languages.CompletionItemKind.Snippet,
            insertText: snip.insertText,
            insertTextRules: languages.CompletionItemInsertTextRule.InsertAsSnippet,
            range,
            detail: snip.detail,
          })
        }

        return { suggestions }
      },
    })
  }
}
