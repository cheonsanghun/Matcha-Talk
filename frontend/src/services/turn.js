const DEFAULT_ICE_SERVERS = [{ urls: 'stun:stun.l.google.com:19302' }]

let cachedIceServers = null
let pendingIceServersPromise = null

function normalizeIceServer(entry) {
  if (!entry) {
    return null
  }

  if (typeof entry === 'string') {
    const url = entry.trim()
    return url ? { urls: url } : null
  }

  if (typeof entry !== 'object') {
    return null
  }

  const rawUrls = entry.urls ?? entry.url
  const urls = []

  if (Array.isArray(rawUrls)) {
    for (const item of rawUrls) {
      if (typeof item === 'string') {
        const trimmed = item.trim()
        if (trimmed) {
          urls.push(trimmed)
        }
      }
    }
  } else if (typeof rawUrls === 'string') {
    for (const item of rawUrls.split(',')) {
      const trimmed = item.trim()
      if (trimmed) {
        urls.push(trimmed)
      }
    }
  }

  if (!urls.length) {
    return null
  }

  const credential = entry.credential ?? entry.password ?? null
  const server = {
    urls: urls.length === 1 ? urls[0] : urls,
  }

  if (entry.username) {
    server.username = entry.username
  }
  if (credential) {
    server.credential = credential
  }

  return server
}

function dedupeIceServers(servers) {
  const seen = new Set()
  const result = []

  for (const server of servers) {
    const normalized = normalizeIceServer(server)
    if (!normalized) {
      continue
    }

    const urlsArray = Array.isArray(normalized.urls) ? normalized.urls : [normalized.urls]
    if (!urlsArray.length) {
      continue
    }
    const key = `${urlsArray.join('|')}|${normalized.username || ''}|${normalized.credential || ''}`
    if (seen.has(key)) {
      continue
    }
    seen.add(key)
    result.push(normalized)
  }

  return result
}

function parseStaticIceServersFromEnv() {
  const urlsEnv = import.meta.env.VITE_TURN_URLS
  if (!urlsEnv) {
    return []
  }

  const username = import.meta.env.VITE_TURN_USERNAME
  const credential = import.meta.env.VITE_TURN_CREDENTIAL
  const urls = urlsEnv
    .split(',')
    .map((url) => url.trim())
    .filter((url) => url.length > 0)

  if (!urls.length) {
    return []
  }

  if (username || credential) {
    return dedupeIceServers([{ urls, username: username || undefined, credential: credential || undefined }])
  }

  return dedupeIceServers(urls.map((url) => ({ urls: url })))
}

function buildCredentialsRequestUrl() {
  const baseUrl = import.meta.env.VITE_TURN_CREDENTIALS_URL
  if (!baseUrl) {
    return null
  }

  let requestUrl = baseUrl.trim()
  if (!requestUrl) {
    return null
  }

  const apiKey = import.meta.env.VITE_TURN_API_KEY
  if (!apiKey) {
    return requestUrl
  }

  if (requestUrl.includes('{{API_KEY}}')) {
    return requestUrl.replace(/\{\{API_KEY\}\}/g, encodeURIComponent(apiKey))
  }

  if (requestUrl.includes('{API_KEY}')) {
    return requestUrl.replace(/\{API_KEY\}/g, encodeURIComponent(apiKey))
  }

  if (!/[?&]apiKey=/.test(requestUrl)) {
    const separator = requestUrl.includes('?') ? '&' : '?'
    requestUrl += `${separator}apiKey=${encodeURIComponent(apiKey)}`
  }

  return requestUrl
}

async function fetchIceServersFromApi() {
  const requestUrl = buildCredentialsRequestUrl()
  if (!requestUrl) {
    return []
  }

  try {
    const response = await fetch(requestUrl, { cache: 'no-store' })
    if (!response.ok) {
      console.warn(`TURN credentials 요청이 실패했습니다. status=${response.status}`)
      return []
    }

    const body = await response.json()
    const candidates = Array.isArray(body?.iceServers) ? body.iceServers : Array.isArray(body) ? body : []

    if (!Array.isArray(candidates) || candidates.length === 0) {
      console.warn('TURN credentials 응답 형식이 예상과 다릅니다.', body)
      return []
    }

    return dedupeIceServers(candidates)
  } catch (error) {
    console.error('TURN credentials를 가져오는 중 오류가 발생했습니다.', error)
    return []
  }
}

export async function getIceServers() {
  if (cachedIceServers) {
    return cachedIceServers
  }
  if (pendingIceServersPromise) {
    return pendingIceServersPromise
  }

  pendingIceServersPromise = (async () => {
    const staticServers = parseStaticIceServersFromEnv()
    const dynamicServers = await fetchIceServersFromApi()

    const combined = dedupeIceServers([
      ...DEFAULT_ICE_SERVERS.map((server) => ({ ...server })),
      ...staticServers,
      ...dynamicServers,
    ])

    if (!combined.length) {
      return DEFAULT_ICE_SERVERS.map((server) => ({ ...server }))
    }

    return combined
  })()

  try {
    cachedIceServers = await pendingIceServersPromise
  } catch (error) {
    console.error('ICE 서버 구성을 불러오는 중 오류가 발생했습니다.', error)
    cachedIceServers = DEFAULT_ICE_SERVERS.map((server) => ({ ...server }))
  } finally {
    pendingIceServersPromise = null
  }

  return cachedIceServers
}

export function resetIceServerCache() {
  cachedIceServers = null
  pendingIceServersPromise = null
}
