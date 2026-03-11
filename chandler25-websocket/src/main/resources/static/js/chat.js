class WebSocketChat {
    constructor() {
        this.stompClient = null;
        this.username = null;
        this.connected = false;
        this.typingTimer = null;

        this.initializeElements();
        this.setupEventListeners();
    }

    initializeElements() {
        this.elements = {
            usernameInput: document.getElementById('usernameInput'),
            joinBtn: document.getElementById('joinBtn'),
            messageInput: document.getElementById('messageInput'),
            sendBtn: document.getElementById('sendBtn'),
            messages: document.getElementById('messages'),
            userList: document.getElementById('userList'),
            connectionStatus: document.getElementById('connectionStatus'),
            typingStatus: document.getElementById('typingStatus'),
            onlineCount: document.getElementById('onlineCount')
        };
    }

    setupEventListeners() {
        // 回车键发送消息
        this.elements.messageInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.sendMessage();
            }
        });

        // 用户名输入框回车加入聊天
        this.elements.usernameInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.joinChat();
            }
        });
    }

    joinChat() {
        this.username = this.elements.usernameInput.value.trim();

        if (!this.username) {
            alert('请输入用户名');
            return;
        }

        if (this.username.length > 20) {
            alert('用户名不能超过20个字符');
            return;
        }

        this.connect();
    }

    connect() {
        // 禁用界面
        this.setUIState(false);
        this.elements.connectionStatus.textContent = '连接中...';

        // 创建SockJS连接
        const socket = new SockJS('/ws-chat');
        this.stompClient = Stomp.over(socket);

        // 连接配置
        this.stompClient.connect({},
            (frame) => this.onConnected(frame),
            (error) => this.onError(error)
        );
    }

    onConnected(frame) {
        console.log('连接成功:', frame);
        this.connected = true;
        this.elements.connectionStatus.textContent = '已连接';
        this.elements.connectionStatus.style.color = 'green';

        // 启用聊天界面
        this.setUIState(true);

        // 订阅公共消息
        this.stompClient.subscribe('/topic/public', (message) => {
            this.handlePublicMessage(JSON.parse(message.body));
        });

        // 订阅私人消息
        this.stompClient.subscribe('/user/queue/private', (message) => {
            this.handlePrivateMessage(JSON.parse(message.body));
        });

        // 订阅错误消息
        this.stompClient.subscribe('/user/queue/errors', (message) => {
            this.handleErrorMessage(JSON.parse(message.body));
        });

        // 订阅在线用户列表
        this.stompClient.subscribe('/topic/online.users', (message) => {
            this.updateOnlineUsers(JSON.parse(message.body));
        });

        // 订阅输入状态
        this.stompClient.subscribe('/topic/typing', (message) => {
            this.handleTypingIndicator(JSON.parse(message.body));
        });

        // 发送加入消息
        this.sendJoinMessage();
    }

    onError(error) {
        console.error('连接错误:', error);
        this.elements.connectionStatus.textContent = '连接失败';
        this.elements.connectionStatus.style.color = 'red';
        this.setUIState(false);

        // 3秒后重试
        setTimeout(() => this.connect(), 3000);
    }

    sendJoinMessage() {
        const joinMessage = {
            sender: this.username,
            type: 'JOIN',
            content: this.username + ' 加入了聊天室'
        };

        this.stompClient.send("/app/chat.addUser", {}, JSON.stringify(joinMessage));
    }

    sendMessage() {
        const messageContent = this.elements.messageInput.value.trim();

        if (!messageContent) return;

        const chatMessage = {
            sender: this.username,
            type: 'CHAT',
            content: messageContent,
            timestamp: new Date().toISOString()
        };

        this.stompClient.send("/app/chat.send", {}, JSON.stringify(chatMessage));
        this.elements.messageInput.value = '';

        // 清除输入状态
        this.sendTypingStopped();
    }

    handlePublicMessage(message) {
        this.displayMessage(message, 'public');

        // 更新在线人数
        if (message.onlineCount !== undefined) {
            this.elements.onlineCount.textContent = message.onlineCount;
        }
    }

    handlePrivateMessage(message) {
        this.displayMessage(message, 'private');
    }

    handleErrorMessage(message) {
        this.displayMessage(message, 'error');

        // 如果是用户名冲突，允许重新输入
        if (message.content.includes('用户名已存在')) {
            this.connected = false;
            this.stompClient.disconnect();
            this.setUIState(false);
            this.elements.usernameInput.disabled = false;
            this.elements.joinBtn.disabled = false;
            alert(message.content);
        }
    }

    displayMessage(message, type) {
        const messageDiv = document.createElement('div');

        // 根据消息类型设置样式
        let cssClass = 'message';
        if (type === 'error') {
            cssClass += ' system error';
        } else if (message.type === 'JOIN' || message.type === 'LEAVE') {
            cssClass += ' system';
        } else if (message.sender === this.username) {
            cssClass += ' outgoing';
        } else {
            cssClass += ' incoming';
        }

        messageDiv.className = cssClass;

        // 格式化时间
        const timestamp = new Date(message.timestamp).toLocaleTimeString();

        // 构建消息内容
        let content = '';
        if (message.type === 'JOIN' || message.type === 'LEAVE') {
            content = `<div class="message-content">${message.content}</div>`;
        } else if (type === 'error') {
            content = `<div class="message-content" style="color: red;">❌ ${message.content}</div>`;
        } else {
            content = `
                <div class="message-sender"><strong>${this.escapeHtml(message.sender)}</strong></div>
                <div class="message-content">${this.escapeHtml(message.content)}</div>
                <div class="message-time">${timestamp}</div>
            `;
        }

        messageDiv.innerHTML = content;
        this.elements.messages.appendChild(messageDiv);

        // 滚动到底部
        this.elements.messages.scrollTop = this.elements.messages.scrollHeight;
    }

    handleTyping() {
        if (!this.connected) return;

        // 清除之前的计时器
        if (this.typingTimer) {
            clearTimeout(this.typingTimer);
        }

        // 发送输入中状态
        const typingMessage = {
            sender: this.username,
            type: 'TYPING',
            content: '正在输入...'
        };

        this.stompClient.send("/app/chat.typing", {}, JSON.stringify(typingMessage));

        // 设置计时器，停止输入2秒后发送停止输入
        this.typingTimer = setTimeout(() => {
            this.sendTypingStopped();
        }, 2000);
    }

    sendTypingStopped() {
        if (this.stompClient && this.connected) {
            const stopTypingMessage = {
                sender: this.username,
                type: 'TYPING',
                content: ''
            };
            this.stompClient.send("/app/chat.typing", {}, JSON.stringify(stopTypingMessage));
        }
    }

    handleTypingIndicator(message) {
        if (message.content && message.sender !== this.username) {
            this.elements.typingStatus.textContent = `${message.sender} 正在输入...`;
        } else {
            this.elements.typingStatus.textContent = '';
        }
    }

    updateOnlineUsers(users) {
        this.elements.userList.innerHTML = '';
        this.elements.onlineCount.textContent = users.length;

        users.forEach(username => {
            const userItem = document.createElement('li');
            userItem.className = 'user-item';
            userItem.textContent = username;
            this.elements.userList.appendChild(userItem);
        });
    }

    setUIState(connected) {
        this.elements.usernameInput.disabled = connected;
        this.elements.joinBtn.disabled = connected;
        this.elements.joinBtn.textContent = connected ? '已加入' : '加入聊天室';
        this.elements.messageInput.disabled = !connected;
        this.elements.sendBtn.disabled = !connected;
    }

    escapeHtml(unsafe) {
        return unsafe
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }

    disconnect() {
        if (this.stompClient) {
            this.stompClient.disconnect();
        }
        this.connected = false;
        this.elements.connectionStatus.textContent = '已断开';
        this.elements.connectionStatus.style.color = 'red';
        this.setUIState(false);
    }
}

// 全局函数供HTML调用
let chatApp;

function joinChat() {
    if (!chatApp) {
        chatApp = new WebSocketChat();
    }
    chatApp.joinChat();
}

function sendMessage() {
    if (chatApp) {
        chatApp.sendMessage();
    }
}

function handleKeyPress(event) {
    if (event.key === 'Enter') {
        sendMessage();
    }
}

function handleTyping() {
    if (chatApp) {
        chatApp.handleTyping();
    }
}

// 页面卸载时断开连接
window.addEventListener('beforeunload', () => {
    if (chatApp) {
        chatApp.disconnect();
    }
});