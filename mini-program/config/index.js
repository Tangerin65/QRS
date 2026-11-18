// 接口待联调；baseUrl 留空时仅展示页面。
module.exports = {
  appName: '档案管理系统',
  baseUrl: '',
  requestTimeout: 10000,
  api: {
    login: '/api/mobile/auth/login',
    session: '/api/mobile/auth/session',
    logout: '/api/mobile/auth/logout',
    bindAccount: '/api/mobile/account/bind',
    pcInspect: '/api/mobile/auth/pc/inspect',
    pcConfirm: '/api/mobile/auth/pc/confirm',
    pcCancel: '/api/mobile/auth/pc/cancel'
  }
};
