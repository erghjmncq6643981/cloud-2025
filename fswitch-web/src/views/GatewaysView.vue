<template>
  <div class="flex-1 flex flex-col overflow-hidden">
    <!-- 顶部标题与操作栏 -->
    <div class="bg-[#0F172A] border-b border-slate-800 px-6 py-4 flex items-center justify-between gap-4 shrink-0">
      <div class="flex items-center gap-4 min-w-0">
        <span class="text-lg font-bold text-white tracking-wide shrink-0">网关 (Gateways)</span>
        <span class="text-sm text-slate-400 hidden xl:inline truncate">运营商 SIP 互联中继，管理对接电信/联通/移动 IMS 专线与第三方 SIP Trunk</span>
      </div>
      <div class="flex items-center gap-3 shrink-0">
        <button 
          @click="showGuide = !showGuide"
          class="bg-slate-800 hover:bg-slate-700 text-cyan-300 text-sm px-4 py-2.5 rounded-xl border border-slate-700 font-semibold transition flex items-center gap-1.5 shrink-0 whitespace-nowrap"
        >
          <span>{{ showGuide ? '▲ 收起对接规范' : '📖 展开对接规范与参数解读' }}</span>
        </button>
        <button 
          @click="openAddModal"
          class="bg-cyan-600 hover:bg-cyan-500 text-white text-sm px-5 py-2.5 rounded-xl font-bold transition flex items-center gap-2 shadow-lg shadow-cyan-950 shrink-0 whitespace-nowrap"
        >
          <span>+ 添加网关</span>
        </button>
      </div>
    </div>

    <!-- 主体内容区 -->
    <div class="flex-1 p-6 overflow-y-auto space-y-5">
      <!-- 运营商对接参数专家解读与规范指南 (可展开/折叠面板) -->
      <div v-if="showGuide" class="bg-[#131C31] border border-cyan-900/60 rounded-2xl p-5 shadow-xl space-y-4">
        <div class="flex items-center justify-between border-b border-slate-800 pb-3">
          <div class="flex items-center gap-2 text-cyan-400 font-bold text-base">
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>
            </svg>
            <span>运营商 SIP 网关对接 4 大核心维度与参数规范指南</span>
          </div>
          <span class="text-xs text-slate-400 font-mono">FreeSWITCH Sofia-SIP (external.xml)</span>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4 text-xs font-sans">
          <!-- 1. 信令连接与模式 -->
          <div class="bg-slate-900/90 p-4 rounded-xl border border-slate-800 space-y-2">
            <div class="font-bold text-amber-300 text-sm flex items-center gap-1.5">
              <span>① 远端连接与注册模式</span>
            </div>
            <p class="text-slate-300 leading-relaxed">
              <b class="text-white">proxy:</b> 运营商远端 SBC/IMS 代理地址与端口（如 <code class="text-cyan-300">116.228.x.x:5060</code>）。<br>
              <b class="text-white">register:</b> <span class="text-emerald-400">true</span> 表示账号密码动态注册（小微中继）；<span class="text-amber-400">false</span> 表示 IP 白名单直通模式（企业专线主流，只验源 IP 免注册）。<br>
              <b class="text-white">ping:</b> 自动向远端发送 SIP OPTIONS 心跳探活周期（建议 25s），探活超时触发熔断。
            </p>
          </div>

          <!-- 2. 鉴权认证与主叫身份 -->
          <div class="bg-slate-900/90 p-4 rounded-xl border border-slate-800 space-y-2">
            <div class="font-bold text-cyan-300 text-sm flex items-center gap-1.5">
              <span>② 鉴权与主叫身份 (From)</span>
            </div>
            <p class="text-slate-300 leading-relaxed">
              <b class="text-white">username / password:</b> 运营商下发的 SIP 中继账号与鉴权密码。<br>
              <b class="text-white">from-domain:</b> From 头归属域名（如 <code class="text-cyan-300">ims.chinaunicom.cn</code>，运营商防虚假呼叫强校验核心）。<br>
              <b class="text-white">caller-id-in-from:</b> 是否将真实外呼主叫号强制写入 From 头（绝大多数运营商要求开启，否则按中继总机出局）。
            </p>
          </div>

          <!-- 3. 路由隔离与安全 -->
          <div class="bg-slate-900/90 p-4 rounded-xl border border-slate-800 space-y-2">
            <div class="font-bold text-indigo-300 text-sm flex items-center gap-1.5">
              <span>③ 路由上下文与安全隔离</span>
            </div>
            <p class="text-slate-300 leading-relaxed">
              <b class="text-white">context:</b> 呼入命中的 Dialplan 上下文（默认为 <code class="text-indigo-300">public</code> 或 <code class="text-indigo-300">from-trunk</code>）。<br>
              <b class="text-white">安全隔离:</b> 严禁将网关 context 设为 default！隔离可以防止外部未经鉴权的来电直接拨打内部分机或越权盗打二次外呼。<br>
              <b class="text-white">extension:</b> 呼入默认路由目的地（如 <code class="text-slate-200">auto_to_user</code> 或引至 IVR）。
            </p>
          </div>

          <!-- 4. 媒体流与按键特征 -->
          <div class="bg-slate-900/90 p-4 rounded-xl border border-slate-800 space-y-2">
            <div class="font-bold text-emerald-300 text-sm flex items-center gap-1.5">
              <span>④ 媒体编解码与 DTMF</span>
            </div>
            <p class="text-slate-300 leading-relaxed">
              <b class="text-white">dtmf_type:</b> 首选 <code class="text-emerald-400">rfc2833</code>（带外 RTP Payload 101，电信级最稳定防丢按键）；政企可选 <code class="text-slate-200">info</code>；带内模拟选 <code class="text-slate-200">inband</code>。<br>
              <b class="text-white">codecs:</b> 语音编码协商优先级。国内固话首选 <code class="text-emerald-400">PCMA (G.711a)</code>；跨省长途或带宽受限专线可优先选 <code class="text-cyan-300">G729</code> 压缩带宽。
            </p>
          </div>
        </div>
      </div>

      <!-- 网关数据表 -->
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl overflow-hidden shadow-lg">
        <table class="w-full text-left">
          <thead class="bg-slate-900/95 text-slate-300 border-b border-slate-800 text-sm uppercase tracking-wider font-bold">
            <tr>
              <th class="py-4 px-6">网关标识</th>
              <th class="py-4 px-6">远端代理 (Proxy IP:Port)</th>
              <th class="py-4 px-6">对接模式</th>
              <th class="py-4 px-6">鉴权账号 / From 域</th>
              <th class="py-4 px-6">DTMF & 编解码</th>
              <th class="py-4 px-6">路由 Context</th>
              <th class="py-4 px-6">链路状态 & Ping</th>
              <th class="py-4 px-6 text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-800/80 font-mono text-base">
            <tr v-for="gw in gatewayList" :key="gw.name" class="hover:bg-slate-800/50 transition">
              <!-- 网关标识 -->
              <td class="py-4 px-6">
                <div class="font-bold text-cyan-400 text-lg">{{ gw.name }}</div>
                <div class="text-xs text-slate-400 font-sans">Profile: {{ gw.profile || 'external' }}</div>
              </td>

              <!-- 远端代理 -->
              <td class="py-4 px-6">
                <div class="text-slate-100 font-semibold">{{ gw.proxy }}</div>
                <div class="text-xs text-slate-400 font-sans">Transport: UDP / 5060</div>
              </td>

              <!-- 对接模式 -->
              <td class="py-4 px-6 font-sans">
                <span 
                  :class="gw.register ? 'bg-cyan-500/15 text-cyan-300 border-cyan-500/30' : 'bg-purple-500/15 text-purple-300 border-purple-500/30'" 
                  class="border px-2.5 py-1 rounded-md text-xs font-bold"
                >
                  {{ gw.register ? '动态账号注册' : 'IP专线直通 (免注册)' }}
                </span>
              </td>

              <!-- 鉴权账号与 From 域 -->
              <td class="py-4 px-6">
                <div class="text-white font-semibold text-sm">{{ gw.auth_user || gw.username || '-' }}</div>
                <div class="text-xs text-slate-400 truncate max-w-xs">{{ gw.from_domain || '-' }}</div>
              </td>

              <!-- DTMF & 编解码 -->
              <td class="py-4 px-6 font-sans text-xs">
                <div class="text-emerald-400 font-mono font-bold">{{ gw.dtmf_type || 'rfc2833' }}</div>
                <div class="text-slate-400 truncate max-w-xs pt-0.5">{{ gw.codecs || 'PCMA, G729' }}</div>
              </td>

              <!-- 路由 Context -->
              <td class="py-4 px-6 font-sans">
                <span class="bg-indigo-950/60 border border-indigo-800 text-indigo-300 px-2.5 py-0.5 rounded text-xs font-mono font-semibold">
                  {{ gw.context || 'public' }}
                </span>
              </td>

              <!-- 链路状态与探活延迟 -->
              <td class="py-4 px-6">
                <div class="flex items-center gap-2">
                  <span 
                    :class="gw.status === 'REGED' ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30' : (gw.status === 'NOREG' ? 'bg-indigo-500/15 text-indigo-300 border-indigo-500/30' : 'bg-rose-500/15 text-rose-400 border-rose-500/30')" 
                    class="border px-3 py-1 rounded-md text-xs font-extrabold font-mono"
                  >
                    {{ gw.status === 'REGED' ? 'REGED (UP)' : (gw.status === 'NOREG' ? 'DIRECT (UP)' : 'DOWN') }}
                  </span>
                  <span class="text-xs font-bold font-mono" :class="gw.status.includes('UP') || gw.status === 'REGED' || gw.status === 'NOREG' ? 'text-emerald-400' : 'text-slate-500'">
                    {{ gw.ping_ms }}
                  </span>
                </div>
              </td>

              <!-- 操作 -->
              <td class="py-4 px-6 text-right space-x-3 text-sm font-sans font-semibold">
                <button @click="openEditModal(gw)" class="text-cyan-400 hover:text-cyan-300 hover:underline">配置详情</button>
                <button @click="pingGateway(gw)" class="text-emerald-400 hover:text-emerald-300 hover:underline">探活</button>
                <button @click="deleteGateway(gw.name)" class="text-rose-400 hover:text-rose-300 hover:underline">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 添加 / 编辑运营商网关配置弹窗 -->
    <div v-if="showModal" class="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl w-full max-w-2xl overflow-hidden shadow-2xl space-y-4">
        <!-- 弹窗标题 -->
        <div class="bg-[#0F172A] border-b border-slate-800 px-6 py-4 flex items-center justify-between">
          <span class="font-extrabold text-white text-lg">
            {{ isEditing ? `编辑网关配置 - ${currentGw.name}` : '添加运营商 SIP 网关 (Trunk Gateway)' }}
          </span>
          <button @click="showModal = false" class="text-slate-400 hover:text-white text-lg">✕</button>
        </div>

        <!-- 弹窗表单分栏 -->
        <div class="px-6 space-y-4 max-h-[70vh] overflow-y-auto text-sm font-sans">
          <!-- 模块 1: 远端信令与连接 -->
          <div class="bg-slate-900/90 p-4 rounded-xl border border-slate-800 space-y-3">
            <div class="font-bold text-amber-300 text-sm flex items-center gap-2">
              <span class="w-2 h-2 rounded-full bg-amber-400"></span>
              <span>1. 远端地址与连接模式</span>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-slate-400 mb-1 font-semibold text-xs">网关唯一标识 (Gateway Name) *</label>
                <input 
                  v-model="currentGw.name" 
                  :disabled="isEditing"
                  type="text" 
                  placeholder="例如: trunk_telecom_ims" 
                  class="w-full bg-slate-950 border border-slate-700 text-white rounded-lg px-3.5 py-2 font-mono text-sm outline-none focus:border-cyan-500 disabled:opacity-50"
                >
              </div>
              <div>
                <label class="block text-slate-400 mb-1 font-semibold text-xs">远端代理服务器 (Proxy IP:Port) *</label>
                <input 
                  v-model="currentGw.proxy" 
                  type="text" 
                  placeholder="例如: 116.228.89.12:5060" 
                  class="w-full bg-slate-950 border border-slate-700 text-white rounded-lg px-3.5 py-2 font-mono text-sm outline-none focus:border-cyan-500"
                >
              </div>
            </div>
            <div class="grid grid-cols-3 gap-4">
              <div>
                <label class="block text-slate-400 mb-1 font-semibold text-xs">对接注册模式</label>
                <select v-model="currentGw.register" class="w-full bg-slate-950 border border-slate-700 text-white rounded-lg px-3 py-2 text-xs outline-none">
                  <option :value="true">账号密码注册 (register=true)</option>
                  <option :value="false">IP专线免注册 (register=false)</option>
                </select>
              </div>
              <div>
                <label class="block text-slate-400 mb-1 font-semibold text-xs">探活周期 (ping)</label>
                <input v-model.number="currentGw.ping_seconds" type="number" placeholder="25" class="w-full bg-slate-950 border border-slate-700 text-white rounded-lg px-3 py-2 font-mono text-xs outline-none">
              </div>
              <div>
                <label class="block text-slate-400 mb-1 font-semibold text-xs">租约周期 (expire-seconds)</label>
                <input v-model.number="currentGw.expire_seconds" type="number" placeholder="3600" class="w-full bg-slate-950 border border-slate-700 text-white rounded-lg px-3 py-2 font-mono text-xs outline-none">
              </div>
            </div>
          </div>

          <!-- 模块 2: 鉴权与主叫身份 -->
          <div class="bg-slate-900/90 p-4 rounded-xl border border-slate-800 space-y-3">
            <div class="font-bold text-cyan-300 text-sm flex items-center gap-2">
              <span class="w-2 h-2 rounded-full bg-cyan-400"></span>
              <span>2. 鉴权认证与主叫 From 头规范</span>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-slate-400 mb-1 font-semibold text-xs">中继账号 (username) *</label>
                <input v-model="currentGw.username" type="text" placeholder="02188991001" class="w-full bg-slate-950 border border-slate-700 text-white rounded-lg px-3.5 py-2 font-mono text-sm outline-none">
              </div>
              <div>
                <label class="block text-slate-400 mb-1 font-semibold text-xs">鉴权密码 (password)</label>
                <input v-model="currentGw.password" type="password" placeholder="••••••••" class="w-full bg-slate-950 border border-slate-700 text-white rounded-lg px-3.5 py-2 font-mono text-sm outline-none">
              </div>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-slate-400 mb-1 font-semibold text-xs">From 归属域名 (from-domain)</label>
                <input v-model="currentGw.from_domain" type="text" placeholder="ims.sh.chinatel.com" class="w-full bg-slate-950 border border-slate-700 text-white rounded-lg px-3.5 py-2 font-mono text-sm outline-none">
              </div>
              <div>
                <label class="block text-slate-400 mb-1 font-semibold text-xs">主叫号码写入 From (caller-id-in-from)</label>
                <select v-model="currentGw.caller_id_in_from" class="w-full bg-slate-950 border border-slate-700 text-white rounded-lg px-3 py-2 text-xs outline-none">
                  <option :value="true">开启 (推荐，外呼手机号写入 From)</option>
                  <option :value="false">关闭 (使用中继账号写入 From)</option>
                </select>
              </div>
            </div>
          </div>

          <!-- 模块 3: 路由与媒体特征 -->
          <div class="bg-slate-900/90 p-4 rounded-xl border border-slate-800 space-y-3">
            <div class="font-bold text-indigo-300 text-sm flex items-center gap-2">
              <span class="w-2 h-2 rounded-full bg-indigo-400"></span>
              <span>3. 路由安全与媒体编解码协商</span>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-slate-400 mb-1 font-semibold text-xs">呼入 Context 上下文</label>
                <input v-model="currentGw.context" type="text" placeholder="public" class="w-full bg-slate-950 border border-slate-700 text-white rounded-lg px-3.5 py-2 font-mono text-sm outline-none">
              </div>
              <div>
                <label class="block text-slate-400 mb-1 font-semibold text-xs">DTMF 传输类型 (dtmf_type)</label>
                <select v-model="currentGw.dtmf_type" class="w-full bg-slate-950 border border-slate-700 text-white rounded-lg px-3 py-2 font-mono text-xs outline-none">
                  <option value="rfc2833">rfc2833 (推荐，带外 RTP Payload 101)</option>
                  <option value="info">info (SIP INFO 信令)</option>
                  <option value="inband">inband (带内音频频带)</option>
                </select>
              </div>
            </div>
            <div>
              <label class="block text-slate-400 mb-1 font-semibold text-xs">语音编解码偏好 (codecs)</label>
              <input v-model="currentGw.codecs" type="text" placeholder="PCMA, PCMU, G729" class="w-full bg-slate-950 border border-slate-700 text-white rounded-lg px-3.5 py-2 font-mono text-sm outline-none">
            </div>
          </div>
        </div>

        <!-- 弹窗底部操作 -->
        <div class="bg-[#0F172A] border-t border-slate-800 px-6 py-4 flex justify-end gap-3.5">
          <button @click="showModal = false" class="bg-slate-800 hover:bg-slate-700 text-slate-200 px-5 py-2 rounded-xl text-sm font-semibold transition">取消</button>
          <button @click="saveGateway" class="bg-cyan-600 hover:bg-cyan-500 text-white px-6 py-2 rounded-xl text-sm font-bold transition shadow-lg shadow-cyan-950">
            {{ isEditing ? '保存配置并热生效' : '确认添加网关' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { Gateway } from '@/api/telephony'
import { telephonyApi } from '@/api/telephony'

const props = defineProps<{
  gateways: Gateway[]
}>()

const emit = defineEmits<{
  (e: 'toast', msg: string): void
}>()

const showGuide = ref(true)
const showModal = ref(false)
const isEditing = ref(false)

const gatewayList = ref<Gateway[]>([])

const defaultGw: Gateway = {
  name: 'trunk_unicom_sh02',
  profile: 'external',
  proxy: '112.65.10.99:5060',
  username: 'unicom_sip_trunk_902',
  password: 'Password@2026',
  auth_user: 'unicom_sip_trunk_902',
  from_user: 'unicom_sip_trunk_902',
  from_domain: 'sip.sh.chinaunicom.cn',
  caller_id_in_from: true,
  context: 'public',
  extension: 'auto_to_user',
  dtmf_type: 'rfc2833',
  codecs: 'PCMA, G729',
  register: true,
  expire_seconds: 3600,
  ping_seconds: 25,
  status: 'REGED',
  ping_ms: '13ms'
}

const currentGw = ref<Gateway>({ ...defaultGw })

onMounted(async () => {
  await loadGateways()
})

async function loadGateways() {
  try {
    const list = await telephonyApi.getGateways()
    if (list && list.length > 0) {
      gatewayList.value = list
    } else if (props.gateways && props.gateways.length > 0) {
      gatewayList.value = props.gateways
    }
  } catch (err) {
    if (props.gateways && props.gateways.length > 0) {
      gatewayList.value = props.gateways
    }
  }
}

function openAddModal() {
  isEditing.value = false
  currentGw.value = { ...defaultGw, name: `trunk_carrier_${gatewayList.value.length + 1}` }
  showModal.value = true
}

function openEditModal(gw: Gateway) {
  isEditing.value = true
  currentGw.value = { ...gw }
  showModal.value = true
}

async function saveGateway() {
  if (!currentGw.value.name || !currentGw.value.proxy) {
    emit('toast', '请完整填写网关标识和代理服务器地址')
    return
  }
  try {
    await telephonyApi.saveGateway(currentGw.value)
    emit('toast', `网关 ${currentGw.value.name} 已成功保存至 PostgreSQL 并热重载生效！`)
    showModal.value = false
    await loadGateways()
  } catch (err: any) {
    emit('toast', '保存网关异常: ' + (err.message || 'Error'))
  }
}

async function pingGateway(gw: Gateway) {
  emit('toast', `正在向网关 ${gw.name} (${gw.proxy}) 发送 SIP OPTIONS 探活心跳...`)
  try {
    const res = await telephonyApi.pingGateway(gw.name)
    gw.status = res.status
    gw.ping_ms = res.ping_ms
    emit('toast', `网关 ${gw.name} 探活成功: ${res.status} (RTT: ${res.ping_ms})`)
  } catch (err: any) {
    emit('toast', `网关 ${gw.name} 探活超时或异常: ${err.message || 'Error'}`)
  }
}

async function deleteGateway(name: string) {
  if (confirm(`确认注销并从 PostgreSQL 删除运营商网关 ${name} 吗？`)) {
    try {
      await telephonyApi.deleteGateway(name)
      emit('toast', `网关 ${name} 已从 PostgreSQL 删除并完成卸载`)
      await loadGateways()
    } catch (err: any) {
      emit('toast', `删除网关异常: ${err.message || 'Error'}`)
    }
  }
}
</script>

