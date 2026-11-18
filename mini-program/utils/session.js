// 认证信息仅保存在内存中。重启后用 wx.login 的新 code 恢复绑定关系。
let current = null;
let version = 0;
let status = 'anonymous';
const listeners = [];
const LOGOUT_KEY = 'records_signed_out';

function snapshot() {
  return {
    status,
    user: current ? {
      userId: current.userId,
      userName: current.userName,
      roleName: current.roleName,
      permissions: current.permissions.slice()
    } : null
  };
}

function notify() {
  const state = snapshot();
  listeners.slice().forEach((listener) => listener(state));
}

function normalize(data, previousToken) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    throw new Error('账号信息返回格式不正确，请联系管理员');
  }
  const token = typeof data.token === 'string' ? data.token.trim() : previousToken;
  if (!token || typeof data.userId !== 'string' && typeof data.userId !== 'number' ||
      !String(data.userId).trim() || typeof data.userId === 'number' && !Number.isSafeInteger(data.userId)) {
    throw new Error('账号信息不完整，请联系管理员');
  }
  return {
    token,
    userId: String(data.userId),
    userName: String(data.userName || '平台用户'),
    roleName: String(data.roleName || '平台用户'),
    permissions: Array.isArray(data.permissions)
      ? data.permissions.filter((item) => typeof item === 'string') : []
  };
}

module.exports = {
  snapshot,
  revision: () => version,
  token: () => current ? current.token : '',
  normalize,
  set(data, previousToken) {
    current = normalize(data, previousToken);
    status = 'authenticated';
    version += 1;
    wx.removeStorageSync(LOGOUT_KEY);
    notify();
    return snapshot();
  },
  clear(nextStatus = 'anonymous') {
    current = null;
    status = nextStatus;
    version += 1;
    notify();
  },
  loading() {
    // 重新校验完成前隐藏所有业务入口；旧 token 只用于校验请求。
    status = 'loading';
    notify();
  },
  signedOut() {
    return wx.getStorageSync(LOGOUT_KEY) === true;
  },
  setSignedOut(value) {
    if (value) wx.setStorageSync(LOGOUT_KEY, true);
    else wx.removeStorageSync(LOGOUT_KEY);
  },
  subscribe(listener) {
    listeners.push(listener);
    listener(snapshot());
    return () => {
      const index = listeners.indexOf(listener);
      if (index !== -1) listeners.splice(index, 1);
    };
  }
};
