import api from './api'

const fallbackIceServers = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' },
]

let cachedIceServers = null

export async function getIceServers() {
  if (cachedIceServers) {
    return cachedIceServers
  }

  try {
    const { data } = await api.get('/webrtc/config')
    const servers = Array.isArray(data?.iceServers) ? data.iceServers : []
    cachedIceServers = servers.length > 0 ? servers : fallbackIceServers
  } catch (error) {
    console.warn('ICE 서버 구성을 불러오지 못했습니다.', error?.response?.status)
    cachedIceServers = fallbackIceServers
  }

  return cachedIceServers
}
