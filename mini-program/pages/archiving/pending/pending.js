const createListPage = require('../list-page');
const api = require('../api');

Page(createListPage({
  fetch: (query) => api.pending(query),
  // 加入批次 = 单件组批并立即归档（契约 §4.4），带 version 做乐观锁
  action: (archiveCode, name, page) => {
    const row = page.data.rows.filter((item) => item.archiveCode === archiveCode)[0] || {};
    return api.joinBatch({ archiveCode, version: row.version });
  },
  confirm: () => ({
    title: '提交归档',
    content: '提交后该档案立即归档并生成归档批次，流程不可撤销，确认提交？',
    confirmText: '提交归档',
    cancelText: '再想想'
  }),
  query() {
    return { filter: this.data.filter };
  },
  initial: {
    filter: 'ALL',
    filters: [
      { value: 'ALL', label: '全部' },
      { value: 'NEED_FIX', label: '需补正' },
      { value: 'UNKNOWN', label: '期限无法识别' }
    ]
  }
}));
