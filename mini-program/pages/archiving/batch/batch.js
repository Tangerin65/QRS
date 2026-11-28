const createListPage = require('../list-page');
const api = require('../api');

Page(createListPage({
  fetch: (query) => api.batch(query),
  // 重新组批（契约 §4.8）：原批次不改写，返回新批次号
  action: (batchNo) => api.retryBatch(batchNo),
  confirm: () => ({
    title: '重新组批',
    content: '将按原批次的失败项重新校验并生成新批次，原批次保留不动，确认继续？',
    confirmText: '重新组批'
  }),
  query() {
    return { filter: this.data.filter };
  },
  initial: {
    filter: 'ALL',
    filters: [
      { value: 'ALL', label: '全部' },
      { value: 'OPEN', label: '进行中' },
      { value: 'RETURNED', label: '有失败项' }
    ],
    detail: null,
    detailStatus: '',
    detailError: ''
  },
  methods: {
    // 展开时按需拉 batch/detail（契约 §4.7）
    toggle(event) {
      const id = String(event.currentTarget.dataset.id);
      const opening = this.data.expandedId !== id;
      this.setData({
        expandedId: opening ? id : '',
        actionError: '',
        detail: null,
        detailStatus: opening ? 'loading' : '',
        detailError: ''
      });
      if (opening) this.loadDetail(id);
    },

    loadDetail(batchNo) {
      api.batchDetail(batchNo).then((detail) => {
        if (this._closed || this.data.expandedId !== batchNo) return;
        this.setData({ detail, detailStatus: 'ready' });
      }).catch((error) => {
        if (this._closed || this.data.expandedId !== batchNo) return;
        this.setData({
          detailStatus: 'error',
          detailError: (error && error.message) || '批次明细加载失败'
        });
      });
    }
  }
}));
