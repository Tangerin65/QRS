const app = getApp();
const config = require('../../config/index');
const navigation = require('../../utils/navigation');

Page({
  data: {
    sessionStatus: 'loading', sessionMessage: '', bound: false,
    userName: '', roleName: '', quickActions: [],
    configured: Boolean(config.baseUrl), connectionText: ''
  },
  onLoad() {
    this._unsubscribe = app.subscribeSession((state) => this.applySession(state));
  },
  onShow() {
    this.loadSession();
  },
  onUnload() {
    this._closed = true;
    if (this._unsubscribe) this._unsubscribe();
  },
  loadSession() {
    this.setData({ sessionStatus: 'loading', sessionMessage: '' });
    return app.refreshSession().then((state) => {
      if (!this._closed) this.applySession(state);
    }).catch((error) => {
      if (!this._closed) this.setData({
        sessionStatus: 'error', sessionMessage: error.message, bound: false, quickActions: []
      });
    });
  },
  applySession(state) {
    const bound = state.status === 'authenticated' && Boolean(state.user);
    const actions = navigation.visible().filter((item) =>
      item.key !== 'home' && item.key !== 'profile');
    actions.push(bound
      ? { key: 'pc', title: '电脑扫码登录', desc: '确认本人发起的电脑登录', icon: '⌗' }
      : { key: 'scan', title: '绑定平台账号', desc: '扫描本人的账号二维码', icon: '⌗' });
    actions.push({ key: 'profile', title: '个人中心', desc: '账号状态与登录管理', icon: '◎' });
    this.setData({
      sessionStatus: state.status === 'loading' ? 'loading' : 'success',
      bound,
      userName: bound ? state.user.userName : '',
      roleName: bound ? state.user.roleName : '',
      quickActions: actions,
      connectionText: state.status === 'expired' ? '登录已失效，请到个人中心重新登录' :
        state.status === 'unbound' ? '微信尚未绑定，请扫描个人账号二维码' :
        '首次使用请先绑定平台账号'
    });
  },
  handleAction(event) {
    navigation.go(event.currentTarget.dataset.target);
  },
  handleRetry() {
    this.loadSession();
  },
  goScan() {
    navigation.go('scan');
  }
});
