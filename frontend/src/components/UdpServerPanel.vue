<template>
  <div class="tool-panel">
    <div class="session-sidebar">
      <el-button class="btn-new" size="small" @click="$emit('add-session')">{{ $t('newSession') }}</el-button>
      <div v-for="s in sessions" :key="s.id" :class="['session-item', {active: activeId===s.id}]" @click="$emit('update:activeId', s.id)" @contextmenu.prevent="$emit('show-context-menu', $event, 'udpServer', s)">
        <span class="s-name">{{ s.id }}</span>
        <span :class="['s-status', s.running?'on':'off']">{{ s.running ? $t('running') : $t('stopped') }}</span>
        <span class="s-del" @click.stop="$emit('remove-session', s.id)">×</span>
      </div>
    </div>
    <div class="session-main" v-if="activeSession">
      <div class="panel-toggle" @click="showLeftPanel = !showLeftPanel" :title="showLeftPanel ? $t('hideConfig') : $t('showConfig')">
        <span class="toggle-arrow" :class="{ collapsed: !showLeftPanel }"></span>
      </div>
      <div class="sm-left" v-show="showLeftPanel">
        <div class="card">
          <div class="card-title">{{ $t('udpServer') }} · {{ activeSession.id }}</div>
          <div style="margin-bottom:10px;"><label style="font-size:11px;color:var(--text-secondary);">{{ $t('listenPort') }}</label><el-input-number v-model="activeSession.port" :min="1" :max="65535" size="small" style="width:100%;margin-top:2px;"></el-input-number></div>
          <el-button v-if="!activeSession.running" class="btn-start" size="small" @click="startServer(activeSession)" :loading="activeSession.loading" style="width:100%;">{{ $t('start') }}</el-button>
          <el-button v-if="activeSession.running" class="btn-stop" size="small" @click="stopServer(activeSession)" style="width:100%;">{{ $t('stop') }}</el-button>
          <div class="stats-bar"><span class="stat-item">{{ $t('status') }}：<span class="stat-value" :style="{color:activeSession.running?'var(--accent-green)':'var(--text-secondary)'}">{{ activeSession.running ? $t('running') : $t('stopped') }}</span></span><span class="stat-item" v-if="activeSession.running">{{ $t('clients') }}：<span class="stat-value">{{ activeSession.clients.length }}</span></span></div>
        </div>
        <div class="card" v-if="activeSession.running && activeSession.clients.length>0">
          <div class="card-title">{{ $t('knownClients') }}</div>
          <div v-for="c in activeSession.clients" :key="c" class="client-item">
            <span class="client-addr">{{ c }}</span>
            <div style="display:flex;gap:4px;">
              <el-button size="mini" type="text" @click="activeSession.targetClient=c" style="color:var(--accent-cyan);padding:0 4px;">{{ $t('select') }}</el-button>
              <el-button size="mini" type="text" @click="forgetClient(activeSession,c)" style="color:var(--accent-red);padding:0 4px;">{{ $t('forget') }}</el-button>
            </div>
          </div>
        </div>
        <div class="card send-card" v-if="activeSession.running">
          <div class="card-title">{{ $t('sendDatagram') }}</div>
          <div v-if="activeSession.targetClient" style="margin-bottom:6px;font-size:11px;color:var(--text-secondary);">{{ $t('target') }}：<el-tag size="mini" closable @close="activeSession.targetClient=''" type="info">{{ activeSession.targetClient }}</el-tag></div>
          <div v-else style="margin-bottom:6px;"><el-tag size="mini" type="warning">{{ $t('broadcastAllKnown') }}</el-tag></div>
          <el-input type="textarea" v-model="activeSession.message" :placeholder="$t('inputPlaceholder')" @keydown.ctrl.enter.native="sendMessage(activeSession)"></el-input>
          <div style="margin-top:6px;display:flex;gap:6px;">
            <el-select v-model="activeSession.encoding" size="small" style="width:95px;flex-shrink:0;" :disabled="activeSession.format === 'hex'"><el-option label="UTF-8" value="UTF-8"></el-option><el-option label="GBK" value="GBK"></el-option><el-option label="ASCII" value="ASCII"></el-option></el-select>
            <el-select v-model="activeSession.format" size="small" style="width:100px;flex-shrink:0;"><el-option :label="$t('text')" value="text"></el-option><el-option :label="$t('hex')" value="hex"></el-option></el-select>
            <el-button class="btn-send" size="small" @click="sendMessage(activeSession)" style="flex:1;">{{ $t('sendCtrlEnter') }}</el-button>
          </div>
        </div>
      </div>
      <div class="sm-right">
        <div class="card log-card">
          <div class="card-title" style="display:flex;align-items:center;position:relative;">
            <span style="position:absolute;left:2%;">{{ $t('messageLog') }}</span>
            <div style="display:flex;gap:8px;align-items:center;margin-left:auto;">
              <el-radio-group v-model="activeSession.receiveMode" size="mini"><el-radio-button label="text">{{ $t('text') }}</el-radio-button><el-radio-button label="hex">HEX</el-radio-button></el-radio-group>
              <el-select v-model="activeSession.receiveEncoding" size="mini" style="width:90px;" :popper-append-to-body="false" :disabled="activeSession.receiveMode === 'hex'"><el-option label="UTF-8" value="UTF-8"></el-option><el-option label="GBK" value="GBK"></el-option><el-option label="ASCII" value="ASCII"></el-option></el-select>
              <el-button class="btn-clear" size="mini" @click="clearLogs(activeSession)">{{ $t('clear') }}</el-button>
            </div>
          </div>
          <div class="log-container" ref="logContainer"><div v-if="activeSession.logs.length===0" class="log-empty">{{ $t('noMessages') }}</div><div v-for="(e,i) in activeSession.logs" :key="i" :class="'log-entry '+e.direction.toLowerCase()" @click="copyLogEntry(e)"><span class="log-time">{{e.timestamp}}</span><span :class="'log-dir '+e.direction.toLowerCase()">{{e.direction==='SENT'?'→':e.direction==='RECEIVED'?'←':'●'}}</span><span class="log-peer">{{e.peer}}</span><span class="log-msg">{{displayContent(activeSession, e)}}</span></div></div>
        </div>
      </div>
    </div>
    <div class="empty-state" v-else>{{ $t('emptyUdpServer') }}</div>
  </div>
</template>

<script>
import { callJava, formatSize, hexDecode } from '../utils.js'
import { i18nMessages, locale } from '../i18n.js'

export default {
  name: 'UdpServerPanel',
  props: {
    sessions: { type: Array, required: true },
    activeId: { type: String, default: null }
  },
  data() {
    return { showLeftPanel: true }
  },
  computed: {
    activeSession() { return this.sessions.find(s => s.id === this.activeId) }
  },
  watch: {
    'activeSession.logs.length': function() {
      var self = this
      this.$nextTick(function() {
        var el = self.$refs.logContainer
        if (el) el.scrollTop = el.scrollHeight
      })
    }
  },
  methods: {
    $t(key, args) {
      var msgs = i18nMessages[locale.value] || i18nMessages['zh-CN']
      var text = msgs[key] !== undefined ? msgs[key] : key
      if (args && args.length) {
        for (var i = 0; i < args.length; i++) {
          text = text.replace('{' + i + '}', args[i])
        }
      }
      return text
    },
    imgUrl(name) { return 'img/' + name },
    displayContent(session, entry) {
      if (!entry) return ''
      if (entry.direction === 'SYSTEM' && entry.i18nKey) {
        return this.$t(entry.i18nKey, entry.i18nArgs || [])
      }
      if (entry.direction !== 'RECEIVED') return entry.content
      if (!session) return entry.content
      var str = entry.content
      if (!str) return str
      var isHexStr = /^([0-9A-Fa-f]{2}(\s|$))+$/.test(str)
      if (session.receiveMode === 'hex') {
        return isHexStr ? str.toUpperCase() : str
      }
      if (isHexStr) {
        try { return this.hexDecode(str, session.receiveEncoding || 'UTF-8') } catch(e) { return str }
      }
      return str
    },
    hexDecode: hexDecode,
    copyLogEntry(entry) {
      if (!entry || !entry.content) return
      var text = entry.content
      var self = this
      var okMsg = this.$t('copied')
      var failMsg = this.$t('copyFailed')
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function() {
          self.$message({ message: okMsg, type: 'success', duration: 1200, showClose: false, center: true })
        }).catch(function() {
          self.$message({ message: failMsg, type: 'warning', duration: 1500, showClose: false, center: true })
        })
      } else {
        var ta = document.createElement('textarea')
        ta.value = text
        ta.style.position = 'fixed'; ta.style.left = '-9999px'; ta.style.top = '-9999px'
        document.body.appendChild(ta)
        ta.select()
        try { document.execCommand('copy'); self.$message({ message: okMsg, type: 'success', duration: 1200, showClose: false, center: true }) } catch(e) { self.$message({ message: failMsg, type: 'warning', duration: 1500, showClose: false, center: true }) }
        document.body.removeChild(ta)
      }
    },
    startServer(s) { s.loading = true; callJava('startUdpServer', s.id, s.port) },
    stopServer(s) { callJava('stopUdpServer', s.id); s.running = false; s.clients = [] },
    sendMessage(s) {
      if (!s.message.trim()) return
      if (s.targetClient) callJava('udpServerSendToClient', s.id, s.targetClient, s.message, s.encoding, s.format)
      else callJava('udpServerSendAll', s.id, s.message, s.encoding, s.format)
      s.message = ''
    },
    forgetClient(s, cid) {
      var idx = s.clients.indexOf(cid)
      if (idx >= 0) s.clients.splice(idx, 1)
      callJava('udpServerForgetClient', s.id, cid)
    },
    clearLogs(session) {
      if (!session) return
      session.logs = []
      callJava('clearLogs', session.id)
    }
  }
}
</script>
