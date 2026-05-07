export interface HttpParams {
  url: string
  method: string
  contentType: string
  body: string
  headers: Record<string, string>
  successCodes: number[]
  connectTimeoutMs: number
  readTimeoutMs: number
  retries: number
  retryDelayMs: number
  expectedBodyContains: string
}

export function defaultHttpParams(): HttpParams {
  return {
    url: '',
    method: 'GET',
    contentType: 'application/json',
    body: '',
    headers: {},
    successCodes: [200],
    connectTimeoutMs: 10_000,
    readTimeoutMs: 60_000,
    retries: 0,
    retryDelayMs: 1_000,
    expectedBodyContains: '',
  }
}
