const createListPage = require('../list-page');
const api = require('../api');

Page(createListPage({
  fetch: (query) => api.status(query),
  action: (id) => api.recheck(id),
  query() {
    return { filter: this.data.filter, keyword: this.data.keyword };
  },
  initial: {
    keyword: '',
    filter: 'ALL',
    filters: [
      { value: 'ALL', label: '全部期限' },
      { value: 'PERMANENT', label: '永久' },
      { value: 'FIXED', label: '有限期' },
      { value: 'UNKNOWN', label: '无法识别' }
    ]
  },
  methods: {
    onKeyword(event) {
      this.setData({ keyword: event.detail.value });
    },

    search() {
      if (this.data.status === 'loading') return;
      this.setData({ expandedId: '', actionError: '' });
      this.reload();
    }
  }
}));
