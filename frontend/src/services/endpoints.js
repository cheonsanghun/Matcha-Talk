const DEFAULT_API_PATH = '/api'
const DEFAULT_WS_PATH = '/ws-stomp'

const rawApiBase = (import.meta.env.VITE_API_BASE_URL || '').trim()
const rawWsPath = (import.meta.env.VITE_WS_PATH || DEFAULT_WS_PATH).trim()

export const WS_PATH = normalizeWsPath(rawWsPath)
export const API_BASE_URL = resolveApiBase(rawApiBase)
export const SOCK_JS_URL = resolveSockJsUrl(API_BASE_URL, WS_PATH)

function resolveApiBase(rawBase) {
  if (!rawBase) {
    return DEFAULT_API_PATH
  }

  if (!isAbsoluteHttpUrl(rawBase)) {
    return normalizeRelativePath(rawBase) || DEFAULT_API_PATH
  }

  try {
    const parsed = new URL(rawBase)

    if (shouldPreferRelativePath(parsed)) {
      return normalizeRelativePath(parsed.pathname) || DEFAULT_API_PATH
    }

    return parsed.toString()
  } catch (error) {
    return DEFAULT_API_PATH
  }
}

function shouldPreferRelativePath(url) {
  if (typeof window === 'undefined' || !window.location) {
    return false
  }

  const { hostname: currentHost, protocol: currentProtocol } = window.location
  if (!currentHost) {
    return false
  }

  const configuredHost = url.hostname
  if (!configuredHost) {
    return false
  }

  const configuredProtocol = url.protocol || 'http:'
  const configuredHostIsLocal = isLocalLikeHostname(configuredHost)
  const currentHostIsLocal = isLocalLikeHostname(currentHost)

  if (configuredHost === currentHost) {
    if (configuredHostIsLocal && hasMixedContentConflict(currentProtocol, configuredProtocol)) {
      return true
    }
    return false
  }

  if (configuredHostIsLocal && !currentHostIsLocal) {
    return true
  }

  if (configuredHostIsLocal && hasMixedContentConflict(currentProtocol, configuredProtocol)) {
    return true
  }

  return false
}

function hasMixedContentConflict(currentProtocol, configuredProtocol) {
  return currentProtocol === 'https:' && configuredProtocol !== 'https:'
}

function isLocalLikeHostname(hostname) {
  if (!hostname) {
    return false
  }

  const normalized = hostname.toLowerCase()

  if (
    normalized === 'localhost' ||
    normalized === '127.0.0.1' ||
    normalized === '0.0.0.0' ||
    normalized === '::1' ||
    normalized === '[::1]'
  ) {
    return true
  }

  if (/^\d{1,3}(?:\.\d{1,3}){3}$/.test(normalized)) {
    const [a, b] = normalized.split('.').map(Number)
    if (a === 10 || a === 127) {
      return true
    }
    if (a === 192 && b === 168) {
      return true
    }
    if (a === 169 && b === 254) {
      return true
    }
    if (a === 172 && b >= 16 && b <= 31) {
      return true
    }
    if (a === 100 && b >= 64 && b <= 127) {
      return true
    }
    if (a === 198 && (b === 18 || b === 19)) {
      return true
    }
    return false
  }

  return false
}

function isAbsoluteHttpUrl(value) {
  return /^https?:\/\//i.test(value)
}

function normalizeRelativePath(path) {
  if (!path) {
    return ''
  }

  const trimmed = path.trim()
  if (!trimmed) {
    return ''
  }

  const withLeading = trimmed.startsWith('/') ? trimmed : `/${trimmed}`
  const withoutTrailing = withLeading.replace(/\/+$/, '')

  if (!withoutTrailing || withoutTrailing === '/') {
    return ''
  }

  return withoutTrailing
}

function normalizeWsPath(path) {
  const value = path || DEFAULT_WS_PATH
  const trimmed = value.trim()
  const normalized = trimmed ? trimmed : DEFAULT_WS_PATH
  return normalized.startsWith('/') ? normalized : `/${normalized}`
}

function resolveSockJsUrl(baseUrl, wsPath) {
  if (isAbsoluteHttpUrl(baseUrl)) {
    const url = new URL(baseUrl)
    const basePath = stripApiSuffix(url.pathname)
    url.pathname = joinPaths(basePath, wsPath)
    return url.toString()
  }

  const basePath = stripApiSuffix(baseUrl)
  if (!basePath) {
    return wsPath
  }

  const normalizedBase = basePath.startsWith('/') ? basePath : `/${basePath}`
  return joinPaths(normalizedBase, wsPath)
}

function stripApiSuffix(pathname = '') {
  if (!pathname) {
    return ''
  }

  if (/\/api\/?$/i.test(pathname)) {
    return pathname.replace(/\/api\/?$/i, '')
  }

  if (/^api\/?$/i.test(pathname)) {
    return ''
  }

  return pathname
}

function joinPaths(basePath, wsPath) {
  const normalizedBase = basePath.replace(/\/+$/, '')
  const normalizedWs = wsPath.startsWith('/') ? wsPath : `/${wsPath}`

  if (!normalizedBase) {
    return normalizedWs
  }

  return `${normalizedBase}${normalizedWs}`
}

export { resolveSockJsUrl }
