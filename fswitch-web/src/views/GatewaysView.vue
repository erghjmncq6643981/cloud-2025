<template>
  <div class="flex-1 flex flex-col overflow-hidden">
    <!-- 顶部标题与操作栏 -->
    <div class="bg-[#0F172A] border-b border-slate-800 px-6 py-4 flex items-center justify-between gap-4 shrink-0">
      <div class="flex items-center gap-4 min-w-0">
        <span class="text-lg font-bold text-white tracking-wide shrink-0">网关 (Gateways)</span>
        <span class="text-sm text-slate-400 truncate" title="运营商SIP互联中继">运营商SIP互联中继</span>
      </div>
      <div class="flex items-center gap-3 shrink-0">
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
      <div v-if="stale" class="rounded-lg border border-amber-700/60 bg-amber-950/30 px-4 py-3 text-sm text-amber-200">
        网关列表刷新失败，当前内容为最后一次成功快照。
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
                <div class="text-xs text-slate-400 font-sans">Profile: {{ gw.profile || '-' }}</div>
              </td>

              <!-- 远端代理 -->
              <td class="py-4 px-6">
                <div class="text-slate-100 font-semibold">{{ gw.proxy }}</div>
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
                <div class="text-emerald-400 font-mono font-bold">{{ gw.dtmf_type || '-' }}</div>
                <div class="text-slate-400 truncate max-w-xs pt-0.5">{{ gw.codecs || '-' }}</div>
              </td>

              <!-- 路由 Context -->
              <td class="py-4 px-6 font-sans">
                <span class="bg-indigo-950/60 border border-indigo-800 text-indigo-300 px-2.5 py-0.5 rounded text-xs font-mono font-semibold">
                  {{ gw.context || '-' }}
                </span>
              </td>

              <!-- 链路状态与探活延迟 -->
              <td class="py-4 px-6">
                <div class="flex items-center gap-2">
                  <span 
                    :class="isGatewayUp(gw.status) ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30' : 'bg-slate-800 text-slate-300 border-slate-700'"
                    class="border px-3 py-1 rounded-md text-xs font-extrabold font-mono"
                  >
                    {{ gw.status || 'UNKNOWN' }}
                  </span>
                  <span v-if="gw.ping_ms" class="text-xs font-bold font-mono" :class="isGatewayUp(gw.status) ? 'text-emerald-400' : 'text-slate-500'">
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
        <div v-if="gatewayList.length === 0" class="px-6 py-14 text-center text-sm text-slate-400">
          {{ stale ? '当前无法确认网关配置' : '尚未配置网关' }}
        </div>
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
                <input v-model="currentGw.password" type="password" autocomplete="new-password" :placeholder="isEditing ? '留空则保留现有密码' : '输入鉴权密码'" class="w-full bg-slate-950 border border-slate-700 text-white rounded-lg px-3.5 py-2 font-mono text-sm outline-none">
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
import { computed, ref } from 'vue'
import type { Gateway } from '@/api/telephony'
import { telephonyApi } from '@/api/telephony'

const props = defineProps<{
  gateways: Gateway[]
  stale: boolean
}>()

const emit = defineEmits<{
  (e: 'toast', msg: string): void
  (e: 'refresh'): void
}>()

const showModal = ref(false)
const isEditing = ref(false)

const gatewayList = computed(() => props.gateways)

const defaultGw: Gateway = {
  name: '',
  profile: '',
  proxy: '',
  username: '',
  password: '',
  auth_user: '',
  from_user: '',
  from_domain: '',
  caller_id_in_from: false,
  context: '',
  extension: '',
  dtmf_type: '',
  codecs: '',
  register: false,
  expire_seconds: 0,
  ping_seconds: 0
}

const currentGw = ref<Gateway>({ ...defaultGw })

function openAddModal() {
  isEditing.value = false
  currentGw.value = { ...defaultGw }
  showModal.value = true
}

function openEditModal(gw: Gateway) {
  isEditing.value = true
  currentGw.value = { ...gw, password: '' }
  showModal.value = true
}

async function saveGateway() {
  if (!currentGw.value.name || !currentGw.value.proxy) {
    emit('toast', '请完整填写网关标识和代理服务器地址')
    return
  }
  if (!isEditing.value && currentGw.value.register && !currentGw.value.password) {
    emit('toast', '注册型网关必须填写鉴权密码')
    return
  }
  try {
    await telephonyApi.saveGateway(currentGw.value)
    emit('toast', `网关 ${currentGw.value.name} 配置已保存，Sofia 重扫描指令已发送`)
    showModal.value = false
    emit('refresh')
  } catch (err: any) {
    emit('toast', '保存网关异常: ' + (err.message || 'Error'))
  }
}

async function pingGateway(gw: Gateway) {
  emit('toast', `正在向网关 ${gw.name} (${gw.proxy}) 发送 SIP OPTIONS 探活心跳...`)
  try {
    const res = await telephonyApi.pingGateway(gw.name)
    emit('toast', `网关 ${gw.name} 探活指令已受理，链路状态尚未确认`)
    emit('refresh')
  } catch (err: any) {
    emit('toast', `网关 ${gw.name} 探活超时或异常: ${err.message || 'Error'}`)
  }
}

async function deleteGateway(name: string) {
  if (confirm(`确认注销并从 PostgreSQL 删除运营商网关 ${name} 吗？`)) {
    try {
      await telephonyApi.deleteGateway(name)
      emit('toast', `网关 ${name} 已从 PostgreSQL 删除并完成卸载`)
      emit('refresh')
    } catch (err: any) {
      emit('toast', `删除网关异常: ${err.message || 'Error'}`)
    }
  }
}

function isGatewayUp(status?: string) {
  const normalized = status?.toUpperCase() || ''
  return normalized === 'REGED' || normalized === 'UP'
}
</script>

