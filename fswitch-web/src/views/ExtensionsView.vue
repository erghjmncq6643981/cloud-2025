<template>
  <div class="flex-1 flex flex-col overflow-hidden">
    <!-- 顶部多条件过滤与操作栏 -->
    <div class="bg-[#0F172A] border-b border-slate-800 px-6 py-4 flex flex-wrap items-center justify-between gap-4 shrink-0">
      <!-- 多条件组合筛选表单 -->
      <div class="flex flex-wrap items-center gap-3 text-sm">
        <!-- 1. 分机号 -->
        <div class="flex items-center gap-1.5">
          <label class="text-slate-400 text-xs font-semibold whitespace-nowrap">分机号:</label>
          <input 
            v-model="queryForm.extension" 
            @keyup.enter="handleSearch"
            type="text" 
            placeholder="如 1001, 1021..." 
            class="bg-slate-900 border border-slate-700 text-white rounded-xl px-3 py-1.5 w-32 outline-none focus:border-cyan-500 font-mono text-sm shadow-sm"
          />
        </div>

        <!-- 2. 注册状态 -->
        <div class="flex items-center gap-1.5">
          <label class="text-slate-400 text-xs font-semibold whitespace-nowrap">状态:</label>
          <select 
            v-model="queryForm.status" 
            class="bg-slate-900 border border-slate-700 text-white rounded-xl px-3 py-1.5 text-sm outline-none focus:border-cyan-500 cursor-pointer"
          >
            <option value="all">全部状态</option>
            <option value="registered">已注册 (Online)</option>
            <option value="unregistered">未注册 (Offline)</option>
          </select>
        </div>

        <!-- 3. 协议类型 -->
        <div class="flex items-center gap-1.5">
          <label class="text-slate-400 text-xs font-semibold whitespace-nowrap">协议:</label>
          <select 
            v-model="queryForm.proto" 
            class="bg-slate-900 border border-slate-700 text-white rounded-xl px-3 py-1.5 text-sm outline-none focus:border-cyan-500 cursor-pointer"
          >
            <option value="all">全部协议</option>
            <option value="udp">UDP (硬件话机)</option>
            <option value="tcp">TCP</option>
            <option value="wss">WSS (WebRTC)</option>
            <option value="tls">TLS (加密SIP)</option>
          </select>
        </div>

        <!-- 4. Context / 呼叫组 -->
        <div class="flex items-center gap-1.5">
          <label class="text-slate-400 text-xs font-semibold whitespace-nowrap">Context/组:</label>
          <input 
            v-model="queryForm.context" 
            @keyup.enter="handleSearch"
            type="text" 
            placeholder="如 default..." 
            class="bg-slate-900 border border-slate-700 text-white rounded-xl px-3 py-1.5 w-28 outline-none focus:border-cyan-500 font-mono text-sm shadow-sm"
          />
        </div>

        <!-- 5. 关键词 (UA / IP) -->
        <div class="flex items-center gap-1.5">
          <label class="text-slate-400 text-xs font-semibold whitespace-nowrap">客户端/IP:</label>
          <input 
            v-model="queryForm.keyword" 
            @keyup.enter="handleSearch"
            type="text" 
            placeholder="UA终端、IP地址..." 
            class="bg-slate-900 border border-slate-700 text-white rounded-xl px-3 py-1.5 w-36 outline-none focus:border-cyan-500 text-sm shadow-sm"
          />
        </div>

        <!-- 查询与重置按钮 -->
        <div class="flex items-center gap-2 ml-1">
          <button 
            @click="handleSearch"
            class="bg-cyan-600 hover:bg-cyan-500 text-white text-xs px-3.5 py-2 rounded-xl font-bold transition flex items-center gap-1.5 shadow-md shadow-cyan-950/50 cursor-pointer"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
            </svg>
            <span>查询</span>
          </button>
          <button 
            @click="handleReset"
            class="bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white text-xs px-3 py-2 rounded-xl border border-slate-700 font-semibold transition cursor-pointer"
          >
            重置
          </button>
        </div>
      </div>

      <!-- 右侧新建与刷新动作 -->
      <div class="flex items-center gap-2.5 shrink-0 ml-auto">
        <button 
          @click="showCreateModal = true"
          class="bg-cyan-600 hover:bg-cyan-500 text-white text-xs px-3.5 py-2 rounded-xl font-bold transition flex items-center gap-1.5 shadow-lg shadow-cyan-950 cursor-pointer"
        >
          <span>+</span>
          <span>新建分机</span>
        </button>
        <button 
          @click="loadExtensions"
          class="bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs px-3.5 py-2 rounded-xl border border-slate-700 font-semibold transition cursor-pointer flex items-center gap-1"
        >
          <span>🔄</span>
          <span>刷新</span>
        </button>
      </div>
    </div>

    <div v-if="loadError" class="mx-6 mt-4 rounded-lg border border-rose-800/60 bg-rose-950/30 px-4 py-3 text-sm text-rose-300">
      {{ loadError }}
    </div>

    <!-- 全量分机高密度数据表 -->
    <div class="flex-1 p-6 overflow-hidden flex flex-col space-y-4">
      <div class="flex items-center justify-between text-base text-slate-300 px-1 font-medium">
        <div>
          共检索到 <b class="text-cyan-400 font-mono text-xl font-black">{{ filteredExtensions.length }}</b> 个分机账户
          <span class="text-slate-500 text-sm ml-2">（配置来自 PostgreSQL，注册态来自 FreeSWITCH）</span>
        </div>
        <div class="flex items-center gap-4 text-sm font-mono">
          <span class="flex items-center gap-1.5 text-emerald-400"><span class="w-2 h-2 rounded-full bg-emerald-400"></span>已注册: {{ registeredCount }}</span>
          <span class="flex items-center gap-1.5 text-slate-400"><span class="w-2 h-2 rounded-full bg-slate-500"></span>未注册: {{ extensionList.length - registeredCount }}</span>
        </div>
      </div>

      <div class="flex-1 bg-[#131C31] border border-slate-800 rounded-2xl overflow-y-auto shadow-lg">
        <table class="w-full text-left text-base">
          <thead class="bg-slate-900/95 text-slate-300 border-b border-slate-800 sticky top-0 font-bold text-sm uppercase tracking-wider whitespace-nowrap">
            <tr>
              <th class="py-4 px-6 w-28">分机号</th>
              <th class="py-4 px-6 w-48">注册状态</th>
              <th class="py-4 px-6 w-56">SIP 认证密码</th>
              <th class="py-4 px-6 min-w-[200px]">网络终端 (User-Agent)</th>
              <th class="py-4 px-6 w-56">网络地址 (IP:Port)</th>
              <th class="py-4 px-6 w-40">Context / 呼叫组</th>
              <th class="py-4 px-6 text-right w-52 pr-6">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-800/80 font-sans whitespace-nowrap">
            <tr v-for="ext in pagedExtensions" :key="ext.extension" class="hover:bg-slate-800/50 transition">
              <!-- 分机号 -->
              <td class="py-4 px-6 font-mono font-black text-cyan-400 text-xl">
                {{ ext.extension }}
              </td>

              <!-- 注册状态 -->
              <td class="py-4 px-6">
                <span 
                  v-if="ext.is_registered" 
                  class="inline-flex items-center gap-2 bg-emerald-500/15 border border-emerald-500/30 text-emerald-400 px-3 py-1 rounded-md text-xs font-mono font-bold"
                >
                  <span class="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                  <span>ONLINE ({{ ext.remaining_seconds }}s)</span>
                </span>
                <span 
                  v-else 
                  class="inline-flex items-center gap-1.5 bg-slate-800/80 border border-slate-700 text-slate-400 px-3 py-1 rounded-md text-xs font-mono font-medium"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-slate-500"></span>
                  <span>OFFLINE (未注册)</span>
                </span>
              </td>

              <!-- SIP 认证密码 (支持查看与明密文切换) -->
              <td class="py-4 px-6">
                <div class="flex items-center gap-2.5 font-mono">
                  <span class="text-base font-bold" :class="revealedPasswords[ext.extension] ? 'text-amber-300' : 'text-slate-400'">
                    {{ revealedPasswords[ext.extension] ? (ext.password || '$${default_password}') : '••••••••' }}
                  </span>
                  <!-- 密码显隐切换按钮 -->
                  <button 
                    @click="togglePassword(ext.extension)"
                    class="text-slate-400 hover:text-cyan-300 text-sm p-1 rounded hover:bg-slate-800 transition cursor-pointer"
                    :title="revealedPasswords[ext.extension] ? '隐藏密码' : '显示密码'"
                  >
                    <svg v-if="revealedPasswords[ext.extension]" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                      <line x1="1" y1="1" x2="23" y2="23"/>
                    </svg>
                    <svg v-else class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
                    </svg>
                  </button>
                  <!-- 快捷修改密码按钮 -->
                  <button 
                    @click="openEditPassword(ext)"
                    class="text-xs bg-slate-800 hover:bg-cyan-950 border border-slate-700 hover:border-cyan-700 text-cyan-400 px-2.5 py-1 rounded font-sans font-medium transition cursor-pointer"
                  >
                    修改
                  </button>
                </div>
              </td>

              <!-- 终端 -->
              <td class="py-4 px-6">
                <div class="text-white font-bold text-base">
                  {{ ext.is_registered ? (ext.user_agent || 'SIP Terminal') : '等待话机接入' }}
                </div>
                <div class="text-xs text-slate-400 font-mono truncate max-w-xs pt-0.5">
                  {{ ext.xml_path || `conf/directory/default/${ext.extension}.xml` }}
                </div>
              </td>

              <!-- 网络地址 -->
              <td class="py-4 px-6 font-mono text-slate-200 text-base">
                <span v-if="ext.is_registered" class="font-semibold text-cyan-300">
                  {{ ext.network_ip }}:{{ ext.network_port }} 
                  <span class="text-xs bg-slate-800 border border-slate-700 px-1.5 py-0.5 rounded text-slate-300 ml-1">
                    {{ ext.network_proto?.toUpperCase() || 'UDP' }}
                  </span>
                </span>
                <span v-else class="text-slate-500 font-sans text-sm">-</span>
              </td>

              <!-- Context / 呼叫组 -->
              <td class="py-4 px-6 text-sm font-mono text-slate-300">
                <span>{{ ext.context || 'default' }}</span>
                <span class="text-slate-500 mx-1">/</span>
                <span class="text-slate-400">{{ ext.callgroup || 'default' }}</span>
              </td>

              <!-- 操作 -->
              <td class="py-4 px-6 text-right space-x-3 text-sm font-semibold">
                <button @click="openDetail(ext)" class="text-cyan-400 hover:text-cyan-300 hover:underline cursor-pointer">详情</button>
                <button @click="openEditPassword(ext)" class="text-amber-400 hover:text-amber-300 hover:underline cursor-pointer">修改密码</button>
                <button @click="$emit('dial-ext', ext.extension)" class="text-emerald-400 hover:text-emerald-300 hover:underline cursor-pointer">拨打</button>
                <button 
                  v-if="ext.is_registered" 
                  @click="$emit('flush-reg', ext.extension)" 
                  class="text-rose-400 hover:text-rose-300 hover:underline cursor-pointer"
                >
                  踢除
                </button>
              </td>
            </tr>
            <tr v-if="filteredExtensions.length === 0">
              <td colspan="7" class="text-center py-16 text-slate-500 text-base font-medium">
                无匹配的分机账户记录
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页工具栏 -->
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl px-6 py-3 flex flex-wrap items-center justify-between gap-4 text-xs text-slate-400 font-sans shrink-0">
        <div class="flex items-center gap-2 font-mono">
          <span>显示第 <b class="text-white">{{ filteredExtensions.length === 0 ? 0 : (currentPage - 1) * pageSize + 1 }}</b> 至 <b class="text-white">{{ Math.min(currentPage * pageSize, filteredExtensions.length) }}</b> 条</span>
          <span class="text-slate-600">|</span>
          <span>共 <b class="text-cyan-400">{{ filteredExtensions.length }}</b> 条分机</span>
          <span class="text-slate-500">（底库总计 {{ extensionList.length }} 条）</span>
        </div>

        <div class="flex items-center gap-3">
          <!-- 每页条数选择 -->
          <div class="flex items-center gap-1.5">
            <span>每页:</span>
            <select 
              v-model="pageSize" 
              @change="currentPage = 1"
              class="bg-slate-900 border border-slate-700 text-slate-200 rounded-lg px-2.5 py-1 outline-none focus:border-cyan-500 cursor-pointer font-mono"
            >
              <option :value="10">10 条/页</option>
              <option :value="20">20 条/页</option>
              <option :value="50">50 条/页</option>
            </select>
          </div>

          <!-- 页码按钮组 -->
          <div class="flex items-center gap-1 font-mono">
            <button 
              @click="prevPage" 
              :disabled="currentPage <= 1"
              class="px-2.5 py-1 rounded-lg border border-slate-700 bg-slate-900 text-slate-300 hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed transition cursor-pointer"
            >
              ‹ 上一页
            </button>

            <template v-for="(p, idx) in displayedPages" :key="idx">
              <span v-if="p === '...'" class="px-1 text-slate-500">...</span>
              <button 
                v-else
                @click="goToPage(p as number)"
                :class="currentPage === p ? 'bg-cyan-600 text-white font-bold border-cyan-500 shadow-sm shadow-cyan-950' : 'bg-slate-900 text-slate-300 hover:bg-slate-800 border-slate-700'"
                class="px-2.5 py-1 rounded-lg border transition text-xs cursor-pointer min-w-[28px] text-center"
              >
                {{ p }}
              </button>
            </template>

            <button 
              @click="nextPage" 
              :disabled="currentPage >= totalPages"
              class="px-2.5 py-1 rounded-lg border border-slate-700 bg-slate-900 text-slate-300 hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed transition cursor-pointer"
            >
              下一页 ›
            </button>
          </div>

          <!-- 快速跳转 -->
          <div class="flex items-center gap-1 font-mono">
            <span>前往</span>
            <input 
              v-model="jumpPageInput" 
              @keyup.enter="handleJumpPage"
              type="number" 
              min="1" 
              :max="totalPages"
              class="bg-slate-900 border border-slate-700 text-white rounded-lg px-2 py-1 w-12 text-center text-xs outline-none focus:border-cyan-500"
            />
            <span>页</span>
            <button 
              @click="handleJumpPage"
              class="bg-slate-800 hover:bg-slate-700 text-slate-300 px-2 py-1 rounded-lg border border-slate-700 text-xs transition cursor-pointer"
            >
              Go
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 弹窗 1: 修改分机密码弹窗 -->
    <div v-if="editingExt" class="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl w-full max-w-md overflow-hidden shadow-2xl space-y-4">
        <div class="bg-[#0F172A] border-b border-slate-800 px-6 py-4 flex items-center justify-between">
          <span class="font-bold text-white text-base">修改分机密码 - {{ editingExt.extension }}</span>
          <button @click="editingExt = null" class="text-slate-400 hover:text-white text-lg">✕</button>
        </div>
        <div class="px-6 space-y-4 text-sm">
          <div class="bg-slate-900/80 p-3 rounded-xl border border-slate-800 text-xs text-slate-300 font-mono space-y-1">
            <div>分机号码: <span class="text-cyan-400 font-bold">{{ editingExt.extension }}</span></div>
            <div>当前 Context: <span class="text-slate-200">{{ editingExt.context }}</span></div>
            <div>配置文件: <span class="text-cyan-300">{{ editingExt.xml_path || `conf/directory/default/${editingExt.extension}.xml` }}</span></div>
          </div>
          <div>
            <label class="block text-slate-400 mb-1.5 font-semibold">新 SIP 认证密码 (Password) *</label>
            <div class="relative">
              <input 
                v-model="editPasswordInput" 
                :type="showEditPassword ? 'text' : 'password'"
                placeholder="输入新密码..." 
                class="w-full bg-slate-900 border border-slate-700 text-white rounded-xl px-4 py-2.5 outline-none font-mono text-base font-bold focus:border-cyan-500 pr-10"
              >
              <button 
                type="button"
                @click="showEditPassword = !showEditPassword"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-white"
                :title="showEditPassword ? '隐藏密码' : '显示密码'"
              >
                <svg v-if="showEditPassword" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                  <line x1="1" y1="1" x2="23" y2="23"/>
                </svg>
                <svg v-else class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
                </svg>
              </button>
            </div>
          </div>
          <div class="text-xs text-slate-400 leading-relaxed bg-cyan-950/30 border border-cyan-900/50 p-3 rounded-xl">
            提交后 Sidecar 会更新分机配置并请求 FreeSWITCH 重新加载。最终注册结果以新的注册事件为准。
          </div>
        </div>
        <div class="bg-[#0F172A] border-t border-slate-800 px-6 py-3.5 flex justify-end gap-3">
          <button @click="editingExt = null" class="bg-slate-800 hover:bg-slate-700 text-slate-300 px-4 py-2 rounded-lg text-sm font-medium">取消</button>
          <button @click="submitUpdatePassword" class="bg-cyan-600 hover:bg-cyan-500 text-white px-5 py-2 rounded-lg text-sm font-bold shadow-lg shadow-cyan-950">确认修改 (XML + Reload)</button>
        </div>
      </div>
    </div>

    <!-- 弹窗 2: 新建分机弹窗 -->
    <div v-if="showCreateModal" class="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl w-full max-w-md overflow-hidden shadow-2xl space-y-4">
        <div class="bg-[#0F172A] border-b border-slate-800 px-6 py-4 flex items-center justify-between">
          <span class="font-bold text-white text-base">新建 SIP 分机账号</span>
          <button @click="showCreateModal = false" class="text-slate-400 hover:text-white text-lg">✕</button>
        </div>
        <div class="px-6 space-y-3.5 text-sm">
          <div>
            <label class="block text-slate-400 mb-1.5 font-medium">分机号码 (Extension) *</label>
            <input v-model="newExt.extension" type="text" placeholder="例如: 1020" class="w-full bg-slate-900 border border-slate-700 text-white rounded-lg px-3.5 py-2.5 outline-none font-mono text-sm focus:border-cyan-500">
          </div>
          <div>
            <label class="block text-slate-400 mb-1.5 font-medium">SIP 认证密码 (Password) *</label>
            <input v-model="newExt.password" type="password" autocomplete="new-password" placeholder="设置认证密码..." class="w-full bg-slate-900 border border-slate-700 text-white rounded-lg px-3.5 py-2.5 outline-none font-mono text-sm focus:border-cyan-500">
          </div>
          <div class="grid grid-cols-2 gap-3.5">
            <div>
              <label class="block text-slate-400 mb-1.5 font-medium">Context</label>
              <input v-model="newExt.context" type="text" class="w-full bg-slate-900 border border-slate-700 text-white rounded-lg px-3.5 py-2.5 outline-none font-mono text-sm">
            </div>
            <div>
              <label class="block text-slate-400 mb-1.5 font-medium">呼叫组</label>
              <input v-model="newExt.callgroup" type="text" class="w-full bg-slate-900 border border-slate-700 text-white rounded-lg px-3.5 py-2.5 outline-none font-mono text-sm">
            </div>
          </div>
        </div>
        <div class="bg-[#0F172A] border-t border-slate-800 px-6 py-3.5 flex justify-end gap-3">
          <button @click="showCreateModal = false" class="bg-slate-800 hover:bg-slate-700 text-slate-300 px-4 py-2 rounded-lg text-sm">取消</button>
          <button @click="submitCreateExt" class="bg-cyan-600 hover:bg-cyan-500 text-white px-5 py-2 rounded-lg text-sm font-medium">确认开户 (XML + Reload)</button>
        </div>
      </div>
    </div>

    <!-- 弹窗 3: 分机详情弹窗 -->
    <div v-if="selectedExt" class="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div class="bg-[#131C31] border border-slate-800 rounded-2xl w-full max-w-lg overflow-hidden shadow-2xl space-y-4">
        <div class="bg-[#0F172A] border-b border-slate-800 px-6 py-4 flex items-center justify-between">
          <span class="font-bold text-white text-base">SIP 分机详情与信令快照 - {{ selectedExt.extension }}</span>
          <button @click="selectedExt = null" class="text-slate-400 hover:text-white text-lg">✕</button>
        </div>
        <div class="px-6 space-y-3.5 text-sm font-mono">
          <div class="bg-slate-900 p-4 rounded-xl border border-slate-800 space-y-2">
            <div class="flex justify-between"><span class="text-slate-400">分机号码:</span><span class="text-cyan-400 font-bold text-lg">{{ selectedExt.extension }}</span></div>
            <div class="flex justify-between"><span class="text-slate-400">注册在线状态:</span><span :class="selectedExt.is_registered ? 'text-emerald-400 font-bold' : 'text-slate-500'">{{ selectedExt.ping_status }}</span></div>
            <div class="flex justify-between"><span class="text-slate-400">User-Agent:</span><span class="text-white font-semibold">{{ selectedExt.user_agent || '-' }}</span></div>
            <div class="flex justify-between"><span class="text-slate-400">网络地址:</span><span class="text-slate-200">{{ selectedExt.network_ip }}:{{ selectedExt.network_port }} ({{ selectedExt.network_proto }})</span></div>
            <div class="flex justify-between"><span class="text-slate-400">Contact URI:</span><span class="text-slate-400 truncate text-xs">{{ selectedExt.url || '-' }}</span></div>
          </div>
          <div class="bg-slate-900 p-4 rounded-xl border border-slate-800 space-y-1.5 text-slate-300">
            <div class="flex justify-between items-center">
              <span class="text-slate-400">SIP 认证密码:</span>
              <div class="flex items-center gap-2">
                <span class="text-amber-300 font-bold">{{ revealedPasswords[selectedExt.extension] ? (selectedExt.password || '$${default_password}') : '••••••••' }}</span>
                <button @click="togglePassword(selectedExt.extension)" class="text-slate-400 hover:text-white text-xs">
                  {{ revealedPasswords[selectedExt.extension] ? '隐藏' : '显示' }}
                </button>
                <button @click="openEditPassword(selectedExt)" class="text-cyan-400 hover:text-cyan-300 text-xs ml-1">修改</button>
              </div>
            </div>
            <div class="flex justify-between"><span class="text-slate-400">XML 配置路径:</span><span class="text-cyan-300 text-xs truncate max-w-xs">{{ selectedExt.xml_path || `conf/directory/default/${selectedExt.extension}.xml` }}</span></div>
            <div class="flex justify-between"><span class="text-slate-400">呼叫 Context:</span><span>{{ selectedExt.context }}</span></div>
            <div class="flex justify-between"><span class="text-slate-400">呼叫组:</span><span>{{ selectedExt.callgroup }}</span></div>
            <div class="flex justify-between"><span class="text-slate-400">Call-ID Token:</span><span class="text-slate-400 text-xs">{{ selectedExt.token || '-' }}</span></div>
          </div>
        </div>
        <div class="bg-[#0F172A] border-t border-slate-800 px-6 py-3.5 flex justify-end gap-3">
          <button @click="selectedExt = null" class="bg-slate-800 hover:bg-slate-700 text-slate-300 px-4 py-2 rounded-lg text-sm">关闭</button>
          <button @click="$emit('dial-ext', selectedExt.extension); selectedExt = null" class="bg-cyan-600 hover:bg-cyan-500 text-white px-5 py-2 rounded-lg text-sm font-medium">发起呼叫</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { ExtensionItem } from '@/api/telephony'
import { telephonyApi } from '@/api/telephony'

const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'dial-ext', ext: string): void
  (e: 'flush-reg', ext: string): void
  (e: 'toast', msg: string): void
}>()

// 组合查询表单状态
const queryForm = ref({
  extension: '',
  status: 'all',
  proto: 'all',
  context: '',
  keyword: ''
})

const appliedFilters = ref({
  extension: '',
  status: 'all',
  proto: 'all',
  context: '',
  keyword: ''
})

// 分页状态
const currentPage = ref(1)
const pageSize = ref(10)
const jumpPageInput = ref('')

const extensionList = ref<ExtensionItem[]>([])
const loadError = ref('')

function handleSearch() {
  appliedFilters.value = { ...queryForm.value }
  currentPage.value = 1
}

function handleReset() {
  queryForm.value = {
    extension: '',
    status: 'all',
    proto: 'all',
    context: '',
    keyword: ''
  }
  appliedFilters.value = { ...queryForm.value }
  currentPage.value = 1
}

const selectedExt = ref<ExtensionItem | null>(null)
const editingExt = ref<ExtensionItem | null>(null)
const editPasswordInput = ref('')
const showEditPassword = ref(false)
const revealedPasswords = ref<Record<string, boolean>>({})

function togglePassword(ext: string) {
  revealedPasswords.value[ext] = !revealedPasswords.value[ext]
}
const newExt = ref({
  extension: '',
  password: '',
  context: '',
  callgroup: ''
})

onMounted(() => {
  loadExtensions()
})

async function loadExtensions() {
  try {
    const list = await telephonyApi.getAllExtensions()
    extensionList.value = list
    loadError.value = ''
  } catch (err: any) {
    loadError.value = `分机列表加载失败：${err.message || '连接异常'}`
  }
}

const registeredCount = computed(() => {
  return extensionList.value.filter(e => e.is_registered).length
})

const filteredExtensions = computed(() => {
  return extensionList.value.filter(e => {
    // 1. 分机号模糊过滤
    if (appliedFilters.value.extension.trim()) {
      const extKw = appliedFilters.value.extension.trim().toLowerCase()
      if (!e.extension.toLowerCase().includes(extKw)) return false
    }

    // 2. 注册状态筛选
    if (appliedFilters.value.status === 'registered' && !e.is_registered) return false
    if (appliedFilters.value.status === 'unregistered' && e.is_registered) return false

    // 3. 协议类型筛选
    if (appliedFilters.value.proto !== 'all') {
      const p = appliedFilters.value.proto.toLowerCase()
      if (!e.network_proto?.toLowerCase().includes(p)) return false
    }

    // 4. Context / 呼叫组筛选
    if (appliedFilters.value.context.trim()) {
      const c = appliedFilters.value.context.trim().toLowerCase()
      const matchContext = e.context?.toLowerCase().includes(c)
      const matchGroup = e.callgroup?.toLowerCase().includes(c)
      if (!matchContext && !matchGroup) return false
    }

    // 5. 客户端终端 / IP / URI 关键字筛选
    if (appliedFilters.value.keyword.trim()) {
      const kw = appliedFilters.value.keyword.trim().toLowerCase()
      const matchExt = e.extension.toLowerCase().includes(kw)
      const matchIp = e.network_ip?.toLowerCase().includes(kw)
      const matchAgent = e.user_agent?.toLowerCase().includes(kw)
      const matchUrl = e.url?.toLowerCase().includes(kw)
      if (!matchExt && !matchIp && !matchAgent && !matchUrl) return false
    }

    return true
  })
})

const totalPages = computed(() => {
  return Math.max(1, Math.ceil(filteredExtensions.value.length / pageSize.value))
})

const pagedExtensions = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredExtensions.value.slice(start, start + pageSize.value)
})

const displayedPages = computed(() => {
  const total = totalPages.value
  const current = currentPage.value
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1)
  }
  const pages: (number | string)[] = []
  pages.push(1)
  if (current > 3) {
    pages.push('...')
  }
  const start = Math.max(2, current - 1)
  const end = Math.min(total - 1, current + 1)
  for (let i = start; i <= end; i++) {
    pages.push(i)
  }
  if (current < total - 2) {
    pages.push('...')
  }
  pages.push(total)
  return pages
})

function goToPage(p: number) {
  if (p >= 1 && p <= totalPages.value) {
    currentPage.value = p
  }
}

function prevPage() {
  if (currentPage.value > 1) {
    currentPage.value--
  }
}

function nextPage() {
  if (currentPage.value < totalPages.value) {
    currentPage.value++
  }
}

function handleJumpPage() {
  const p = parseInt(jumpPageInput.value, 10)
  if (!isNaN(p)) {
    goToPage(p)
    jumpPageInput.value = ''
  }
}

function openDetail(ext: ExtensionItem) {
  selectedExt.value = ext
}

function openEditPassword(ext: ExtensionItem) {
  editingExt.value = ext
  editPasswordInput.value = ext.password && ext.password !== '$${default_password}' ? ext.password : ''
  showEditPassword.value = false
}

async function submitUpdatePassword() {
  if (!editingExt.value || !editPasswordInput.value) return
  const extNum = editingExt.value.extension
  const newPwd = editPasswordInput.value
  try {
    await telephonyApi.updateExtensionPassword({
      extension: extNum,
      password: newPwd,
      context: editingExt.value.context || '',
      callgroup: editingExt.value.callgroup || ''
    })
    emit('toast', `分机 ${extNum} 密码已更新，最终注册状态以 FreeSWITCH 事件为准`)
    editingExt.value = null
    editPasswordInput.value = ''
    await loadExtensions()
    emit('refresh')
  } catch (err: any) {
    emit('toast', `密码更新失败: ${err.message || 'Error'}`)
  }
}

async function submitCreateExt() {
  if (!newExt.value.extension || !newExt.value.password) {
    emit('toast', '请填写分机号和密码')
    return
  }
  try {
    await telephonyApi.createExtension(newExt.value)
    showCreateModal.value = false
    emit('toast', `分机 ${newExt.value.extension} 已创建，最终注册状态以 FreeSWITCH 事件为准`)
    newExt.value = { extension: '', password: '', context: '', callgroup: '' }
    await loadExtensions()
    emit('refresh')
  } catch (err: any) {
    emit('toast', '分机创建异常: ' + (err.message || 'Error'))
  }
}
</script>
