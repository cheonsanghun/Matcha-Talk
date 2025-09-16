import api from './api'

export async function translate(text, { sourceLang = 'auto', targetLang = 'en', save = false } = {}) {
  const { data } = await api.post('/translate', {
    text,
    sourceLang,
    targetLang,
    save,
  })
  return data
}

export async function saveVocabulary(original, translated) {
  const { data } = await api.post('/vocabulary', { original, translated })
  return data
}
