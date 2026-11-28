const request = require('../../../utils/request');

function pad(value) {
  return value < 10 ? '0' + value : '' + value;
}

function format(date) {
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
}

function addDays(base, days) {
  const next = new Date(base.getTime());
  next.setDate(next.getDate() + days);
  return next;
}

Page({
  data: {
    borrowId: '',
    oldEnd: '',
    newEnd: '',
    reason: '',
    submitting: false
  },

  onLoad(options) {
    const oldEnd = (options && options.end) || '';
    const base = oldEnd ? new Date(oldEnd + 'T00:00:00') : new Date();
    this.setData({
      borrowId: options && options.id,
      oldEnd,
      newEnd: format(addDays(base, 7))
    });
  },

  onDateChange(event) {
    this.setData({ newEnd: event.detail.value });
  },

  onReasonInput(event) {
    this.setData({ reason: event.detail.value });
  },

  submit() {
    if (!this.data.newEnd) return wx.showToast({ title: '请选择新的应归还日', icon: 'none' });
    if (!this.data.reason.trim()) return wx.showToast({ title: '请填写续借原因', icon: 'none' });
    if (this.data.submitting) return;
    this.setData({ submitting: true });
    request.post('/api/mobile/borrow/renew', {
      borrowId: this.data.borrowId,
      requestedEndDate: this.data.newEnd,
      reason: this.data.reason.trim()
    })
      .then(() => {
        wx.showToast({ title: '续借已提交' });
        setTimeout(() => wx.navigateBack(), 800);
      })
      .catch((error) => {
        wx.showToast({ title: (error && error.message) || '提交失败', icon: 'none' });
      })
      .finally(() => this.setData({ submitting: false }));
  }
});
