const config = require('./config/index');
const auth = require('./utils/auth');
const request = require('./utils/request');
const session = require('./utils/session');

App({
  onLaunch() {
    // 清除初版曾经写入本地的凭证；业务数据与二维码凭证都不持久化。
    wx.removeStorageSync('records_session');
    this._refresh = null;
    this._networkListener = (state) => {
      if (!state.isConnected) wx.showToast({ title: '网络已断开，请联网后重试', icon: 'none' });
    };
    wx.onNetworkStatusChange(this._networkListener);
  },

  getSession: () => session.snapshot(),
  subscribeSession: (listener) => session.subscribe(listener),
  loginCode: () => auth.loginCode(),

  setSession(data, expectedRevision) {
    if (expectedRevision !== undefined && session.revision() !== expectedRevision) {
      throw new Error('登录状态已变化，请重新操作');
    }
    return session.set(data);
  },

  refreshSession(options = {}) {
    if (!config.baseUrl || session.signedOut() && !options.explicit) {
      return Promise.resolve(session.snapshot());
    }
    if (this._refresh) return this._refresh;
    const revision = session.revision();
    const token = session.token();
    session.loading();
    const task = token
      ? request.get(config.api.session)
      : auth.loginCode().then((code) => {
        if (revision !== session.revision()) throw new Error('登录状态已变化，请重新操作');
        return request.post(config.api.login, { code }, { auth: false });
      });
    const pending = task.then((data) => {
      if (revision !== session.revision()) return session.snapshot();
      if (data && data.bound === false) {
        session.clear('unbound');
        return session.snapshot();
      }
      return session.set(session.normalize(data, token));
    }).catch((error) => {
      // 已退出或已换账号的旧响应不再改变新会话。
      if (revision !== session.revision()) return session.snapshot();
      if (error.code === 'ACCOUNT_UNBOUND') {
        session.clear('unbound');
        return session.snapshot();
      }
      session.clear('error');
      throw error;
    }).finally(() => {
      if (this._refresh === pending) this._refresh = null;
    });
    this._refresh = pending;
    return pending;
  },

  logout() {
    const token = session.token();
    // 发起吊销请求后立即清理本地状态；失败时也不恢复旧账号。
    const revoke = token && config.baseUrl
      ? request.post(config.api.logout) : Promise.resolve();
    session.setSignedOut(true);
    session.clear();
    this._refresh = null;
    return revoke;
  }
});
