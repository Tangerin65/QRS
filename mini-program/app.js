const config = require('./config/index');
const auth = require('./utils/auth');
const request = require('./utils/request');
const session = require('./utils/session');

App({
  onLaunch() {
    // 兼容初版，清除旧会话缓存。
    wx.removeStorageSync('records_session');
    this._refresh = null;
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
      // 忽略切换账号前的响应。
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
    // 本地立即退出，再等待服务端吊销结果。
    const revoke = token && config.baseUrl
      ? request.post(config.api.logout) : Promise.resolve();
    session.setSignedOut(true);
    session.clear();
    this._refresh = null;
    return revoke;
  }
});
