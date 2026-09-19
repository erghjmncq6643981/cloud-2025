<template>
  <div class="flex-1 flex flex-col overflow-hidden">
    <!-- 头部子 Tab -->
    <div class="bg-[#0F172A] border-b border-slate-800 px-6 py-4 flex items-center justify-between shrink-0">
      <div class="flex items-center gap-2 bg-slate-900 p-1.5 rounded-xl border border-slate-800 text-base font-semibold">
        <button 
          @click="activeSub = 'fcc-governance'"
          :class="activeSub === 'fcc-governance' ? subActive : subInactive"
          class="px-5 py-2.5 rounded-lg transition"
        >
          FCC 守护网关与治理配置 (Sidecar Governance)
        </button>
        <button 
          @click="activeSub = 'fs-modules'"
          :class="activeSub === 'fs-modules' ? subActive : subInactive"
          class="px-5 py-2.5 rounded-lg transition"
        >
          FreeSWITCH 原生模块矩阵 (Core Modules)
        </button>
      </div>

      <div class="flex items-center gap-3">
        <button 
          @click="reloadAllModules"
          class="bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm px-4 py-2.5 rounded-xl border border-slate-700 font-semibold transition cursor-pointer"
        >
          重载模块配置
        </button>
      </div>
    </div>

    <!-- 面板 1: FCC 守护网关与治理配置 -->
    <div v-if="activeSub === 'fcc-governance'" class="flex-1 p-6 overflow-y-auto space-y-6">
      <!-- 节点概况卡片 -->
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl p-6 shadow-lg space-y-5">
        <div class="flex flex-wrap justify-between items-center border-b border-slate-800 pb-4 gap-3">
          <div class="flex items-center gap-3">
            <span class="w-3.5 h-3.5 rounded-full bg-emerald-400 animate-pulse"></span>
            <span class="font-extrabold text-white text-lg">FCC Sidecar Agent 节点参数 (:8088)</span>
            <span class="bg-cyan-950 text-cyan-400 border border-cyan-800/80 text-xs px-3 py-1 rounded font-mono font-bold">GO CLOUD-NATIVE</span>
          </div>
          <span class="text-sm text-slate-400 font-mono font-medium">NODE_ID: qiandingjundeMacBook-Pro.local</span>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-5 text-base font-mono">
          <div class="bg-slate-900 p-5 rounded-xl border border-slate-800 space-y-3">
            <div class="flex justify-between"><span class="text-slate-400 font-sans font-medium">NATS 总线连接:</span><span class="text-white font-bold">nats://127.0.0.1:4222</span></div>
            <div class="flex justify-between"><span class="text-slate-400 font-sans font-medium">FS ESL 监听端口:</span><span class="text-cyan-400 font-bold">127.0.0.1:8021</span></div>
            <div class="flex justify-between"><span class="text-slate-400 font-sans font-medium">HTTP 管理端口:</span><span class="text-white font-bold">:8088</span></div>
            <div class="flex justify-between"><span class="text-slate-400 font-sans font-medium">PostgreSQL DSN:</span><span class="text-emerald-400 font-bold">freeswitch:5432 (OK)</span></div>
          </div>

          <div class="bg-slate-900 p-5 rounded-xl border border-slate-800 space-y-3">
            <div class="flex justify-between"><span class="text-slate-400 font-sans font-medium">最大并发通道 (Max Channels):</span><span class="text-white font-bold">1000</span></div>
            <div class="flex justify-between"><span class="text-slate-400 font-sans font-medium">心跳上报频率 (Heartbeat):</span><span class="text-white font-bold">3 秒 / 次</span></div>
            <div class="flex justify-between"><span class="text-slate-400 font-sans font-medium">熔断排空保护 (Call Draining):</span><span class="text-emerald-400 font-bold">ENABLED</span></div>
            <div class="flex justify-between"><span class="text-slate-400 font-sans font-medium">事件清洗器 (Normalizer):</span><span class="text-cyan-400 font-bold">10大类核心通信事件</span></div>
          </div>
        </div>
      </div>

      <!-- NATS 与事件订阅表 (完整 10 类事件流矩阵) -->
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl overflow-hidden shadow-lg">
        <div class="px-6 py-4 border-b border-slate-800 flex flex-wrap items-center justify-between gap-3 bg-slate-900/60">
          <div class="flex items-center gap-3">
            <span class="text-base font-bold text-white tracking-wide">FCC 实时事件流与 NATS 广播映射矩阵</span>
            <span class="bg-cyan-950 text-cyan-400 border border-cyan-800/80 text-xs px-2.5 py-0.5 rounded font-bold font-sans">
              电信级全链路事件体系 (8+ 核心矩阵)
            </span>
          </div>
          <span class="text-sm text-slate-400">双向驱动控制面状态机流转、话务路由、坐席工作台推屏与质量审计</span>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full text-left text-sm border-collapse">
            <thead class="bg-slate-900/95 text-slate-300 border-b border-slate-800 text-xs font-bold uppercase tracking-wider">
              <tr>
                <th class="py-3.5 px-4 align-middle whitespace-nowrap">业务分类</th>
                <th class="py-3.5 px-4 align-middle whitespace-nowrap">FreeSWITCH ESL 底层事件源</th>
                <th class="py-3.5 px-4 align-middle whitespace-nowrap">标准化 RPC 方法</th>
                <th class="py-3.5 px-4 align-middle whitespace-nowrap">NATS 广播主题 (Subject Pattern)</th>
                <th class="py-3.5 px-4 align-middle">业务场景与流转价值</th>
                <th class="py-3.5 px-4 align-middle text-center whitespace-nowrap">状态</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800/80 font-sans">
              <tr v-for="evt in eventMatrix" :key="evt.name" class="hover:bg-slate-800/40 transition">
                <td class="py-3 px-4 align-middle whitespace-nowrap">
                  <div class="flex items-center gap-2 font-bold text-white text-sm">
                    <span :class="evt.dotClass" class="w-2.5 h-2.5 rounded-full shrink-0"></span>
                    <span>{{ evt.name }}</span>
                  </div>
                </td>
                <td class="py-3 px-4 align-middle font-mono text-cyan-300 text-xs whitespace-nowrap">{{ evt.eslEvents }}</td>
                <td class="py-3 px-4 align-middle font-mono text-emerald-400 font-bold text-xs whitespace-nowrap">{{ evt.rpcMethod }}</td>
                <td class="py-3 px-4 align-middle font-mono text-slate-300 text-xs whitespace-nowrap">{{ evt.subject }}</td>
                <td class="py-3 px-4 align-middle text-slate-300 text-xs leading-relaxed min-w-[280px]">{{ evt.purpose }}</td>
                <td class="py-3 px-4 align-middle text-center whitespace-nowrap">
                  <span class="bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 text-xs px-2.5 py-0.5 rounded font-extrabold font-mono">
                    {{ evt.status }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 面板 2: FreeSWITCH 原生模块矩阵 -->
    <div v-else-if="activeSub === 'fs-modules'" class="flex-1 p-6 overflow-y-auto space-y-5">
      <div class="text-base text-slate-300 font-semibold">FreeSWITCH 核心软交换加载模块矩阵清单 (mod_*) :</div>
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl overflow-hidden shadow-lg text-sm font-sans">
        <table class="w-full text-left">
          <thead class="bg-slate-900/95 text-slate-300 border-b border-slate-800 text-xs font-bold uppercase tracking-wider">
            <tr>
              <th class="py-4 px-6">模块名称 (Module)</th>
              <th class="py-4 px-6">分类</th>
              <th class="py-4 px-6">模块职责与功能</th>
              <th class="py-4 px-6">运行状态</th>
              <th class="py-4 px-6 text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-800/80">
            <tr v-for="mod in modulesList" :key="mod.name" class="hover:bg-slate-800/40 transition">
              <td class="py-4 px-6 font-bold text-cyan-400 font-mono text-base">{{ mod.name }}</td>
              <td class="py-4 px-6 text-slate-300 font-medium">{{ mod.category }}</td>
              <td class="py-4 px-6 text-slate-200 text-sm leading-relaxed">{{ mod.desc }}</td>
              <td class="py-4 px-6">
                <span class="bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 px-3 py-1 rounded-md text-xs font-extrabold font-mono">
                  {{ mod.status }}
                </span>
              </td>
              <td class="py-4 px-6 text-right space-x-3 text-sm">
                <button @click="reloadSingleModule(mod.name)" class="text-cyan-400 hover:underline font-semibold cursor-pointer">重载</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { telephonyApi } from '@/api/telephony'

const emit = defineEmits<{
  (e: 'toast', msg: string): void
}>()

const activeSub = ref<'fcc-governance' | 'fs-modules'>('fcc-governance')
const subActive = 'bg-cyan-950 text-cyan-400 border border-cyan-800/80 shadow-md font-bold'
const subInactive = 'text-slate-400 hover:text-white font-medium'

const eventMatrix = ref([
  {
    name: '呼叫生命周期',
    dotClass: 'bg-emerald-400 animate-pulse',
    eslEvents: 'CHANNEL_CREATE, ANSWER, HANGUP_COMPLETE, DESTROY',
    rpcMethod: 'Event.Channel (START / ANSWERED / DESTROY)',
    subject: 'fs.event.{nodeId}.channel',
    purpose: '驱动底层状态机流转、主被叫号码识别、接听/未接听状态归一化结算及 CDR 话单入库',
    status: 'ACTIVE'
  },
  {
    name: '早期媒体与振铃',
    dotClass: 'bg-amber-400',
    eslEvents: 'CHANNEL_PROGRESS, CHANNEL_PROGRESS_MEDIA',
    rpcMethod: 'Event.Channel (RINGING / MEDIA)',
    subject: 'fs.event.{nodeId}.channel',
    purpose: '180 振铃工作台来电动画/弹屏、183 早期媒体彩铃播放与 AMD (空号/停机/忙音) 前置拦截探测',
    status: 'ACTIVE'
  },
  {
    name: '通道桥接与驻留',
    dotClass: 'bg-cyan-400',
    eslEvents: 'CHANNEL_PARK, CHANNEL_BRIDGE, CHANNEL_UNBRIDGE',
    rpcMethod: 'Event.Channel (READY / BRIDGE / UNBRIDGE)',
    subject: 'fs.event.{nodeId}.channel',
    purpose: '双向外呼双方对齐自动 Bridge、坐席呼叫转接解绑重桥接、IVR 静默等待与流转控制',
    status: 'ACTIVE'
  },
  {
    name: '呼叫保持与恢复',
    dotClass: 'bg-indigo-400',
    eslEvents: 'CHANNEL_HOLD, CHANNEL_UNHOLD',
    rpcMethod: 'Event.Channel (HOLD / UNHOLD)',
    subject: 'fs.event.{nodeId}.channel',
    purpose: '客服咨询专家或查单时执行通话保持、工作台保持态高亮、通话时长与保持时长精细化统计',
    status: 'ACTIVE'
  },
  {
    name: '录音生命周期',
    dotClass: 'bg-rose-400',
    eslEvents: 'RECORD_START, RECORD_STOP',
    rpcMethod: 'Event.Record (START / STOP)',
    subject: 'fs.event.{nodeId}.record',
    purpose: '录音文件落盘完整审计、质检双轨录音关联、真实录音秒数/采样率校验及云存储异步上传',
    status: 'ACTIVE'
  },
  {
    name: '分机接入与过期',
    dotClass: 'bg-purple-400',
    eslEvents: 'CUSTOM sofia::register, unregister, expire',
    rpcMethod: 'Event.Registration (REG / UNREG / EXPIRED)',
    subject: 'fs.event.{nodeId}.registration',
    purpose: '话机/软电话在线状态毫秒感知、断网断电租约过期自动剔除、排队路由防空投无效分机',
    status: 'ACTIVE'
  },
  {
    name: '中继网关链路探活',
    dotClass: 'bg-orange-400',
    eslEvents: 'CUSTOM sofia::gateway_state, gateway_add, del',
    rpcMethod: 'Event.Gateway (UP / DOWN / FAIL)',
    subject: 'fs.event.{nodeId}.gateway',
    purpose: '运营商 SIP Trunk 故障/闪断/掉线秒级感知，企微高危报警，自动熔断避险与灾备中继热切',
    status: 'ACTIVE'
  },
  {
    name: '按键收号与交互',
    dotClass: 'bg-blue-400',
    eslEvents: 'DTMF, CHANNEL_EXECUTE_COMPLETE (play_and_get_digits)',
    rpcMethod: 'Event.DTMF (DIGIT / BATCH)',
    subject: 'fs.event.{nodeId}.dtmf',
    purpose: 'IVR 多级导航按键选择分支、自动外呼意向按键确认、通话挂断后满意度评分实时打分',
    status: 'ACTIVE'
  },
  {
    name: '现场质检与监管',
    dotClass: 'bg-teal-400',
    eslEvents: 'CUSTOM eavesdrop::start/stop, conference::maint',
    rpcMethod: 'Event.Supervision (MONITOR / WHISPER / BARGE)',
    subject: 'fs.event.{nodeId}.supervision',
    purpose: '班长席静默监听、教练模式耳语指导新员工、强插三方会话协助处理纠纷及强拆恶意通话',
    status: 'ACTIVE'
  },
  {
    name: '节点治理与心跳',
    dotClass: 'bg-emerald-400',
    eslEvents: 'HEARTBEAT / Status Up Ping (3s Ticker)',
    rpcMethod: 'NodeStatusSnapshot (HEALTHY / DRAINING)',
    subject: 'fs.status.{nodeId}.heartbeat',
    purpose: '高可用集群动态节点注册、实时并发话道与 CPS 负载均衡、假死故障实例自动驱逐',
    status: 'ACTIVE'
  }
])

const modulesList = ref([
  { name: 'mod_sofia', category: 'Endpoint', desc: 'SIP 协议栈引擎，管理分机注册、NAT 穿透与呼叫会话', status: 'LOADED' },
  { name: 'mod_pgsql', category: 'Database', desc: 'PostgreSQL 核心原生数据库连接驱动，挂载 core-db', status: 'LOADED' },
  { name: 'mod_event_socket', category: 'Interface', desc: 'Inbound/Outbound ESL 控制信道，对外提供 8021 端口', status: 'LOADED' },
  { name: 'mod_conference', category: 'Application', desc: '多方语音会议室与实时混音引擎', status: 'LOADED' },
  { name: 'mod_dptools', category: 'Dialplan', desc: 'XML 拨号计划控制工具集 (bridge, echo, answer, park)', status: 'LOADED' },
  { name: 'mod_opus', category: 'Codec', desc: '高清语音 Opus 音频编解码协商模块', status: 'LOADED' },
  { name: 'mod_commands', category: 'Core', desc: 'FreeSWITCH 核心原生 API 控制指令集 (originate, status, kill)', status: 'LOADED' }
])

async function reloadAllModules() {
  emit('toast', '正在重新载入 FreeSWITCH 核心配置与 XML ...')
  try {
    const res = await telephonyApi.reloadXml()
    emit('toast', `核心配置重载成功: ${res || '+OK'}`)
  } catch (err: any) {
    emit('toast', `重载失败: ${err.message || 'Error'}`)
  }
}

async function reloadSingleModule(modName: string) {
  emit('toast', `正在向 FreeSWITCH 发送重载模块指令: reload ${modName} ...`)
  try {
    const res = await telephonyApi.executeCli(`reload ${modName}`)
    emit('toast', `模块 ${modName} 重载完成: ${res || '+OK'}`)
  } catch (err: any) {
    emit('toast', `模块 ${modName} 重载失败: ${err.message || 'Error'}`)
  }
}
</script>
