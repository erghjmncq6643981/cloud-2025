<template>
  <div class="h-screen flex flex-col bg-[#0B1120] text-slate-200 overflow-hidden font-sans">
    <!-- 顶部导航栏 -->
    <Navbar 
      :status="status"
      @reload-xml="onReloadXml"
      @simulate-call="onSimulateCall"
    />

    <!-- 中间主体: 左侧 Tab + 右侧视图 -->
    <div class="flex-1 flex overflow-hidden">
      <Sidebar 
        v-model="currentTab"
        :reg-count="registrations.length"
        :channel-count="channels.length"
      />

      <main class="flex-1 flex flex-col overflow-hidden bg-[#0B1120]">
        <!-- 1. 概览大盘 -->
        <DashboardView 
          v-if="currentTab === 'dashboard'"
          :status="status"
          :registrations="registrations"
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
          :registrations="registrations"
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
          @refresh="fetchData"
          @simulate-call="onSimulateCall"
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
          @toast="showToast"
        />

        <!-- 7. FCC与原生module -->
        <ModulesView 
          v-else-if="currentTab === 'modules'"
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

const status = ref<Partial<SystemStatus>>({
  node_id: 'node-01',
  uptime: '7h 45m',
  channels: 0,
  max_sessions: 1000,
  pg_connected: true,
  fs_alive: true
})

const registrations = ref<Registration[]>([])
const channels = ref<Channel[]>([])
const calls = ref<Call[]>([])
const profiles = ref<SofiaProfile[]>([])
const gateways = ref<Gateway[]>([])

const toastVisible = ref(false)
const toastMessage = ref('')
let pollTimer: any = null

onMounted(() => {
  fetchData()
  pollTimer = setInterval(fetchData, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})

async function fetchData() {
  try {
    const [st, regs, chs, profs, gws] = await Promise.all([
      telephonyApi.getStatus(),
      telephonyApi.getRegistrations(),
      telephonyApi.getChannels(),
      telephonyApi.getProfiles(),
      telephonyApi.getGateways()
    ])
    status.value = st
    registrations.value = regs
    channels.value = chs.channels
    calls.value = chs.calls
    profiles.value = profs
    gateways.value = gws
  } catch (err) {
    // 允许离线或降级
  }
}

function showToast(msg: string) {
  toastMessage.value = msg
  toastVisible.value = true
  setTimeout(() => {
    toastVisible.value = false
  }, 2500)
}

async function onReloadXml() {
  showToast('正在执行 reloadxml ...')
  try {
    const res = await telephonyApi.reloadXml()
    showToast(`XML 拨号盘重载成功: ${res || '+OK'}`)
  } catch (err: any) {
    showToast(`重载失败: ${err.message || 'Error'}`)
  }
}

async function onSimulateCall() {
  showToast('正在向 FreeSWITCH 发起模拟呼叫 (1017 ➔ 1007)...')
  try {
    const cmd = 'originate user/1007 &bridge(user/1017)'
    await telephonyApi.executeCli(cmd)
    currentTab.value = 'channels'
    showToast('呼叫指令已执行，话道正在建立')
    fetchData()
  } catch (err: any) {
    showToast(`呼叫执行异常: ${err.message}`)
  }
}

async function onDialExt(ext: string) {
  showToast(`正在向分机 ${ext} 发起呼叫...`)
  try {
    await telephonyApi.executeCli(`originate user/${ext} &echo`)
    showToast(`分机 ${ext} 已振铃呼叫`)
    fetchData()
  } catch (err: any) {
    showToast(`呼叫失败: ${err.message}`)
  }
}

async function onQuickBridge(caller: string, callee: string) {
  showToast(`正在建立 Bridge: ${caller} ➔ ${callee}`)
  try {
    const cmd = `originate user/${callee} &bridge(user/${caller})`
    await telephonyApi.executeCli(cmd)
    currentTab.value = 'channels'
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
      showToast('通道已挂断，CDR 话单已保存')
      fetchData()
    } catch (err: any) {
      showToast(`挂断失败: ${err.message}`)
    }
  }
}

async function onTransferChannel(uuid: string, target: string) {
  try {
    await telephonyApi.transferChannel(uuid, target)
    showToast(`通话已成功转接至分机 ${target}`)
    fetchData()
  } catch (err: any) {
    showToast(`转接失败: ${err.message}`)
  }
}
</script>
