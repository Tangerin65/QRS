const createListPage = require('../list-page');
const api = require('../api');

Page(createListPage({
  fetch: (query) => api.location(query),
  query() {
    return { filter: this.data.filter };
  },
  initial: {
    filter: 'ALL',
    filters: [
      { value: 'ALL', label: '全部' },
      { value: 'PAPER', label: '纸质' },
      { value: 'ELECTRONIC', label: '电子' },
      { value: 'MIXED', label: '混合' }
    ]
  }
}));
