const refreshCollectionsBtn = document.getElementById('refreshCollectionsBtn');
const collectionSelect = document.getElementById('collectionSelect');
const limitInput = document.getElementById('limitInput');
const loadRecordsBtn = document.getElementById('loadRecordsBtn');
const prevBtn = document.getElementById('prevBtn');
const nextBtn = document.getElementById('nextBtn');
const pageInfo = document.getElementById('pageInfo');
const overviewBox = document.getElementById('overviewBox');
const recordsContainer = document.getElementById('recordsContainer');

let collections = [];
let currentOffset = 0;

function setOverview(data) {
    overviewBox.textContent = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
}

async function fetchJson(url) {
    const response = await fetch(url);
    const text = await response.text();
    if (!response.ok) {
        throw new Error(text || `HTTP ${response.status}`);
    }
    return text ? JSON.parse(text) : {};
}

function renderCollectionOptions() {
    collectionSelect.innerHTML = '';
    if (!collections.length) {
        const option = document.createElement('option');
        option.value = '';
        option.textContent = '暂无集合';
        collectionSelect.appendChild(option);
        return;
    }
    collections.forEach(item => {
        const option = document.createElement('option');
        option.value = item.name;
        option.textContent = `${item.name} (count=${item.count})`;
        collectionSelect.appendChild(option);
    });
}

function renderRecords(result) {
    recordsContainer.innerHTML = '';
    const records = Array.isArray(result.records) ? result.records : [];
    if (!records.length) {
        const empty = document.createElement('div');
        empty.style.padding = '12px';
        empty.style.border = '1px solid #e5e7eb';
        empty.style.borderRadius = '10px';
        empty.textContent = '当前分页没有记录';
        recordsContainer.appendChild(empty);
        return;
    }
    records.forEach((record, index) => {
        const card = document.createElement('div');
        card.style.border = '1px solid #e5e7eb';
        card.style.borderRadius = '10px';
        card.style.padding = '12px';
        card.style.background = '#ffffff';
        const title = document.createElement('div');
        title.style.fontWeight = '600';
        title.style.color = '#111827';
        title.style.marginBottom = '8px';
        title.textContent = `#${index + 1 + currentOffset} | id=${record.id || ''}`;

        const docLabel = document.createElement('div');
        docLabel.style.marginBottom = '6px';
        docLabel.style.color = '#374151';
        docLabel.textContent = 'document:';

        const docPre = document.createElement('pre');
        docPre.style.background = '#f8fafc';
        docPre.style.padding = '8px';
        docPre.style.borderRadius = '8px';
        docPre.style.whiteSpace = 'pre-wrap';
        docPre.style.wordBreak = 'break-all';
        docPre.textContent = record.document == null ? '' : String(record.document);

        const metaLabel = document.createElement('div');
        metaLabel.style.marginTop = '8px';
        metaLabel.style.marginBottom = '6px';
        metaLabel.style.color = '#374151';
        metaLabel.textContent = 'metadata:';

        const metaPre = document.createElement('pre');
        metaPre.style.background = '#f8fafc';
        metaPre.style.padding = '8px';
        metaPre.style.borderRadius = '8px';
        metaPre.style.whiteSpace = 'pre-wrap';
        metaPre.style.wordBreak = 'break-all';
        metaPre.textContent = JSON.stringify(record.metadata, null, 2);

        card.appendChild(title);
        card.appendChild(docLabel);
        card.appendChild(docPre);
        card.appendChild(metaLabel);
        card.appendChild(metaPre);
        recordsContainer.appendChild(card);
    });
}

async function refreshCollections() {
    refreshCollectionsBtn.disabled = true;
    setOverview('正在加载集合列表...');
    try {
        const result = await fetchJson('/api/embedding/chroma/collections');
        collections = Array.isArray(result.collections) ? result.collections : [];
        renderCollectionOptions();
        setOverview(result);
    } catch (e) {
        setOverview(`加载集合失败: ${e.message}`);
    } finally {
        refreshCollectionsBtn.disabled = false;
    }
}

async function loadRecords() {
    const collectionName = collectionSelect.value;
    if (!collectionName) {
        setOverview('请先选择集合');
        return;
    }
    const limit = Number(limitInput.value) || 20;
    loadRecordsBtn.disabled = true;
    setOverview('正在加载记录...');
    try {
        const url = `/api/embedding/chroma/records?collectionName=${encodeURIComponent(collectionName)}&limit=${encodeURIComponent(limit)}&offset=${encodeURIComponent(currentOffset)}`;
        const result = await fetchJson(url);
        pageInfo.textContent = `offset=${result.offset} | limit=${result.limit} | total=${result.count}`;
        setOverview(result);
        renderRecords(result);
    } catch (e) {
        setOverview(`加载记录失败: ${e.message}`);
        recordsContainer.innerHTML = '';
    } finally {
        loadRecordsBtn.disabled = false;
    }
}

refreshCollectionsBtn.addEventListener('click', async () => {
    currentOffset = 0;
    await refreshCollections();
});

loadRecordsBtn.addEventListener('click', loadRecords);

prevBtn.addEventListener('click', async () => {
    const limit = Number(limitInput.value) || 20;
    currentOffset = Math.max(currentOffset - limit, 0);
    await loadRecords();
});

nextBtn.addEventListener('click', async () => {
    const limit = Number(limitInput.value) || 20;
    currentOffset += limit;
    await loadRecords();
});

refreshCollections();
