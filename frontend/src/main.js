import Vue from 'vue'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'
import 'xterm/css/xterm.css'
import App from './App.vue'
import './i18n'

Vue.use(ElementUI)

new Vue({
  el: '#app',
  render: h => h(App)
})
