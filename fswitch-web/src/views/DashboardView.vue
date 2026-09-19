<template>
  <div class="flex-1 overflow-y-auto p-6 space-y-6">
    <div v-if="stale" class="rounded-lg border border-amber-700/60 bg-amber-950/30 px-4 py-3 text-sm text-amber-200">
      Sidecar 数据刷新失败，以下内容是最后一次成功快照，不能视为实时状态。
    </div>

    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <section class="rounded-lg border border-slate-800 bg-[#131C31] p-5">
        <div class="flex items-center justify-between text-sm text-slate-400">
          <span>FreeSWITCH</span>
          <span :class="status.fs_alive && !stale ? 'text-emerald-400' : 'text-amber-300'">
            {{ stale ? 'STALE' : status.fs_alive ? 'READY' : 'OFFLINE' }}
          </span>
        </div>
        <div class="mt-3 font-mono text-2xl font-bold text-white">{{ status.version || '版本未提供' }}</div>
        <div class="mt-3 text-sm text-slate-400">运行时间：{{ formatUptime(status.uptime) }}</div>
      </section>

      <section class="rounded-lg border border-slate-800 bg-[#131C31] p-5">
        <div class="text-sm text-slate-400">活跃话道</div>
        <div class="mt-3 flex items-baseline gap-2 font-mono">
          <span class="text-3xl font-bold text-cyan-400">{{ status.channels ?? 0 }}</span>
          <span class="text-sm text-slate-400">/ {{ status.max_sessions || '容量未提供' }}</span>
        </div>
        <div class="mt-3 text-sm text-slate-400">CPS {{ status.cps ?? 0 }} · 累计会话 {{ status.total_sessions ?? 0 }}</div>
      </section>

      <section class="rounded-lg border border-slate-800 bg-[#131C31] p-5">
        <div class="text-sm text-slate-400">已注册 SIP 终端</div>
        <div class="mt-3 font-mono text-3xl font-bold text-emerald-400">{{ registrations.length }}</div>
        <button class="mt-3 text-sm font-semibold text-cyan-400 hover:underline" @click="$emit('switch-tab', 'extensions')">查看分机</button>
      </section>

      <section class="rounded-lg border border-slate-800 bg-[#131C31] p-5">
        <div class="text-sm text-slate-400">PostgreSQL</div>
        <div class="mt-3 font-mono text-2xl font-bold" :class="status.pg_connected && !stale ? 'text-emerald-400' : 'text-amber-300'">
          {{ stale ? 'STALE' : status.pg_connected ? 'CONNECTED' : 'OFFLINE' }}
        </div>
        <div class="mt-3 truncate text-sm text-slate-400" :title="status.node_id || ''">节点：{{ status.node_id || '未提供' }}</div>
      </section>
    </div>

    <div class="grid grid-cols-1 gap-6 xl:grid-cols-3">
      <section class="space-y-5 rounded-lg border border-slate-800 bg-[#131C31] p-6 xl:col-span-2">
        <div class="flex items-center justify-between border-b border-slate-800 pb-4">
          <h2 class="font-bold text-white">SIP 注册终端</h2>
          <button class="text-sm font-semibold text-cyan-400 hover:underline" @click="$emit('switch-tab', 'extensions')">管理分机</button>
        </div>

        <div v-if="registrations.length === 0" class="py-10 text-center text-sm text-slate-400">
          {{ stale ? '当前无法确认注册终端状态' : '当前没有已注册终端' }}
        </div>
        <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <article v-for="reg in registrations" :key="`${reg.realm}:${reg.reg_user}`" class="rounded-lg border border-slate-800 bg-slate-900 p-4 space-y-3">
            <div class="flex items-center justify-between gap-3">
              <span class="truncate font-mono text-xl font-bold text-cyan-400">{{ reg.reg_user }}</span>
              <span class="text-xs font-semibold text-emerald-400">REGISTERED</span>
            </div>
            <div class="truncate text-sm font-semibold text-slate-200" :title="reg.user_agent || ''">{{ reg.user_agent || '终端信息未提供' }}</div>
            <div class="space-y-1 text-xs text-slate-400">
              <div class="truncate" :title="`${reg.network_ip}:${reg.network_port}`">{{ reg.network_ip || '-' }}:{{ reg.network_port || '-' }}</div>
              <div>{{ reg.network_proto || '-' }} · 租期 {{ reg.remaining_seconds }}s</div>
            </div>
            <div class="flex gap-2">
              <button :disabled="stale" class="flex-1 rounded-md border border-cyan-800 bg-cyan-950 px-3 py-2 text-sm font-semibold text-cyan-300 disabled:cursor-not-allowed disabled:opacity-40" @click="$emit('dial-ext', reg.reg_user)">回声测试</button>
              <button class="rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-slate-200" @click="$emit('view-detail', reg)">详情</button>
            </div>
          </article>
        </div>

        <div class="flex flex-wrap items-end gap-3 rounded-lg border border-slate-800 bg-slate-900 p-4">
          <label class="min-w-44 flex-1 text-xs text-slate-400">
            主叫分机
            <select v-model="quickCaller" class="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 font-mono text-sm text-white">
              <option value="">请选择</option>
              <option v-for="reg in registrations" :key="`caller-${reg.reg_user}`" :value="reg.reg_user">{{ reg.reg_user }}</option>
            </select>
          </label>
          <label class="min-w-44 flex-1 text-xs text-slate-400">
            被叫分机
            <select v-model="quickCallee" class="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 font-mono text-sm text-white">
              <option value="">请选择</option>
              <option v-for="reg in registrations" :key="`callee-${reg.reg_user}`" :value="reg.reg_user">{{ reg.reg_user }}</option>
            </select>
          </label>
          <button :disabled="!canBridge" class="rounded-md bg-emerald-600 px-4 py-2 text-sm font-bold text-white disabled:cursor-not-allowed disabled:opacity-40" @click="$emit('quick-bridge', quickCaller, quickCallee)">
            建立桥接
          </button>
        </div>
      </section>

      <section class="space-y-4 rounded-lg border border-slate-800 bg-[#131C31] p-6">
        <div class="flex items-center justify-between border-b border-slate-800 pb-4">
          <h2 class="font-bold text-white">Sofia Profiles</h2>
          <button class="text-sm font-semibold text-cyan-400 hover:underline" @click="$emit('switch-tab', 'sip')">查看详情</button>
        </div>
        <div v-if="profiles.length === 0" class="py-8 text-center text-sm text-slate-400">Sidecar 未返回 Profile 数据</div>
        <article v-for="profile in profiles" :key="profile.name" class="rounded-lg border border-slate-800 bg-slate-900 p-4 text-sm">
          <div class="flex items-center justify-between gap-3">
            <span class="font-mono font-bold text-white">{{ profile.name }}</span>
            <span class="font-mono text-xs text-cyan-400">{{ profile.state || 'UNKNOWN' }}</span>
          </div>
          <div class="mt-3 space-y-1 text-slate-400">
            <div class="truncate" :title="profile.bind_ip">监听：{{ profile.bind_ip || '未提供' }}<span v-if="profile.sip_port">:{{ profile.sip_port }}</span></div>
            <div>Context：{{ profile.context || '未提供' }}</div>
            <div class="truncate" :title="profile.codecs">Codecs：{{ profile.codecs || '未提供' }}</div>
          </div>
        </article>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Registration, SofiaProfile, SystemStatus } from '@/api/telephony'

const props = defineProps<{
  status: Partial<SystemStatus>
  registrations: Registration[]
  profiles: SofiaProfile[]
  stale: boolean
}>()

defineEmits<{
  (e: 'switch-tab', tab: string): void
  (e: 'dial-ext', ext: string): void
  (e: 'view-detail', reg: Registration): void
  (e: 'quick-bridge', caller: string, callee: string): void
}>()

const quickCaller = ref('')
const quickCallee = ref('')

watch(
  () => props.registrations.map(item => item.reg_user),
  users => {
    if (!users.includes(quickCaller.value)) quickCaller.value = users[0] || ''
    if (!users.includes(quickCallee.value) || quickCallee.value === quickCaller.value) {
      quickCallee.value = users.find(user => user !== quickCaller.value) || ''
    }
  },
  { immediate: true }
)

const canBridge = computed(() => Boolean(
  !props.stale && quickCaller.value && quickCallee.value && quickCaller.value !== quickCallee.value
))

function formatUptime(uptime?: string) {
  return uptime?.trim() || '未提供'
}
</script>
