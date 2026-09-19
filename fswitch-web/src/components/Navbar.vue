<template>
  <header class="bg-[#0F172A] border-b border-slate-800 h-16 flex items-center justify-between px-6 shrink-0 z-30">
    <!-- Brand & Cluster Status -->
    <div class="flex items-center gap-6">
      <div class="flex items-center gap-3">
        <div class="w-9 h-9 rounded-xl bg-gradient-to-tr from-cyan-600 to-teal-400 flex items-center justify-center shadow-lg shadow-cyan-500/20">
          <svg class="w-5 h-5 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3">
            <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
          </svg>
        </div>
        <div class="flex items-baseline gap-2.5">
          <span class="text-white font-extrabold text-xl tracking-wide">fswitch-web</span>
          <span class="text-xs bg-cyan-950 text-cyan-400 border border-cyan-800/80 px-2.5 py-0.5 rounded font-mono font-semibold">Control Plane</span>
        </div>
      </div>

      <!-- 核心引擎指标指示灯 -->
      <div class="hidden lg:flex items-center gap-3 bg-slate-900/90 border border-slate-800 px-3.5 py-1.5 rounded-xl text-sm font-medium shrink-0">
        <div class="flex items-center gap-2">
          <span class="w-2.5 h-2.5 rounded-full shrink-0" :class="stale ? 'bg-amber-400' : status.fs_alive ? 'bg-emerald-400 animate-pulse' : 'bg-red-400'"></span>
          <span class="text-slate-400 shrink-0">Node:</span>
          <span class="text-white font-mono font-semibold text-sm max-w-[120px] truncate" :title="status.node_id">{{ status.node_id || '未连接' }}</span>
        </div>
        <span class="text-slate-700">|</span>
        <div class="flex items-center gap-1.5 shrink-0">
          <span class="text-slate-400">Uptime:</span>
          <span class="text-emerald-400 font-mono font-semibold text-sm">{{ formatUptime(status.uptime) }}</span>
        </div>
        <span class="text-slate-700">|</span>
        <div class="flex items-center gap-1.5 shrink-0">
          <span class="text-slate-400">并发话道:</span>
          <span class="text-cyan-400 font-mono font-bold text-sm">{{ status.channels ?? '-' }} / {{ status.max_sessions ?? '-' }}</span>
        </div>
        <span class="text-slate-700">|</span>
        <div class="flex items-center gap-1.5 shrink-0">
          <span class="text-slate-400">PG 核心库:</span>
          <span :class="status.pg_connected ? 'text-emerald-400' : 'text-red-400'" class="font-mono font-semibold text-sm">
            {{ status.pg_connected ? 'freeswitch (OK)' : 'OFFLINE' }}
          </span>
        </div>
      </div>
    </div>

    <!-- 右侧快捷指令与操作 -->
    <div class="flex items-center gap-3 shrink-0">
      <button 
        @click="$emit('reload-xml')"
        class="shrink-0 whitespace-nowrap flex items-center gap-2 bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm px-3.5 py-2 rounded-xl border border-slate-700 font-semibold transition"
      >
        <svg class="w-4 h-4 text-cyan-400 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
          <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/>
        </svg>
        <span>重载 XML</span>
      </button>


      <!-- 用户信息 -->
      <div class="flex items-center gap-2.5 pl-3 border-l border-slate-800">
        <div class="w-9 h-9 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-xs font-bold text-cyan-400">
          OP
        </div>
        <span class="text-base text-slate-200 font-semibold hidden sm:inline">运维控制台</span>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import type { SystemStatus } from '@/api/telephony'

defineProps<{
  status: Partial<SystemStatus>
  stale: boolean
}>()

defineEmits<{
  (e: 'reload-xml'): void
}>()

function formatUptime(uptime?: string) {
  if (!uptime) return '0h 0m'
  if (uptime.includes('hours') && uptime.includes('minutes')) {
    const hMatch = uptime.match(/(\d+)\s+hours/)
    const mMatch = uptime.match(/(\d+)\s+minutes/)
    const h = hMatch ? hMatch[1] : '0'
    const m = mMatch ? mMatch[1] : '0'
    return `${h}h ${m}m`
  }
  return uptime.slice(0, 15)
}
</script>
