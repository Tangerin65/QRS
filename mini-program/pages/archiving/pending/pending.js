const createListPage = require('../list-page');
const api = require('../api');

Page(createListPage({
  fetch: (query) => api.pending(query),
  action: (id) => api.joinBatch(id),
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
