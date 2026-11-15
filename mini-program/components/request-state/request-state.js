Component({
  properties: {
    status: { type: String, value: 'idle' },
    message: { type: String, value: '' },
    retryText: { type: String, value: '重新加载' }
  },

  methods: {
    handleRetry() {
      this.triggerEvent('retry');
    }
  }
});
