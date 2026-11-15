/**
 * 与成员 6 确认接口后修改此处。AppSecret 等认证秘密必须保存在后端。
 * baseUrl 留空时只展示公共页面，不请求服务或伪造登录成功。
 */
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
