import { defineStore } from 'pinia'

interface Workspace {
  id: number
  name: string
  description: string
}

export const useWorkspaceStore = defineStore('workspace', {
  state: () => ({
    workspaces: [] as Workspace[],
    currentWorkspace: null as Workspace | null,
  }),
  actions: {
    setWorkspaces(list: Workspace[]) {
      this.workspaces = list
    },
    setCurrent(ws: Workspace) {
      this.currentWorkspace = ws
    },
  },
})
