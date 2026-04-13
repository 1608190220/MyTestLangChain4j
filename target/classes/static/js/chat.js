/**
 * LangChain Chat 前端脚本
 * 处理消息发送、流式接收和UI更新
 * 支持 Markdown 和数学公式渲染
 */

// DOM 元素
const chatContainer = document.getElementById('chatContainer');
const messageInput = document.getElementById('messageInput');
const sendBtn = document.getElementById('sendBtn');

// 状态
let isStreaming = false;

/**
 * 初始化
 */
function init() {
    messageInput.focus();
    messageInput.addEventListener('keypress', handleKeyPress);
    sendBtn.addEventListener('click', sendMessage);
}

/**
 * 处理键盘事件
 */
function handleKeyPress(event) {
    if (event.key === 'Enter') {
        sendMessage();
    }
}

/**
 * HTML 转义，防止 XSS（用于纯文本）
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * 渲染 Markdown 文本
 */
function renderMarkdown(text) {
    if (typeof marked === 'undefined') {
        // 如果 marked 未加载，返回纯文本
        return escapeHtml(text);
    }

    // 配置 marked
    marked.setOptions({
        breaks: true,  // 支持换行
        gfm: true      // GitHub Flavored Markdown
    });

    return marked.parse(text);
}

/**
 * 渲染数学公式
 */
function renderMath(element) {
    if (typeof renderMathInElement !== 'undefined') {
        renderMathInElement(element, {
            delimiters: [
                {left: '$$', right: '$$', display: true},
                {left: '$', right: '$', display: false},
                {left: '\\[', right: '\\]', display: true},
                {left: '\\(', right: '\\)', display: false}
            ],
            throwOnError: false
        });
    }
}

/**
 * 滚动到底部
 */
function scrollToBottom() {
    chatContainer.scrollTop = chatContainer.scrollHeight;
}

/**
 * 添加用户消息
 */
function addUserMessage(text) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message message-user';
    messageDiv.innerHTML = `<div class="message-bubble">${escapeHtml(text)}</div>`;
    chatContainer.appendChild(messageDiv);
    scrollToBottom();
}

/**
 * 添加 AI 消息容器
 * 返回消息气泡元素，用于后续更新
 */
function addAIMessageContainer() {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message message-ai';

    const bubbleDiv = document.createElement('div');
    bubbleDiv.className = 'message-bubble markdown-body';

    messageDiv.appendChild(bubbleDiv);
    chatContainer.appendChild(messageDiv);
    scrollToBottom();

    return bubbleDiv;
}

/**
 * 添加打字指示器
 */
function addTypingIndicator() {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message message-ai typing-indicator-container';
    messageDiv.innerHTML = `
        <div class="typing-indicator">
            <span></span>
            <span></span>
            <span></span>
        </div>
    `;
    chatContainer.appendChild(messageDiv);
    scrollToBottom();
}

/**
 * 移除打字指示器
 */
function removeTypingIndicator() {
    const indicator = document.querySelector('.typing-indicator-container');
    if (indicator) {
        indicator.remove();
    }
}

/**
 * 显示错误消息
 */
function showError(message) {
    removeTypingIndicator();
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message message-ai';
    messageDiv.innerHTML = `<div class="message-bubble" style="color: #dc3545;">${escapeHtml(message)}</div>`;
    chatContainer.appendChild(messageDiv);
    scrollToBottom();
}

/**
 * 读取流式响应
 * @param {ReadableStreamDefaultReader} reader - 流读取器
 * @param {HTMLElement} messageBubble - 消息气泡元素
 */
async function readStream(reader, messageBubble) {
    const decoder = new TextDecoder('utf-8');
    let fullText = '';
    let buffer = '';

    try {
        while (true) {
            const { done, value } = await reader.read();

            // 解码数据（流结束时也要解码剩余数据）
            const chunk = decoder.decode(value, { stream: !done });
            buffer += chunk;

            // 检查是否包含结束标记
            const doneIndex = buffer.indexOf('[DONE]');
            if (doneIndex !== -1) {
                // 提取结束标记前的内容
                const content = buffer.substring(0, doneIndex);
                fullText += content;
                // 显示最终内容（渲染 Markdown）
                if (messageBubble) {
                    messageBubble.innerHTML = renderMarkdown(fullText);
                    renderMath(messageBubble);
                    scrollToBottom();
                }
                break;
            }

            // 如果没有结束标记且流已结束，处理剩余内容
            if (done) {
                fullText += buffer;
                if (messageBubble) {
                    messageBubble.innerHTML = renderMarkdown(fullText);
                    renderMath(messageBubble);
                    scrollToBottom();
                }
                break;
            }

            // 将 buffer 内容追加到 fullText 并清空 buffer
            fullText += buffer;
            buffer = '';

            // 更新显示（逐字效果，使用纯文本）
            if (messageBubble) {
                messageBubble.textContent = fullText;
                scrollToBottom();
            }
        }
    } catch (error) {
        console.error('读取流时出错:', error);
        // 发生错误时，显示已接收的内容
        if (messageBubble && fullText) {
            messageBubble.innerHTML = renderMarkdown(fullText);
            renderMath(messageBubble);
            scrollToBottom();
        }
        throw error;
    }

    return fullText;
}

/**
 * 发送消息
 */
async function sendMessage() {
    const message = messageInput.value.trim();
    if (!message || isStreaming) return;

    // 清空输入框
    messageInput.value = '';

    // 显示用户消息
    addUserMessage(message);

    // 设置状态
    isStreaming = true;
    sendBtn.disabled = true;

    // 显示打字指示器
    addTypingIndicator();

    try {
        // 发送请求
        const response = await fetch(`/api/chat/stream/text?message=${encodeURIComponent(message)}`);

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        // 获取 reader
        const reader = response.body.getReader();

        // 移除打字指示器并创建消息容器
        removeTypingIndicator();
        const messageBubble = addAIMessageContainer();

        // 读取流
        await readStream(reader, messageBubble);

    } catch (error) {
        console.error('Error:', error);
        showError(`发送失败: ${error.message}`);
    } finally {
        isStreaming = false;
        sendBtn.disabled = false;
    }
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', init);
