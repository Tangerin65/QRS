const config = require('../config/index');
const session = require('./session');

class RequestError extends Error {
  constructor(message, code, statusCode) {
    super(message);
    this.code = code;
    this.statusCode = statusCode || 0;
  }
}

const messages = {
  401: '登录已失效，请重新登录',
  403: '当前账号没有执行此操作的权限',
  404: '服务接口尚未接入，请联系管理员',
  409: '此二维码已处理或账号已绑定，请重新扫码',
  410: '二维码已过期，请刷新电脑端二维码后重新扫描',
  429: '操作过于频繁，请稍后重试',
  ACCOUNT_DISABLED: '账号已停用，请联系管理员',
  ACCOUNT_UNBOUND: '微信尚未绑定平台账号，请先扫码绑定',
  ALREADY_BOUND: '账号已绑定，重绑请联系管理员解除旧绑定',
  QR_EXPIRED: '二维码已过期，请刷新电脑端二维码后重新扫描',
  QR_USED: '二维码已使用，请刷新电脑端二维码后重新扫描',
  INVALID_QR: '二维码无效，请重新扫描本系统二维码'
};

function request(options = {}) {
  const base = (config.baseUrl || '').replace(/\/+$/, '');
  if (!base) return Promise.reject(new RequestError('服务尚未接入，请稍后重试', 'NOT_CONFIGURED'));
  if (!/^https:\/\/[^/?#]+(?:\/[^?#]*)?$/.test(base)) {
    return Promise.reject(new RequestError('服务地址需要使用 HTTPS，请检查环境配置', 'CONFIG_ERROR'));
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
          errorCode === 'ACCOUNT_DISABLED' || errorCode === 'ACCOUNT_UNBOUND';
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

request.upload = function (options = {}) {
  //文件上传
  const base = (config.baseUrl || '').replace(/\/+$/, '');//地址拼接
  const token = session.token();
  const header = token ? { Authorization: 'Bearer ' + token } : {};

  return new Promise((resolve, reject) => {
    const task = wx.uploadFile({
      url: base + options.url,
      filePath: options.filePath,
      name: options.name || 'file',
      formData: options.formData || {},
      header,

      success(response) {
        const payload = JSON.parse(response.data);

        if (payload.code === 0 || payload.code === 200) {
          resolve(payload.data);
        } else {
          reject(new RequestError(
            payload.msg || '文件上传失败',
            payload.code,
            response.statusCode
          ));
        }
      },

      fail() {
        reject(new RequestError(
          '网络连接失败，请重试',
          'NETWORK_ERROR'
        ));
      }
    });

    if (task && options.onProgress) {
      task.onProgressUpdate((event) => {
        options.onProgress(event.progress);
      });
    }
  });
};


request.RequestError = RequestError;
module.exports = request;
