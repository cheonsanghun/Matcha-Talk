// src/polyfills.js
if (typeof window !== 'undefined') {
    if (!window.global) window.global = window;   // ← sockjs-client가 기대하는 global
    if (!window.process) window.process = { env: {} }; // 일부 라이브러리에서 process.env 접근
}
