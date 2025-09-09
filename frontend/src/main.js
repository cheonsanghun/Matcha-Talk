import { createApp } from 'vue'
import { createPinia } from 'pinia'

import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import 'vuetify/styles'

import App from './App.vue'
import router from './router'

const pinia = createPinia()
const vuetify = createVuetify({ components, directives })


// 2) 앱 생성
const app = createApp(App)

// 3) 적용 순서 매우 중요: Pinia → Router → Vuetify
app.use(pinia)
app.use(router)
app.use(vuetify)

// 4) 마운트
app.mount('#app')
