<template>
  <div id="app">
    <div class="header">
      <div class="header-logo">
        <img :src="imgUrl('logo.svg')" width="38px" height="38px" style="margin-right: 10px"/>
        <span class="cyan">Net</span>
        <span class="purple">Debugger</span>
        <span style="font-size:10px;color:rgb(179 179 179 / 0.8);margin-left:4px;margin-top: 2px;font-weight:400;letter-spacing:0;">{{ appVersion }}</span>
      </div>
      <div class="header-drag" @pointerdown="startDrag" @dblclick="winMaximize"></div>
      <div class="header-status">
        <span><span class="status-dot" :class="tcpHasRunning ? 'green' : 'gray'"></span> {{ $t('statusTcpServer') }} {{ tcpRunningCount }}/{{ tcpSessions.length }}</span>
        <span><span class="status-dot" :class="tcpCliHasConnected ? 'green' : 'gray'"></span> {{ $t('statusTcpClient') }} {{ tcpCliConnectedCount }}/{{ tcpCliSessions.length }}</span>
        <span><span class="status-dot" :class="udpHasRunning ? 'green' : 'gray'"></span> {{ $t('statusUdpServer') }} {{ udpRunningCount }}/{{ udpSessions.length }}</span>
        <span><span class="status-dot" :class="udpCliHasBound ? 'green' : 'gray'"></span> {{ $t('statusUdpClient') }} {{ udpCliBoundCount }}/{{ udpCliSessions.length }}</span>
        <span><span class="status-dot" :class="sshHasConnected ? 'green' : 'gray'"></span> {{ $t('statusSshClient') }} {{ sshConnectedCount }}/{{ sshSessions.length }}</span>
      </div>
      <!-- Language Switcher -->
      <el-dropdown trigger="click" @command="setLang" style="margin-right: 6px">
        <span class="lang-btn" :title="$t('language')">{{ langLabel }}</span>
        <el-dropdown-menu slot="dropdown">
          <el-dropdown-item command="zh-CN">中文</el-dropdown-item>
          <el-dropdown-item command="en">English</el-dropdown-item>
        </el-dropdown-menu>
      </el-dropdown>
      <!-- Theme Switcher -->
      <el-dropdown trigger="click" @command="setTheme" style="margin-right: 10px">
        <span class="theme-btn" :title="$t('theme') + '：' + themeLabel">
          <span v-if="theme==='light'">☀️</span>
          <span v-else-if="theme==='dark'">🌙</span>
          <span v-else>💻</span>
        </span>
        <el-dropdown-menu slot="dropdown">
          <el-dropdown-item command="light">☀️ {{ $t('themeLight') }}</el-dropdown-item>
          <el-dropdown-item command="dark">🌙 {{ $t('themeDark') }}</el-dropdown-item>
          <el-dropdown-item command="auto">💻 {{ $t('themeAuto') }}</el-dropdown-item>
        </el-dropdown-menu>
      </el-dropdown>
      <div class="win-btn" @click="winMinimize" :title="$t('minimize')">─</div>
      <div class="win-btn" @click="winMaximize" :title="isMaximized ? $t('restore') : $t('maximize')">{{ isMaximized ? '⧉' : '▢' }}</div>
      <div class="win-btn close" @click="winClose" :title="$t('close')">✕</div>
    </div>

    <div class="main-content">
      <el-tabs v-model="activeTab" type="default">

        <!-- ============ SSH CLIENT ============ -->
        <el-tab-pane name="sshClient">
          <span slot="label"><img :src="imgUrl('sshClient.svg')" class="tab-icon"/>{{ $t('tabSshClient') }}</span>
          <ssh-client-panel
            ref="sshPanel"
            :sessions="sshSessions"
            :activeId.sync="sshActiveId"
            @add-session="addSshClient"
            @remove-session="removeSession('sshClient', $event)"
            @show-context-menu="showSessionContextMenu"
          />
        </el-tab-pane>

        <!-- ============ TCP SERVER ============ -->
        <el-tab-pane name="tcpServer">
          <span slot="label"><img :src="imgUrl('tcpServer.svg')" class="tab-icon"/>{{ $t('tabTcpServer') }}</span>
          <tcp-server-panel
            :sessions="tcpSessions"
            :activeId.sync="tcpActiveId"
            @add-session="addTcpServer"
            @remove-session="removeSession('tcpServer', $event)"
            @show-context-menu="showSessionContextMenu"
          />
        </el-tab-pane>

        <!-- ============ TCP CLIENT ============ -->
        <el-tab-pane name="tcpClient">
          <span slot="label"><img :src="imgUrl('tcpClient.svg')" class="tab-icon"/>{{ $t('tabTcpClient') }}</span>
          <tcp-client-panel
            :sessions="tcpCliSessions"
            :activeId.sync="tcpCliActiveId"
            @add-session="addTcpClient"
            @remove-session="removeSession('tcpClient', $event)"
            @show-context-menu="showSessionContextMenu"
          />
        </el-tab-pane>

        <!-- ============ UDP SERVER ============ -->
        <el-tab-pane name="udpServer">
          <span slot="label"><img :src="imgUrl('udpServer.svg')" class="tab-icon"/>{{ $t('tabUdpServer') }}</span>
          <udp-server-panel
            :sessions="udpSessions"
            :activeId.sync="udpActiveId"
            @add-session="addUdpServer"
            @remove-session="removeSession('udpServer', $event)"
            @show-context-menu="showSessionContextMenu"
          />
        </el-tab-pane>

        <!-- ============ UDP CLIENT ============ -->
        <el-tab-pane name="udpClient">
          <span slot="label"><img :src="imgUrl('udpClient.svg')" class="tab-icon"/>{{ $t('tabUdpClient') }}</span>
          <udp-client-panel
            :sessions="udpCliSessions"
            :activeId.sync="udpCliActiveId"
            @add-session="addUdpClient"
            @remove-session="removeSession('udpClient', $event)"
            @show-context-menu="showSessionContextMenu"
          />
        </el-tab-pane>

      </el-tabs>
    </div>

    <!-- Session Context Menu -->
    <div class="session-context-menu" v-show="sessionContextMenu.visible" :style="{ left: sessionContextMenu.x + 'px', top: sessionContextMenu.y + 'px' }" @click.stop>
      <div class="ctx-item" @click="copySession">
        <span class="ctx-icon">📋</span><span>{{ $t('sessionCopy') }}</span>
      </div>
      <div class="ctx-item" @click="renameSession">
        <span class="ctx-icon">✏️</span><span>{{ $t('sessionRename') }}</span>
      </div>
      <div class="ctx-separator"></div>
      <div class="ctx-item ctx-danger" @click="deleteSessionFromMenu">
        <span class="ctx-icon">❌</span><span>{{ $t('sessionDelete') }}</span>
      </div>
    </div>
  </div>
</template>

<script>
import { callJava, makeSession, resetCounter, formatSize } from './utils.js'
import { i18nMessages, locale } from './i18n.js'
import './bridge.js'

import SshClientPanel from './components/SshClientPanel.vue'
import TcpServerPanel from './components/TcpServerPanel.vue'
import TcpClientPanel from './components/TcpClientPanel.vue'
import UdpServerPanel from './components/UdpServerPanel.vue'
import UdpClientPanel from './components/UdpClientPanel.vue'

export default {
  name: 'App',
  components: {
    SshClientPanel,
    TcpServerPanel,
    TcpClientPanel,
    UdpServerPanel,
    UdpClientPanel
  },
  data() {
    return {
      appVersion: __APP_VERSION__,
      activeTab: 'tcpServer',
      isMaximized: false,
      tcpSessions: [], tcpActiveId: null,
      tcpCliSessions: [], tcpCliActiveId: null,
      udpSessions: [], udpActiveId: null,
      udpCliSessions: [], udpCliActiveId: null,
      sshSessions: [], sshActiveId: null,
      sessionContextMenu: { visible: false, x: 0, y: 0, type: '', session: null },
      theme: 'auto',
      lang: locale.value
    }
  },
  computed: {
    tcpActiveSession() { return this.tcpSessions.find(s=>s.id===this.tcpActiveId) },
    tcpCliActiveSession() { return this.tcpCliSessions.find(s=>s.id===this.tcpCliActiveId) },
    udpActiveSession() { return this.udpSessions.find(s=>s.id===this.udpActiveId) },
    udpCliActiveSession() { return this.udpCliSessions.find(s=>s.id===this.udpCliActiveId) },
    sshActiveSession() { return this.sshSessions.find(s=>s.id===this.sshActiveId) },
    tcpHasRunning() { return this.tcpSessions.some(s=>s.running) },
    tcpRunningCount() { return this.tcpSessions.filter(s=>s.running).length },
    tcpCliHasConnected() { return this.tcpCliSessions.some(s=>s.connected) },
    tcpCliConnectedCount() { return this.tcpCliSessions.filter(s=>s.connected).length },
    udpHasRunning() { return this.udpSessions.some(s=>s.running) },
    udpRunningCount() { return this.udpSessions.filter(s=>s.running).length },
    udpCliHasBound() { return this.udpCliSessions.some(s=>s.bound) },
    udpCliBoundCount() { return this.udpCliSessions.filter(s=>s.bound).length },
    sshHasConnected() { return this.sshSessions.some(s=>s.connected) },
    sshConnectedCount() { return this.sshSessions.filter(s=>s.connected).length },
    sessionConfigSnapshot() {
      var self = this
      function extract(arr, type) {
        return arr.map(function(s) {
          var cfg = { i: s.id, t: type }
          if (type === 'tcpServer') { cfg.p = s.port; cfg.m = s.maxConn }
          else if (type === 'tcpClient') { cfg.h = s.host; cfg.p = s.port }
          else if (type === 'udpServer') { cfg.p = s.port }
          else if (type === 'udpClient') { cfg.p = s.port; cfg.sh = s.sendHost; cfg.sp = s.sendPort }
          else if (type === 'sshClient') { cfg.h = s.host; cfg.p = s.port; cfg.u = s.username; cfg.pw = s.password }
          return cfg
        })
      }
      return JSON.stringify(
        [].concat(
          extract(self.tcpSessions, 'tcpServer'),
          extract(self.tcpCliSessions, 'tcpClient'),
          extract(self.udpSessions, 'udpServer'),
          extract(self.udpCliSessions, 'udpClient'),
          extract(self.sshSessions, 'sshClient')
        )
      )
    },
    themeLabel() {
      var m = { light: this.$t('themeLightShort'), dark: this.$t('themeDarkShort'), auto: this.$t('themeAutoShort') }
      return m[this.theme] || this.$t('themeAutoShort')
    },
    langLabel() { return this.lang === 'zh-CN' ? '中' : 'EN' }
  },
  watch: {
    activeTab: function(val) {
      var self = this
      this.$nextTick(function() {
        self.resizeTextareas()
        if (val === 'sshClient' && self.sshActiveSession && self.sshActiveSession.connected) {
          self.$nextTick(function() {
            var sp = self.$refs.sshPanel
            if (sp) sp.initSshTerminal(self.sshActiveSession)
          })
        }
      })
      callJava('persistConfig', 'activeTab', val)
    },
    sshActiveId: function(newId) {
      var self = this
      if (newId) {
        this.$nextTick(function() {
          var newSession = self.sshSessions.find(function(s){return s.id===newId})
          if (newSession && newSession.connected && self.activeTab === 'sshClient') {
            self.$nextTick(function() {
              var sp = self.$refs.sshPanel
              if (sp) sp.initSshTerminal(newSession)
            })
          }
        })
      }
    }
  },
  methods: {
    $t: function(key, args) {
      var msgs = i18nMessages[this.lang] || i18nMessages['zh-CN']
      var text = msgs[key] !== undefined ? msgs[key] : key
      if (args && args.length) {
        for (var i = 0; i < args.length; i++) {
          text = text.replace('{' + i + '}', args[i])
        }
      }
      return text
    },
    setLang: function(cmd) {
      this.lang = cmd
      locale.value = cmd
      localStorage.setItem('netdebugger-language', cmd)
      callJava('setLanguage', cmd)
    },
    imgUrl: function(name) { return 'img/' + name },
    startDrag: function(e) {
      if (e.clientY <= 6) return
      callJava('windowDragStart')
      var el = e.currentTarget
      el.setPointerCapture(e.pointerId)
      var onUp = function(ev) {
        callJava('windowDragEnd')
        el.releasePointerCapture(ev.pointerId)
        el.removeEventListener('pointerup', onUp)
      }
      el.addEventListener('pointerup', onUp)
    },
    winMinimize: function() { callJava('windowMinimize') },
    winMaximize: function() { callJava('windowMaximize'); this.isMaximized = !this.isMaximized },
    winClose: function() { callJava('windowClose') },
    resizeTextareas: function() {
      var self = this
      this.$nextTick(function() {
        var panels = document.querySelectorAll('.send-card')
        panels.forEach(function(card) {
          var inner = card.querySelector('.el-textarea__inner')
          if (!inner) return
          inner.style.height = ''
        })
      })
    },
    // ==================== Java Bridge Event Handling ====================
    handleEvent: function(json) {
      var evt
      try { evt = JSON.parse(json) } catch(e) { console.error('[NetDebugger] JSON parse error:', e, json.substring(0,200)); return }
      var id = evt.id, type = evt.type, data = evt.data
      if (type === 'restoreState') {
        this.restoreFromPersisted(data)
        return
      }
      var s = this.findSession(id)
      if (type === 'log' && s) {
        try { var e = typeof data==='string'?JSON.parse(data):data; s.logs.push(e); if(s.logs.length>500)s.logs.shift(); var self=this; this.$nextTick(function(){ self.scrollLog(id) }) } catch(ex){ console.error('[NetDebugger] Log parse:', ex) }
        return
      }
      if (!s) return
      switch(type) {
        case 'serverStarted': s.running=true; s.loading=false; break
        case 'serverStopped': s.running=false; s.clients=[]; break
        case 'clientList': try { var arr=JSON.parse(data); this.$set(s,'clients',arr) } catch(ex){ console.error('[NetDebugger] clientList parse:', ex, data) } break
        case 'clientConnected': break
        case 'clientDisconnected': var idx2=s.clients.indexOf(data); if(idx2>=0) s.clients.splice(idx2,1); break
        case 'connected':
          s.connected=true; s.loading=false
          if (this.sshSessions.indexOf(s) >= 0) {
            var sp = this.$refs.sshPanel
            if (s.id === this.sshActiveId && this.activeTab === 'sshClient' && sp) { sp.initSshTerminal(s) }
            if (sp) sp.refreshSftp(s)
          }
          break
        case 'disconnected':
          s.connected=false
          if (this.sshSessions.indexOf(s) >= 0) {
            var sp = this.$refs.sshPanel
            if (sp) sp.destroySshTerminal(s)
          }
          break
        case 'bound': s.bound=true; s.loading=false; break
        case 'unbound': s.bound=false; break
        case 'error': this.$message.error('['+id+'] '+data); s.loading=false; if (this.sshSessions.indexOf(s) >= 0) { var sp = this.$refs.sshPanel; if (sp) sp.$set(s, 'sftpLoading', false) } break
        case 'terminalData':
          if (this.sshSessions.indexOf(s) >= 0) {
            var sp = this.$refs.sshPanel
            if (sp) sp.handleTerminalData(s, data)
          }
          break
        case 'sftpList':
          var sp = this.$refs.sshPanel
          if (sp) sp.handleSftpList(s, data)
          break
        case 'sftpFileData':
          var sp = this.$refs.sshPanel
          if (sp) sp.handleSftpFileData(s, data)
          break
        case 'sftpUploaded':
          var sp = this.$refs.sshPanel
          if (sp) { sp.finishUpload(); sp.refreshSftp(s) }
          break
        case 'sftpProgress':
          var sp = this.$refs.sshPanel
          if (sp) sp.handleSftpProgress(data)
          break
        case 'uploadCancelled':
          var sp = this.$refs.sshPanel
          if (sp) sp.cancelUpload()
          break
        case 'sftpPwd':
          var sp = this.$refs.sshPanel
          if (sp) sp.handleSftpPwd(s, data)
          break
      }
    },
    findSession: function(id) {
      var all = this.tcpSessions.concat(this.tcpCliSessions, this.udpSessions, this.udpCliSessions, this.sshSessions)
      return all.find(function(s){return s.id===id})
    },
    scrollLog: function(id) {
      if (this.sshSessions.find(s=>s.id===id)) return
      // Log containers are now inside child components; child components auto-scroll via watchers
    },
    removeSession: function(type, id) {
      var arr = type==='tcpServer'?this.tcpSessions:type==='tcpClient'?this.tcpCliSessions:type==='udpServer'?this.udpSessions:type==='udpClient'?this.udpCliSessions:this.sshSessions
      var aid = type==='tcpServer'?'tcpActiveId':type==='tcpClient'?'tcpCliActiveId':type==='udpServer'?'udpActiveId':type==='udpClient'?'udpCliActiveId':'sshActiveId'
      var idx = arr.findIndex(function(s){return s.id===id})
      if (idx < 0) return
      if (type === 'sshClient') {
        var sess = arr[idx]
        if (sess.terminal) { sess.terminal.dispose(); sess.terminal = null; sess.fitAddon = null }
      }
      arr.splice(idx, 1)
      if (this[aid] === id) this[aid] = arr.length > 0 ? arr[0].id : null
      callJava('removeInstance', id)
      callJava('clearLogs', id)
      this.saveAllSessions()
    },
    // ==================== Create Sessions ====================
    addTcpServer() { var s = makeSession('srv-', {port:8888, maxConn:10, running:false, clients:[], targetClient:''}); this.tcpSessions.push(s); this.tcpActiveId=s.id; callJava('createTcpServer', s.id); this.saveAllSessions() },
    addTcpClient() { var s = makeSession('cli-', {host:'127.0.0.1', port:8888, connected:false}); this.tcpCliSessions.push(s); this.tcpCliActiveId=s.id; callJava('createTcpClient', s.id); this.saveAllSessions() },
    addUdpServer() { var s = makeSession('udpSrv-', {port:9999, running:false, sendHost:'127.0.0.1', sendPort:9999, clients:[], targetClient:''}); this.udpSessions.push(s); this.udpActiveId=s.id; callJava('createUdpServer', s.id); this.saveAllSessions() },
    addUdpClient() { var s = makeSession('udpCli-', {port:10000, bound:false, sendHost:'127.0.0.1', sendPort:9999}); this.udpCliSessions.push(s); this.udpCliActiveId=s.id; callJava('createUdpClient', s.id); this.saveAllSessions() },
    addSshClient() { var s = makeSession('ssh-', {host:'192.168.1.1', port:22, username:'root', password:'', connected:false, terminal:null, fitAddon:null, sftpPath:'/', sftpFiles:[], sftpLoading:false, dlDir:'', _showDlSettings:false, _sftpPathEditing:false, _sftpPathInput:''}); this.sshSessions.push(s); this.sshActiveId=s.id; callJava('createSshClient', s.id); this.saveAllSessions() },
    // ==================== Theme ====================
    setTheme: function(cmd) { this.theme = cmd; localStorage.setItem('netdebugger-theme', cmd); callJava('persistConfig', 'theme', cmd) },
    // ==================== Session Context Menu ====================
    showSessionContextMenu(e, type, session) {
      this.sessionContextMenu.visible = true
      this.sessionContextMenu.x = e.clientX
      this.sessionContextMenu.y = e.clientY
      this.sessionContextMenu.type = type
      this.sessionContextMenu.session = session
    },
    hideSessionContextMenu() {
      this.sessionContextMenu.visible = false
    },
    copySession() {
      var ctx = this.sessionContextMenu
      if (!ctx.session) return
      var oldSession = ctx.session
      var type = ctx.type
      var self = this
      var prefix = oldSession.id.replace(/\d+$/, '')
      var arr, createMethod
      if (type === 'tcpServer') { arr = self.tcpSessions; createMethod = 'createTcpServer' }
      else if (type === 'tcpClient') { arr = self.tcpCliSessions; createMethod = 'createTcpClient' }
      else if (type === 'udpServer') { arr = self.udpSessions; createMethod = 'createUdpServer' }
      else if (type === 'udpClient') { arr = self.udpCliSessions; createMethod = 'createUdpClient' }
      else if (type === 'sshClient') { arr = self.sshSessions; createMethod = 'createSshClient' }
      else { this.hideSessionContextMenu(); return }
      var newSession = makeSession(prefix, { logs: [] })
      var newId = newSession.id
      if (type === 'tcpServer') {
        newSession.port = oldSession.port; newSession.maxConn = oldSession.maxConn
        newSession.running = false; newSession.clients = []; newSession.targetClient = ''
      } else if (type === 'tcpClient') {
        newSession.host = oldSession.host; newSession.port = oldSession.port; newSession.connected = false
      } else if (type === 'udpServer') {
        newSession.port = oldSession.port; newSession.running = false; newSession.clients = []; newSession.targetClient = ''
        newSession.sendHost = oldSession.sendHost || '127.0.0.1'; newSession.sendPort = oldSession.sendPort || oldSession.port
      } else if (type === 'udpClient') {
        newSession.port = oldSession.port; newSession.bound = false
        newSession.sendHost = oldSession.sendHost || '127.0.0.1'; newSession.sendPort = oldSession.sendPort || 9999
      } else if (type === 'sshClient') {
        newSession.host = oldSession.host; newSession.port = oldSession.port
        newSession.username = oldSession.username; newSession.password = oldSession.password
        newSession.connected = false; newSession.terminal = null; newSession.fitAddon = null
        newSession.sftpPath = '/'; newSession.sftpFiles = []; newSession.sftpLoading = false
        newSession._sftpPathEditing = false; newSession._sftpPathInput = ''
      }
      arr.push(newSession)
      callJava(createMethod, newId)
      self.saveAllSessions()
      if (type === 'tcpServer') self.tcpActiveId = newId
      else if (type === 'tcpClient') self.tcpCliActiveId = newId
      else if (type === 'udpServer') self.udpActiveId = newId
      else if (type === 'udpClient') self.udpCliActiveId = newId
      else if (type === 'sshClient') self.sshActiveId = newId
      this.hideSessionContextMenu()
    },
    renameSession() {
      var ctx = this.sessionContextMenu
      if (!ctx.session) return
      var self = this
      var oldId = ctx.session.id
      var session = ctx.session
      var type = ctx.type
      this.$prompt(
        (this.$t('sessionRenamePrompt') || '输入新名称').replace('{0}', oldId),
        this.$t('sessionRename') || '重命名',
        { confirmButtonText: this.$t('confirm') || '确定', cancelButtonText: this.$t('cancel') || '取消', inputValue: oldId }
      ).then(function(result) {
        var newId = (result && result.value) ? result.value.trim() : ''
        if (!newId || newId === oldId) return
        callJava('removeInstance', oldId)
        session.id = newId
        if (type === 'tcpServer') callJava('createTcpServer', newId)
        else if (type === 'tcpClient') callJava('createTcpClient', newId)
        else if (type === 'udpServer') callJava('createUdpServer', newId)
        else if (type === 'udpClient') callJava('createUdpClient', newId)
        else if (type === 'sshClient') callJava('createSshClient', newId)
        var activeKey = type === 'tcpServer' ? 'tcpActiveId' : type === 'tcpClient' ? 'tcpCliActiveId' : type === 'udpServer' ? 'udpActiveId' : type === 'udpClient' ? 'udpCliActiveId' : 'sshActiveId'
        if (self[activeKey] === oldId) self[activeKey] = newId
        self.saveAllSessions()
      }).catch(function() {})
      this.hideSessionContextMenu()
    },
    deleteSessionFromMenu() {
      var ctx = this.sessionContextMenu
      if (!ctx.session) return
      var self = this
      var sessionId = ctx.session.id
      var type = ctx.type
      this.$confirm(
        (this.$t('sessionDeleteConfirm') || '确定要删除会话 {0} 吗？').replace('{0}', sessionId),
        this.$t('sessionDelete') || '删除会话',
        { confirmButtonText: this.$t('confirm') || '确定', cancelButtonText: this.$t('cancel') || '取消', type: 'warning' }
      ).then(function() {
        self.removeSession(type, sessionId)
      }).catch(function() {})
      this.hideSessionContextMenu()
    },
    // ==================== Persistence ====================
    saveAllSessions: function() {
      var all = []
      function addSessions(arr, type) {
        arr.forEach(function(s) {
          var cfg = { id: s.id, type: type }
          if (type === 'tcpServer') { cfg.port = s.port; cfg.maxConn = s.maxConn }
          else if (type === 'tcpClient') { cfg.host = s.host; cfg.port = s.port }
          else if (type === 'udpServer') { cfg.port = s.port }
          else if (type === 'udpClient') { cfg.port = s.port; cfg.sendHost = s.sendHost; cfg.sendPort = s.sendPort }
          else if (type === 'sshClient') { cfg.host = s.host; cfg.port = s.port; cfg.username = s.username; cfg.password = s.password }
          all.push(cfg)
        })
      }
      addSessions(this.tcpSessions, 'tcpServer')
      addSessions(this.tcpCliSessions, 'tcpClient')
      addSessions(this.udpSessions, 'udpServer')
      addSessions(this.udpCliSessions, 'udpClient')
      addSessions(this.sshSessions, 'sshClient')
      callJava('persistSessions', JSON.stringify(all))
    },
    scheduleSaveAllSessions: function() {
      var self = this
      if (this._saveTimer) clearTimeout(this._saveTimer)
      this._saveTimer = setTimeout(function() { self.saveAllSessions() }, 500)
    },
    restoreFromPersisted: function(data) {
      try {
        var state = typeof data === 'string' ? JSON.parse(data) : data
        var self = this
        if (state.theme) {
          this.theme = state.theme
          localStorage.setItem('netdebugger-theme', state.theme)
        }
        if (state.activeTab) {
          this.activeTab = state.activeTab
        }
        if (state.language) {
          this.lang = state.language
          locale.value = state.language
        }
        var sessions = state.sessions || []
        sessions.forEach(function(s) {
          var logs = (s.logs || []).map(function(e) {
            return typeof e === 'string' ? JSON.parse(e) : e
          })
          if (s.type === 'tcpServer') {
            var sess = makeSession('srv-', {port: s.port || 8888, maxConn: s.maxConn || 10, running: false, clients: [], targetClient: '', logs: logs})
            sess.id = s.id
            self.tcpSessions.push(sess)
            if (!self.tcpActiveId) self.tcpActiveId = s.id
            callJava('createTcpServer', s.id)
          } else if (s.type === 'tcpClient') {
            var sess = makeSession('cli-', {host: s.host || '127.0.0.1', port: s.port || 8888, connected: false, logs: logs})
            sess.id = s.id
            self.tcpCliSessions.push(sess)
            if (!self.tcpCliActiveId) self.tcpCliActiveId = s.id
            callJava('createTcpClient', s.id)
          } else if (s.type === 'udpServer') {
            var sess = makeSession('udpSrv-', {port: s.port || 9999, running: false, sendHost: '127.0.0.1', sendPort: 9999, clients: [], targetClient: '', logs: logs})
            sess.id = s.id
            self.udpSessions.push(sess)
            if (!self.udpActiveId) self.udpActiveId = s.id
            callJava('createUdpServer', s.id)
          } else if (s.type === 'udpClient') {
            var sess = makeSession('udpCli-', {port: s.port || 10000, bound: false, sendHost: s.sendHost || '127.0.0.1', sendPort: s.sendPort || 9999, logs: logs})
            sess.id = s.id
            self.udpCliSessions.push(sess)
            if (!self.udpCliActiveId) self.udpCliActiveId = s.id
            callJava('createUdpClient', s.id)
          } else if (s.type === 'sshClient') {
            var sess = makeSession('ssh-', {host: s.host || '192.168.1.1', port: s.port || 22, username: s.username || 'root', password: s.password || '', connected: false, terminal: null, fitAddon: null, sftpPath:'/', sftpFiles:[], sftpLoading:false, _sftpPathEditing:false, _sftpPathInput:'', logs: logs})
            sess.id = s.id
            self.sshSessions.push(sess)
            if (!self.sshActiveId) self.sshActiveId = s.id
            callJava('createSshClient', s.id)
          }
        })
        var maxNum = 0
        sessions.forEach(function(s) {
          var m = s.id.match(/(\d+)$/)
          if (m) { var n = parseInt(m[1], 10); if (n > maxNum) maxNum = n }
        })
        resetCounter(maxNum)
        console.log('[NetDebugger] Restored ' + sessions.length + ' sessions')
      } catch(e) {
        console.error('[NetDebugger] Failed to restore state:', e)
      }
    }
  },
  mounted: function() {
    var self = this
    window.handleBridgeEvent = function(json) { self.handleEvent(json) }
    this.$nextTick(function() { self.resizeTextareas() })
    window.addEventListener('resize', function() { self.resizeTextareas() })
    this.$watch('sessionConfigSnapshot', function() { self.scheduleSaveAllSessions() })
    var saved = localStorage.getItem('netdebugger-theme')
    if (saved && (saved === 'light' || saved === 'dark' || saved === 'auto')) {
      this.theme = saved
    }
    document.documentElement.setAttribute('data-theme', this.theme)
    this.$watch('theme', function(val) {
      document.documentElement.setAttribute('data-theme', val)
    })
    if (window.matchMedia) {
      var mq = window.matchMedia('(prefers-color-scheme: dark)')
      var sysChange = function() { if (self.theme === 'auto') { self.$forceUpdate() } }
      if (mq.addEventListener) { mq.addEventListener('change', sysChange) }
      else if (mq.addListener) { mq.addListener(sysChange) }
    }
    var savedLang = localStorage.getItem('netdebugger-language')
    if (savedLang && i18nMessages[savedLang]) {
      this.lang = savedLang
      locale.value = savedLang
    }
    var BORDER = 6, resizing = false
    function getEdge(x, y) {
      var e = 0
      if (y <= BORDER) e |= 1
      if (y >= window.innerHeight - BORDER) e |= 2
      if (x <= BORDER) e |= 8
      if (x >= window.innerWidth - BORDER) e |= 4
      return e
    }
    function cursorFor(e) {
      switch(e) { case 1: case 2: return 'ns-resize'; case 4: case 8: return 'ew-resize'
        case 5: case 10: return 'nesw-resize'; case 6: case 9: return 'nwse-resize'
        default: return 'default' }
    }
    document.addEventListener('pointermove', function(e) {
      if (resizing || self.isMaximized) return
      document.body.style.cursor = cursorFor(getEdge(e.clientX, e.clientY))
    })
    document.addEventListener('pointerdown', function(e) {
      if (self.isMaximized) return
      var edge = getEdge(e.clientX, e.clientY)
      if (edge !== 0) {
        resizing = true
        document.body.style.cursor = cursorFor(edge)
        callJava('windowResizeStart', edge)
        document.body.setPointerCapture(e.pointerId)
        e.preventDefault()
        e.stopPropagation()
      }
    }, true)
    var endResize = function(e) {
      if (resizing) { resizing = false; callJava('windowResizeEnd'); document.body.style.cursor = 'default'; document.body.releasePointerCapture(e.pointerId) }
    }
    document.addEventListener('pointerup', endResize)
    document.addEventListener('click', function() { self.sessionContextMenu.visible = false })
  },
  updated: function() {
    this.$nextTick(function() { this.resizeTextareas() })
  }
}
</script>

<style>
:root {
  --bg-primary: #f0f4f8; --bg-secondary: #ffffff; --bg-card: #ffffff;
  --border-color: #e2e8f0; --text-primary: #1e293b; --text-secondary: #64748b;
  --accent-cyan: #0891b2; --accent-purple: #7c3aed; --accent-green: #10b981;
  --accent-red: #ef4444; --sent-color: #3b82f6; --received-color: #10b981;
  --system-color: #94a3b8; --shadow: 0 1px 3px rgba(0,0,0,0.06); --radius: 8px;
  --input-bg: #f8fafc; --input-placeholder: #94a3b8;
  --scrollbar-thumb: #cbd5e1; --header-hover: #e2e8f0;
  --log-hover: #f8fafc; --log-active: #eef2ff;
  --session-hover-bg: #f8fafc; --session-off-bg: #f1f5f9;
  --client-bg: #f8fafc; --empty-color: #cbd5e1;
  --dropdown-bg: #ffffff; --dropdown-hover: #f1f5f9;
  --stat-off: #cbd5e1; --btn-clear-bg: #f1f5f9; --btn-clear-hover: #e2e8f0;
}
[data-theme="dark"] {
  --bg-primary: #0f172a; --bg-secondary: #1e293b; --bg-card: #1e293b;
  --border-color: #334155; --text-primary: #e2e8f0; --text-secondary: #94a3b8;
  --accent-cyan: #22d3ee; --accent-purple: #a78bfa; --accent-green: #34d399;
  --accent-red: #f87171; --sent-color: #60a5fa; --received-color: #34d399;
  --system-color: #64748b; --shadow: 0 1px 3px rgba(0,0,0,0.3); --radius: 8px;
  --input-bg: #0f172a; --input-placeholder: #64748b;
  --scrollbar-thumb: #475569; --header-hover: #334155;
  --log-hover: #1e293b; --log-active: #1e3a5f;
  --session-hover-bg: #1e293b; --session-off-bg: #1e293b;
  --client-bg: #0f172a; --empty-color: #475569;
  --dropdown-bg: #1e293b; --dropdown-hover: #334155;
  --stat-off: #475569; --btn-clear-bg: #1e293b; --btn-clear-hover: #334155;
}
@media (prefers-color-scheme: dark) {
  [data-theme="auto"] {
    --bg-primary: #0f172a; --bg-secondary: #1e293b; --bg-card: #1e293b;
    --border-color: #334155; --text-primary: #e2e8f0; --text-secondary: #94a3b8;
    --accent-cyan: #22d3ee; --accent-purple: #a78bfa; --accent-green: #34d399;
    --accent-red: #f87171; --sent-color: #60a5fa; --received-color: #34d399;
    --system-color: #64748b; --shadow: 0 1px 3px rgba(0,0,0,0.3); --radius: 8px;
    --input-bg: #0f172a; --input-placeholder: #64748b;
    --scrollbar-thumb: #475569; --header-hover: #334155;
    --log-hover: #1e293b; --log-active: #1e3a5f;
    --session-hover-bg: #1e293b; --session-off-bg: #1e293b;
    --client-bg: #0f172a; --empty-color: #475569;
    --dropdown-bg: #1e293b; --dropdown-hover: #334155;
    --stat-off: #475569; --btn-clear-bg: #1e293b; --btn-clear-hover: #334155;
  }
}
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif; background: var(--bg-primary); color: var(--text-primary); overflow: hidden; height: 100vh; user-select: none; }
#app { display: flex; flex-direction: column; height: 100vh; background: linear-gradient(135deg, #f8fafc 0%, #eef2ff 50%, #f0fdf4 100%); }
[data-theme="dark"] #app { background: var(--bg-primary); }
@media (prefers-color-scheme:dark) { [data-theme="auto"] #app { background: var(--bg-primary); } }

.header { height: 48px; display: flex; align-items: center; padding: 0 8px; background: var(--bg-secondary); border-bottom: 1px solid var(--border-color); flex-shrink: 0; -webkit-user-select: none; user-select: none; }
.header-logo { display: flex; align-items: center; gap: 2px; font-size: 16px; font-weight: 700; letter-spacing: 1px; padding: 0 8px; }
.header-logo .cyan { color: var(--accent-cyan); } .header-logo .purple { color: var(--accent-purple); }
.header-drag { flex: 1; height: 100%; cursor: default; }
.header-status { display: flex; gap: 14px; align-items: center; font-size: 11px; color: var(--text-secondary); margin-right: 20px; }
.win-btn { width: 36px; height: 28px; display: flex; align-items: center; justify-content: center; cursor: pointer; border-radius: 4px; font-size: 14px; color: var(--text-secondary); transition: all 0.15s; margin-left: 2px; }
.win-btn:hover { background: var(--header-hover); }
.win-btn.close:hover { background: #ef4444; color: #fff; }
.status-dot { width: 6px; height: 6px; border-radius: 50%; display: inline-block; margin-right: 4px; }
.status-dot.green { background: var(--accent-green); box-shadow: 0 0 4px var(--accent-green); }
.status-dot.gray { background: var(--stat-off); }

.main-content { flex: 1; overflow: hidden; padding: 12px; display: flex; flex-direction: column; }
.main-content > .el-tabs { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.main-content .el-tabs__content { flex: 1; overflow: hidden; }
.main-content .el-tab-pane { height: 100%; display: flex; flex-direction: column; }
.el-tabs__nav { margin-left: 30px; }
.el-tabs__nav-wrap::after { background-color: var(--border-color) !important; }
.el-tabs__item { color: var(--text-secondary) !important; font-weight: 500; font-size: 13px; transition: color 0.25s; padding: 0; margin-right: 40px }
.el-tabs__item:hover { color: var(--text-primary) !important; }
.el-tabs__item.is-active { color: var(--accent-cyan) !important; font-weight: 600; }
.el-tabs__active-bar { background: linear-gradient(90deg, var(--accent-cyan), var(--accent-purple)) !important; height: 2px !important; }
.tab-icon { width: 24px; height: 24px; vertical-align: middle; margin-right: 6px; margin-top: -2px; }

.tool-panel { display: flex; gap: 12px; flex: 1; overflow: hidden; }

/* Session Sidebar */
.session-sidebar { width: 220px; flex-shrink: 0; display: flex; flex-direction: column; gap: 6px; overflow-y: auto; }
.session-item { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 6px; cursor: pointer; border: 1px solid transparent; transition: all 0.15s; background: var(--bg-card); font-size: 12px; }
.session-item:hover { border-color: var(--border-color); background: var(--session-hover-bg); }
.session-item.active { border-color: var(--accent-cyan); background: rgba(8,145,178,0.05); }
.session-item .s-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-family: monospace; color: var(--accent-cyan); }
.session-item .s-status { font-size: 10px; padding: 1px 6px; border-radius: 10px; }
.session-item .s-status.on { background: rgba(16,185,129,0.1); color: #059669; }
.session-item .s-status.off { background: var(--session-off-bg); color: var(--text-secondary); }
.session-item .s-del { color: var(--empty-color); cursor: pointer; font-size: 14px; line-height: 1; padding: 0 2px; }
.session-item .s-del:hover { color: var(--accent-red); }

/* Session Main */
.session-main { flex: 1; display: flex; gap: 12px; min-width: 0; min-height: 0; overflow: hidden; position: relative; }
.sm-left { width: 400px; flex-shrink: 0; display: flex; flex-direction: column; gap: 10px; overflow: hidden; min-height: 0; }
.sm-right { flex: 1; display: flex; flex-direction: column; min-width: 0; min-height: 0; overflow: hidden; }

/* Panel toggle button */
.panel-toggle {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 16px;
  z-index: 10;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.15;
  transition: opacity 0.2s, background 0.2s;
  border-radius: 0 4px 4px 0;
}
.panel-toggle:hover {
  opacity: 0.6;
  background: rgba(0,0,0,0.04);
}
[data-theme="dark"] .panel-toggle:hover,
@media (prefers-color-scheme: dark) { [data-theme="auto"] .panel-toggle:hover { background: rgba(255,255,255,0.06); } }
.toggle-arrow {
  display: inline-block;
  width: 0;
  height: 0;
  border-top: 5px solid transparent;
  border-bottom: 5px solid transparent;
  border-right: 6px solid var(--text-secondary);
  transition: transform 0.25s ease;
}
.toggle-arrow.collapsed {
  transform: rotate(180deg);
}

.card { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius); padding: 12px; box-shadow: var(--shadow); }
.card-title { font-size: 12px; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 1.5px; margin-bottom: 10px; display: flex; align-items: center; gap: 6px; }
.card-title::before { content: ''; width: 3px; height: 12px; border-radius: 2px; background: var(--accent-cyan); }
.log-card { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.send-card { flex: 1; display: flex; flex-direction: column; overflow: hidden; min-height: 0; }
.send-card .el-textarea { flex: 1 !important; display: flex !important; flex-direction: column !important; min-height: 0 !important; }
.send-card .el-textarea__inner { flex: 1 !important; min-height: 0 !important; resize: none !important; height: auto !important; }

.el-input__inner, .el-textarea__inner { background: var(--input-bg) !important; border-color: var(--border-color) !important; color: var(--text-primary) !important; border-radius: 6px !important; font-size: 13px !important; }
.el-input__inner:focus, .el-textarea__inner:focus { border-color: var(--accent-cyan) !important; box-shadow: 0 0 0 2px rgba(8,145,178,0.12) !important; }
.el-input__inner::placeholder, .el-textarea__inner::placeholder { color: var(--input-placeholder) !important; }
.el-input-number__decrease, .el-input-number__increase { background: var(--input-bg) !important; color: var(--text-secondary) !important; border-color: var(--border-color) !important; }
.el-input-number__decrease:hover, .el-input-number__increase:hover { color: var(--accent-cyan) !important; }
.el-radio-button__inner { background: var(--input-bg) !important; border-color: var(--border-color) !important; color: var(--text-secondary) !important; }
.el-radio-button.is-active .el-radio-button__inner { background: var(--accent-cyan) !important; color: #fff !important; border-color: var(--accent-cyan) !important; }
.el-tag--warning { background: rgba(251, 191, 36, 0.12) !important; color: #fbbf24 !important; border-color: rgba(251, 191, 36, 0.25) !important; }
.el-tag--info { background: var(--dropdown-hover) !important; color: var(--text-secondary) !important; border-color: var(--border-color) !important; }

/* MessageBox / $prompt / $confirm dark-mode support */
.el-message-box { background: var(--bg-card) !important; border: 1px solid var(--border-color) !important; border-radius: var(--radius) !important; box-shadow: 0 8px 32px rgba(0,0,0,0.18) !important; }
.el-message-box__title { color: var(--text-primary) !important; font-size: 15px !important; font-weight: 600 !important; }
.el-message-box__message { color: var(--text-secondary) !important; }
.el-message-box__headerbtn .el-message-box__close { color: var(--text-secondary) !important; }
.el-message-box__headerbtn:hover .el-message-box__close { color: var(--text-primary) !important; }
.el-message-box__btns .el-button--default { background: var(--btn-clear-bg) !important; border: 1px solid var(--border-color) !important; color: var(--text-secondary) !important; }
.el-message-box__btns .el-button--default:hover { background: var(--btn-clear-hover) !important; color: var(--text-primary) !important; }

.btn-start { background: linear-gradient(135deg, #059669, #10b981) !important; border: none !important; color: #fff !important; border-radius: 6px !important; font-weight: 500 !important; }
.btn-start:hover { box-shadow: 0 4px 12px rgba(16,185,129,0.3) !important; transform: translateY(-1px); }
.btn-start.is-disabled { background: var(--stat-off) !important; color: var(--text-secondary) !important; }
.btn-stop { background: linear-gradient(135deg, #dc2626, #ef4444) !important; border: none !important; color: #fff !important; border-radius: 6px !important; font-weight: 500 !important; }
.btn-stop:hover { box-shadow: 0 4px 12px rgba(239,68,68,0.3) !important; transform: translateY(-1px); }
.btn-send { background: linear-gradient(135deg, #2563eb, #3b82f6) !important; border: none !important; color: #fff !important; border-radius: 6px !important; font-weight: 500 !important; }
.btn-send:hover { box-shadow: 0 4px 12px rgba(59,130,246,0.3) !important; transform: translateY(-1px); }
.btn-clear { background: var(--btn-clear-bg) !important; border: 1px solid var(--border-color) !important; color: var(--text-secondary) !important; border-radius: 6px !important; }
.btn-clear:hover { background: var(--btn-clear-hover) !important; }
.btn-new { background: var(--bg-card) !important; border: 2px dashed var(--border-color) !important; color: var(--accent-cyan) !important; font-weight: 600 !important; border-radius: 6px !important; width: 100% !important; }

.log-container { flex: 1; overflow-y: auto; font-family: 'JetBrains Mono','Consolas',monospace; font-size: 12px; line-height: 1.7; padding: 4px 0; }
.log-container::-webkit-scrollbar,
.session-sidebar::-webkit-scrollbar,
.send-card .el-textarea__inner::-webkit-scrollbar { width: 4px; }
.log-container::-webkit-scrollbar-thumb,
.session-sidebar::-webkit-scrollbar-thumb,
.send-card .el-textarea__inner::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 2px; }
.log-container::-webkit-scrollbar-track,
.session-sidebar::-webkit-scrollbar-track,
.send-card .el-textarea__inner::-webkit-scrollbar-track { background: transparent; }
.log-entry { padding: 2px 10px; display: flex; gap: 6px; border-left: 2px solid transparent; font-size: 12px; cursor: pointer; transition: background 0.15s; }
.log-entry:hover { background: var(--log-hover); }
.log-entry:active { background: var(--log-active); }
.log-entry.sent { border-left-color: var(--sent-color); } .log-entry.received { border-left-color: var(--received-color); } .log-entry.system { border-left-color: var(--system-color); }
.log-time { color: var(--text-secondary); flex-shrink: 0; min-width: 75px; font-size: 11px; }
.log-dir { flex-shrink: 0; min-width: 20px; font-size: 11px; font-weight: 600; text-align: center; border-radius: 3px; }
.log-dir.sent { color: var(--sent-color); background: rgba(59,130,246,0.08); }
.log-dir.received { color: var(--received-color); background: rgba(16,185,129,0.08); }
.log-dir.system { color: var(--system-color); }
.log-peer { color: var(--text-secondary); flex-shrink: 0; max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 11px; }
.log-msg { word-break: break-all; white-space: pre-wrap; flex: 1; }
.log-empty { text-align: center; color: var(--empty-color); padding: 30px; font-size: 12px; }

.stats-bar { display: flex; gap: 12px; margin-top: 6px; }
.stat-item { font-size: 11px; color: var(--text-secondary); }
.stat-value { color: var(--accent-cyan); font-weight: 600; margin-left: 2px; font-family: monospace; }

.client-item { display: flex; align-items: center; justify-content: space-between; padding: 6px 8px; margin-bottom: 3px; border-radius: 4px; background: var(--client-bg); font-size: 12px; }
.client-addr { color: var(--accent-cyan); font-family: monospace; font-size: 11px; }

.empty-state { flex: 1; display: flex; align-items: center; justify-content: center; color: var(--empty-color); font-size: 14px; }

.el-select-dropdown { background: var(--dropdown-bg) !important; border: 1px solid var(--border-color) !important; box-shadow: 0 4px 16px rgba(0,0,0,0.08) !important; }
.el-select-dropdown__item { color: var(--text-primary) !important; }
.el-select-dropdown__item:hover { background: var(--dropdown-hover) !important; }
.el-select-dropdown__item.selected { color: var(--accent-cyan) !important; background-color: var(--dropdown-bg) !important; }
.theme-btn, .lang-btn { display: inline-flex; align-items: center; justify-content: center; width: 30px; height: 28px; cursor: pointer; border-radius: 4px; font-size: 14px; transition: background 0.15s; }
.theme-btn:hover, .lang-btn:hover { background: var(--header-hover); }
.lang-btn { font-size: 12px; font-weight: 600; width: auto; padding: 0 6px; }
.el-dropdown-menu { background: var(--dropdown-bg) !important; border: 1px solid var(--border-color) !important; }
.el-dropdown-menu__item { color: var(--text-primary) !important; }
.el-dropdown-menu__item:hover { background: var(--dropdown-hover) !important; }
.el-popper[x-placement^=bottom] .popper__arrow { border-bottom-color: var(--border-color) !important; }
.el-popper[x-placement^=bottom] .popper__arrow::after { border-bottom-color: var(--dropdown-bg) !important; }

.ssh-terminal-container { flex: 1; overflow: hidden; border-radius: 6px; border: 1px solid var(--border-color); }
.ssh-terminal-container .xterm { height: 100%; padding: 4px; }
.ssh-terminal-container .xterm-viewport::-webkit-scrollbar { width: 6px; }
.ssh-terminal-container .xterm-viewport::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 3px; }
.ssh-terminal-container .xterm-viewport::-webkit-scrollbar-track { background: transparent; }
.ssh-config-row { display: flex; gap: 8px; flex-wrap: wrap; }
.ssh-config-row > div { flex: 1; min-width: 120px; }
.ssh-config-row label { font-size: 11px; color: var(--text-secondary); display: block; margin-bottom: 2px; }

.sftp-card { flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
.sftp-toolbar { display: flex; align-items: center; gap: 4px; margin-bottom: 4px; }
.sftp-path { flex: 1; font-size: 11px; font-family: monospace; color: var(--accent-cyan); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sftp-actions { margin-left: auto; display: flex; align-items: center; gap: 2px; }
.sftp-actions .el-button { padding: 4px; border-radius: 4px; min-width: 22px; }
.sftp-actions .el-button img { display: block; }
.sftp-upload-btn { position: relative; overflow: hidden; }
.sftp-upload-btn input { position: absolute; left: 0; top: 0; width: 100%; height: 100%; opacity: 0; cursor: pointer; }
.sftp-upload-btn:hover { color: var(--accent-cyan); background: var(--dropdown-hover); }
.sftp-list { flex: 1; overflow-y: auto; font-size: 12px; }
.sftp-list::-webkit-scrollbar { width: 4px; }
.sftp-list::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 2px; }
.sftp-item { display: flex; align-items: center; gap: 6px; padding: 3px 6px; border-radius: 4px; cursor: default; }
.sftp-item:hover { background: var(--log-hover); }
.sftp-item.sftp-dir { cursor: pointer; }
.sftp-icon { font-size: 14px; flex-shrink: 0; width: 20px; text-align: center; }
.sftp-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sftp-size { font-size: 10px; color: var(--text-secondary); flex-shrink: 0; min-width: 50px; text-align: right; }
.sftp-dl-dir { font-size: 10px; color: var(--text-secondary); cursor: default; max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sftp-context-menu, .session-context-menu, .ssh-term-context-menu { position: fixed; z-index: 9999; background: var(--dropdown-bg, #fff); border: 1px solid var(--border-color); border-radius: 6px; box-shadow: 0 4px 12px rgba(0,0,0,0.15); padding: 4px 0; min-width: 130px; }
.sftp-context-menu .ctx-item, .session-context-menu .ctx-item, .ssh-term-context-menu .ctx-item { display: flex; align-items: center; gap: 8px; padding: 6px 12px; font-size: 12px; cursor: pointer; color: var(--text-primary); transition: background 0.15s; }
.sftp-context-menu .ctx-item:hover, .session-context-menu .ctx-item:hover, .ssh-term-context-menu .ctx-item:hover { background: var(--dropdown-hover, #f0f0f0); }
.sftp-context-menu .ctx-item.ctx-danger, .session-context-menu .ctx-item.ctx-danger, .ssh-term-context-menu .ctx-item.ctx-danger { color: var(--accent-red, #e74c3c); }
.sftp-context-menu .ctx-item .ctx-icon, .session-context-menu .ctx-item .ctx-icon, .ssh-term-context-menu .ctx-item .ctx-icon { font-size: 14px; width: 16px; text-align: center; flex-shrink: 0; }
.sftp-context-menu .ctx-separator, .session-context-menu .ctx-separator, .ssh-term-context-menu .ctx-separator { height: 1px; background: var(--border-color); margin: 2px 6px; }
/* Command history dialog */
.cmd-history-overlay { position: fixed; z-index: 10000; left: 0; top: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; }
.cmd-history-box { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius); box-shadow: 0 8px 32px rgba(0,0,0,0.18); min-width: 360px; max-width: 500px; max-height: 400px; display: flex; flex-direction: column; }
.cmd-history-title { padding: 12px 16px; font-size: 14px; font-weight: 600; border-bottom: 1px solid var(--border-color); color: var(--text-primary); }
.cmd-history-list { flex: 1; overflow-y: auto; padding: 6px 0; }
.cmd-history-list::-webkit-scrollbar { width: 4px; }
.cmd-history-list::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 2px; }
.cmd-history-item { padding: 8px 16px; font-size: 12px; font-family: monospace; cursor: pointer; color: var(--text-primary); transition: background 0.15s; }
.cmd-history-item:hover { background: var(--dropdown-hover); }
.cmd-history-empty { padding: 20px; text-align: center; color: var(--empty-color); font-size: 12px; }
.cmd-history-footer { padding: 8px 16px; border-top: 1px solid var(--border-color); text-align: right; }
.sftp-item.ctx-active { background: var(--dropdown-hover); }
.has-downloads { animation: pulse 1.5s infinite; color: var(--accent-cyan) !important; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.4} }
@keyframes spin { from {transform:rotate(0deg)} to {transform:rotate(360deg)} }
</style>
