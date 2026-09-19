<template>
  <div class="flex-1 flex flex-col overflow-hidden">
    <!-- 头部子 Tab -->
    <div class="bg-[#0F172A] border-b border-slate-800 px-6 py-4 flex items-center justify-between shrink-0">
      <div class="flex items-center gap-2 bg-slate-900 p-1.5 rounded-xl border border-slate-800 text-base font-semibold">
        <button 
          @click="activeSubTab = 'channels'"
          :class="activeSubTab === 'channels' ? subActive : subInactive"
          class="px-5 py-2.5 rounded-lg transition"
        >
          并发话道实时监视 (Active Channels)
        </button>
        <button 
          @click="activeSubTab = 'bridge'"
          :class="activeSubTab === 'bridge' ? subActive : subInactive"
          class="px-5 py-2.5 rounded-lg transition"
        >
          双向通话桥接拓扑 (Bridge Matrix)
        </button>
      </div>

      <div class="flex items-center gap-3.5">
        <button 
          @click="$emit('simulate-call')"
          class="bg-cyan-600 hover:bg-cyan-500 text-white text-sm px-5 py-2.5 rounded-xl font-bold transition flex items-center gap-2 shadow-lg shadow-cyan-950"
        >
          <span>+ 发起模拟通话</span>
        </button>
        <button 
          @click="$emit('refresh')"
          class="bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm px-4 py-2.5 rounded-xl border border-slate-700 font-semibold transition"
        >
          刷新
        </button>
      </div>
    </div>

    <!-- 话道主内容区 -->
    <div class="flex-1 p-6 overflow-y-auto space-y-6">
      <!-- 空状态 -->
      <div v-if="channels.length === 0" class="bg-[#131C31] border border-slate-800 rounded-2xl p-20 text-center space-y-5 shadow-lg">
        <div class="w-20 h-20 rounded-full bg-slate-900 border border-slate-800 flex items-center justify-center mx-auto text-slate-400">
          <svg class="w-10 h-10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
          </svg>
        </div>
        <div class="text-white font-extrabold text-xl">软交换引擎当前无活跃通道 (0 Active Channels)</div>
        <div class="text-base text-slate-400 max-w-lg mx-auto leading-relaxed">
          当前通信引擎处于空闲就绪态。您可使用物理话机（1017 / 1007 / 1008）直接拨号，或点击上方“发起模拟通话”建立实时通信话道。
        </div>
      </div>

      <!-- 活跃通话卡片展示 (PG 实时数据) -->
      <div v-else class="space-y-5">
        <div v-for="ch in channels" :key="ch.uuid" class="bg-[#131C31] border border-cyan-500/50 rounded-2xl p-6 shadow-xl space-y-5">
          <div class="flex flex-wrap items-center justify-between border-b border-slate-800 pb-4 gap-3">
            <div class="flex items-center gap-3">
              <span class="w-3.5 h-3.5 rounded-full bg-emerald-400 animate-pulse"></span>
              <span class="font-black text-white text-base uppercase tracking-wider">活跃话道通道</span>
              <span class="text-slate-400 font-mono text-sm truncate max-w-md">UUID: {{ ch.uuid }}</span>
            </div>
            <div class="flex items-center gap-4">
              <span class="text-base text-slate-300 font-medium">已通话: <b class="text-cyan-400 font-mono text-xl font-extrabold">{{ formatDuration(ch.duration_sec) }}</b></span>
              <button @click="openTransfer(ch.uuid)" class="bg-indigo-950 hover:bg-indigo-900 border border-indigo-800 text-indigo-300 px-4 py-2 rounded-xl text-sm font-bold transition">盲转 (Transfer)</button>
              <button @click="$emit('kill-channel', ch.uuid)" class="bg-rose-950 hover:bg-rose-900 border border-rose-800 text-rose-300 px-4 py-2 rounded-xl text-sm font-bold transition">强拆挂断 (Kill)</button>
            </div>
          </div>

          <!-- 通道详情 -->
          <div class="grid grid-cols-1 md:grid-cols-2 gap-5 text-base font-mono">
            <div class="bg-slate-900 p-5 rounded-2xl border border-slate-800 space-y-2.5">
              <div class="flex justify-between text-cyan-400 font-extrabold text-xl">
                <span>主叫: {{ ch.cid_num || ch.cid_name }}</span>
                <span class="text-xs bg-cyan-950 border border-cyan-800 px-2.5 py-1 rounded font-bold">{{ ch.state }}</span>
              </div>
              <div class="text-slate-200 text-base">被叫目标: <b class="text-white text-lg font-bold">{{ ch.dest || ch.callee_num }}</b></div>
              <div class="text-slate-300 text-sm">音频编解码: <span class="text-cyan-300 font-bold">{{ ch.read_codec }} ({{ ch.read_rate }}Hz)</span></div>
              <div class="text-slate-400 text-sm truncate">执行应用: {{ ch.application }} ({{ ch.application_data }})</div>
            </div>

            <div class="bg-slate-900 p-5 rounded-2xl border border-slate-800 space-y-2.5">
              <div class="flex justify-between text-emerald-400 font-extrabold text-xl">
                <span>网络与媒体状态</span>
                <span class="text-xs bg-emerald-950 border border-emerald-800 px-2.5 py-1 rounded font-bold">{{ ch.callstate }}</span>
              </div>
              <div class="text-slate-200 text-base">远端网络 IP: <b class="text-white text-lg font-bold">{{ ch.ip_addr }}</b></div>
              <div class="text-slate-300 text-sm">Context: {{ ch.context }} · Dialplan: {{ ch.dialplan }}</div>
              <div class="text-slate-400 text-sm truncate">通道全称: {{ ch.name }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 盲转弹窗 (Transfer Modal) -->
    <div v-if="transferUUID" class="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl w-full max-w-md overflow-hidden shadow-2xl space-y-5">
        <div class="bg-[#0F172A] border-b border-slate-800 px-6 py-4 flex items-center justify-between">
          <span class="font-extrabold text-white text-lg">话道盲转 (uuid_transfer)</span>
          <button @click="transferUUID = ''" class="text-slate-400 hover:text-white text-lg">✕</button>
        </div>
        <div class="px-6 space-y-4 text-base">
          <p class="text-slate-300 font-medium">将当前活跃通道转接至目标 SIP 分机：</p>
          <div>
            <label class="block text-slate-400 mb-2 font-semibold text-sm">目标分机号 (Target Extension)</label>
            <select v-model="targetExt" class="w-full bg-slate-900 border border-slate-700 text-white rounded-xl px-4 py-3 outline-none font-mono text-base font-bold">
              <option value="1008">1008 (Zoiper 5)</option>
              <option value="1007">1007 (Linphone Desktop)</option>
              <option value="1017">1017 (MicroSIP)</option>
              <option value="1000">1000 (SIP Phone)</option>
            </select>
          </div>
        </div>
        <div class="bg-[#0F172A] border-t border-slate-800 px-6 py-4 flex justify-end gap-3.5">
          <button @click="transferUUID = ''" class="bg-slate-800 hover:bg-slate-700 text-slate-200 px-4 py-2.5 rounded-xl text-sm font-semibold">取消</button>
          <button @click="submitTransfer" class="bg-indigo-600 hover:bg-indigo-500 text-white px-6 py-2.5 rounded-xl text-sm font-bold shadow-lg">执行转接</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { Channel, Call } from '@/api/telephony'

defineProps<{
  channels: Channel[]
  calls: Call[]
}>()

const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'simulate-call'): void
  (e: 'kill-channel', uuid: string): void
  (e: 'transfer-channel', uuid: string, target: string): void
}>()

const activeSubTab = ref<'channels' | 'bridge'>('channels')
const subActive = 'bg-cyan-950 text-cyan-400 border border-cyan-800/60 shadow-sm font-semibold'
const subInactive = 'text-slate-400 hover:text-white'

const transferUUID = ref('')
const targetExt = ref('1008')

function openTransfer(uuid: string) {
  transferUUID.value = uuid
}

function submitTransfer() {
  emit('transfer-channel', transferUUID.value, targetExt.value)
  transferUUID.value = ''
}

function formatDuration(sec?: number) {
  if (!sec) return '00:00:01'
  const m = String(Math.floor(sec / 60)).padStart(2, '0')
  const s = String(sec % 60).padStart(2, '0')
  return `00:${m}:${s}`
}
</script>
