<template>
  <div class="flex-1 flex flex-col overflow-hidden">
    <div class="shrink-0 border-b border-slate-800 bg-[#0F172A] px-6 py-4 flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-2 rounded-lg border border-slate-800 bg-slate-900 p-1 text-sm font-semibold">
        <button :class="activeSubTab === 'channels' ? subActive : subInactive" class="rounded-md px-4 py-2" @click="activeSubTab = 'channels'">活跃话道</button>
        <button :class="activeSubTab === 'bridge' ? subActive : subInactive" class="rounded-md px-4 py-2" @click="activeSubTab = 'bridge'">桥接关系</button>
      </div>
      <button class="rounded-md border border-slate-700 bg-slate-800 px-4 py-2 text-sm font-semibold text-slate-200 hover:bg-slate-700" @click="$emit('refresh')">刷新</button>
    </div>

    <div class="flex-1 overflow-y-auto p-6 space-y-5">
      <div v-if="stale" class="rounded-lg border border-amber-700/60 bg-amber-950/30 px-4 py-3 text-sm text-amber-200">
        话道刷新失败，当前数据为历史快照。挂断和转接操作已禁用。
      </div>

      <template v-if="activeSubTab === 'channels'">
        <div v-if="channels.length === 0" class="rounded-lg border border-slate-800 bg-[#131C31] px-6 py-16 text-center">
          <div class="font-bold text-white">{{ stale ? '当前无法确认话道状态' : '当前查询未返回活跃话道' }}</div>
          <p class="mt-2 text-sm text-slate-400">{{ stale ? '请恢复 Sidecar 连接后刷新。' : '该结果仅表示本次查询结果为空。' }}</p>
        </div>

        <article v-for="ch in channels" :key="ch.uuid" class="rounded-lg border border-slate-800 bg-[#131C31] p-5 space-y-4">
          <header class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800 pb-4">
            <div class="min-w-0">
              <div class="font-semibold text-white">话道 {{ ch.state || ch.callstate || 'UNKNOWN' }}</div>
              <div class="mt-1 truncate font-mono text-xs text-slate-400" :title="ch.uuid">{{ ch.uuid }}</div>
            </div>
            <div class="flex flex-wrap items-center gap-3">
              <span class="font-mono text-cyan-400">{{ formatDuration(ch.duration_sec) }}</span>
              <button :disabled="stale" class="rounded-md border border-indigo-800 bg-indigo-950 px-3 py-2 text-sm font-semibold text-indigo-300 disabled:opacity-40" @click="openTransfer(ch.uuid)">盲转</button>
              <button :disabled="stale" class="rounded-md border border-rose-800 bg-rose-950 px-3 py-2 text-sm font-semibold text-rose-300 disabled:opacity-40" @click="$emit('kill-channel', ch.uuid)">挂断</button>
            </div>
          </header>
          <div class="grid grid-cols-1 gap-4 text-sm md:grid-cols-2">
            <div class="rounded-lg border border-slate-800 bg-slate-900 p-4 space-y-2">
              <div>主叫：<span class="font-mono text-cyan-300">{{ ch.cid_num || ch.cid_name || '-' }}</span></div>
              <div>被叫：<span class="font-mono text-white">{{ ch.dest || ch.callee_num || '-' }}</span></div>
              <div class="truncate text-slate-400" :title="`${ch.application || ''} ${ch.application_data || ''}`">应用：{{ ch.application || '-' }} {{ ch.application_data || '' }}</div>
            </div>
            <div class="rounded-lg border border-slate-800 bg-slate-900 p-4 space-y-2">
              <div>远端地址：<span class="font-mono text-white">{{ ch.ip_addr || '-' }}</span></div>
              <div>媒体：<span class="font-mono text-cyan-300">{{ formatCodec(ch) }}</span></div>
              <div class="truncate text-slate-400" :title="ch.name">通道：{{ ch.name || '-' }}</div>
            </div>
          </div>
        </article>
      </template>

      <template v-else>
        <div v-if="calls.length === 0" class="rounded-lg border border-slate-800 bg-[#131C31] px-6 py-16 text-center text-sm text-slate-400">
          {{ stale ? '当前无法确认桥接关系' : '当前查询未返回桥接关系' }}
        </div>
        <article v-for="call in calls" :key="call.call_uuid" class="rounded-lg border border-slate-800 bg-[#131C31] p-5">
          <div class="truncate font-mono text-sm font-semibold text-cyan-300" :title="call.call_uuid">Call UUID: {{ call.call_uuid }}</div>
          <div class="mt-4 grid grid-cols-1 gap-3 font-mono text-sm md:grid-cols-[1fr_auto_1fr] md:items-center">
            <div class="truncate rounded-md bg-slate-900 px-3 py-2" :title="call.caller_uuid">{{ call.caller_uuid }}</div>
            <span class="text-slate-500">↔</span>
            <div class="truncate rounded-md bg-slate-900 px-3 py-2" :title="call.callee_uuid">{{ call.callee_uuid }}</div>
          </div>
        </article>
      </template>
    </div>

    <div v-if="transferUUID" class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      <div class="w-full max-w-md overflow-hidden rounded-lg border border-slate-800 bg-[#131C31]">
        <div class="flex items-center justify-between border-b border-slate-800 bg-[#0F172A] px-6 py-4">
          <span class="font-bold text-white">话道盲转</span>
          <button class="text-slate-400 hover:text-white" aria-label="关闭" @click="closeTransfer">×</button>
        </div>
        <div class="px-6 py-5">
          <label class="block text-sm text-slate-400">
            目标分机
            <input v-model.trim="targetExt" list="registered-extension-options" class="mt-2 w-full rounded-md border border-slate-700 bg-slate-900 px-4 py-3 font-mono text-white" placeholder="输入目标分机">
          </label>
          <datalist id="registered-extension-options">
            <option v-for="reg in registrations" :key="reg.reg_user" :value="reg.reg_user" />
          </datalist>
          <p v-if="transferError" class="mt-2 text-sm text-rose-400">{{ transferError }}</p>
        </div>
        <div class="flex justify-end gap-3 border-t border-slate-800 bg-[#0F172A] px-6 py-4">
          <button class="rounded-md bg-slate-800 px-4 py-2 text-sm text-slate-200" @click="closeTransfer">取消</button>
          <button class="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white" @click="submitTransfer">提交转接</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { Call, Channel, Registration } from '@/api/telephony'

defineProps<{
  channels: Channel[]
  calls: Call[]
  registrations: Registration[]
  stale: boolean
}>()

const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'kill-channel', uuid: string): void
  (e: 'transfer-channel', uuid: string, target: string): void
}>()

const activeSubTab = ref<'channels' | 'bridge'>('channels')
const subActive = 'bg-cyan-950 text-cyan-400 border border-cyan-800'
const subInactive = 'text-slate-400 hover:text-white'
const transferUUID = ref('')
const targetExt = ref('')
const transferError = ref('')

function openTransfer(uuid: string) {
  transferUUID.value = uuid
  targetExt.value = ''
  transferError.value = ''
}

function closeTransfer() {
  transferUUID.value = ''
  targetExt.value = ''
  transferError.value = ''
}

function submitTransfer() {
  if (!targetExt.value) {
    transferError.value = '请输入目标分机'
    return
  }
  emit('transfer-channel', transferUUID.value, targetExt.value)
  closeTransfer()
}

function formatDuration(sec?: number) {
  const total = Math.max(0, Math.floor(sec || 0))
  const hours = String(Math.floor(total / 3600)).padStart(2, '0')
  const minutes = String(Math.floor((total % 3600) / 60)).padStart(2, '0')
  const seconds = String(total % 60).padStart(2, '0')
  return `${hours}:${minutes}:${seconds}`
}

function formatCodec(channel: Channel) {
  if (!channel.read_codec) return '-'
  return channel.read_rate ? `${channel.read_codec} (${channel.read_rate}Hz)` : channel.read_codec
}
</script>
