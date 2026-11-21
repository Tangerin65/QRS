const app = getApp();
const config = require('../../config/index');
const request = require('../../utils/request');
const navigation = require('../../utils/navigation');
const session = require('../../utils/session');

// M12-B 个人中心：头像与姓名首字占位、基础资料、绑定状态、退出/切换账号。
// 复用成员3 的 utils/session.js，不建第二套登录状态；
// 资料取自后端 /api/mobile/auth/profile，服务端每次都会重新校验令牌与账号状态。
Page({
  data: {
    bound: false, profileName: '未登录',
    profileRole: '绑定后使用已授权的业务功能',
    busy: false, message: '', configured: Boolean(config.baseUrl),
    // 基础资料（未登录或读取失败时为空，页面按空态显示，不显示假数据）
    userNo: '', loginName: '', deptName: '', phonenumber: '',
    archivist: false, boundAt: '', permissionCount: 0,
    profileState: 'idle', profileError: ''
  },

  onLoad() {
    // 订阅会话变化：登录态一变就刷新资料，退出后立刻清空
    this._unsubscribe = app.subscribeSession((state) => {
      const bound = state.status === 'authenticated' && Boolean(state.user);
      this.setData({
        bound,
        profileName: bound ? state.user.userName : '未登录',
        profileRole: bound ? state.user.roleName :
          state.status === 'expired' ? '登录已失效，请重新登录' : '绑定后使用已授权的业务功能',
        permissionCount: bound && Array.isArray(state.user.permissions) ? state.user.permissions.length : 0
      });
      if (bound) {
        this.loadProfile();
      } else {
        this.clearProfile();
      }
    });
  },

  onShow() {
    if (!this._signingOut) this.login(false);
  },

  onUnload() {
    this._closed = true;
    if (this._unsubscribe) this._unsubscribe();
  },

  // 清空资料，避免退出后仍残留上一个账号的信息
  clearProfile() {
    this.setData({
      userNo: '', loginName: '', deptName: '', phonenumber: '',
      archivist: false, boundAt: '', profileState: 'idle', profileError: ''
    });
  },

  // 读取个人资料：失败只在卡片内提示，不影响退出等本地操作
  loadProfile() {
    if (!config.baseUrl || !session.token()) {
      return Promise.resolve();
    }
    this.setData({ profileState: 'loading', profileError: '' });
    return request.get(config.api.profile).then((data) => {
      if (this._closed) return;
      if (!data || typeof data !== 'object') {
        throw new Error('资料返回格式不正确，请联系管理员');
      }
      this.setData({
        profileState: 'success', profileError: '',
        userNo: String(data.userNo || ''),
        loginName: String(data.loginName || ''),
        deptName: String(data.deptName || ''),
        phonenumber: String(data.phonenumber || ''),
        // 字段名与后端 /api/mobile/auth/profile 保持一致
        archivist: data.arcArchivist === true,
        boundAt: String(data.boundAt || '')
      });
    }).catch((error) => {
      if (this._closed) return;
      this.setData({ profileState: 'error', profileError: error.message });
    });
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

  retryProfile() { this.loadProfile(); },

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
