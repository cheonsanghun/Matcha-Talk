import api from './api'

export async function translate(text, targetLang = 'en', sourceLang) {
  if (!text) {
    return ''
  }
  const { data } = await api.post('/translate', {
    text,
    targetLang,
    sourceLang
  })
  return data?.translatedText ?? ''
}
