const navigation = require('../../utils/navigation');
const session = require('../../utils/session');

Component({
  properties: { active: { type: String, value: 'home' } },
  data: { items: [] },
  lifetimes: {
    attached() {
      this._unsubscribe = session.subscribe(() => {
        this.setData({ items: navigation.visible() });
      });
    },
    detached() {
      if (this._unsubscribe) this._unsubscribe();
    }
  },
  methods: {
    navigate(event) {
      const key = event.currentTarget.dataset.key;
      if (key !== this.properties.active) navigation.go(key);
    },
    // 图标缺失时退化为文字，避免页签出现空白
    onIconError(event) {
      const index = event.currentTarget.dataset.index;
      if (index === undefined) return;
      this.setData({ ['items[' + index + '].iconFailed']: true });
    }
  }
});
