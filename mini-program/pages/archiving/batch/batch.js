const createListPage = require('../list-page');
const api = require('../api');

Page(createListPage({
  fetch: (query) => api.batch(query),
  action: (id) => api.retryBatch(id),
  query() {
    return { filter: this.data.filter };
  },
  initial: {
    filter: 'ALL',
    filters: [
      { value: 'ALL', label: '全部' },
      { value: 'OPEN', label: '进行中' },
      { value: 'RETURNED', label: '已退回' }
    ]
  }
}));
