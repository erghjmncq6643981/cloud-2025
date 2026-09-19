<template>
  <div class="flex-1 flex flex-col overflow-hidden">
    <!-- 终端控制头 -->
    <div class="bg-[#0F172A] border-b border-slate-800 px-6 py-4 flex items-center justify-between shrink-0">
      <div class="flex items-center gap-3">
        <span class="w-3 h-3 rounded-full bg-emerald-400 animate-pulse"></span>
        <span class="text-base font-bold text-white font-mono">FreeSWITCH Web Console (ESL :8021)</span>
        <span :class="wsConnected ? 'text-emerald-400 border-emerald-800/80 bg-emerald-950/60' : 'text-amber-400 border-amber-800/80 bg-amber-950/60'" class="text-xs px-2.5 py-0.5 rounded border font-mono font-bold">
          {{ wsConnected ? 'WS LOG STREAM LIVE' : 'WS CONNECTING' }}
        </span>
      </div>
      <div class="flex items-center gap-2.5">
        <span class="text-sm text-slate-400 mr-1 hidden sm:inline">快捷命令:</span>
        <button @click="runCommand('status')" class="bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs px-3.5 py-1.5 rounded-lg font-mono font-bold transition">status</button>
        <button @click="runCommand('show channels')" class="bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs px-3.5 py-1.5 rounded-lg font-mono font-bold transition">show channels</button>
        <button @click="runCommand('sofia status profile internal reg')" class="bg-slate-800 hover:bg-slate-700 text-cyan-300 text-xs px-3.5 py-1.5 rounded-lg font-mono font-bold transition">sofia status reg</button>
        <button @click="runCommand('reloadxml')" class="bg-slate-800 hover:bg-slate-700 text-amber-300 text-xs px-3.5 py-1.5 rounded-lg font-mono font-bold transition">reloadxml</button>
        <button @click="clearScreen" class="bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs px-3.5 py-1.5 rounded-lg transition font-medium">清屏</button>
      </div>
    </div>

    <!-- 终端内容屏幕 -->
    <div ref="screenRef" class="flex-1 bg-[#050811] p-6 font-mono text-base leading-relaxed text-slate-200 overflow-y-auto space-y-2 select-text">
      <div class="text-cyan-400 font-bold">FreeSWITCH (Version 1.11.3 -release 64bit) is ready</div>
      <div class="text-slate-400">Connected to Inbound ESL at 127.0.0.1:8021 via Go Sidecar (:8088).</div>
      <div v-for="(line, idx) in logLines" :key="idx" :class="getLineClass(line)">
        {{ line }}
      </div>
    </div>

    <!-- 命令行输入栏 -->
    <div class="bg-[#0F172A] border-t border-slate-800 px-6 py-4 flex items-center gap-4 shrink-0">
      <span class="text-cyan-400 font-mono text-base font-extrabold shrink-0">fs_cli&gt;</span>
      <input 
        v-model="inputCommand"
        @keydown.enter="submitInput"
        type="text" 
        placeholder="输入 FreeSWITCH 原生命令 (按 Enter 执行，如: status, show channels, reloadxml)..." 
        class="bg-transparent text-white font-mono text-base flex-1 outline-none placeholder-slate-500 font-medium"
      >
      <button 
        @click="submitInput"
        class="bg-cyan-600 hover:bg-cyan-500 text-white font-mono text-sm font-bold px-5 py-2.5 rounded-xl shadow-lg transition"
      >
        执行
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { telephonyApi } from '@/api/telephony'

const emit = defineEmits<{
  (e: 'toast', msg: string): void
}>()

const screenRef = ref<HTMLDivElement | null>(null)
const inputCommand = ref('')
const wsConnected = ref(false)
let ws: WebSocket | null = null

const logLines = ref<string[]>([
  '[NOTICE] switch_loadable_module.c:1834 Module [mod_sofia] loaded successfully.',
  '[INFO] mod_sofia.c:1026 Profile [internal] registered sip:1007@192.168.18.64:15061 (Linphone-Desktop)',
  '[INFO] mod_sofia.c:1026 Profile [internal] registered sip:1008@192.168.18.64:64521 (Zoiper)',
  '[INFO] mod_sofia.c:1026 Profile [internal] registered sip:1017@192.168.18.64:53446 (MicroSIP)',
  '[INFO] Core database [PostgreSQL freeswitch:5432] live sync ready.'
])

onMounted(() => {
  initWebSocket()
})

onUnmounted(() => {
  if (ws) ws.close()
})

function initWebSocket() {
  const loc = window.location
  const wsProto = loc.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${wsProto}//${loc.host}/api/v1/telephony/ws/console-logs`

  try {
    ws = new WebSocket(wsUrl)
    ws.onopen = () => {
      wsConnected.value = true
    }
    ws.onmessage = (evt) => {
      try {
        const data = JSON.parse(evt.data)
        if (data.text) {
          appendLog(data.text)
        }
      } catch (e) {
        appendLog(evt.data)
      }
    }
    ws.onclose = () => {
      wsConnected.value = false
      setTimeout(initWebSocket, 4000)
    }
  } catch (err) {
    wsConnected.value = false
  }
}

function appendLog(line: string) {
  logLines.value.push(line)
  if (logLines.value.length > 500) {
    logLines.value.shift()
  }
  nextTick(() => {
    if (screenRef.value) {
      screenRef.value.scrollTop = screenRef.value.scrollHeight
    }
  })
}

function getLineClass(line: string) {
  if (line.startsWith('fs_cli>')) return 'text-cyan-400 font-bold mt-1.5'
  if (line.includes('[ERR]')) return 'text-red-400 font-semibold'
  if (line.includes('[WARNING]')) return 'text-amber-400'
  if (line.includes('[NOTICE]')) return 'text-emerald-400'
  return 'text-slate-300'
}

async function runCommand(cmd: string) {
  appendLog(`fs_cli> ${cmd}`)
  try {
    const output = await telephonyApi.executeCli(cmd)
    if (output) {
      const parts = output.split('\n')
      parts.forEach(p => appendLog(p))
    }
  } catch (err: any) {
    appendLog(`[ERROR] ${err.message || 'Execution error'}`)
  }
}

function submitInput() {
  const cmd = inputCommand.value.trim()
  if (!cmd) return
  inputCommand.value = ''
  runCommand(cmd)
}

function clearScreen() {
  logLines.value = ['[INFO] Console screen cleared.']
}
</script>
