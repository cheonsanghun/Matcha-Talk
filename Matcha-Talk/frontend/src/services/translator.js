export async function translate(text, targetLang = 'en') {
  // 간단한 예시 번역기: 실제 서비스 연동 전까지는 문자열을 뒤집어 반환합니다.
  // TODO: 외부 번역 API와 연동하여 실제 번역 결과를 제공하세요.
  return text.split('').reverse().join('');
}
