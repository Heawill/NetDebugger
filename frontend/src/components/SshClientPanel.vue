<template>
  <div class="tool-panel">
    <div class="session-sidebar">
      <el-button class="btn-new" size="small" @click="$emit('add-session')">{{ $t('newSession') }}</el-button>
      <div v-for="s in sessions" :key="s.id" :class="['session-item', {active: activeId===s.id}]" @click="switchSession(s.id)" @contextmenu.prevent="$emit('show-context-menu', $event, 'sshClient', s)">
        <span class="s-name">{{ s.id }}</span>
        <span :class="['s-status', s.connected?'on':'off']">{{ s.connected ? $t('connected') : $t('disconnected') }}</span>
        <span class="s-del" @click.stop="$emit('remove-session', s.id)">×</span>
      </div>
    </div>
    <div class="session-main" v-if="activeSession">
      <div class="panel-toggle" @click="showLeftPanel = !showLeftPanel" :title="showLeftPanel ? $t('hideConfig') : $t('showConfig')">
        <span class="toggle-arrow" :class="{ collapsed: !showLeftPanel }"></span>
      </div>
      <div class="sm-left" v-show="showLeftPanel">
        <div class="card">
          <div class="card-title">{{ $t('sshConfig') }} · {{ activeSession.id }}</div>
          <div class="ssh-config-row">
            <div><label>{{ $t('remoteHost') }}</label><el-input v-model="activeSession.host" size="small" placeholder="192.168.1.1"></el-input></div>
            <div style="flex:0 0 100px;"><label>{{ $t('port') }}</label><el-input-number v-model="activeSession.port" :min="1" :max="65535" size="small" style="width:100%;"></el-input-number></div>
          </div>
          <div class="ssh-config-row" style="margin-top:8px;">
            <div><label>{{ $t('username') }}</label><el-input v-model="activeSession.username" size="small" placeholder="root"></el-input></div>
            <div><label>{{ $t('password') }}</label><el-input v-model="activeSession.password" size="small" type="password" show-password placeholder=""></el-input></div>
          </div>
          <div style="margin-top:10px;">
            <el-button v-if="!activeSession.connected" class="btn-start" size="small" @click="connect(activeSession)" :loading="activeSession.loading" style="width:100%;">{{ $t('connect') }}</el-button>
            <el-button v-if="activeSession.connected" class="btn-stop" size="small" @click="disconnect(activeSession)" style="width:100%;">{{ $t('disconnect') }}</el-button>
          </div>
          <div class="stats-bar">
            <span class="stat-item">{{ $t('status') }}：<span class="stat-value" :style="{color:activeSession.connected?'var(--accent-green)':'var(--text-secondary)'}">{{ activeSession.connected ? $t('connected') : $t('disconnected') }}</span></span>
            <span class="stat-item" v-if="activeSession.connected">{{ $t('peer') }}：<span class="stat-value">{{ activeSession.host }}:{{ activeSession.port }}</span></span>
          </div>
        </div>
        <!-- SFTP Panel -->
        <div class="card sftp-card" v-if="activeSession.connected"
             :class="{ 'drag-over': dragOverSftp === activeSession.id }"
             @dragover.prevent="onSftpDragOver(activeSession)"
             @dragleave="onSftpDragLeave(activeSession)"
             @drop.prevent="onSftpDrop(activeSession, $event)">
          <div class="card-title" style="display:flex;align-items:center;">
            <span>{{ $t('sftpTitle') }}</span>
            <div class="sftp-actions">
              <el-button size="mini" type="text" class="sftp-upload-btn" @click="navigateSftp(activeSession, '..')" :title="$t('sftpUp')"><img :src="imgUrl('back.svg')" width="14" height="14"/></el-button>
              <el-button size="mini" type="text" class="sftp-upload-btn" @click="refreshSftp(activeSession)" :title="$t('refresh')">
                <img :src="imgUrl('refresh.svg')" width="14" height="14" :style="{animation:activeSession.sftpLoading?'spin 1s linear infinite':'none'}"/>
              </el-button>
              <el-button size="mini" type="text" :title="$t('sftpUpload')" @click="startUpload(activeSession)"><img :src="imgUrl('upload.svg')" width="14" height="14"/></el-button>
              <el-button size="mini" type="text" :title="$t('sftpDlDirSet')" @click="toggleDlSettings(activeSession)"><img :src="imgUrl('settings.svg')" width="14" height="14"/></el-button>
            </div>
          </div>
          <div v-if="activeSession._showDlSettings" style="padding:6px 10px;border-bottom:1px solid var(--border-color);display:flex;align-items:center;gap:8px;font-size:12px;">
            <span style="color:var(--text-secondary);flex-shrink:0;">{{ $t('sftpDlDir') }}</span>
            <el-input size="mini" v-model="activeSession.dlDir" placeholder="~/Downloads" style="flex:1;"></el-input>
          </div>
          <div class="sftp-toolbar">
            <el-input v-if="activeSession._sftpPathEditing" v-model="activeSession._sftpPathInput"
              size="mini" @keyup.enter.native="navigateToSftpPath(activeSession)"
              @blur="cancelEditSftpPath(activeSession)"
              style="flex:1;" placeholder="/"></el-input>
            <span v-else class="sftp-path" @click="editSftpPath(activeSession)"
              :title="$t('sftpEditPath')">{{ activeSession.sftpPath || '/' }}</span>
          </div>
          <div class="sftp-list">
            <div v-if="!activeSession.sftpFiles || activeSession.sftpFiles.length===0" class="log-empty" style="padding:10px;">{{ $t('noMessages') }}</div>
            <div v-for="f in activeSession.sftpFiles" :key="f.name"
                 :class="['sftp-item', { 'sftp-dir': f.isDir }]"
                 :title="f.isDir ? $t('sftpOpenDir') : $t('sftpDownload')"
                 @contextmenu.prevent="showSftpContextMenu($event, activeSession, f)">
              <span class="sftp-icon">{{ f.isDir ? '📁' : '📄' }}</span>
              <span class="sftp-name" @dblclick="f.isDir && navigateSftp(activeSession, f.name)">{{ f.name }}</span>
              <span class="sftp-size">{{ f.isDir ? '' : formatSize(f.size) }}</span>
            </div>
          </div>
          <!-- Inline download progress -->
          <div v-if="downloads.length>0" style="border-top:1px solid var(--border-color);padding:6px 0 0 0;max-height:120px;overflow-y:auto;">
            <div v-for="dl in downloads" :key="dl.id" style="font-size:12px;padding:3px 0;">
              <div style="display:flex;justify-content:space-between;"><span style="color:var(--text-primary);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">{{ dl.name }}</span><span style="color:var(--accent-cyan);flex-shrink:0;margin-left:4px;">{{ formatSize(dl.size) }}</span></div>
              <div v-if="dl.status==='active'" style="margin-top:2px;">
                <div style="height:4px;background:var(--border-color);border-radius:2px;overflow:hidden;">
                  <div :style="{width:(dl.progress||0)+'%',height:'100%',background:'linear-gradient(90deg,var(--accent-cyan),var(--accent-green))',transition:'width 0.3s',borderRadius:'2px'}"></div>
                </div>
                <div style="display:flex;justify-content:space-between;margin-top:1px;font-size:10px;color:var(--text-secondary);">
                  <span>{{ dl.progress || 0 }}%</span>
                  <span>{{ formatSize(dl.transferred||0) }} / {{ formatSize(dl.total) }}</span>
                </div>
              </div>
              <div v-else><span :style="{color:dl.status==='done'?'var(--accent-green)':'var(--accent-red)',fontSize:'10px'}">{{ dl.status === 'done' ? $t('sftpDlDone') : $t('sftpDlFail') }}</span></div>
            </div>
          </div>
          <!-- SFTP Context Menu -->
          <div class="sftp-context-menu" v-show="sftpContextMenu.visible" :style="{ left: sftpContextMenu.x + 'px', top: sftpContextMenu.y + 'px' }" @click.stop>
            <div v-if="sftpContextMenu.file && !sftpContextMenu.file.isDir" class="ctx-item" @click="downloadFromContextMenu">
              <span class="ctx-icon">⏬</span><span>{{ $t('sftpDownload') }}</span>
            </div>
            <div class="ctx-item" @click="renameFromContextMenu">
              <span class="ctx-icon">✏️</span><span>{{ $t('sftpRename') }}</span>
            </div>
            <div class="ctx-separator"></div>
            <div class="ctx-item ctx-danger" @click="deleteFromContextMenu">
              <span class="ctx-icon">❌</span><span>{{ $t('sftpDelete') }}</span>
            </div>
          </div>
        </div>
      </div>
      <!-- Persistent terminal area: each session owns its own container -->
      <div class="sm-right" ref="sshTerminalArea">
        <div v-for="s in sessions" :key="'term-'+s.id"
             :ref="'sshTerm_' + s.id"
             class="ssh-terminal-container"
             v-show="s.id === activeId && s.connected"
             @contextmenu.prevent="showSshTermContextMenu($event, s)"></div>
        <div class="empty-state" v-if="!activeSession.connected">{{ $t('sshNotConnected') }}</div>
      </div>
    </div>
    <div class="empty-state" v-else>{{ $t('emptySshClient') }}</div>

    <!-- SSH Term Context Menu -->
    <div class="ssh-term-context-menu" v-show="sshTermContextMenu.visible" :style="{ left: sshTermContextMenu.x + 'px', top: sshTermContextMenu.y + 'px' }" @click.stop>
      <div class="ctx-item" @click="copyFromTermContextMenu">
        <span class="ctx-icon">📋</span><span>{{ $t('sshTermCopy') }}</span>
      </div>
      <div class="ctx-item" @click="pasteToTermContextMenu">
        <span class="ctx-icon">📝</span><span>{{ $t('sshTermPaste') }}</span>
      </div>
      <div class="ctx-separator"></div>
      <div class="ctx-item" @click="showCmdHistory">
        <span class="ctx-icon">📜</span><span>{{ $t('sshTermCmdHistory') }}</span>
      </div>
    </div>

    <!-- Command History Dialog -->
    <div class="cmd-history-overlay" v-if="cmdHistoryDialog.visible" @click.self="hideCmdHistory">
      <div class="cmd-history-box">
        <div class="cmd-history-title">{{ $t('sshTermCmdHistory') }}</div>
        <div class="cmd-history-list">
          <div v-if="!cmdHistoryDialog.session || !cmdHistoryDialog.session._cmdHistory || cmdHistoryDialog.session._cmdHistory.length===0" class="cmd-history-empty">{{ $t('sshTermCmdHistoryEmpty') }}</div>
          <div v-for="(cmd, idx) in cmdHistoryDialog.session._cmdHistory" :key="idx" class="cmd-history-item" @click="selectCmdHistory(cmd)">
            <span style="color:var(--text-secondary);margin-right:8px;">{{ idx + 1 }}.</span>{{ cmd }}
          </div>
        </div>
        <div class="cmd-history-footer">
          <el-button size="mini" @click="hideCmdHistory">{{ $t('cancel') }}</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { Terminal } from 'xterm'
import { FitAddon } from 'xterm-addon-fit'
import { callJava, makeSession, resetCounter, indexOfBytes, hexDecode, formatSize } from '../utils.js'
import { i18nMessages, locale } from '../i18n.js'

export default {
  name: 'SshClientPanel',
  props: {
    sessions: { type: Array, required: true },
    activeId: { type: String, default: null },
    downloads: { type: Array, default: function() { return [] } }
  },
  data() {
    return {
      sftpContextMenu: { visible: false, x: 0, y: 0, session: null, file: null },
      sshTermContextMenu: { visible: false, x: 0, y: 0, session: null },
      cmdHistoryDialog: { visible: false, session: null },
      showLeftPanel: true,
      dragOverSftp: null
    }
  },
  computed: {
    activeSession() { return this.sessions.find(s => s.id === this.activeId) }
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
    formatSize: formatSize,
    switchSession(id) {
      this.$emit('update:activeId', id)
    },
    // ---- Connection ----
    connect(s) {
      s.loading = true
      callJava('connectSsh', s.id, s.host, s.port, s.username, s.password, 80, 24)
    },
    disconnect(s) {
      this.destroySshTerminal(s)
      callJava('disconnectSsh', s.id)
      s.connected = false
    },
    // ---- Terminal ----
    handleTerminalData(s, base64Data) {
      if (!s || !s.terminal) return
      try {
        var binaryStr = atob(base64Data)
        var bytes = new Uint8Array(binaryStr.length)
        for (var i = 0; i < binaryStr.length; i++) { bytes[i] = binaryStr.charCodeAt(i) }
        var marker = [0x1B, 0x5D, 0x37, 0x37, 0x37, 0x3B, 0x70, 0x77, 0x64, 0x3B]
        var idx = indexOfBytes(bytes, marker)
        if (idx >= 0) {
          var start = idx + marker.length
          var end = start
          while (end < bytes.length && bytes[end] !== 0x07) end++
          if (end > start) {
            var pathBytes = bytes.slice(start, end)
            var path = new TextDecoder().decode(pathBytes)
            if (path.startsWith('/')) {
              this.$set(s, 'sftpPath', path)
              var self = this
              this.$nextTick(function() { self.refreshSftp(s) })
            }
          }
          var before = bytes.slice(0, idx)
          var after = bytes.slice(end + 1)
          var filtered = new Uint8Array(before.length + after.length)
          filtered.set(before)
          filtered.set(after, before.length)
          bytes = filtered
        }
        if (bytes.length > 0) s.terminal.write(bytes)
      } catch(e) { console.error('[NetDebugger] terminalData error:', e) }
    },
    initSshTerminal(s) {
      var self = this
      this.$nextTick(function() {
        if (s.terminal) return
        var refArr = self.$refs['sshTerm_' + s.id]
        var container = refArr && refArr.length ? refArr[0] : null
        if (!container) return
        var term = new Terminal({
          cursorBlink: true,
          cursorStyle: 'bar',
          fontSize: 14,
          fontFamily: "'JetBrains Mono','Consolas','Courier New',monospace",
          theme: {
            background: '#0f172a',
            foreground: '#e2e8f0',
            cursor: '#22d3ee',
            selectionBackground: '#334155'
          },
          cols: 80,
          rows: 24
        })
        var fitAddon = new FitAddon()
        term.loadAddon(fitAddon)
        term.open(container)
        try { fitAddon.fit() } catch(e) {}
        setTimeout(function() {
          try { fitAddon.fit() } catch(e) {}
        }, 100)
        if (!s._cmdBuffer) s._cmdBuffer = ''
        if (!s._cmdHistory) s._cmdHistory = []
        term.onData(function(data) {
          if (data === '\r' || data === '\n') {
            var cmd = s._cmdBuffer.trim()
            if (cmd && cmd.indexOf('\t') >= 0) {
              var prefix = cmd.replace(/\t.*$/, '').trim()
              try {
                var lineObj = term.buffer.active.getLine(term.buffer.active.cursorY)
                if (lineObj && prefix) {
                  var lineText = lineObj.translateToString().trim()
                  var ci = lineText.lastIndexOf(prefix)
                  if (ci >= 0) {
                    cmd = lineText.substring(ci)
                  }
                }
              } catch(e) { /* buffer API unavailable */ }
              cmd = cmd.replace(/\t/g, '').trim()
            }
            if (cmd) {
              s._cmdHistory.unshift(cmd)
              if (s._cmdHistory.length > 50) s._cmdHistory.pop()
            }
            s._cmdBuffer = ''
          } else if (data === '\x7f' || data === '\b') {
            if (s._cmdBuffer.length > 0) {
              s._cmdBuffer = s._cmdBuffer.slice(0, -1)
            }
          } else if (data === '\x03') {
            s._cmdBuffer = ''
          } else if (data === '\t') {
            s._cmdBuffer += '\t'
          } else if (data.length === 1 && data.charCodeAt(0) >= 0x20) {
            s._cmdBuffer += data
          }
          var encoder = new TextEncoder()
          var bytes = encoder.encode(data)
          var binaryStr = ''
          for (var i = 0; i < bytes.length; i++) { binaryStr += String.fromCharCode(bytes[i]) }
          var base64 = btoa(binaryStr)
          callJava('sshInput', s.id, base64)
        })
        term.onResize(function(size) {
          callJava('resizeSshPty', s.id, size.cols, size.rows)
        })
        var resizeObserver = new ResizeObserver(function() {
          try { fitAddon.fit() } catch(e) {}
        })
        resizeObserver.observe(container)
        s.terminal = term
        s.fitAddon = fitAddon
        s._resizeObserver = resizeObserver
        term.focus()
      })
    },
    destroySshTerminal(s) {
      if (s._resizeObserver) { s._resizeObserver.disconnect(); s._resizeObserver = null }
      if (s.terminal) { s.terminal.dispose(); s.terminal = null; s.fitAddon = null }
    },
    // ---- SFTP ----
    refreshSftp(s) {
      s.sftpLoading = true
      callJava('sftpList', s.id, s.sftpPath || '/')
    },
    navigateSftp(s, dir) {
      if (!s.sftpPath) s.sftpPath = '/'
      if (dir === '..') {
        if (s.sftpPath === '/') return
        s.sftpPath = s.sftpPath.substring(0, s.sftpPath.lastIndexOf('/')) || '/'
      } else {
        s.sftpPath = (s.sftpPath === '/' ? '/' + dir : s.sftpPath + '/' + dir)
      }
      this.refreshSftp(s)
    },
    editSftpPath(s) {
      this.$set(s, '_sftpPathInput', s.sftpPath || '/')
      this.$set(s, '_sftpPathEditing', true)
      var self = this
      this.$nextTick(function() {
        var input = document.querySelector('.sftp-toolbar .el-input__inner')
        if (input) { input.focus(); input.select() }
      })
    },
    navigateToSftpPath(s) {
      var newPath = (s._sftpPathInput || '').trim()
      if (!newPath) {
        this.$set(s, '_sftpPathEditing', false)
        return
      }
      if (newPath.charAt(0) !== '/') newPath = '/' + newPath
      if (newPath.length > 1 && newPath.charAt(newPath.length - 1) === '/') {
        newPath = newPath.substring(0, newPath.length - 1)
      }
      this.$set(s, '_sftpPathEditing', false)
      if (newPath === s.sftpPath) return
      s.sftpLoading = true
      callJava('sftpList', s.id, newPath)
    },
    cancelEditSftpPath(s) {
      this.$set(s, '_sftpPathEditing', false)
    },
    downloadSftpFile(s, file) {
      var existing = this.downloads.find(function(d){return d.name===file.name && d.status==='active'})
      if (existing) {
        this.$message.warning(file.name + ' ' + (this.$t('sftpAlreadyDling') || 'is already downloading'))
        return
      }
      var dlDir = s.dlDir || ''
      if (dlDir && !/^(\/|[A-Za-z]:\\|~)/.test(dlDir)) {
        this.$message.warning((this.$t('sftpDlDirInvalid') || 'Invalid download directory') + ': ' + dlDir)
        return
      }
      var dlId = file.name + '_' + Date.now()
      this.downloads.unshift({ id: dlId, name: file.name, size: file.size, status: 'active', progress: 0, total: 0, transferred: 0 })
      callJava('sftpDownload', s.id, (s.sftpPath || '/') + (s.sftpPath === '/' ? '' : '/') + file.name, s.dlDir || '')
      s._pendingDlId = dlId
    },
    startUpload(s) {
      var dlId = 'up_' + Date.now()
      this.downloads.unshift({ id: dlId, name: 'Upload...', size: 0, status: 'active', progress: 0, total: 0, transferred: 0 })
      callJava('pickAndUpload', s.id)
    },
    toggleDlSettings(s) {
      this.$set(s, '_showDlSettings', !s._showDlSettings)
    },
    handleSftpList(s, data) {
      s.sftpLoading = false
      try {
        var result = typeof data === 'string' ? JSON.parse(data) : data
        var files = result.files || []
        files.sort(function(a, b) {
          if (a.isDir !== b.isDir) return a.isDir ? -1 : 1
          return a.name.localeCompare(b.name)
        })
        this.$set(s, 'sftpFiles', files)
        this.$set(s, 'sftpPath', result.path)
      } catch(e) { console.error('[NetDebugger] sftpList parse:', e) }
    },
    handleSftpPwd(s, data) {
      var path = typeof data === 'string' ? data : (data.path || data)
      if (path && path.startsWith('/')) {
        this.$set(s, 'sftpPath', path)
        this.refreshSftp(s)
      }
    },
    handleSftpFileData(s, data) {
      try {
        var result = typeof data === 'string' ? JSON.parse(data) : data
        var dl = this.downloads.find(function(d){return d.status==='active' && d.name===result.name})
        if (dl) {
          this.$set(dl, 'status', 'done')
          this.$set(dl, 'size', result.size)
          var self = this
          setTimeout(function() {
            var idx = -1
            self.downloads.forEach(function(d,i){ if(d.id===dl.id) idx=i })
            if (idx>=0) self.downloads.splice(idx,1)
          }, 5000)
        } else {
          var dlId = result.name + '_' + Date.now()
          this.downloads.unshift({ id: dlId, name: result.name, size: result.size, status: 'done' })
          var self = this
          setTimeout(function() {
            var idx = -1
            self.downloads.forEach(function(d,i){ if(d.id===dlId) idx=i })
            if (idx>=0) self.downloads.splice(idx,1)
          }, 5000)
        }
      } catch(e) {
        console.error('[NetDebugger] sftpFileData:', e)
      }
    },
    handleSftpProgress(data) {
      try {
        var p = typeof data === 'string' ? JSON.parse(data) : data
        var dl = this.downloads.find(function(d){return d.name===p.name && d.status==='active'})
        if (!dl) dl = this.downloads.find(function(d){return d.name==='Upload...' && d.status==='active'})
        if (dl) {
          this.$set(dl, 'name', p.name)
          this.$set(dl, 'total', p.total)
          this.$set(dl, 'transferred', p.transferred)
          this.$set(dl, 'size', p.total > 0 ? p.total : dl.size)
          this.$set(dl, 'progress', p.total > 0 ? Math.round(p.transferred * 100 / p.total) : 0)
        }
      } catch(e) {}
    },
    cancelUpload() {
      var idx = -1
      this.downloads.forEach(function(d, i) {
        if (d.id.indexOf('up_') === 0 && d.status === 'active') idx = i
      })
      if (idx >= 0) this.downloads.splice(idx, 1)
    },
    finishUpload() {
      var dl = this.downloads.find(function(d) { return d.id.indexOf('up_') === 0 && d.status === 'active' })
      if (dl) {
        this.$set(dl, 'status', 'done')
        var self = this
        setTimeout(function() {
          var idx = -1
          self.downloads.forEach(function(d, i) { if (d.id === dl.id) idx = i })
          if (idx >= 0) self.downloads.splice(idx, 1)
        }, 3000)
      }
    },
    // ---- SFTP Drag & Drop Upload ----
    onSftpDragOver(s) {
      this.dragOverSftp = s.id
    },
    onSftpDragLeave(s) {
      if (this.dragOverSftp === s.id) this.dragOverSftp = null
    },
    onSftpDrop(s, e) {
      this.dragOverSftp = null
      var files = e.dataTransfer.files
      if (!files || files.length === 0) return
      this.uploadDroppedFiles(s, files)
    },
    uploadDroppedFiles(s, files) {
      var self = this
      var port = window.location.port || '80'
      for (var i = 0; i < files.length; i++) {
        ;(function(file) {
          var dlId = 'up_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6)
          self.downloads.unshift({ id: dlId, name: file.name, size: file.size, status: 'active', progress: 0, total: file.size, transferred: 0 })
          var reader = new FileReader()
          reader.onload = function() {
            var arrayBuffer = reader.result
            var xhr = new XMLHttpRequest()
            xhr.open('POST', 'http://localhost:' + port + '/upload/' + encodeURIComponent(s.id) + '/' + encodeURIComponent(file.name))
            xhr.upload.onprogress = function(evt) {
              if (evt.lengthComputable) {
                var pct = Math.round(evt.loaded * 100 / evt.total)
                var dl = self.downloads.find(function(d){return d.id===dlId})
                if (dl) {
                  self.$set(dl, 'progress', pct)
                  self.$set(dl, 'transferred', evt.loaded)
                }
              }
            }
            xhr.onload = function() {
              var dl = self.downloads.find(function(d){return d.id===dlId})
              if (dl) {
                if (xhr.status === 200) {
                  self.$set(dl, 'status', 'done')
                  self.$set(dl, 'progress', 100)
                } else {
                  self.$set(dl, 'status', 'fail')
                }
                setTimeout(function() {
                  var idx = -1
                  self.downloads.forEach(function(d, j){ if(d.id===dlId) idx=j })
                  if (idx>=0) self.downloads.splice(idx,1)
                }, 5000)
              }
              self.refreshSftp(s)
            }
            xhr.onerror = function() {
              var dl = self.downloads.find(function(d){return d.id===dlId})
              if (dl) {
                self.$set(dl, 'status', 'fail')
                setTimeout(function() {
                  var idx = -1
                  self.downloads.forEach(function(d, j){ if(d.id===dlId) idx=j })
                  if (idx>=0) self.downloads.splice(idx,1)
                }, 5000)
              }
            }
            xhr.send(arrayBuffer)
          }
          reader.readAsArrayBuffer(file)
        })(files[i])
      }
    },
    // ---- SFTP Context Menu ----
    showSftpContextMenu(e, session, file) {
      this.sftpContextMenu.visible = true
      this.sftpContextMenu.x = e.clientX
      this.sftpContextMenu.y = e.clientY
      this.sftpContextMenu.session = session
      this.sftpContextMenu.file = file
    },
    hideSftpContextMenu() {
      this.sftpContextMenu.visible = false
    },
    downloadFromContextMenu() {
      var ctx = this.sftpContextMenu
      if (ctx.file && ctx.session && !ctx.file.isDir) {
        this.downloadSftpFile(ctx.session, ctx.file)
      }
      this.hideSftpContextMenu()
    },
    deleteFromContextMenu() {
      var ctx = this.sftpContextMenu
      if (!ctx.file || !ctx.session) return
      var self = this
      var fileName = ctx.file.name
      this.$confirm(
        (this.$t('sftpDeleteConfirm') || '确认删除').replace('{0}', fileName),
        this.$t('sftpDelete') || '删除',
        { confirmButtonText: this.$t('confirm') || '确定', cancelButtonText: this.$t('cancel') || '取消', type: 'warning' }
      ).then(function() {
        var remotePath = (ctx.session.sftpPath || '/') + (ctx.session.sftpPath === '/' ? '' : '/') + fileName
        callJava('sftpDelete', ctx.session.id, remotePath)
        self.refreshSftp(ctx.session)
      }).catch(function() {})
      this.hideSftpContextMenu()
    },
    renameFromContextMenu() {
      var ctx = this.sftpContextMenu
      if (!ctx.file || !ctx.session) return
      var self = this
      var oldName = ctx.file.name
      this.$prompt(
        (this.$t('sftpRenamePrompt') || '输入新名称').replace('{0}', oldName),
        this.$t('sftpRename') || '重命名',
        { confirmButtonText: this.$t('confirm') || '确定', cancelButtonText: this.$t('cancel') || '取消', inputValue: oldName }
      ).then(function(result) {
        var newName = (result && result.value) ? result.value.trim() : ''
        if (!newName || newName === oldName) return
        var oldPath = (ctx.session.sftpPath || '/') + (ctx.session.sftpPath === '/' ? '' : '/') + oldName
        callJava('sftpRename', ctx.session.id, oldPath, newName)
        self.refreshSftp(ctx.session)
      }).catch(function() {})
      this.hideSftpContextMenu()
    },
    // ---- SSH Term Context Menu ----
    showSshTermContextMenu(e, s) {
      this.sshTermContextMenu.visible = true
      this.sshTermContextMenu.x = e.clientX
      this.sshTermContextMenu.y = e.clientY
      this.sshTermContextMenu.session = s
    },
    hideSshTermContextMenu() {
      this.sshTermContextMenu.visible = false
    },
    copyFromTermContextMenu() {
      var ctx = this.sshTermContextMenu
      var s = ctx.session
      if (!s || !s.terminal) { this.hideSshTermContextMenu(); return }
      var selection = s.terminal.getSelection()
      if (!selection) {
        this.$message({ message: 'No selection', type: 'warning', duration: 1200 })
        this.hideSshTermContextMenu()
        return
      }
      var self = this
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(selection).then(function() {
          self.$message({ message: self.$t('copied'), type: 'success', duration: 1200 })
        }).catch(function() {
          self.$message({ message: self.$t('copyFailed'), type: 'warning', duration: 1500 })
        })
      } else {
        var ta = document.createElement('textarea')
        ta.value = selection
        ta.style.position = 'fixed'; ta.style.left = '-9999px'; ta.style.top = '-9999px'
        document.body.appendChild(ta)
        ta.select()
        try { document.execCommand('copy'); self.$message({ message: self.$t('copied'), type: 'success', duration: 1200 }) } catch(e) { self.$message({ message: self.$t('copyFailed'), type: 'warning', duration: 1500 }) }
        document.body.removeChild(ta)
      }
      this.hideSshTermContextMenu()
    },
    pasteToTermContextMenu() {
      var ctx = this.sshTermContextMenu
      var s = ctx.session
      this.hideSshTermContextMenu()
      if (!s || !s.terminal) return
      var self = this
      if (window.bridgeReady && window.cefQuery) {
        window.cefQuery({
          request: JSON.stringify({method: 'sshPaste', args: [s.id]}),
          onSuccess: function() {},
          onFailure: function(code, msg) {
            self.$message({ message: self.$t('sshPasteFailed') + ': ' + msg, type: 'warning', duration: 2000 })
          }
        })
      }
    },
    showCmdHistory() {
      var ctx = this.sshTermContextMenu
      var s = ctx.session
      this.hideSshTermContextMenu()
      if (!s) return
      if (!s._cmdHistory) this.$set(s, '_cmdHistory', [])
      this.cmdHistoryDialog.visible = true
      this.cmdHistoryDialog.session = s
    },
    hideCmdHistory() {
      this.cmdHistoryDialog.visible = false
      this.cmdHistoryDialog.session = null
    },
    selectCmdHistory(cmd) {
      var s = this.cmdHistoryDialog.session
      if (!s || !s.terminal || !cmd) return
      this.hideCmdHistory()
      var text = cmd + '\n'
      var encoder = new TextEncoder()
      var bytes = encoder.encode(text)
      var binaryStr = ''
      for (var i = 0; i < bytes.length; i++) { binaryStr += String.fromCharCode(bytes[i]) }
      var base64 = btoa(binaryStr)
      callJava('sshInput', s.id, base64)
    }
  }
}
</script>
