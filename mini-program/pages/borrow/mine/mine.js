const request = require('../../../utils/request');

const STATUS_LABELS = {
  PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回',
  WITHDRAWN: '已撤回', LENT: '已借出', RETURNED: '已归还'
};

Page({
  data: {
    rows: [],
    stats: { total: 0, lent: 0, overdue: 0, pending: 0 },
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
        this.loadStats();
      })
      .catch((error) => {
        this.setData({ error: (error && error.message) || '加载失败', loading: false });
      });
  },

  loadStats() {
    request.get('/api/mobile/borrow/statistics', {})
      .then((data) => {
        this.setData({
          stats: {
            total: Number(data.total || 0),
            lent: Number(data.lent || 0),
            overdue: Number(data.overdue || 0),
            pending: Number(data.pending || 0)
          }
        });
      })
      .catch(() => {});
  },

  goApply() {
    wx.navigateTo({ url: '/pages/borrow/apply/apply' });
  },

  goDetail(event) {
    wx.navigateTo({ url: '/pages/borrow/detail/detail?id=' + event.currentTarget.dataset.id });
  },

  renew(event) {
    const dataset = event.currentTarget.dataset;
    wx.navigateTo({ url: '/pages/borrow/renew/renew?id=' + dataset.id + '&end=' + (dataset.end || '') });
  },

  giveBack(event) {
    const borrowId = event.currentTarget.dataset.id;
    wx.showModal({
      title: '确认归还',
      content: '确认登记归还该借阅单？',
      success: (res) => {
        if (!res.confirm) return;
        request.post('/api/mobile/borrow/return', { borrowId: borrowId })
          .then(() => {
            wx.showToast({ title: '已归还' });
            this.load();
          })
          .catch((error) => wx.showToast({ title: (error && error.message) || '归还失败', icon: 'none' }));
      }
    });
  },

  goHome() {
    wx.reLaunch({ url: '/pages/home/home' });
  }
});
