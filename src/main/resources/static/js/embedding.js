const textInput = document.getElementById('textInput');
const idInput = document.getElementById('idInput');
const metadataInput = document.getElementById('metadataInput');
const testBtn = document.getElementById('testBtn');
const ingestBtn = document.getElementById('ingestBtn');
const resultBox = document.getElementById('resultBox');

function setResult(data) {
    resultBox.textContent = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
}

function parseMetadata(raw) {
    if (!raw || !raw.trim()) {
        return {};
    }
    return JSON.parse(raw);
}

async function postJson(url, body) {
    const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    const text = await response.text();
    if (!response.ok) {
        throw new Error(text || `HTTP ${response.status}`);
    }
    return text ? JSON.parse(text) : {};
}

async function testEmbedding() {
    const text = textInput.value.trim();
    if (!text) {
        setResult('请输入文本');
        return;
    }
    testBtn.disabled = true;
    setResult('正在测试 embedding...');
    try {
        const result = await postJson('/api/embedding/test', { text });
        setResult(result);
    } catch (e) {
        setResult(`测试失败: ${e.message}`);
    } finally {
        testBtn.disabled = false;
    }
}

async function ingestText() {
    const text = textInput.value.trim();
    if (!text) {
        setResult('请输入文本');
        return;
    }
    ingestBtn.disabled = true;
    setResult('正在写入 ChromaDB...');
    try {
        const metadata = parseMetadata(metadataInput.value);
        const body = {
            text,
            id: idInput.value.trim() || null,
            metadata
        };
        const result = await postJson('/api/embedding/ingest', body);
        setResult(result);
    } catch (e) {
        setResult(`入库失败: ${e.message}`);
    } finally {
        ingestBtn.disabled = false;
    }
}

testBtn.addEventListener('click', testEmbedding);
ingestBtn.addEventListener('click', ingestText);
