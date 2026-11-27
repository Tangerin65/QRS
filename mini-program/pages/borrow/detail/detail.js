const request = require('../../../utils/request');

const STATUS_LABELS = {
  PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回',
  WITHDRAWN: '已撤回', LENT: '已借出', RETURNED: '已归还'
};

const RENEW_LABELS = {
  PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回', WITHDRAWN: '已关闭'
};

Page({
  data: {
    record: null,
    approvals: [],
    renews: [],
    loading: true,
    error: ''
  },

  onLoad(options) {
    this.borrowId = options && options.id;
    this.load();
  },

  load() {
    this.setData({ loading: true, error: '' });
    request.get('/api/mobile/borrow/detail/' + this.borrowId, {})
      .then((record) => {
        const params = (record && record.params) || {};
        this.setData({
          record: Object.assign({}, record, {
            statusText: STATUS_LABELS[record.borrowStatus] || record.borrowStatus,
            overdueText: record.overdue ? '已逾期' : ''
          }),
          approvals: (params.approvals || []).map((item) => Object.assign({}, item, {
            resultText: item.result === 'APPROVED' ? '批准' : '驳回'
          })),
          renews: (params.renews || []).map((item) => Object.assign({}, item, {
            statusText: RENEW_LABELS[item.renewStatus] || item.renewStatus
          })),
          loading: false
        });
      })
      .catch((error) => {
        this.setData({ error: (error && error.message) || '加载失败', loading: false });
      });
  },

  renew() {
    const record = this.data.record || {};
    wx.navigateTo({ url: '/pages/borrow/renew/renew?id=' + this.borrowId + '&end=' + (record.endDate || '') });
  },

  giveBack() {
    wx.showModal({
      title: '确认归还',
      content: '确认登记归还该借阅单？',
      success: (res) => {
        if (!res.confirm) return;
        request.post('/api/mobile/borrow/return', { borrowId: this.borrowId })
          .then(() => {
            wx.showToast({ title: '已归还' });
            this.load();
          })
          .catch((error) => wx.showToast({ title: (error && error.message) || '归还失败', icon: 'none' }));
      }
    });
  }
});
