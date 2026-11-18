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
    }
  }
});
