import { defineStore } from 'pinia'

interface Datasource {
  id: number
  name: string
  datasourceType: string
}

export const useDatasourceStore = defineStore('datasource', {
  state: () => ({
    datasources: [] as Datasource[],
  }),
  getters: {
    byType: (state) => (type: string) =>
      state.datasources.filter((ds) => ds.datasourceType.toUpperCase() === type.toUpperCase()),
  },
  actions: {
    setDatasources(list: Datasource[]) {
      this.datasources = list
    },
  },
})
