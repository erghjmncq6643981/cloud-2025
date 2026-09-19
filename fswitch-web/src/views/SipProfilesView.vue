<template>
  <div class="flex-1 flex flex-col overflow-hidden">
    <!-- 头部子Tab -->
    <div class="bg-[#0F172A] border-b border-slate-800 px-6 py-4 flex items-center justify-between shrink-0">
      <div class="flex items-center gap-2 bg-slate-900 p-1.5 rounded-xl border border-slate-800 text-base font-semibold">
        <button 
          @click="activeProfile = 'internal'"
          :class="activeProfile === 'internal' ? subActive : subInactive"
          class="px-5 py-2.5 rounded-lg transition"
        >
          internal (内网话机分机)
        </button>
        <button 
          @click="activeProfile = 'external'"
          :class="activeProfile === 'external' ? subActive : subInactive"
          class="px-5 py-2.5 rounded-lg transition"
        >
          external (外网中继接入)
        </button>
      </div>
      <button 
        @click="$emit('toast', '打开 SIP Profile 参数配置弹窗')"
        class="bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm px-5 py-2.5 rounded-xl border border-slate-700 font-semibold transition"
      >
        编辑 Profile 参数
      </button>
    </div>

    <!-- 详细配置内容 -->
    <div class="flex-1 p-6 overflow-y-auto space-y-6 text-base">
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl p-6 space-y-5 shadow-lg">
        <div class="flex justify-between items-center border-b border-slate-800 pb-4">
          <div class="flex items-center gap-3">
            <span class="font-extrabold text-white text-lg">Profile: {{ currentProfile?.name || activeProfile }}</span>
            <span class="text-sm text-slate-400">Sofia SIP Profile 物理监听与信令驱动</span>
          </div>
          <span class="text-emerald-400 font-bold bg-emerald-500/15 border border-emerald-500/30 px-3 py-1 rounded text-xs font-mono">
            {{ currentProfile?.state || 'RUNNING' }}
          </span>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6 font-mono text-base">
          <div class="space-y-3.5 bg-slate-900/60 p-5 rounded-xl border border-slate-800/80">
            <div class="flex justify-between"><span class="text-slate-400 font-sans">SIP 绑定地址:</span><span class="text-white font-bold">{{ currentProfile?.bind_ip || '192.168.18.64' }}:{{ currentProfile?.sip_port || 5060 }}</span></div>
            <div class="flex justify-between"><span class="text-slate-400 font-sans">WebSocket / WSS:</span><span class="text-white font-bold">:5066 (ws) / :7443 (wss)</span></div>
            <div class="flex justify-between"><span class="text-slate-400 font-sans">RTP 绑定 IP:</span><span class="text-white font-bold">{{ currentProfile?.bind_ip || '192.168.18.64' }}</span></div>
            <div class="flex justify-between"><span class="text-slate-400 font-sans">Dialplan:</span><span class="text-cyan-400 font-bold">{{ currentProfile?.dialplan || 'XML' }} (context: {{ currentProfile?.context || 'default' }})</span></div>
          </div>
          <div class="space-y-3.5 bg-slate-900/60 p-5 rounded-xl border border-slate-800/80">
            <div class="flex justify-between"><span class="text-slate-400 font-sans">音频 Codecs:</span><span class="text-cyan-300 font-bold">{{ currentProfile?.codecs || 'OPUS, G722, PCMU, PCMA' }}</span></div>
            <div class="flex justify-between"><span class="text-slate-400 font-sans">DTMF 模式:</span><span class="text-white font-bold">rfc2833 (payload 101)</span></div>
            <div class="flex justify-between"><span class="text-slate-400 font-sans">NAT 穿透 (Auto-NAT):</span><span class="text-slate-300 font-bold">false (Local Subnet)</span></div>
            <div class="flex justify-between"><span class="text-slate-400 font-sans">底层心跳状态:</span><span class="text-emerald-400 font-bold">HEALTHY</span></div>
          </div>
        </div>
      </div>

      <!-- Raw Sofia Output Box -->
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl p-6 space-y-4 shadow-lg">
        <span class="font-extrabold text-slate-200 text-base uppercase tracking-wider">FreeSWITCH 原生 Sofia Profile 终端回显:</span>
        <pre class="bg-slate-950 p-5 rounded-xl text-sm font-mono text-slate-300 overflow-x-auto max-h-80 leading-relaxed select-text border border-slate-800">{{ rawOutput }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { SofiaProfile } from '@/api/telephony'

const props = defineProps<{
  profiles: SofiaProfile[]
}>()

defineEmits<{
  (e: 'toast', msg: string): void
}>()

const activeProfile = ref<'internal' | 'external'>('internal')
const subActive = 'bg-cyan-950 text-cyan-400 border border-cyan-800/60 shadow-sm'
const subInactive = 'text-slate-400 hover:text-white'

const currentProfile = computed(() => {
  return props.profiles.find(p => p.name === activeProfile.value)
})

const rawOutput = computed(() => {
  if (currentProfile.value?.raw_lines) {
    return currentProfile.value.raw_lines.join('\n')
  }
  return 'Loading sofia status...'
})
</script>
