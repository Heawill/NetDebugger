/* ==================== UTILITY ==================== */

export function callJava(method) {
  var args = Array.prototype.slice.call(arguments, 1)
  if (window.bridgeReady && window.cefQuery) {
    window.cefQuery({
      request: JSON.stringify({method: method, args: args}),
      onSuccess: function(){},
      onFailure: function(c, m){ console.error(method, c, m) }
    })
  }
}

var ctr = 0

export function makeSession(prefix, extras) {
  var s = {
    id: prefix + (++ctr),
    logs: [],
    message: '',
    encoding: 'UTF-8',
    format: 'text',
    receiveMode: 'text',
    receiveEncoding: 'UTF-8',
    loading: false
  }
  return Object.assign(s, extras)
}

export function resetCounter(maxNum) {
  ctr = maxNum
}

export function getCounter() {
  return ctr
}

// Byte array search helper
export function indexOfBytes(haystack, needle) {
  for (var i = 0; i <= haystack.length - needle.length; i++) {
    var found = true
    for (var j = 0; j < needle.length; j++) {
      if (haystack[i + j] !== needle[j]) { found = false; break }
    }
    if (found) return i
  }
  return -1
}

export function hexDecode(hexStr, encoding) {
  var hex = hexStr.replace(/\s/g, '')
  var len = hex.length / 2
  var bytes = new Uint8Array(len)
  for (var i = 0; i < len; i++) {
    bytes[i] = parseInt(hex.substr(i * 2, 2), 16)
  }
  var enc = (encoding || 'UTF-8').toLowerCase().replace('-', '')
  // ASCII: direct byte-to-char mapping
  if (enc === 'ascii') {
    var asciiResult = ''
    for (var k = 0; k < bytes.length; k++) {
      asciiResult += bytes[k] < 0x80 ? String.fromCharCode(bytes[k]) : '\ufffd'
    }
    return asciiResult
  }
  try {
    return new TextDecoder(enc).decode(bytes)
  } catch(e) {
    // Fallback: manual UTF-8 decoder
    var result = ''
    var j = 0
    while (j < bytes.length) {
      var b = bytes[j]
      if (b < 0x80) { result += String.fromCharCode(b); j += 1 }
      else if (b < 0xE0) { result += String.fromCharCode(((b & 0x1F) << 6) | (bytes[j+1] & 0x3F)); j += 2 }
      else if (b < 0xF0) { result += String.fromCharCode(((b & 0x0F) << 12) | ((bytes[j+1] & 0x3F) << 6) | (bytes[j+2] & 0x3F)); j += 3 }
      else {
        var cp = ((b & 0x07) << 18) | ((bytes[j+1] & 0x3F) << 12) | ((bytes[j+2] & 0x3F) << 6) | (bytes[j+3] & 0x3F)
        cp -= 0x10000
        result += String.fromCharCode(0xD800 + (cp >> 10), 0xDC00 + (cp & 0x3FF))
        j += 4
      }
    }
    return result
  }
}

export function formatSize(bytes) {
  if (!bytes || bytes === 0) return '0 B'
  var units = ['B', 'KB', 'MB', 'GB', 'TB']
  var i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + units[i]
}
