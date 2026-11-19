const app = getApp();
const config = require('../../config/index');
const navigation = require('../../utils/navigation');

Page({
  data: {
    bound: false, profileName: '未登录',
    profileRole: '绑定后使用已授权的业务功能',
    busy: false, message: '', configured: Boolean(config.baseUrl)
  },
  onLoad() {
    this._unsubscribe = app.subscribeSession((state) => {
      const bound = state.status === 'authenticated' && Boolean(state.user);
      this.setData({
        bound,
        profileName: bound ? state.user.userName : '未登录',
        profileRole: bound ? state.user.roleName :
          state.status === 'expired' ? '登录已失效，请重新登录' : '绑定后使用已授权的业务功能'
      });
    });
  },
  onShow() {
    if (!this._signingOut) this.login(false);
  },
  onUnload() {
    this._closed = true;
    if (this._unsubscribe) this._unsubscribe();
  },
  login(explicit) {
    if (this.data.busy) return;
    this.setData({ busy: true, message: '' });
    app.refreshSession({ explicit: explicit === true }).catch((error) => {
      if (!this._closed) this.setData({ message: error.message });
    }).finally(() => {
      if (!this._closed) this.setData({ busy: false });
    });
  },
  resumeLogin() { this.login(true); },
  confirmLogout(event) {
    if (this.data.busy) return;
    const switching = event.currentTarget.dataset.switch === 'yes';
    wx.showModal({
      title: switching ? '切换账号' : '退出登录',
      content: switching
        ? '将先退出当前账号。若需绑定其他平台账号，请联系管理员解除旧绑定。'
        : '退出后再次使用时需要主动登录，是否继续？',
      confirmText: switching ? '退出并切换' : '退出登录',
      success: (result) => {
        if (result.confirm && !this._closed) this.signOut(switching);
      }
    });
  },
  signOut(switching) {
    this._signingOut = true;
    this.setData({ busy: true, message: '' });
    app.logout().then(() => {
      if (!this._closed && switching) navigation.go('scan');
    }).catch(() => {
      if (!this._closed) this.setData({
        message: '本机已退出，服务端退出结果未确认，请联网后重试登录。'
      });
    }).finally(() => {
      if (!this._closed) this.setData({ busy: false });
      this._signingOut = false;
    });
  },
  goScan() { navigation.go('scan'); },
  goPc() { navigation.go('pc'); }
});
