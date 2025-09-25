/**
 * 케이스 변환 유틸리티 함수들
 * API 통신에서 프론트엔드(camelCase)와 백엔드(snake_case) 간 자동 변환을 위한 함수들
 */

/**
 * snake_case 문자열을 camelCase로 변환
 * @param {string} str - 변환할 문자열
 * @returns {string} camelCase 문자열
 */
function camelize(str) {
  return str.replace(/_([a-z])/g, (match, letter) => letter.toUpperCase())
}

/**
 * camelCase 문자열을 snake_case로 변환
 * @param {string} str - 변환할 문자열  
 * @returns {string} snake_case 문자열
 */
function snakify(str) {
  return str.replace(/[A-Z]/g, (match) => `_${match.toLowerCase()}`)
}

/**
 * 객체가 변환 가능한지 확인
 * @param {*} obj - 확인할 객체
 * @returns {boolean} 변환 가능 여부
 */
export function isTransformable(obj) {
  return obj !== null && 
         obj !== undefined && 
         typeof obj === 'object' && 
         !Array.isArray(obj) && 
         !(obj instanceof Date) && 
         !(obj instanceof File) && 
         !(obj instanceof FormData)
}

/**
 * 객체의 모든 키를 snake_case에서 camelCase로 변환 (깊은 변환)
 * @param {Object|Array} obj - 변환할 객체 또는 배열
 * @returns {Object|Array} camelCase로 변환된 객체 또는 배열
 */
export function camelizeKeys(obj) {
  if (Array.isArray(obj)) {
    return obj.map(camelizeKeys)
  }
  
  if (!isTransformable(obj)) {
    return obj
  }
  
  const result = {}
  
  for (const [key, value] of Object.entries(obj)) {
    const camelKey = camelize(key)
    
    if (Array.isArray(value)) {
      result[camelKey] = value.map(camelizeKeys)
    } else if (isTransformable(value)) {
      result[camelKey] = camelizeKeys(value)
    } else {
      result[camelKey] = value
    }
  }
  
  return result
}

/**
 * 객체의 모든 키를 camelCase에서 snake_case로 변환 (깊은 변환)
 * @param {Object|Array} obj - 변환할 객체 또는 배열
 * @returns {Object|Array} snake_case로 변환된 객체 또는 배열
 */
export function snakifyKeys(obj) {
  if (Array.isArray(obj)) {
    return obj.map(snakifyKeys)
  }
  
  if (!isTransformable(obj)) {
    return obj
  }
  
  const result = {}
  
  for (const [key, value] of Object.entries(obj)) {
    const snakeKey = snakify(key)
    
    if (Array.isArray(value)) {
      result[snakeKey] = value.map(snakifyKeys)
    } else if (isTransformable(value)) {
      result[snakeKey] = snakifyKeys(value)
    } else {
      result[snakeKey] = value
    }
  }
  
  return result
}

// 테스트용 예제 (개발 시에만 사용)
if (import.meta.env.DEV) {
  console.log('🔧 Case Utils loaded:')
  console.log('camelizeKeys example:', camelizeKeys({ user_name: 'test', user_id: 1 }))
  console.log('snakifyKeys example:', snakifyKeys({ userName: 'test', userId: 1 }))
}
