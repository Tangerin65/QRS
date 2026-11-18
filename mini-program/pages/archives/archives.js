const navigation = require('../../utils/navigation');
const app = getApp();
Page({
  onLoad() {
    this._unsubscribe = app.subscribeSession((state) => {
      if (state.status !== 'loading') navigation.guard('archives');
    });
  },
  onShow() {
    app.refreshSession().then(() => navigation.guard('archives')).catch(() => {
      navigation.guard('archives');
    });
  },
  onUnload() { if (this._unsubscribe) this._unsubscribe(); },
  goHome() { navigation.go('home'); }
});
