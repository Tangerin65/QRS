const app = getApp();
const config = require('../../config/index');
const navigation = require('../../utils/navigation');

Page({
  data: {
    sessionStatus: 'loading', sessionMessage: '', bound: false,
    userName: '', roleName: '', quickActions: [],
    configured: Boolean(config.baseUrl), connectionText: '', openGuide: ''
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
    const actions = navigation.visible({ workbench: true }).filter((item) =>
      item.key !== 'home' && item.key !== 'profile');
    actions.push(bound
      ? { key: 'pc', title: '电脑登录' }
      : { key: 'scan', title: '账号绑定' });
    actions.push({ key: 'profile', title: '个人中心' });
    this.setData({
      sessionStatus: state.status === 'loading' ? 'loading' : 'success',
      bound,
      userName: bound ? state.user.userName : '',
      roleName: bound ? state.user.roleName : '',
      quickActions: actions,
      connectionText: state.status === 'expired' ? '登录已失效，请到个人中心重新登录' :
        state.status === 'unbound' ? '微信尚未绑定平台账号' : '首次使用请先绑定账号'
    });
  },
  handleAction(event) {
    navigation.go(event.currentTarget.dataset.target);
  },
  handleRetry() {
    this.loadSession();
  },
  goScan() {
    navigation.go(this.data.bound ? 'pc' : 'scan');
  },
  goProfile() {
    navigation.go('profile');
  },
  toggleGuide(event) {
    const guide = event.currentTarget.dataset.guide;
    this.setData({ openGuide: this.data.openGuide === guide ? '' : guide });
  }
});
