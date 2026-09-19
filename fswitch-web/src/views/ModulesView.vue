<template>
  <div class="flex-1 flex flex-col overflow-hidden">
    <div class="shrink-0 border-b border-slate-800 bg-[#0F172A] px-6 py-4 flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-2 rounded-lg border border-slate-800 bg-slate-900 p-1 text-sm font-semibold">
        <button :class="activeSub === 'contract' ? subActive : subInactive" class="rounded-md px-4 py-2" @click="activeSub = 'contract'">Sidecar 契约</button>
        <button :class="activeSub === 'modules' ? subActive : subInactive" class="rounded-md px-4 py-2" @click="activeSub = 'modules'">FreeSWITCH 模块</button>
      </div>
      <button class="rounded-md border border-slate-700 bg-slate-800 px-4 py-2 text-sm font-semibold text-slate-200 hover:bg-slate-700" @click="reloadXml">重载 XML 配置</button>
    </div>

    <div class="flex-1 overflow-y-auto p-6 space-y-5">
      <div v-if="stale" class="rounded-lg border border-amber-700/60 bg-amber-950/30 px-4 py-3 text-sm text-amber-200">
        节点状态刷新失败，以下节点信息为历史快照。
      </div>

      <template v-if="activeSub === 'contract'">
        <section class="rounded-lg border border-slate-800 bg-[#131C31] p-5">
          <div class="grid grid-cols-1 gap-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
            <div><div class="text-slate-500">节点 ID</div><div class="mt-1 truncate font-mono text-white" :title="status.node_id || ''">{{ status.node_id || '未提供' }}</div></div>
            <div><div class="text-slate-500">节点状态</div><div class="mt-1 font-mono text-white">{{ status.node_state || 'UNKNOWN' }}</div></div>
            <div><div class="text-slate-500">ESL</div><div class="mt-1 font-mono" :class="status.fs_alive && !stale ? 'text-emerald-400' : 'text-amber-300'">{{ stale ? 'STALE' : status.fs_alive ? 'CONNECTED' : 'OFFLINE' }}</div></div>
            <div><div class="text-slate-500">PostgreSQL</div><div class="mt-1 font-mono" :class="status.pg_connected && !stale ? 'text-emerald-400' : 'text-amber-300'">{{ stale ? 'STALE' : status.pg_connected ? 'CONNECTED' : 'OFFLINE' }}</div></div>
          </div>
        </section>

        <section class="overflow-hidden rounded-lg border border-slate-800 bg-[#131C31]">
          <div class="border-b border-slate-800 px-5 py-4">
            <h2 class="font-bold text-white">事件协议映射</h2>
            <p class="mt-1 text-sm text-slate-400">下表描述当前代码契约，不代表某个运行节点已实际产生这些事件。</p>
          </div>
          <div class="overflow-x-auto">
            <table class="w-full min-w-[760px] text-left text-sm">
              <thead class="bg-slate-900 text-xs text-slate-400">
                <tr><th class="px-4 py-3">类别</th><th class="px-4 py-3">ESL 事件</th><th class="px-4 py-3">标准事件</th><th class="px-4 py-3">NATS Subject</th></tr>
              </thead>
              <tbody class="divide-y divide-slate-800">
                <tr v-for="event in eventMatrix" :key="event.method">
                  <td class="px-4 py-3 text-white">{{ event.name }}</td>
                  <td class="px-4 py-3 font-mono text-xs text-cyan-300">{{ event.esl }}</td>
                  <td class="px-4 py-3 font-mono text-xs text-emerald-400">{{ event.method }}</td>
                  <td class="px-4 py-3 font-mono text-xs text-slate-300">{{ event.subject }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>

      <section v-else class="rounded-lg border border-slate-800 bg-[#131C31] px-6 py-16 text-center">
        <h2 class="font-bold text-white">运行时模块状态不可用</h2>
        <p class="mt-2 text-sm text-slate-400">Sidecar 当前没有提供结构化模块查询接口，因此本页不推断模块是否已加载。</p>
        <button class="mt-5 text-sm font-semibold text-cyan-400 hover:underline" @click="openTerminal">通过控制台查询</button>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { SystemStatus } from '@/api/telephony'
import { telephonyApi } from '@/api/telephony'

defineProps<{
  status: Partial<SystemStatus>
  stale: boolean
}>()

const emit = defineEmits<{
  (e: 'toast', msg: string): void
}>()

const activeSub = ref<'contract' | 'modules'>('contract')
const subActive = 'bg-cyan-950 text-cyan-400 border border-cyan-800'
const subInactive = 'text-slate-400 hover:text-white'

const eventMatrix = [
  { name: '话道生命周期', esl: 'CHANNEL_CREATE / ANSWER / HANGUP_COMPLETE / DESTROY', method: 'Event.Channel', subject: 'fs.event.{nodeId}.channel' },
  { name: '桥接', esl: 'CHANNEL_BRIDGE / CHANNEL_UNBRIDGE', method: 'Event.Channel', subject: 'fs.event.{nodeId}.channel' },
  { name: '录音', esl: 'RECORD_START / RECORD_STOP', method: 'Event.Recording', subject: 'fs.event.{nodeId}.record' },
  { name: '分机注册', esl: 'sofia::register / unregister / expire', method: 'Event.Registration', subject: 'fs.event.{nodeId}.registration' },
  { name: '网关', esl: 'sofia::gateway_state', method: 'Event.Gateway', subject: 'fs.event.{nodeId}.gateway' },
  { name: 'DTMF', esl: 'DTMF', method: 'Event.DTMF', subject: 'fs.event.{nodeId}.dtmf' }
]

async function reloadXml() {
  emit('toast', '正在发送 reloadxml 指令...')
  try {
    const result = await telephonyApi.reloadXml()
    emit('toast', result ? `reloadxml 返回：${result}` : 'reloadxml 指令已受理')
  } catch (error: any) {
    emit('toast', `reloadxml 失败：${error.message || '连接异常'}`)
  }
}

function openTerminal() {
  window.location.hash = 'terminal'
  window.location.reload()
}
</script>
