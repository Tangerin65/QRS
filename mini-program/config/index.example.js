// 本地配置模板：复制为同目录下的 index.js 后按本机环境填写。
// index.js 已被 .gitignore 忽略，不提交 Git；每位成员可各自设置端口/地址，互不影响。
// baseUrl 留空时只展示公共页面（不发起登录请求）。
// 本地联调示例（后端 local profile 默认 8080）：'http://localhost:8080'
// 真机调试需换成电脑局域网地址（如 'http://192.168.1.20:8080'）；发布必须是已备案的 HTTPS 合法域名。
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
