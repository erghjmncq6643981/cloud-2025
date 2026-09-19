<template>
  <div class="flex-1 flex flex-col overflow-hidden">
    <!-- 顶栏标题与操作 -->
    <div class="bg-[#0F172A] border-b border-slate-800 px-6 py-4 flex items-center justify-between shrink-0">
      <div class="flex items-center gap-4">
        <span class="text-lg font-bold text-white tracking-wide">话单记录CDR (PostgreSQL fs_cdr)</span>
        <span class="text-sm text-slate-400 hidden lg:inline">记录 SIP 呼叫详细记录、挂断原因与 RTP 传输质检指标</span>
      </div>
      <div class="flex items-center gap-3">
        <div class="flex items-center gap-2">
          <input 
            v-model="searchCaller" 
            @keyup.enter="loadCdrs(1)"
            type="text" 
            placeholder="搜索主叫号码..." 
            class="bg-slate-900 border border-slate-700 text-slate-200 text-xs px-3 py-2 rounded-xl outline-none focus:border-cyan-500 w-36 font-mono"
          />
          <button 
            @click="loadCdrs(1)"
            class="bg-slate-800 hover:bg-slate-700 text-cyan-300 text-xs px-3.5 py-2 rounded-xl border border-slate-700 font-bold transition"
          >
            查询
          </button>
        </div>
        <button 
          @click="loadCdrs(pageNum)"
          :disabled="isLoading"
          class="bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm px-4 py-2 rounded-xl border border-slate-700 font-semibold transition flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
        >
          <span :class="isLoading ? 'animate-spin' : ''">🔄</span>
          <span>刷新话单</span>
        </button>
      </div>
    </div>

    <!-- 表格内容 -->
    <div class="flex-1 p-6 overflow-y-auto space-y-4">
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl overflow-hidden shadow-lg">
        <table class="w-full text-left">
          <thead class="bg-slate-900/95 text-slate-300 border-b border-slate-800 text-sm uppercase tracking-wider font-bold">
            <tr>
              <th class="py-4 px-6">Call UUID</th>
              <th class="py-4 px-6">主叫</th>
              <th class="py-4 px-6">被叫</th>
              <th class="py-4 px-6">方向</th>
              <th class="py-4 px-6">SIP 释放态</th>
              <th class="py-4 px-6">挂断原因 (Cause)</th>
              <th class="py-4 px-6">通话时长</th>
              <th class="py-4 px-6">建立时间</th>
              <th class="py-4 px-6 text-right">信令快照</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-800/80 font-mono">
            <tr v-if="isLoading && cdrList.length === 0">
              <td colspan="9" class="py-12 text-center text-slate-400 font-sans">正在从 PostgreSQL 查询真实话单...</td>
            </tr>
            <tr v-else-if="!isLoading && cdrList.length === 0">
              <td colspan="9" class="py-12 text-center text-slate-400 font-sans">暂无符合条件的通话话单</td>
            </tr>
            <tr v-for="cdr in cdrList" :key="cdr.call_uuid" class="hover:bg-slate-800/50 transition">
              <td class="py-4 px-6 text-cyan-400 font-bold text-base truncate max-w-xs" :title="cdr.call_uuid">
                {{ cdr.call_uuid.substring(0, 18) }}...
              </td>
              <td class="py-4 px-6 text-white font-bold text-base">
                {{ cdr.caller_id_number }}
                <span v-if="cdr.caller_id_name && cdr.caller_id_name !== cdr.caller_id_number" class="text-xs text-slate-400 font-sans block">
                  {{ cdr.caller_id_name }}
                </span>
              </td>
              <td class="py-4 px-6 text-white font-bold text-base">{{ cdr.destination_number }}</td>
              <td class="py-4 px-6 text-slate-300 font-medium text-sm font-sans">
                <span :class="cdr.direction === 'inbound' ? 'bg-indigo-500/15 text-indigo-300 border border-indigo-500/30' : 'bg-amber-500/15 text-amber-300 border border-amber-500/30'" class="px-2 py-0.5 rounded text-xs">
                  {{ cdr.direction === 'inbound' ? '呼入 (IN)' : '外呼 (OUT)' }}
                </span>
              </td>
              <td class="py-4 px-6">
                <span class="bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 px-3 py-1 rounded-md text-xs font-extrabold tracking-wide font-sans">
                  {{ cdr.sip_hangup_disposition || 'send_bye' }}
                </span>
              </td>
              <td class="py-4 px-6 text-slate-200 font-semibold text-sm font-sans">{{ cdr.hangup_cause }}</td>
              <td class="py-4 px-6 text-cyan-300 font-extrabold text-base">{{ cdr.billsec }}s</td>
              <td class="py-4 px-6 text-slate-400 text-xs font-sans">{{ cdr.created_at || '-' }}</td>
              <td class="py-4 px-6 text-right font-sans">
                <button @click="openJsonDetail(cdr)" class="text-cyan-400 hover:text-cyan-300 font-semibold text-sm hover:underline cursor-pointer">
                  查看快照
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页工具栏 -->
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl px-6 py-3.5 flex items-center justify-between text-xs text-slate-400 font-sans">
        <span>共 {{ total }} 条 PostgreSQL 真实原始话单</span>
        <div class="flex items-center gap-2 font-mono">
          <button 
            @click="prevPage" 
            :disabled="pageNum <= 1" 
            class="px-3 py-1.5 bg-slate-900 border border-slate-700 text-slate-300 rounded-lg disabled:opacity-30 hover:bg-slate-800 cursor-pointer"
          >
            上一页
          </button>
          <span class="font-bold text-white px-2">第 {{ pageNum }} 页</span>
          <button 
            @click="nextPage" 
            :disabled="pageNum * pageSize >= total" 
            class="px-3 py-1.5 bg-slate-900 border border-slate-700 text-slate-300 rounded-lg disabled:opacity-30 hover:bg-slate-800 cursor-pointer"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

    <!-- CDR JSON 动态变量快照弹窗 -->
    <div v-if="showJsonModal" class="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl w-full max-w-xl overflow-hidden shadow-2xl space-y-4">
        <div class="bg-[#0F172A] border-b border-slate-800 px-6 py-4 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="font-bold text-white text-base">底层原始 CDR 变量快照</span>
            <span class="text-xs font-mono text-cyan-400 bg-cyan-950 px-2 py-0.5 rounded border border-cyan-800">PG JSONB</span>
          </div>
          <button @click="showJsonModal = false" class="text-slate-400 hover:text-white text-lg">✕</button>
        </div>
        <div class="px-6 space-y-3">
          <div class="text-xs text-slate-400 font-mono flex justify-between">
            <span>UUID: {{ selectedCdr?.call_uuid }}</span>
            <span>Duration: {{ selectedCdr?.duration }}s / Billsec: {{ selectedCdr?.billsec }}s</span>
          </div>
          <pre class="bg-slate-950 p-4 rounded-lg text-xs font-mono text-cyan-300 overflow-x-auto max-h-80 border border-slate-800/80 leading-relaxed">{{ formattedJson }}</pre>
        </div>
        <div class="bg-[#0F172A] border-t border-slate-800 px-6 py-3.5 flex justify-end">
          <button @click="showJsonModal = false" class="bg-slate-800 hover:bg-slate-700 text-slate-300 px-4 py-2 rounded-lg text-sm transition">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { telephonyApi, type FsCdr } from '@/api/telephony'

const emit = defineEmits<{
  (e: 'toast', msg: string): void
}>()

const showJsonModal = ref(false)
const selectedCdr = ref<FsCdr | null>(null)
const cdrList = ref<FsCdr[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const isLoading = ref(false)
const searchCaller = ref('')

onMounted(async () => {
  await loadCdrs(1)
})

async function loadCdrs(page: number = 1) {
  isLoading.value = true
  pageNum.value = page
  try {
    const res = await telephonyApi.getCdrs({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      caller: searchCaller.value || undefined,
    })
    cdrList.value = res.data || []
    total.value = res.total || 0
  } catch (err: any) {
    emit('toast', '话单查询异常: ' + (err.message || 'Error'))
  } finally {
    isLoading.value = false
  }
}

function prevPage() {
  if (pageNum.value > 1) {
    loadCdrs(pageNum.value - 1)
  }
}

function nextPage() {
  if (pageNum.value * pageSize.value < total.value) {
    loadCdrs(pageNum.value + 1)
  }
}

function openJsonDetail(cdr: FsCdr) {
  selectedCdr.value = cdr
  showJsonModal.value = true
}

const formattedJson = computed(() => {
  if (!selectedCdr.value) return '{}'
  const obj = {
    call_uuid: selectedCdr.value.call_uuid,
    caller_id_name: selectedCdr.value.caller_id_name,
    caller_id_number: selectedCdr.value.caller_id_number,
    destination_number: selectedCdr.value.destination_number,
    context: selectedCdr.value.context,
    direction: selectedCdr.value.direction,
    read_codec: selectedCdr.value.read_codec,
    write_codec: selectedCdr.value.write_codec,
    sip_user_agent: selectedCdr.value.sip_user_agent,
    sip_hangup_disposition: selectedCdr.value.sip_hangup_disposition,
    hangup_cause: selectedCdr.value.hangup_cause,
    duration: selectedCdr.value.duration,
    billsec: selectedCdr.value.billsec,
    quality_percentage: selectedCdr.value.quality_percentage,
    variables: selectedCdr.value.variables_json,
  }
  return JSON.stringify(obj, null, 2)
})
</script>
