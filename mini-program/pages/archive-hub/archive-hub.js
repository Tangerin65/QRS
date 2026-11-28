const api = require('../archiving/api');
const sections = require('./sections');

function render(list, summary) {
  return list.map((section) => {
    const value = Number(summary[section.valueKey] || 0);
    const hintValue = section.hintKey ? Number(summary[section.hintKey] || 0) : 0;
    return {
      key: section.key,
      module: section.module,
      title: section.title,
      desc: section.desc,
      path: section.path,
      glyph: section.glyph,
      tone: section.tone,
      disabled: Boolean(section.disabled),
      valueText: section.valueUnit ? value + ' ' + section.valueUnit : String(value),
      hint: section.hint ? section.hint.replace('{n}', hintValue) : ''
    };
  });
}

Page({
  data: {
    sections: [],
    summary: { pending: 0, batchOpen: 0, batchReturned: 0, locationAvailable: 0 },
    status: 'loading',
    error: ''
  },

  onLoad() {
    this._closed = false;
    // 会话只在内存里：冷启动直接进 hub 时先用 wx.login 恢复绑定，再拉汇总（与其他页一致）
    const app = getApp();
    const ready = app && app.refreshSession ? app.refreshSession().catch(() => {}) : Promise.resolve();
    ready.then(() => {
      if (!this._closed) this.load();
    });
  },

  onUnload() {
    this._closed = true;
  },

  onPullDownRefresh() {
    this.load().then(() => {
      if (wx.stopPullDownRefresh) wx.stopPullDownRefresh();
    });
  },

  load() {
    this.setData({ status: 'loading', error: '' });
    return api.hub().then((summary) => {
      if (this._closed) return;
      this.setData({
        status: 'ready',
        summary,
        sections: render(sections, summary)
      });
    }).catch((error) => {
      if (this._closed) return;
      this.setData({
        status: 'error',
        error: (error && error.message) || '加载失败，请重试'
      });
    });
  },

  retry() {
    this.load();
  },

  open(event) {
    const key = event.currentTarget.dataset.key;
    const section = this.data.sections.filter((item) => item.key === key)[0];
    if (!section) return;
    if (section.disabled) {
      wx.showToast({ title: '该入口待接入', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: section.path });
  },

  goHome() {
    wx.reLaunch({ url: '/pages/home/home' });
  }
});
