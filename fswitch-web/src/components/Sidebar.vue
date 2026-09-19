<template>
  <aside class="w-64 xl:w-72 2xl:w-80 bg-[#0F172A]/95 border-r border-slate-800 flex flex-col justify-between shrink-0 py-5 select-none transition-all">
    <div class="space-y-2 px-3.5">
      <div class="px-3 text-xs font-bold text-slate-400 uppercase tracking-wider mb-3">软交换运维控制台</div>
      
      <!-- Tab 1: 概览大盘 -->
      <button 
        @click="$emit('update:modelValue', 'dashboard')"
        :class="modelValue === 'dashboard' ? activeClass : inactiveClass"
        class="w-full flex items-center gap-3.5 px-4 py-3 rounded-xl text-base font-semibold transition"
      >
        <svg class="w-5 h-5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
          <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
        </svg>
        <span class="whitespace-nowrap">概览大盘</span>
      </button>

      <!-- Tab 2: 话单记录CDR (原 软交换底层CDR) -->
      <button 
        @click="$emit('update:modelValue', 'cdr')"
        :class="modelValue === 'cdr' ? activeClass : inactiveClass"
        class="w-full flex items-center gap-3.5 px-4 py-3 rounded-xl text-base font-semibold transition"
      >
        <svg class="w-5 h-5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/>
        </svg>
        <span class="whitespace-nowrap">话单记录CDR</span>
      </button>

      <!-- Tab 3: SIP 分机与注册 -->
      <button 
        @click="$emit('update:modelValue', 'extensions')"
        :class="modelValue === 'extensions' ? activeClass : inactiveClass"
        class="w-full flex items-center justify-between px-4 py-3 rounded-xl text-base font-semibold transition"
      >
        <div class="flex items-center gap-3.5">
          <svg class="w-5 h-5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
            <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
          <span class="whitespace-nowrap">SIP 分机与注册</span>
        </div>
        <span class="bg-emerald-500/20 text-emerald-400 text-xs px-2.5 py-0.5 rounded-full font-mono font-bold">{{ regCount }}</span>
      </button>

      <!-- Tab 4: 活跃话道与通话 -->
      <button 
        @click="$emit('update:modelValue', 'channels')"
        :class="modelValue === 'channels' ? activeClass : inactiveClass"
        class="w-full flex items-center justify-between px-4 py-3 rounded-xl text-base font-semibold transition"
      >
        <div class="flex items-center gap-3.5">
          <svg class="w-5 h-5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
            <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
          </svg>
          <span class="whitespace-nowrap">活跃话道与通话</span>
        </div>
        <span :class="channelCount > 0 ? 'bg-emerald-500/20 text-emerald-400 font-bold' : 'bg-slate-800 text-slate-400'" class="text-xs px-2.5 py-0.5 rounded-full font-mono font-bold">
          {{ channelCount }}
        </span>
      </button>

      <!-- Tab 5: SIP 网络配置 -->
      <button 
        @click="$emit('update:modelValue', 'sip')"
        :class="modelValue === 'sip' ? activeClass : inactiveClass"
        class="w-full flex items-center gap-3.5 px-4 py-3 rounded-xl text-base font-semibold transition"
      >
        <svg class="w-5 h-5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
          <circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
        </svg>
        <span class="whitespace-nowrap">SIP 网络配置</span>
      </button>

      <!-- Tab 6: 网关 (原 运营商中继) -->
      <button 
        @click="$emit('update:modelValue', 'gateways')"
        :class="modelValue === 'gateways' ? activeClass : inactiveClass"
        class="w-full flex items-center gap-3.5 px-4 py-3 rounded-xl text-base font-semibold transition"
      >
        <svg class="w-5 h-5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
          <rect x="2" y="2" width="20" height="8" rx="2" ry="2"/><rect x="2" y="14" width="20" height="8" rx="2" ry="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/>
        </svg>
        <span class="whitespace-nowrap">网关</span>
      </button>

      <!-- Tab 7: FCC与原生module -->
      <button 
        @click="$emit('update:modelValue', 'modules')"
        :class="modelValue === 'modules' ? activeClass : inactiveClass"
        class="w-full flex items-center gap-3.5 px-4 py-3 rounded-xl text-base font-semibold transition"
      >
        <svg class="w-5 h-5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
          <circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
        </svg>
        <span class="whitespace-nowrap">FCC与原生module</span>
      </button>

      <!-- Tab 8: fs_cli 终端与日志 -->
      <button 
        @click="$emit('update:modelValue', 'terminal')"
        :class="modelValue === 'terminal' ? activeClass : inactiveClass"
        class="w-full flex items-center gap-3.5 px-4 py-3 rounded-xl text-base font-semibold transition"
      >
        <svg class="w-5 h-5 shrink-0 text-cyan-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
          <polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/>
        </svg>
        <span class="whitespace-nowrap">fs_cli终端与日志</span>
      </button>
    </div>

  </aside>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: string
  regCount?: number
  channelCount?: number
}>()

defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const activeClass = 'text-cyan-400 bg-cyan-950/70 border border-cyan-800/80 shadow-md font-bold'
const inactiveClass = 'text-slate-300 hover:text-white hover:bg-slate-800/70 font-semibold'
</script>
