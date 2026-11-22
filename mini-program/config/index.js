// 接口待联调；baseUrl 留空时仅展示页面。
module.exports = {
  appName: '档案管理系统',
  baseUrl: '',
  requestTimeout: 10000,
  api: {
    login: '/api/mobile/auth/login',
    session: '/api/mobile/auth/session',
    logout: '/api/mobile/auth/logout',
    // 成员6 提供的个人中心资料接口，返回头像占位、基础资料与绑定状态
    profile: '/api/mobile/auth/profile',
    bindAccount: '/api/mobile/account/bind',
    pcInspect: '/api/mobile/auth/pc/inspect',
    pcConfirm: '/api/mobile/auth/pc/confirm',
    pcCancel: '/api/mobile/auth/pc/cancel',
    archiveSearch: '/api/mobile/archives/search'
  }
};
