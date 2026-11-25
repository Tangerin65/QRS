const request = require('../../../utils/request');

const STATUS_LABELS = {
  PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回',
  WITHDRAWN: '已撤回', LENT: '已借出', RETURNED: '已归还'
};

Page({
  data: {
    rows: [],
    loading: true,
    error: ''
  },

  onShow() {
    this.load();
  },

  load() {
    this.setData({ loading: true, error: '' });
    request.post('/api/mobile/borrow/list', { pageNum: 1, pageSize: 20 })
      .then((data) => {
        const rows = ((data && data.rows) || []).map((row) => Object.assign({}, row, {
          statusText: STATUS_LABELS[row.borrowStatus] || row.borrowStatus,
          overdueText: row.overdue ? '已逾期' : ''
        }));
        this.setData({ rows, loading: false });
      })
      .catch((error) => {
        this.setData({ error: (error && error.message) || '加载失败', loading: false });
      });
  },

  goApply() {
    wx.navigateTo({ url: '/pages/borrow/apply/apply' });
  },

  goHome() {
    wx.reLaunch({ url: '/pages/home/home' });
  }
});
