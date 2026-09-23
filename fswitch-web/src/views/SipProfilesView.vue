<template>
  <div class="flex-1 flex flex-col overflow-hidden">
    <!-- 头部子Tab 与操作栏 -->
    <div class="bg-[#0F172A] border-b border-slate-800 px-6 py-4 flex flex-wrap items-center justify-between gap-4 shrink-0">
      <div class="flex items-center gap-2 bg-slate-900 p-1.5 rounded-xl border border-slate-800 text-base font-semibold">
        <button 
          @click="activeProfile = 'internal'"
          :class="activeProfile === 'internal' ? subActive : subInactive"
          class="px-5 py-2.5 rounded-lg transition cursor-pointer"
        >
          internal (内网话机分机)
        </button>
        <button 
          @click="activeProfile = 'external'"
          :class="activeProfile === 'external' ? subActive : subInactive"
          class="px-5 py-2.5 rounded-lg transition cursor-pointer"
        >
          external (外网中继接入)
        </button>
        <button 
          @click="activeProfile = 'vars'"
          :class="activeProfile === 'vars' ? subActive : subInactive"
          class="px-5 py-2.5 rounded-lg transition cursor-pointer flex items-center gap-2"
        >
          <span>⚙️ vars.xml 常用配置</span>
          <span v-if="varsConfig?.vars.local_ip_v4" class="text-xs bg-cyan-900/50 text-cyan-300 px-2 py-0.5 rounded-full font-mono font-normal">
            {{ varsConfig.vars.local_ip_v4 }}
          </span>
        </button>
      </div>

      <!-- 右侧动作栏 -->
      <div v-if="activeProfile === 'vars'" class="flex items-center gap-3">
        <button 
          @click="loadVars" 
          :disabled="isSaving"
          class="bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm px-4 py-2.5 rounded-xl border border-slate-700 font-semibold transition cursor-pointer flex items-center gap-1.5 disabled:opacity-50"
        >
          <span>🔄</span>
          <span>刷新变量</span>
        </button>
        <button 
          @click="saveVars" 
          :disabled="isSaving"
          class="bg-cyan-600 hover:bg-cyan-500 text-white text-sm px-5 py-2.5 rounded-xl font-bold shadow-lg shadow-cyan-950 transition cursor-pointer flex items-center gap-1.5 disabled:opacity-50"
        >
          <span v-if="isSaving" class="animate-spin">⏳</span>
          <span v-else>💾</span>
          <span>保存并热重载 (Reload XML)</span>
        </button>
      </div>
      <div v-else class="text-xs text-slate-500 font-mono">
        Sofia Profile 实时信令快照
      </div>
    </div>

    <!-- 视图 1 & 2: Sofia Profile (internal / external) -->
    <div v-if="activeProfile !== 'vars'" class="flex-1 p-6 overflow-y-auto space-y-6 text-base">
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl p-6 space-y-5 shadow-lg">
        <div class="flex justify-between items-center border-b border-slate-800 pb-4">
          <div class="flex items-center gap-3">
            <span class="font-extrabold text-white text-lg">Profile: {{ currentProfile?.name || activeProfile }}</span>
            <span class="text-sm text-slate-400">Sofia SIP Profile 物理监听与信令驱动</span>
          </div>
          <span class="text-emerald-400 font-bold bg-emerald-500/15 border border-emerald-500/30 px-3 py-1 rounded text-xs font-mono">
            {{ currentProfile?.state || 'UNKNOWN' }}
          </span>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6 font-mono text-base">
          <div class="space-y-3.5 bg-slate-900/60 p-5 rounded-xl border border-slate-800/80">
            <div class="flex justify-between"><span class="text-slate-400 font-sans">SIP 绑定地址:</span><span class="text-white font-bold">{{ currentProfile?.bind_ip || '未提供' }}:{{ currentProfile?.sip_port || '-' }}</span></div>
            <div class="flex justify-between"><span class="text-slate-400 font-sans">WebSocket / WSS:</span><span class="text-white font-bold">{{ currentProfile?.ws_port || '-' }} / {{ currentProfile?.wss_port || '-' }}</span></div>
            <div class="flex justify-between"><span class="text-slate-400 font-sans">Dialplan:</span><span class="text-cyan-400 font-bold">{{ currentProfile?.dialplan || '-' }} (context: {{ currentProfile?.context || '-' }})</span></div>
          </div>
          <div class="space-y-3.5 bg-slate-900/60 p-5 rounded-xl border border-slate-800/80">
            <div class="flex justify-between"><span class="text-slate-400 font-sans">音频 Codecs:</span><span class="text-cyan-300 font-bold">{{ currentProfile?.codecs || '未提供' }}</span></div>
          </div>
        </div>
      </div>

      <!-- Raw Sofia Output Box -->
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl p-6 space-y-4 shadow-lg">
        <span class="font-extrabold text-slate-200 text-base uppercase tracking-wider">FreeSWITCH 原生 Sofia Profile 终端回显:</span>
        <pre class="bg-slate-950 p-5 rounded-xl text-sm font-mono text-slate-300 overflow-x-auto max-h-80 leading-relaxed select-text border border-slate-800">{{ rawOutput }}</pre>
      </div>
    </div>

    <!-- 视图 3: vars.xml 常用配置与网络变量编辑器 -->
    <div v-else class="flex-1 p-6 overflow-y-auto space-y-6 text-base">
      <!-- 提示条 -->
      <div class="bg-cyan-950/30 border border-cyan-800/50 rounded-2xl p-4 flex items-start gap-3 text-sm text-cyan-200">
        <span class="text-lg">💡</span>
        <div class="space-y-1">
          <div class="font-bold text-white">vars.xml 全局核心预处理变量配置说明</div>
          <div class="text-xs text-slate-300 leading-relaxed">
            FreeSWITCH 启动或重载时，所有 SIP Profile、分机鉴权、RTP 媒体流均以 <code>vars.xml</code> 中的变量为基准。
            修改后点击「保存并热重载」，系统将自动更新配置文件、调用 <code>reloadxml</code>，若变更了核心 IP 还将自动重启 Sofia 协议栈。
          </div>
        </div>
      </div>

      <!-- 表单区域 -->
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl p-6 space-y-6 shadow-lg">
        <div class="flex justify-between items-center border-b border-slate-800 pb-4">
          <div class="flex items-center gap-3">
            <span class="font-extrabold text-white text-lg">核心网络与 SIP 变量配置</span>
            <span class="text-xs text-slate-400 font-mono">配置文件: {{ varsConfig?.file_path || '/opt/homebrew/etc/freeswitch/vars.xml' }}</span>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-6 text-sm">
          <!-- 1. local_ip_v4 -->
          <div class="space-y-2 bg-slate-900/70 p-4 rounded-xl border border-slate-800">
            <label class="block font-bold text-slate-200">
              本地网络绑定 IP (local_ip_v4) *
              <span class="text-xs font-normal text-slate-400 ml-1">Sofia SIP/RTP 绑定的主网卡 IP</span>
            </label>
            <input 
              v-model="varsForm.local_ip_v4" 
              type="text" 
              placeholder="例如: 192.168.18.64"
              class="w-full bg-slate-950 border border-slate-700 text-cyan-400 font-mono font-bold text-base rounded-xl px-4 py-2.5 outline-none focus:border-cyan-500"
            />
            <!-- 快捷嗅探 IP 填充按钮组 -->
            <div v-if="varsConfig?.available_ips?.length" class="flex flex-wrap items-center gap-2 pt-1">
              <span class="text-xs text-slate-400">探测到本机可用网卡:</span>
              <button 
                v-for="ip in varsConfig.available_ips" 
                :key="ip"
                type="button"
                @click="varsForm.local_ip_v4 = ip"
                :class="varsForm.local_ip_v4 === ip ? 'bg-cyan-600 text-white font-bold border-cyan-500' : 'bg-slate-800 text-slate-300 hover:bg-slate-700 border-slate-700'"
                class="text-xs px-2.5 py-1 rounded-lg border font-mono transition cursor-pointer"
              >
                {{ ip }}
              </button>
            </div>
          </div>

          <!-- 2. domain -->
          <div class="space-y-2 bg-slate-900/70 p-4 rounded-xl border border-slate-800">
            <label class="block font-bold text-slate-200">
              SIP 注册鉴权域 (domain) *
              <span class="text-xs font-normal text-slate-400 ml-1">分机与客户端注册所用 Realm</span>
            </label>
            <input 
              v-model="varsForm.domain" 
              type="text" 
              placeholder="默认: $${local_ip_v4}"
              @blur="varsForm.domain = normalizeFsVarRef(varsForm.domain)"
              class="w-full bg-slate-950 border border-slate-700 text-white font-mono text-base rounded-xl px-4 py-2.5 outline-none focus:border-cyan-500"
            />
            <div v-if="hasSingleDollarVar(varsForm.domain)" class="text-xs text-amber-400 flex items-center gap-1 font-medium">
              <span>⚠️ 检测到单美元符号 ${...}，失焦或提交时将自动纠正为合法的全局宏 $${...}</span>
            </div>
            <p class="text-xs text-slate-400">支持直接使用变量引用 <code>$${local_ip_v4}</code>，或自定义固定 IP / 域名。</p>
          </div>

          <!-- 3. default_password -->
          <div class="space-y-2 bg-slate-900/70 p-4 rounded-xl border border-slate-800">
            <label class="block font-bold text-slate-200">
              默认分机认证密码 (default_password) *
              <span class="text-xs font-normal text-slate-400 ml-1">未单独设密分机默认引用的密码</span>
            </label>
            <div class="relative">
              <input 
                v-model="varsForm.default_password" 
                :type="showPwd ? 'text' : 'password'"
                placeholder="例如: 919220 或 1234"
                class="w-full bg-slate-950 border border-slate-700 text-amber-300 font-mono font-bold text-base rounded-xl px-4 py-2.5 outline-none focus:border-cyan-500 pr-12"
              />
              <button 
                type="button"
                @click="showPwd = !showPwd"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-white text-xs cursor-pointer p-1"
              >
                {{ showPwd ? '隐藏' : '显示' }}
              </button>
            </div>
            <p class="text-xs text-slate-400">修改后，所有引用 <code>$${default_password}</code> 的分机将同步以此密码鉴权。</p>
          </div>

          <!-- 4. external_sip_ip -->
          <div class="space-y-2 bg-slate-900/70 p-4 rounded-xl border border-slate-800">
            <label class="block font-bold text-slate-200">
              外网映射 SIP IP (external_sip_ip)
              <span class="text-xs font-normal text-slate-400 ml-1">向公网宣告的信令 IP (SDP c=)</span>
            </label>
            <input 
              v-model="varsForm.external_sip_ip" 
              type="text" 
              placeholder="默认: $${local_ip_v4}"
              @blur="varsForm.external_sip_ip = normalizeFsVarRef(varsForm.external_sip_ip)"
              class="w-full bg-slate-950 border border-slate-700 text-slate-200 font-mono text-base rounded-xl px-4 py-2.5 outline-none focus:border-cyan-500"
            />
            <div v-if="hasSingleDollarVar(varsForm.external_sip_ip)" class="text-xs text-amber-400 flex items-center gap-1 font-medium">
              <span>⚠️ 检测到单美元符号 ${...}，失焦或提交时将自动纠正为合法的全局宏 $${...}</span>
            </div>
            <p class="text-xs text-slate-400">局域网/开发环境通常为 <code>$${local_ip_v4}</code>，公网生产请填写公网弹性 IP。</p>
          </div>

          <!-- 5. external_rtp_ip -->
          <div class="space-y-2 bg-slate-900/70 p-4 rounded-xl border border-slate-800">
            <label class="block font-bold text-slate-200">
              外网映射 RTP IP (external_rtp_ip)
              <span class="text-xs font-normal text-slate-400 ml-1">向公网宣告的音频媒体 IP</span>
            </label>
            <input 
              v-model="varsForm.external_rtp_ip" 
              type="text" 
              placeholder="默认: $${local_ip_v4}"
              @blur="varsForm.external_rtp_ip = normalizeFsVarRef(varsForm.external_rtp_ip)"
              class="w-full bg-slate-950 border border-slate-700 text-slate-200 font-mono text-base rounded-xl px-4 py-2.5 outline-none focus:border-cyan-500"
            />
            <div v-if="hasSingleDollarVar(varsForm.external_rtp_ip)" class="text-xs text-amber-400 flex items-center gap-1 font-medium">
              <span>⚠️ 检测到单美元符号 ${...}，失焦或提交时将自动纠正为合法的全局宏 $${...}</span>
            </div>
            <p class="text-xs text-slate-400">音频 RTP 媒体流直通地址，填错将导致通话出现单通或无声。</p>
          </div>

          <!-- 6. sound_prefix -->
          <div class="space-y-2 bg-slate-900/70 p-4 rounded-xl border border-slate-800">
            <label class="block font-bold text-slate-200">
              提示音基础路径 (sound_prefix)
              <span class="text-xs font-normal text-slate-400 ml-1">IVR、报号提示音根目录</span>
            </label>
            <input 
              v-model="varsForm.sound_prefix" 
              type="text" 
              placeholder="默认: $${sounds_dir}/en/us/callie"
              @blur="varsForm.sound_prefix = normalizeFsVarRef(varsForm.sound_prefix)"
              class="w-full bg-slate-950 border border-slate-700 text-slate-200 font-mono text-base rounded-xl px-4 py-2.5 outline-none focus:border-cyan-500"
            />
            <div v-if="hasSingleDollarVar(varsForm.sound_prefix)" class="text-xs text-amber-400 flex items-center gap-1 font-medium">
              <span>⚠️ 检测到单美元符号 ${...}，失焦或提交时将自动纠正为合法的全局宏 $${...}</span>
            </div>
            <p class="text-xs text-slate-400">FreeSWITCH 播放系统提示音时优先搜寻该目录下的 wav 资源。</p>
          </div>

          <!-- 7. hold_music -->
          <div class="space-y-2 bg-slate-900/70 p-4 rounded-xl border border-slate-800 md:col-span-2">
            <label class="block font-bold text-slate-200">
              等待与保持音乐 (hold_music)
              <span class="text-xs font-normal text-slate-400 ml-1">通话保持、排队转接时的背景音</span>
            </label>
            <input 
              v-model="varsForm.hold_music" 
              type="text" 
              placeholder="默认: local_stream://moh"
              @blur="varsForm.hold_music = normalizeFsVarRef(varsForm.hold_music)"
              class="w-full bg-slate-950 border border-slate-700 text-slate-200 font-mono text-base rounded-xl px-4 py-2.5 outline-none focus:border-cyan-500"
            />
            <div v-if="hasSingleDollarVar(varsForm.hold_music)" class="text-xs text-amber-400 flex items-center gap-1 font-medium">
              <span>⚠️ 检测到单美元符号 ${...}，失焦或提交时将自动纠正为合法的全局宏 $${...}</span>
            </div>
            <p class="text-xs text-slate-400">通常为 <code>local_stream://moh</code> 对应 MOH 循环音频流。</p>
          </div>
        </div>
      </div>

      <!-- vars.xml 原始文本快照 -->
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl p-6 space-y-4 shadow-lg">
        <div class="flex items-center justify-between">
          <span class="font-extrabold text-slate-200 text-base uppercase tracking-wider">vars.xml 底层完整内容预览:</span>
          <button 
            @click="showRawXml = !showRawXml" 
            class="text-xs text-cyan-400 hover:text-cyan-300 font-medium cursor-pointer"
          >
            {{ showRawXml ? '收起预览' : '展开原始 XML' }}
          </button>
        </div>
        <pre v-if="showRawXml" class="bg-slate-950 p-5 rounded-xl text-xs font-mono text-slate-300 overflow-x-auto max-h-96 leading-relaxed select-text border border-slate-800">{{ varsConfig?.raw_content || '暂无内容' }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { SofiaProfile, VarsConfig } from '@/api/telephony'
import { telephonyApi } from '@/api/telephony'

const props = defineProps<{
  profiles: SofiaProfile[]
}>()

const emit = defineEmits<{
  (e: 'toast', msg: string): void
}>()

const activeProfile = ref<'internal' | 'external' | 'vars'>('internal')
const subActive = 'bg-cyan-950 text-cyan-400 border border-cyan-800/60 shadow-sm'
const subInactive = 'text-slate-400 hover:text-white'

const currentProfile = computed(() => {
  return props.profiles.find(p => p.name === activeProfile.value)
})

const rawOutput = computed(() => {
  if (currentProfile.value?.raw_lines) {
    return currentProfile.value.raw_lines.join('\n')
  }
  return '未返回 Profile 回显'
})

// vars.xml 状态
const varsConfig = ref<VarsConfig | null>(null)
const isSaving = ref(false)
const showPwd = ref(false)
const showRawXml = ref(false)

const varsForm = ref({
  local_ip_v4: '',
  domain: '',
  default_password: '',
  external_sip_ip: '',
  external_rtp_ip: '',
  sound_prefix: '',
  hold_music: '',
  rtp_sdes_suites: ''
})

/**
 * 校验并自动纠正 FreeSWITCH 预处理宏变量格式：
 * 将单美元符号 ${var} 纠正为合法的双美元符号 $${var}。
 * FreeSWITCH 的 vars.xml 变量引用必须使用 $${...}，单 ${...} 会被作为普通字符串而导致解析或寻址失败。
 */
function normalizeFsVarRef(val: string): string {
  if (!val) return val
  return val
    .replace(/\$\$\{([^}]+)\}/g, '___DLR_DLR_$1___')
    .replace(/\$\{([^}]+)\}/g, '$$\${$1}')
    .replace(/___DLR_DLR_([^}]+)___/g, '$$\${$1}')
}

function hasSingleDollarVar(val: string): boolean {
  if (!val) return false
  return /(^|[^\$])\$\{([^}]+)\}/.test(val)
}

onMounted(async () => {
  await loadVars()
})

async function loadVars() {
  try {
    const data = await telephonyApi.getVars()
    varsConfig.value = data
    if (data && data.vars) {
      varsForm.value = {
        local_ip_v4: data.vars.local_ip_v4 || '',
        domain: data.vars.domain || '',
        default_password: data.vars.default_password || '',
        external_sip_ip: data.vars.external_sip_ip || '',
        external_rtp_ip: data.vars.external_rtp_ip || '',
        sound_prefix: data.vars.sound_prefix || '',
        hold_music: data.vars.hold_music || '',
        rtp_sdes_suites: data.vars.rtp_sdes_suites || ''
      }
    }
  } catch (err: any) {
    emit('toast', `加载 vars.xml 失败: ${err.message || '网络异常'}`)
  }
}

async function saveVars() {
  if (!varsForm.value.local_ip_v4) {
    emit('toast', '本地网络绑定 IP (local_ip_v4) 不能为空')
    return
  }
  isSaving.value = true
  try {
    // 提交前全面清洗并纠正 ${...} -> $${...}
    const sanitizedVars: Record<string, string> = {}
    for (const [k, v] of Object.entries(varsForm.value)) {
      sanitizedVars[k] = normalizeFsVarRef(v)
      // 同步回填表单
      ;(varsForm.value as any)[k] = sanitizedVars[k]
    }
    const res = await telephonyApi.updateVars(sanitizedVars)
    emit('toast', res.message || 'vars.xml 配置已保存并热重载！')
    await loadVars()
  } catch (err: any) {
    emit('toast', `保存失败: ${err.message || 'Error'}`)
  } finally {
    isSaving.value = false
  }
}
</script>
