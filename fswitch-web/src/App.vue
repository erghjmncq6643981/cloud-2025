<template>
  <div class="h-screen flex flex-col bg-[#0B1120] text-slate-200 overflow-hidden font-sans">
    <!-- 顶部导航栏 -->
    <Navbar 
      :status="status"
      :stale="stale"
      @reload-xml="onReloadXml"
    />

    <!-- 中间主体: 左侧 Tab + 右侧视图 -->
    <div class="flex-1 flex overflow-hidden">
      <Sidebar 
        v-model="currentTab"
        :reg-count="registrations.length"
        :channel-count="channels.length"
      />

      <main class="flex-1 flex flex-col overflow-hidden bg-[#0B1120]">
        <div v-if="stale" role="status" class="shrink-0 border-b border-amber-800 bg-amber-950/40 px-4 py-2 text-sm text-amber-200">
          数据尚未获取或刷新失败，当前快照不能代表实时状态。
        </div>
        <!-- 1. 概览大盘 -->
        <DashboardView 
          v-if="currentTab === 'dashboard'"
          :status="status"
          :registrations="registrations"
          :profiles="profiles"
          :stale="stale"
          @switch-tab="currentTab = $event"
          @dial-ext="onDialExt"
          @view-detail="onViewDetail"
          @quick-bridge="onQuickBridge"
        />

        <!-- 2. 话单记录CDR -->
        <CdrView 
          v-else-if="currentTab === 'cdr'"
          @toast="showToast"
        />

        <!-- 3. SIP 分机与注册 -->
        <ExtensionsView 
          v-else-if="currentTab === 'extensions'"
          @refresh="fetchData"
          @dial-ext="onDialExt"
          @flush-reg="onFlushReg"
          @toast="showToast"
        />

        <!-- 4. 活跃话道与通话 -->
        <ChannelsView 
          v-else-if="currentTab === 'channels'"
          :channels="channels"
          :calls="calls"
          :registrations="registrations"
          :stale="stale"
          @refresh="fetchData"
          @kill-channel="onKillChannel"
          @transfer-channel="onTransferChannel"
        />

        <!-- 5. SIP 网络配置 -->
        <SipProfilesView 
          v-else-if="currentTab === 'sip'"
          :profiles="profiles"
          @toast="showToast"
        />

        <!-- 6. 网关 -->
        <GatewaysView 
          v-else-if="currentTab === 'gateways'"
          :gateways="gateways"
          :stale="stale"
          @refresh="fetchData"
          @toast="showToast"
        />

        <!-- 7. FCC与原生module -->
        <ModulesView 
          v-else-if="currentTab === 'modules'"
          :status="status"
          :stale="stale"
          @toast="showToast"
        />

        <!-- 8. fs_cli 终端与日志 -->
        <TerminalView 
          v-else-if="currentTab === 'terminal'"
          @toast="showToast"
        />
      </main>
    </div>

    <!-- 浮动通知 Toast -->
    <div 
      :class="toastVisible ? 'translate-y-0 opacity-100' : 'translate-y-20 opacity-0 pointer-events-none'"
      class="fixed bottom-5 right-5 bg-slate-900 border border-cyan-500 text-white text-xs px-4 py-2.5 rounded-xl shadow-2xl flex items-center gap-2 transform transition-all duration-300 z-50"
    >
      <span class="w-2 h-2 rounded-full bg-cyan-400 animate-ping"></span>
      <span>{{ toastMessage }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import Navbar from './components/Navbar.vue'
import Sidebar from './components/Sidebar.vue'
import DashboardView from './views/DashboardView.vue'
import ExtensionsView from './views/ExtensionsView.vue'
import ChannelsView from './views/ChannelsView.vue'
import SipProfilesView from './views/SipProfilesView.vue'
import GatewaysView from './views/GatewaysView.vue'
import ModulesView from './views/ModulesView.vue'
import TerminalView from './views/TerminalView.vue'
import CdrView from './views/CdrView.vue'

import { telephonyApi } from './api/telephony'
import type { SystemStatus, Registration, Channel, Call, SofiaProfile, Gateway } from './api/telephony'

const getInitialTab = () => {
  if (typeof window !== 'undefined') {
    const urlParams = new URLSearchParams(window.location.search)
    const tabParam = urlParams.get('tab')
    if (tabParam) return tabParam
    const hash = window.location.hash.replace('#', '')
    if (hash) return hash
  }
  return 'dashboard'
}

const currentTab = ref(getInitialTab())

const status = ref<Partial<SystemStatus>>({})

const registrations = ref<Registration[]>([])
const channels = ref<Channel[]>([])
const calls = ref<Call[]>([])
const profiles = ref<SofiaProfile[]>([])
const gateways = ref<Gateway[]>([])

const toastVisible = ref(false)
const toastMessage = ref('')
let pollTimer: ReturnType<typeof setTimeout> | null = null
let toastTimer: ReturnType<typeof setTimeout> | null = null
let disposed = false
let fetching = false
const stale = ref(true)

onMounted(() => {
  poll()
})

onUnmounted(() => {
  disposed = true
  if (pollTimer) clearTimeout(pollTimer)
  if (toastTimer) clearTimeout(toastTimer)
})

async function poll() {
  await fetchData()
  if (!disposed) pollTimer = setTimeout(poll, 3000)
}

async function fetchData() {
  if (fetching || disposed) return
  fetching = true
  try {
    const [st, regs, chs, profs, gws] = await Promise.all([
      telephonyApi.getStatus(),
      telephonyApi.getRegistrations(),
      telephonyApi.getChannels(),
      telephonyApi.getProfiles(),
      telephonyApi.getGateways()
    ])
    if (disposed) return
    status.value = st
    registrations.value = regs
    channels.value = chs.channels
    calls.value = chs.calls
    profiles.value = profs
    gateways.value = gws
    stale.value = !st.fs_alive || !st.pg_connected
  } catch (err: any) {
    stale.value = true
    showToast(`刷新失败，当前显示最后一次成功数据：${err.message || '连接异常'}`)
  } finally {
    fetching = false
  }
}

function showToast(msg: string) {
  toastMessage.value = msg
  toastVisible.value = true
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastVisible.value = false
  }, 2500)
}

async function onReloadXml() {
  showToast('正在执行 reloadxml ...')
  try {
    const res = await telephonyApi.reloadXml()
    showToast(res ? `reloadxml 返回：${res}` : 'reloadxml 指令已受理')
  } catch (err: any) {
    showToast(`重载失败: ${err.message || 'Error'}`)
  }
}

async function onDialExt(ext: string) {
  showToast(`正在向分机 ${ext} 发起呼叫...`)
  try {
    await telephonyApi.dialEcho(ext)
    showToast(`分机 ${ext} 测试呼叫指令已受理，最终状态以话道事件为准`)
    fetchData()
  } catch (err: any) {
    showToast(`呼叫失败: ${err.message}`)
  }
}

async function onQuickBridge(caller: string, callee: string) {
  showToast(`正在建立 Bridge: ${caller} ➔ ${callee}`)
  try {
    await telephonyApi.bridgeExtensions(caller, callee)
    currentTab.value = 'channels'
    showToast('桥接指令已受理，最终状态以话道事件为准')
    fetchData()
  } catch (err: any) {
    showToast(`Bridge 失败: ${err.message}`)
  }
}

function onViewDetail(reg: Registration) {
  currentTab.value = 'extensions'
}

async function onFlushReg(ext: string) {
  if (confirm(`确认强制踢掉分机 ${ext} 的注册吗？`)) {
    try {
      await telephonyApi.flushRegistration(ext)
      showToast(`分机 ${ext} 注册已注销`)
      fetchData()
    } catch (err: any) {
      showToast(`注销失败: ${err.message}`)
    }
  }
}

async function onKillChannel(uuid: string) {
  if (confirm('确认强拆挂断指定通话话道吗？')) {
    try {
      await telephonyApi.killChannel(uuid)
      showToast('挂断指令已受理，最终状态与 CDR 以 FreeSWITCH 事件为准')
      fetchData()
    } catch (err: any) {
      showToast(`挂断失败: ${err.message}`)
    }
  }
}

async function onTransferChannel(uuid: string, target: string) {
  try {
    await telephonyApi.transferChannel(uuid, target)
    showToast(`转接指令已受理，目标分机 ${target} 的最终状态以话道事件为准`)
    fetchData()
  } catch (err: any) {
    showToast(`转接失败: ${err.message}`)
  }
}
</script>
