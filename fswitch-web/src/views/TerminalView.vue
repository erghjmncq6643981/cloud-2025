<template>
  <div class="flex-1 flex flex-col overflow-hidden">
    <div class="shrink-0 border-b border-slate-800 bg-[#0F172A] px-6 py-4 flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-3">
        <span class="h-2.5 w-2.5 rounded-full" :class="wsState === 'connected' ? 'bg-emerald-400' : wsState === 'connecting' ? 'bg-amber-400' : 'bg-rose-400'"></span>
        <span class="font-mono text-sm font-bold text-white">Sidecar 控制台与日志流</span>
        <span class="rounded border px-2 py-0.5 font-mono text-xs" :class="wsState === 'connected' ? 'border-emerald-800 text-emerald-400' : 'border-slate-700 text-slate-400'">{{ wsState.toUpperCase() }}</span>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <button v-for="command in quickCommands" :key="command" class="rounded-md bg-slate-800 px-3 py-1.5 font-mono text-xs text-slate-200 hover:bg-slate-700" @click="runCommand(command)">{{ command }}</button>
        <button class="rounded-md bg-slate-800 px-3 py-1.5 text-xs text-slate-300 hover:bg-slate-700" @click="clearScreen">清屏</button>
      </div>
    </div>

    <div ref="screenRef" class="flex-1 overflow-y-auto bg-[#050811] p-6 font-mono text-sm leading-relaxed text-slate-200 select-text">
      <div v-if="logLines.length === 0" class="text-slate-500">等待 Sidecar 日志或命令输出...</div>
      <div v-for="(line, idx) in logLines" :key="idx" :class="getLineClass(line)">{{ line }}</div>
    </div>

    <div class="shrink-0 border-t border-slate-800 bg-[#0F172A] px-6 py-4 flex items-center gap-4">
      <span class="shrink-0 font-mono font-bold text-cyan-400">fs_cli&gt;</span>
      <input v-model="inputCommand" type="text" class="min-w-0 flex-1 bg-transparent font-mono text-sm text-white outline-none placeholder:text-slate-500" placeholder="输入受信任的 FreeSWITCH 运维命令" @keydown.enter="submitInput">
      <button class="rounded-md bg-cyan-600 px-4 py-2 text-sm font-bold text-white hover:bg-cyan-500" @click="submitInput">执行</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { telephonyApi } from '@/api/telephony'

const emit = defineEmits<{
  (e: 'toast', msg: string): void
}>()

const quickCommands = ['status', 'show channels', 'sofia status profile internal reg', 'reloadxml']
const screenRef = ref<HTMLDivElement | null>(null)
const inputCommand = ref('')
const wsState = ref<'connecting' | 'connected' | 'disconnected'>('connecting')
const logLines = ref<string[]>([])
let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let active = false

onMounted(() => {
  active = true
  connectLogStream()
})

onUnmounted(() => {
  active = false
  if (reconnectTimer) clearTimeout(reconnectTimer)
  reconnectTimer = null
  if (ws) {
    ws.onclose = null
    ws.close()
  }
  ws = null
})

function connectLogStream() {
  if (!active || ws?.readyState === WebSocket.OPEN || ws?.readyState === WebSocket.CONNECTING) return
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }

  wsState.value = 'connecting'
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const url = `${protocol}//${window.location.host}/api/v1/telephony/ws/console-logs`

  try {
    ws = new WebSocket(url)
    ws.onopen = () => {
      wsState.value = 'connected'
      appendLog('[SYSTEM] Sidecar 日志流已连接')
    }
    ws.onmessage = event => {
      try {
        const data = JSON.parse(event.data)
        if (typeof data.text === 'string') appendLog(data.text)
      } catch {
        appendLog(String(event.data))
      }
    }
    ws.onerror = () => {
      wsState.value = 'disconnected'
    }
    ws.onclose = () => {
      ws = null
      wsState.value = 'disconnected'
      scheduleReconnect()
    }
  } catch (error: any) {
    ws = null
    wsState.value = 'disconnected'
    emit('toast', `日志流连接失败：${error.message || '连接异常'}`)
    scheduleReconnect()
  }
}

function scheduleReconnect() {
  if (!active || reconnectTimer) return
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connectLogStream()
  }, 4000)
}

function appendLog(line: string) {
  logLines.value.push(line)
  if (logLines.value.length > 500) logLines.value.shift()
  nextTick(() => {
    if (screenRef.value) screenRef.value.scrollTop = screenRef.value.scrollHeight
  })
}

function getLineClass(line: string) {
  if (line.startsWith('fs_cli>')) return 'text-cyan-400 font-bold mt-2'
  if (line.includes('[ERR') || line.includes('[ERROR')) return 'text-rose-400'
  if (line.includes('[WARN')) return 'text-amber-400'
  if (line.includes('[SYSTEM]')) return 'text-emerald-400'
  return 'text-slate-300'
}

async function runCommand(command: string) {
  appendLog(`fs_cli> ${command}`)
  try {
    const output = await telephonyApi.executeCli(command)
    if (output) output.split('\n').forEach(appendLog)
    else appendLog('[SYSTEM] 指令已受理，未返回输出')
  } catch (error: any) {
    appendLog(`[ERROR] ${error.message || '命令执行失败'}`)
  }
}

function submitInput() {
  const command = inputCommand.value.trim()
  if (!command) return
  inputCommand.value = ''
  runCommand(command)
}

function clearScreen() {
  logLines.value = []
}
</script>
