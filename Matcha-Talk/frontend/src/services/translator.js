import api from './api'

export async function translate(text, targetLang = 'en', { sourceLang = 'auto', save = false, context = null } = {}) {
  try {
    const payload = {
      text,
      sourceLang,
      targetLang,
      save,
      context,
    }

    const { data } = await api.post('/translate', payload)
    return {
      translatedText: data.translatedText ?? data.translated_text ?? '',
      saved: Boolean(data.saved),
    }
  } catch (error) {
    const responseMessage = error?.response?.data?.message
    const message = typeof responseMessage === 'string'
      ? responseMessage
      : '번역 요청 처리 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.'
    const wrapped = new Error(message)
    wrapped.cause = error
    throw wrapped
  }
}
