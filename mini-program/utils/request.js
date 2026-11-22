const config = require('../config/index');
const session = require('./session');

class RequestError extends Error {
  constructor(message, code, statusCode) {
    super(message);
    this.code = code;
    this.statusCode = statusCode || 0;
  }
}

// 字符串业务码一律优先于 HTTP 状态码匹配：后端已按 doc/m12-binding-integration.md §3.4
// 约定返回 {code:"PASSWORD_MISMATCH"} 这类字符串码，若只看状态码，
// 口令错误（403）会被说成「没有权限」、凭证未配置（500）会被说成「服务暂时不可用」。
const messages = {
  401: '登录已失效，请重新登录',
  403: '当前账号没有执行此操作的权限',
  404: '服务接口尚未接入，请联系管理员',
  409: '此二维码已处理或账号已绑定，请重新扫码',
  410: '二维码已过期，请刷新电脑端二维码后重新扫描',
  429: '操作过于频繁，请稍后重试',
  // 认证与令牌（M12-B）
  TOKEN_INVALID: '登录已失效，请重新登录',
  ACCOUNT_UNBOUND: '微信尚未绑定平台账号，请先扫码绑定',
  ACCOUNT_DISABLED: '账号已停用，请联系管理员',
  // 绑定（M12-B）
  PASSWORD_MISMATCH: '平台账号口令不正确，请重新输入',
  USER_NO_NOT_FOUND: '用户编号不存在，请核对管理员提供的编号',
  ALREADY_BOUND: '账号已绑定，重绑请联系管理员解除旧绑定',
  BIND_EXPIRED: '本次绑定请求已过期，请重新扫码',
  // 微信凭证与服务端状态
  WX_CODE_EMPTY: '微信登录凭证为空，请退出后重新进入小程序',
  WX_CODE_INVALID: '微信登录凭证无效，请退出后重新进入小程序',
  WX_CODE_USED: '微信登录凭证已被使用，请退出后重新进入小程序',
  WX_USER_RISK: '当前微信账号存在风险，已限制登录',
  SERVER_NOT_CONFIGURED: '服务端未配置小程序凭证（wx.miniapp），请联系管理员',
  WX_SERVICE_UNAVAILABLE: '微信登录服务暂时不可用，请稍后重试',
  RATE_LIMITED: '操作过于频繁，请稍后重试',
  // 二维码（PC 扫码登录）
  QR_EXPIRED: '二维码已过期，请刷新电脑端二维码后重新扫描',
  QR_USED: '二维码已使用，请刷新电脑端二维码后重新扫描',
  INVALID_QR: '二维码无效，请重新扫描本系统二维码'
};

function request(options = {}) {
  const base = (config.baseUrl || '').replace(/\/+$/, '');
  if (!base) return Promise.reject(new RequestError('服务尚未接入，请稍后重试', 'NOT_CONFIGURED'));
  // 公网地址仍强制 HTTPS；仅放行本机与内网的 http，便于开发者工具本地联调（端口不限）
  const isHttps = /^https:\/\/[^/?#]+(?:\/[^?#]*)?$/i.test(base);
  const isLocalHttp = /^http:\/\/(localhost|127\.0\.0\.1|\[::1\]|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})(?::\d{1,5})?(?:\/[^?#]*)?$/i.test(base);
  if (!isHttps && !isLocalHttp) {
    return Promise.reject(new RequestError('服务地址需要使用 HTTPS（本地调试允许 http://localhost:端口）', 'CONFIG_ERROR'));
  }
  if (typeof options.url !== 'string' || !/^\/[\w/-]+$/.test(options.url)) {
    return Promise.reject(new RequestError('请求地址不正确', 'INVALID_PATH'));
  }
  const token = options.auth === false ? '' : session.token();
  const revision = session.revision();
  const headers = { 'content-type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  return new Promise((resolve, reject) => {
    wx.request({
      url: base + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header: headers,
      timeout: config.requestTimeout,
      success(response) {
        const payload = response.data;
        const validPayload = payload && typeof payload === 'object' && !Array.isArray(payload);
        const code = validPayload ? payload.code : undefined;
        const numericCode = typeof code === 'number' || /^\d+$/.test(String(code))
          ? Number(code) : null;
        const success = numericCode === 0 || numericCode === 200;
        if (response.statusCode >= 200 && response.statusCode < 300 && validPayload && success) {
          resolve(payload.data);
          return;
        }
        const errorCode = numericCode || code || response.statusCode;
        const authExpired = response.statusCode === 401 || errorCode === 401 ||
          errorCode === 'TOKEN_INVALID' || errorCode === 'ACCOUNT_DISABLED' ||
          errorCode === 'ACCOUNT_UNBOUND';
        // 旧请求不能清空切换账号后建立的新会话。
        if (authExpired && token && revision === session.revision()) {
          session.clear('expired');
        }
        let message = messages[errorCode] || messages[response.statusCode];
        if (!message) {
          message = response.statusCode >= 500 ? '服务暂时不可用，请稍后重试' :
            '操作未完成，请稍后重试或联系管理员';
        }
        reject(new RequestError(message, errorCode, response.statusCode));
      },
      fail(error) {
        const timeout = error && /timeout/i.test(error.errMsg || '');
        reject(new RequestError(
          timeout ? '请求超时，请检查网络后重试' : '网络连接失败，请检查网络后重试',
          timeout ? 'TIMEOUT' : 'NETWORK_ERROR'
        ));
      }
    });
  });
}
request.get = (url, data, options = {}) => request(Object.assign({}, options, { url, data, method: 'GET' }));
request.post = (url, data, options = {}) => request(Object.assign({}, options, { url, data, method: 'POST' }));
request.RequestError = RequestError;
module.exports = request;
