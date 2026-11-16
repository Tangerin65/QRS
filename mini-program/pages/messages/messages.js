const navigation = require('../../utils/navigation');
const app = getApp();
Page({
  onLoad() {
    this._unsubscribe = app.subscribeSession((state) => {
      if (state.status !== 'loading') navigation.guard('messages');
    });
  },
  onShow() {
    app.refreshSession().then(() => navigation.guard('messages')).catch(() => {
      navigation.guard('messages');
    });
  },
  onUnload() { if (this._unsubscribe) this._unsubscribe(); },
  goHome() { navigation.go('home'); }
});
