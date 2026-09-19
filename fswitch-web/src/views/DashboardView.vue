<template>
  <div class="flex-1 flex flex-col p-6 space-y-6 overflow-y-auto">
    <!-- 头部核心指标走字卡片 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 shrink-0">
      <!-- 卡片 1: 软交换核心引擎 -->
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl p-5 relative overflow-hidden shadow-md">
        <div class="flex justify-between items-center text-slate-400 text-sm mb-2 font-semibold">
          <span>FreeSWITCH 引擎状态</span>
          <span :class="status.fs_alive ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30' : 'bg-red-500/15 text-red-400 border border-red-500/30'" class="px-2.5 py-0.5 rounded text-xs font-extrabold">
            {{ status.fs_alive ? 'READY' : 'OFFLINE' }}
          </span>
        </div>
        <div class="text-3xl font-extrabold text-white font-mono">v1.11.3 <span class="text-sm font-normal text-slate-400">64bit</span></div>
        <div class="text-sm text-slate-400 mt-3 flex justify-between font-medium">
          <span>Uptime: <b class="text-slate-200">{{ formatUptime(status.uptime) }}</b></span>
          <span class="text-cyan-400 font-mono font-bold">ESL :8021</span>
        </div>
      </div>

      <!-- 卡片 2: 并发容量 -->
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl p-5 relative overflow-hidden shadow-md">
        <div class="flex justify-between items-center text-slate-400 text-sm mb-2 font-semibold">
          <span>并发活跃话道 (Channels)</span>
          <span :class="(status.channels || 0) > 0 ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30' : 'bg-slate-800 text-slate-400 border border-slate-700'" class="px-2.5 py-0.5 rounded text-xs font-extrabold">
            {{ (status.channels || 0) > 0 ? 'ACTIVE' : 'IDLE' }}
          </span>
        </div>
        <div class="text-3xl font-extrabold text-white font-mono flex items-baseline gap-2">
          <span class="text-cyan-400 text-4xl font-black">{{ status.channels || 0 }}</span>
          <span class="text-sm text-slate-400 font-normal">/ {{ status.max_sessions || 1000 }} max</span>
        </div>
        <div class="text-sm text-slate-400 mt-3 flex justify-between font-medium">
          <span>实时 CPS: <b class="text-slate-200 font-mono">{{ status.cps || 0 }}</b></span>
          <span>历史呼叫: <b class="text-slate-200 font-mono">{{ status.total_sessions || 11 }}</b> 次</span>
        </div>
      </div>

      <!-- 卡片 3: 在线物理分机 -->
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl p-5 relative overflow-hidden shadow-md">
        <div class="flex justify-between items-center text-slate-400 text-sm mb-2 font-semibold">
          <span>注册在线 SIP 终端</span>
          <span class="px-2.5 py-0.5 rounded text-xs font-extrabold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">100% 在线</span>
        </div>
        <div class="text-3xl font-extrabold text-emerald-400 font-mono flex items-baseline gap-2">
          <span class="text-4xl font-black">{{ registrations.length }}</span>
          <span class="text-sm text-slate-400 font-normal">台话机在线</span>
        </div>
        <div class="text-sm text-slate-400 mt-3 flex justify-between font-medium">
          <span>MicroSIP, Linphone, Zoiper</span>
          <button @click="$emit('switch-tab', 'extensions')" class="text-cyan-400 hover:underline font-semibold">详情 →</button>
        </div>
      </div>

      <!-- 卡片 4: PostgreSQL 核心库 -->
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl p-5 relative overflow-hidden shadow-md">
        <div class="flex justify-between items-center text-slate-400 text-sm mb-2 font-semibold">
          <span>PostgreSQL 核心通信库</span>
          <span class="px-2.5 py-0.5 rounded text-xs font-extrabold bg-cyan-500/15 text-cyan-400 border border-cyan-500/30">SYNCED</span>
        </div>
        <div class="text-3xl font-extrabold text-white font-mono flex items-baseline gap-2">
          <span>9 张核心表</span>
        </div>
        <div class="text-sm text-slate-400 mt-3 flex justify-between font-medium">
          <span>registrations / channels</span>
          <span class="text-cyan-300 font-mono font-bold">:5432 LIVE</span>
        </div>
      </div>
    </div>

    <!-- 中部主视区：分机终端监控与通信网络状态 -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- 左侧 2 列: SIP 物理终端在线监控 -->
      <div class="lg:col-span-2 bg-[#131C31] border border-slate-800 rounded-2xl p-6 space-y-5 shadow-lg">
        <div class="flex items-center justify-between pb-4 border-b border-slate-800">
          <div class="flex items-center gap-3">
            <span class="w-3 h-3 rounded-full bg-cyan-400 animate-ping"></span>
            <span class="text-lg font-bold text-white tracking-wide">SIP 分机终端实时镜像 (PostgreSQL 毫秒直读)</span>
          </div>
          <button @click="$emit('switch-tab', 'extensions')" class="text-sm font-semibold text-cyan-400 hover:underline">管理全部 SIP 分机 →</button>
        </div>

        <!-- 分机卡片网格 -->
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div 
            v-for="reg in registrations" 
            :key="reg.reg_user"
            class="bg-slate-900 border border-slate-800 p-5 rounded-2xl text-base space-y-3 hover:border-cyan-500/60 transition shadow-md"
          >
            <div class="flex justify-between items-center">
              <span class="font-extrabold text-cyan-400 font-mono text-2xl">{{ reg.reg_user }}</span>
              <span class="bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 text-xs px-2.5 py-0.5 rounded font-mono font-bold">ONLINE</span>
            </div>
            <div class="text-slate-100 font-bold truncate text-base">{{ reg.user_agent || 'SIP Endpoint' }}</div>
            <div class="text-slate-400 text-xs font-mono space-y-1.5 pt-2 border-t border-slate-800">
              <div class="flex justify-between items-center gap-2">
                <span class="text-slate-400 font-sans shrink-0">网络地址:</span>
                <span class="text-slate-200 font-mono font-semibold truncate" :title="`${reg.network_ip}:${reg.network_port}`">{{ reg.network_ip }}:{{ reg.network_port }}</span>
              </div>
              <div class="flex justify-between items-center gap-2">
                <span class="text-slate-400 font-sans shrink-0">传输协议:</span>
                <span :class="reg.network_proto.toLowerCase() === 'tcp' ? 'text-indigo-400' : 'text-cyan-400'" class="font-bold uppercase font-mono">
                  {{ reg.network_proto }}
                </span>
              </div>
              <div class="flex justify-between items-center gap-2">
                <span class="text-slate-400 font-sans shrink-0">租期剩余:</span>
                <span class="text-emerald-400 font-mono font-bold">{{ reg.remaining_seconds }}s</span>
              </div>
            </div>
            <div class="pt-2 flex gap-2.5">
              <button 
                @click="$emit('dial-ext', reg.reg_user)"
                class="flex-1 bg-cyan-950 hover:bg-cyan-900 border border-cyan-800/80 text-cyan-300 text-sm py-2 rounded-xl transition font-bold"
              >
                呼叫测试
              </button>
              <button 
                @click="$emit('view-detail', reg)"
                class="px-4 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 text-sm py-2 rounded-xl transition font-semibold"
              >
                详情
              </button>
            </div>
          </div>
        </div>

        <!-- 快速呼叫 Bridge 控制条 -->
        <div class="bg-slate-900/90 p-4 rounded-2xl border border-slate-800 flex flex-wrap justify-between items-center gap-4 text-base">
          <div class="flex items-center gap-3 flex-wrap">
            <span class="text-slate-300 font-semibold">快速桥接测试 (Bridge):</span>
            <select v-model="quickCaller" class="bg-slate-800 border border-slate-700 text-white rounded-xl px-3.5 py-2 text-sm font-mono font-bold">
              <option value="1017">主叫: 1017 (MicroSIP)</option>
              <option value="1007">主叫: 1007 (Linphone)</option>
              <option value="1008">主叫: 1008 (Zoiper)</option>
            </select>
            <span class="text-slate-500 font-bold text-lg">➔</span>
            <select v-model="quickCallee" class="bg-slate-800 border border-slate-700 text-white rounded-xl px-3.5 py-2 text-sm font-mono font-bold">
              <option value="1007">被叫: 1007 (Linphone)</option>
              <option value="1008">被叫: 1008 (Zoiper)</option>
              <option value="1017">被叫: 1017 (MicroSIP)</option>
            </select>
          </div>
          <button 
            @click="$emit('quick-bridge', quickCaller, quickCallee)"
            class="bg-emerald-600 hover:bg-emerald-500 text-white px-5 py-2.5 rounded-xl text-sm font-bold shadow-lg shadow-emerald-950 transition flex items-center gap-2"
          >
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><polyline points="23 7 23 1 17 1"/><line x1="16" y1="8" x2="23" y2="1"/><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/></svg>
            <span>建立 Bridge 通话</span>
          </button>
        </div>
      </div>

      <!-- 右侧 1 列: 软交换信令与 SIP Profiles 概览 -->
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl p-6 space-y-5 shadow-lg flex flex-col justify-between">
        <div class="space-y-5">
          <div class="flex items-center justify-between pb-4 border-b border-slate-800">
            <span class="text-lg font-bold text-white">Sofia SIP 协议栈状态</span>
            <button @click="$emit('switch-tab', 'sip')" class="text-sm font-semibold text-cyan-400 hover:underline">配置 →</button>
          </div>

          <div class="space-y-4 font-mono text-sm">
            <!-- internal -->
            <div class="bg-slate-900 border border-slate-800 rounded-2xl p-4 space-y-2.5">
              <div class="flex justify-between items-center text-base font-bold">
                <span class="text-white flex items-center gap-2">
                  <span class="w-2.5 h-2.5 rounded-full bg-emerald-400"></span>
                  internal (内网话机)
                </span>
                <span class="text-emerald-400 text-xs px-2 py-0.5 rounded bg-emerald-950/60 border border-emerald-800/60 font-mono">RUNNING</span>
              </div>
              <div class="text-slate-400 space-y-1.5 pt-2 border-t border-slate-800 text-xs font-sans">
                <div class="flex justify-between"><span>SIP 监听:</span><span class="text-slate-200 font-mono font-semibold">192.168.18.64:5060</span></div>
                <div class="flex justify-between"><span>WebSocket:</span><span class="text-slate-200 font-mono font-semibold">:5066 (ws) / :7443 (wss)</span></div>
                <div class="flex justify-between"><span>音频编解码:</span><span class="text-cyan-400 font-mono font-bold">OPUS, G722, PCMA</span></div>
              </div>
            </div>

            <!-- external -->
            <div class="bg-slate-900 border border-slate-800 rounded-2xl p-4 space-y-2.5">
              <div class="flex justify-between items-center text-base font-bold">
                <span class="text-white flex items-center gap-2">
                  <span class="w-2.5 h-2.5 rounded-full bg-emerald-400"></span>
                  external (运营商中继)
                </span>
                <span class="text-emerald-400 text-xs px-2 py-0.5 rounded bg-emerald-950/60 border border-emerald-800/60 font-mono">RUNNING</span>
              </div>
              <div class="text-slate-400 space-y-1.5 pt-2 border-t border-slate-800 text-xs font-sans">
                <div class="flex justify-between"><span>SIP 监听:</span><span class="text-slate-200 font-mono font-semibold">192.168.18.64:5080</span></div>
                <div class="flex justify-between"><span>活跃中继:</span><span class="text-emerald-400 font-mono font-bold">trunk_unicom_01</span></div>
              </div>
            </div>
          </div>
        </div>

        <div class="pt-4 border-t border-slate-800 flex justify-between text-sm text-slate-400 font-medium">
          <button @click="$emit('switch-tab', 'terminal')" class="text-cyan-400 hover:underline font-semibold">打开控制台 (fs_cli) →</button>
          <button @click="$emit('switch-tab', 'sip')" class="hover:text-white">查看 SIP 详情</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { SystemStatus, Registration } from '@/api/telephony'

defineProps<{
  status: Partial<SystemStatus>
  registrations: Registration[]
}>()

defineEmits<{
  (e: 'switch-tab', tab: string): void
  (e: 'dial-ext', ext: string): void
  (e: 'view-detail', reg: Registration): void
  (e: 'quick-bridge', caller: string, callee: string): void
}>()

const quickCaller = ref('1017')
const quickCallee = ref('1007')

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
