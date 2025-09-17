const toCamel = (key = '') => {
  const normalized = key.replace(/[_-]+(\w)/g, (_, c) => (c ? c.toUpperCase() : ''))
  if (!normalized) return normalized
  return normalized.charAt(0).toLowerCase() + normalized.slice(1)
}

const toSnake = (key = '') =>
  key
    .replace(/([A-Z]+)/g, '_$1')
    .replace(/[-\s]+/g, '_')
    .replace(/_{2,}/g, '_')
    .replace(/^_+/, '')
    .toLowerCase()

const isPlainObject = (value) =>
  Object.prototype.toString.call(value) === '[object Object]'

export const camelizeKeys = (input) => {
  if (Array.isArray(input)) {
    return input.map((item) => camelizeKeys(item))
  }
  if (isPlainObject(input)) {
    return Object.entries(input).reduce((acc, [key, value]) => {
      const camelKey = toCamel(key)
      acc[camelKey] = camelizeKeys(value)
      return acc
    }, {})
  }
  return input
}

export const snakifyKeys = (input) => {
  if (Array.isArray(input)) {
    return input.map((item) => snakifyKeys(item))
  }
  if (isPlainObject(input)) {
    return Object.entries(input).reduce((acc, [key, value]) => {
      const snakeKey = toSnake(key)
      acc[snakeKey] = snakifyKeys(value)
      return acc
    }, {})
  }
  return input
}

export const isTransformable = (value) => Array.isArray(value) || isPlainObject(value)
