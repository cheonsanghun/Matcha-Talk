// src/polyfills.js
if (typeof window !== 'undefined') {
    if (!window.global) window.global = window;   // 일부 라이브러리는 window.global을 기대함
    if (!window.process) window.process = { env: {} }; // process.env 접근을 사용하는 패키지 대응
}
