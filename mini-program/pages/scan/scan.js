const app = getApp();
const config = require('../../config/index');
const request = require('../../utils/request');
const session = require('../../utils/session');
const qr = require('../../utils/qr');

Page({
  data: {
    mode: 'bind', status: 'idle', message: '', userNo: '',
    busy: false, scanning: false, configured: Boolean(config.baseUrl),
    pcReady: false, pcDone: false, pcCancelled: false,
    deviceName: '', location: '', expiresText: '', secondsLeft: 0,
    accountName: '', bound: false,
    // 绑定口令是否已填（只用于控制按钮可点状态，口令本身不进 data）
    passwordFilled: false
  },

  onLoad(options = {}) {
    this._operation = 0;
    this._ticket = '';
    this.setMode(options.mode === 'pc' ? 'pc' : 'bind');
    this._unsubscribe = app.subscribeSession((state) => {
      const bound = state.status === 'authenticated' && Boolean(state.user);
      this.setData({ bound, accountName: bound ? state.user.userName : '' });
      if (this._ticket && state.status !== 'authenticated' && state.status !== 'loading') {
        this.clearTicket();
        this.setData({ status: 'error', message: '登录状态已变化，请重新登录后扫码' });
      }
    });
  },

  onShow() { this.startTimer(); },
  onHide() { this.stopTimer(); },
  onUnload() {
    this._closed = true;
    this._operation += 1;
    this.clearTicket();
    this.clearPassword();
    if (this._unsubscribe) this._unsubscribe();
  },

  setMode(mode) {
    this.clearTicket();
    this.clearPassword();
    this.setData({
      mode, status: 'idle', message: '', userNo: '',
      pcDone: false, pcCancelled: false
    });
    wx.setNavigationBarTitle({ title: mode === 'pc' ? '确认电脑登录' : '绑定平台账号' });
  },

  changeMode(event) {
    if (this.data.busy || this.data.scanning) return;
    this._operation += 1;
    this.setMode(event.currentTarget.dataset.mode === 'pc' ? 'pc' : 'bind');
  },

  // 清掉绑定时输入的口令，切换模式、重新扫码、页面卸载时都要调用
  clearPassword() {
    this._bindPassword = '';
    if (this.data.passwordFilled) this.setData({ passwordFilled: false });
  },

  clearTicket() {
    this._ticket = '';
    this._deadline = 0;
    this.stopTimer();
    this.setData({ pcReady: false, secondsLeft: 0, deviceName: '', location: '', expiresText: '' });
  },

  stopTimer() {
    if (this._timer) clearInterval(this._timer);
    this._timer = null;
  },

  startTimer() {
    this.stopTimer();
    if (!this._ticket) return;
    this.tick();
    if (this._ticket) this._timer = setInterval(() => this.tick(), 1000);
  },

  tick() {
    const secondsLeft = Math.max(0, Math.ceil((this._deadline - Date.now()) / 1000));
    this.setData({ secondsLeft });
    if (!secondsLeft && !this.data.busy) {
      this.clearTicket();
      this.setData({ status: 'error', message: '二维码已过期，请刷新电脑端二维码后重新扫描' });
    }
  },

  startScan() {
    if (this.data.busy || this.data.scanning) return;
    this.clearTicket();
    // 重新扫码可能对应另一个账号，必须清掉上一次输入的口令
    this.clearPassword();
    this.setData({ userNo: '', status: 'idle', message: '', pcDone: false, pcCancelled: false });
    if (this.data.mode === 'bind' && session.snapshot().status === 'authenticated') {
      this.fail(new Error('当前账号已登录。如需切换绑定，请先到个人中心退出并联系管理员解除旧绑定。'));
      return;
    }
    if (this.data.mode === 'pc' && !config.baseUrl) {
      this.fail(new Error('服务尚未接入，暂不能确认电脑登录'));
      return;
    }
    const operation = ++this._operation;
    this.setData({ scanning: true });
    wx.scanCode({
      onlyFromCamera: true,
      scanType: ['qrCode'],
      success: (result) => {
        if (this._closed || operation !== this._operation) return;
        this.setData({ scanning: false });
        if (this.data.mode === 'pc') {
          const ticket = qr.parsePcTicket(result.result);
          if (!ticket) {
            this.fail(new Error('请扫描电脑登录页的动态二维码，个人账号二维码不能用于电脑登录'));
            return;
          }
          this.inspectPc(ticket, operation);
        } else {
          const userNo = qr.parseUserNo(result.result);
          if (!userNo) {
            this.fail(new Error('请扫描管理员为本人生成的账号绑定二维码'));
            return;
          }
          this.setData({ userNo });
        }
      },
      fail: (error) => {
        if (this._closed || operation !== this._operation) return;
        this.setData({ scanning: false });
        if (error && /cancel/i.test(error.errMsg || '')) return;
        this.fail(new Error('扫码失败，请在微信设置中允许使用摄像头后重试'));
      }
    });
  },

  confirmBind() {
    if (!this.data.userNo || this.data.busy || this.data.scanning) return;
    if (!config.baseUrl) {
      this.fail(new Error('服务尚未接入，暂不能绑定账号'));
      return;
    }
    // 首次绑定必须校验平台账号口令（后端强制要求）。
    if (!this.requirePassword()) return;
    wx.showModal({
      title: '确认绑定本人账号',
      content: '平台账号：' + this.data.userNo + '。请确认这是管理员为你本人生成的二维码。',
      confirmText: '确认绑定',
      success: (result) => {
        if (result.confirm && !this._closed) this.bindAccount();
      }
    });
  },

  // 口令输入：只存在实例字段上，避免出现在 data / 日志 / 页面源码里
  onPasswordInput(event) {
    this._bindPassword = String((event.detail && event.detail.value) || '');
    const filled = Boolean(this._bindPassword);
    if (this.data.passwordFilled !== filled) {
      this.setData({ passwordFilled: filled });
    }
  },

  // 校验口令是否已填写；未填写时给出明确提示并返回 false
  requirePassword() {
    const password = String(this._bindPassword || '');
    if (password) return true;
    this.setData({ status: 'error', message: '请先输入该账号的登录口令，再确认绑定' });
    return false;
  },

  /**
   * 以编程方式设置绑定口令。
   *
   * 仅供页面内部与自动化测试使用；用户侧入口是 WXML 的 onPasswordInput。
   * 这样测试不必模拟输入事件就能覆盖绑定主流程，也避免测试去改页面私有字段。
   */
  setBindPassword(value) {
    this._bindPassword = String(value == null ? '' : value);
    const filled = Boolean(this._bindPassword);
    if (this.data.passwordFilled !== filled) {
      this.setData({ passwordFilled: filled });
    }
    return filled;
  },

  bindAccount() {
    if (this.data.busy) return;
    if (session.snapshot().status === 'authenticated') {
      this.fail(new Error('当前已有登录账号，请先退出后再绑定'));
      return;
    }
    if (!this.requirePassword()) return;
    const password = String(this._bindPassword || '');
    const revision = session.revision();
    const operation = ++this._operation;
    const userNo = this.data.userNo;
    this.setData({ busy: true, status: 'loading', message: '' });
    app.loginCode().then((code) => {
      if (this._closed || operation !== this._operation || revision !== session.revision()) {
        throw new Error('登录状态已变化，请重新操作');
      }
      // password 由后端用 MD5(loginName + password + salt) 校验，前端不做任何处理
      return request.post(config.api.bindAccount, { code, userNo, password }, { auth: false });
    }).then((data) => {
      if (this._closed || operation !== this._operation) return;
      // 绑定成功后立即丢弃口令，不留在内存里
      this.clearPassword();
      app.setSession(data, revision);
      wx.showToast({ title: '绑定成功', icon: 'success' });
      wx.reLaunch({ url: '/pages/home/home' });
    }).catch((error) => {
      if (!this._closed && operation === this._operation) this.fail(error);
    }).finally(() => {
      if (!this._closed && operation === this._operation) this.setData({ busy: false });
    });
  },

  inspectPc(ticket, operation) {
    this.setData({ busy: true, status: 'loading', message: '' });
    app.refreshSession().then((state) => {
      if (this._closed || operation !== this._operation) return null;
      if (state.status !== 'authenticated') throw new Error('请先到个人中心登录或绑定账号，再重新扫描电脑二维码');
      this._accountRevision = session.revision();
      return request.post(config.api.pcInspect, { ticket });
    }).then((data) => {
      if (this._closed || operation !== this._operation || !data) return;
      if (this._accountRevision !== session.revision()) throw new Error('登录账号已变化，请重新扫码');
      if (data.state !== 'pending') throw new Error('二维码已失效或已处理，请刷新电脑端二维码后重新扫描');
      const expiresAt = typeof data.expiresAt === 'number' ? data.expiresAt : Date.parse(data.expiresAt);
      const serverTime = typeof data.serverTime === 'number' ? data.serverTime : Date.parse(data.serverTime);
      if (!Number.isFinite(expiresAt) || !Number.isFinite(serverTime)) {
        throw new Error('登录请求的有效期信息不完整，请联系管理员');
      }
      const remaining = expiresAt - serverTime;
      if (remaining <= 0) throw new Error('二维码已过期，请刷新电脑端二维码后重新扫描');
      this._ticket = ticket;
      this._deadline = Date.now() + remaining;
      this.setData({
        pcReady: true, status: 'idle',
        deviceName: String(data.deviceName || '电脑端'),
        location: String(data.location || '未提供位置'),
        expiresText: new Date(expiresAt).toLocaleString(),
        secondsLeft: Math.ceil(remaining / 1000)
      });
      this.startTimer();
    }).catch((error) => {
      if (!this._closed && operation === this._operation) {
        this.clearTicket();
        this.fail(error);
      }
    }).finally(() => {
      if (!this._closed && operation === this._operation) this.setData({ busy: false });
    });
  },

  confirmPc() { this.finishPc(true); },
  cancelPc() { this.finishPc(false); },

  finishPc(confirm) {
    if (this.data.busy || !this._ticket || !this.data.pcReady) return;
    if (Date.now() >= this._deadline) { this.tick(); return; }
    if (this._accountRevision !== session.revision() || session.snapshot().status !== 'authenticated') {
      this.clearTicket();
      this.fail(new Error('登录账号已变化，请重新扫码'));
      return;
    }
    const operation = ++this._operation;
    const ticket = this._ticket;
    this.setData({ busy: true, status: 'loading', message: '' });
    request.post(confirm ? config.api.pcConfirm : config.api.pcCancel, { ticket }).then((data) => {
      if (this._closed || operation !== this._operation) return;
      if (this._accountRevision !== session.revision()) throw new Error('登录状态已变化，请核对电脑端结果');
      const expectedState = confirm ? 'confirmed' : 'cancelled';
      if (!data || data.state !== expectedState) throw new Error('服务端未确认处理结果，请核对电脑端状态后重新扫码');
      this.clearTicket();
      this.setData({ status: 'idle', pcDone: confirm, pcCancelled: !confirm });
    }).catch((error) => {
      if (!this._closed && operation === this._operation) {
        this.clearTicket();
        this.fail(new Error(error.code === 'NETWORK_ERROR' || error.code === 'TIMEOUT'
          ? '处理结果尚未确认，请先核对电脑端状态，再刷新二维码重新扫描' : error.message));
      }
    }).finally(() => {
      if (!this._closed && operation === this._operation) this.setData({ busy: false });
    });
  },

  retry() {
    // 重新扫码，不重发上一次提交。
    this.startScan();
  },
  fail(error) { this.setData({ status: 'error', message: error.message || '操作未完成，请重新扫码' }); },
  goProfile() { wx.reLaunch({ url: '/pages/profile/profile' }); }
});
