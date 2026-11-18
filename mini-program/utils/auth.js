function loginCode() {
  return new Promise((resolve, reject) => {
    wx.login({
      success(result) {
        if (result.code) {
          resolve(result.code);
          return;
        }
        reject(new Error('微信登录凭证获取失败，请重试'));
      },
      fail() {
        reject(new Error('无法连接微信登录服务，请检查网络后重试'));
      }
    });
  });
}

module.exports = {
  loginCode
};
