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
    archives: [],
    options: [],
    index: -1,
    reason: '',
    start: '',
    end: '',
    loading: true,
    error: '',
    submitting: false
  },

  onLoad() {
    const today = new Date();
    this.setData({ start: format(today), end: format(addDays(today, 7)) });
    this.loadArchives();
  },

  loadArchives() {
    this.setData({ loading: true, error: '' });
    request.get('/api/mobile/borrow/archives', {})
      .then((list) => {
        const archives = list || [];
        this.setData({
          archives,
          options: archives.map((item) => item.archive_code + '　' + item.title),
          loading: false
        });
      })
      .catch((error) => {
        this.setData({ error: (error && error.message) || '档案加载失败', loading: false });
      });
  },

  onArchiveChange(event) {
    this.setData({ index: Number(event.detail.value) });
  },

  onStartChange(event) {
    this.setData({ start: event.detail.value });
  },

  onEndChange(event) {
    this.setData({ end: event.detail.value });
  },

  onReasonInput(event) {
    this.setData({ reason: event.detail.value });
  },

  submit() {
    const archive = this.data.archives[this.data.index];
    if (!archive) return wx.showToast({ title: '请选择档案', icon: 'none' });
    if (!this.data.reason.trim()) return wx.showToast({ title: '请填写借阅事由', icon: 'none' });
    if (this.data.end < this.data.start) return wx.showToast({ title: '归还日期不能早于借阅日期', icon: 'none' });
    if (this.data.submitting) return;

    this.setData({ submitting: true });
    request.post('/api/mobile/borrow/apply', {
      archiveId: archive.archive_id,
      reason: this.data.reason.trim(),
      startDate: this.data.start,
      endDate: this.data.end
    })
      .then(() => {
        wx.showToast({ title: '已提交' });
        setTimeout(() => wx.navigateBack(), 800);
      })
      .catch((error) => {
        wx.showToast({ title: (error && error.message) || '提交失败', icon: 'none' });
      })
      .finally(() => this.setData({ submitting: false }));
  }
});
