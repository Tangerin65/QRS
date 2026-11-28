const createListPage = require('../list-page');
const api = require('../api');

// 契约 §6：报告表未建时的固定文案（PC 与小程序统一，不再写「暂无校验报告」）
const REPORT_DISABLED = '校验报告未启用，请在数据库执行 sql/setup_qrs.sql 的 M10 段（arc_archive_check_report）后重试';

Page(createListPage({
  fetch: (query) => api.status(query),
  // 重新检查走通用校验接口（契约 §4.3），与待归档页共用
  action: (archiveCode, name, page) => api.check({ archiveCode }).then((result) => {
    const disabled = result && result.reportEnabled === false;
    page.setData({
      reportNotice: disabled ? REPORT_DISABLED : '',
      reportNoticeId: disabled ? archiveCode : ''
    });
    return result;
  }),
  query() {
    return { filter: this.data.filter, keyword: this.data.keyword };
  },
  initial: {
    keyword: '',
    filter: 'ALL',
    reportNotice: '',
    reportNoticeId: '',
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
      this.setData({ expandedId: '', actionError: '', reportNotice: '', reportNoticeId: '' });
      this.reload();
    }
  }
}));
