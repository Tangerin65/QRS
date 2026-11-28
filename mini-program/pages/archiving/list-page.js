// 归档子页共用的列表骨架：加载状态、分页、错误重试、逐项展开、单项操作。
// 与 borrow / archive-workbench 的写法保持一致：request-state 三态 + 「加载更多」按钮。
// 页面传入：fetch（返回 {rows,total,hasMore}）、query（附加过滤条件）、action（单项操作）。

function createListPage(spec) {
  const options = spec || {};
  const pageSize = options.pageSize || 6;
  const fetch = options.fetch;
  const action = options.action;

  const page = {
    data: Object.assign({
      rows: [],
      total: 0,
      page: 1,
      hasMore: false,
      status: 'loading',
      error: '',
      loadingMore: false,
      expandedId: '',
      actionId: '',
      actionError: ''
    }, options.initial || {}),

    onLoad() {
      this._closed = false;
      this._ticket = 0;
      this.reload();
    },

    onUnload() {
      this._closed = true;
    },

    onPullDownRefresh() {
      this.reload().then(() => {
        if (wx.stopPullDownRefresh) wx.stopPullDownRefresh();
      });
    },

    buildQuery(pageNum) {
      const query = { pageNum, pageSize };
      if (options.query) Object.assign(query, options.query.call(this) || {});
      return query;
    },

    load(pageNum) {
      const ticket = ++this._ticket;
      const first = pageNum <= 1;
      this.setData(first
        ? { status: 'loading', error: '' }
        : { loadingMore: true });
      return fetch(this.buildQuery(pageNum)).then((result) => {
        if (this._closed || ticket !== this._ticket) return;
        const rows = first ? result.rows : this.data.rows.concat(result.rows);
        this.setData({
          rows,
          total: Number(result.total || rows.length),
          page: pageNum,
          hasMore: Boolean(result.hasMore),
          status: rows.length ? 'ready' : 'empty',
          loadingMore: false
        });
      }).catch((error) => {
        if (this._closed || ticket !== this._ticket) return;
        const message = (error && error.message) || '加载失败，请重试';
        if (first) {
          this.setData({ status: 'error', error: message, loadingMore: false });
        } else {
          this.setData({ loadingMore: false });
          wx.showToast({ title: message, icon: 'none' });
        }
      });
    },

    reload() {
      return this.load(1);
    },

    retry() {
      this.reload();
    },

    loadMore() {
      if (this.data.hasMore && !this.data.loadingMore) this.load(this.data.page + 1);
    },

    // 切换筛选胶囊（页面 data 里有 filter / filters 时使用）
    setFilter(event) {
      const value = event.currentTarget.dataset.value;
      if (value === this.data.filter || this.data.status === 'loading') return;
      this.setData({ filter: value, expandedId: '', actionError: '' });
      this.reload();
    },

    toggle(event) {
      const id = String(event.currentTarget.dataset.id);
      this.setData({
        expandedId: this.data.expandedId === id ? '' : id,
        actionError: ''
      });
    },

    runAction(event) {
      const id = String(event.currentTarget.dataset.id);
      const name = event.currentTarget.dataset.action;
      if (!action || this.data.actionId) return;
      this.setData({ actionId: id, actionError: '' });
      return action(id, name, this).then((result) => {
        if (this._closed) return;
        wx.showToast({ title: (result && result.message) || '操作已完成', icon: 'none' });
        return this.reload();
      }).catch((error) => {
        if (this._closed) return;
        this.setData({ actionError: (error && error.message) || '操作未完成，请稍后重试' });
      }).then(() => {
        if (!this._closed) this.setData({ actionId: '' });
      });
    }
  };

  Object.assign(page, options.methods || {});
  return page;
}

module.exports = createListPage;
