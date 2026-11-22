const navigation = require('../../utils/navigation');
const request = require('../../utils/request');
const session = require('../../utils/session');
const { api } = require('../../config/index');
const app = getApp();
const PAGE_SIZE = 20;

Page({
  data: {
    keyword: '', categories: [{ id: '', name: '全部分类' }], categoryIndex: 0,
    sorts: ['最新登记', '形成日期从新到旧', '形成日期从旧到新', '档号升序'], sortIndex: 0,
    rows: [], pageNum: 1, total: 0, hasMore: false, status: 'loading', message: ''
  },
  onLoad() {
    this._requestId = 0;
    this._unsubscribe = app.subscribeSession(() => {
      const revision = session.revision();
      if (revision === this._revision) return;
      this._revision = revision;
      this._requestId++;
      this._query = null;
      this.setData({
        rows: [], total: 0, hasMore: false, status: 'loading', message: '',
        categories: [{ id: '', name: '全部分类' }], categoryIndex: 0
      });
    });
  },
  async onShow() {
    try {
      await app.refreshSession();
      if (!this._closed && navigation.guard('archives')) this.search();
    } catch (error) {
      if (!this._closed) navigation.guard('archives');
    }
  },
  onUnload() {
    this._closed = true;
    if (this._unsubscribe) this._unsubscribe();
  },
  inputKeyword(event) {
    this.setData({ keyword: event.detail.value });
  },
  chooseCategory(event) {
    this.setData({ categoryIndex: Number(event.detail.value) });
  },
  chooseSort(event) {
    this.setData({ sortIndex: Number(event.detail.value) });
  },
  search() {
    const sort = [
      ['createTime', 'desc'], ['formingDate', 'desc'], ['formingDate', 'asc'], ['archiveCode', 'asc']
    ][this.data.sortIndex];
    this._query = {
      keyword: this.data.keyword.trim(),
      categoryId: this.data.categories[this.data.categoryIndex].id,
      orderByColumn: sort[0],
      isAsc: sort[1]
    };
    this.setData({ rows: [], pageNum: 1, total: 0, hasMore: false });
    return this.load(1);
  },
  nextPage() {
    if (this.data.hasMore && this.data.status !== 'loading') return this.load(this.data.pageNum + 1);
  },
  prevPage() {
    if (this.data.pageNum > 1 && this.data.status !== 'loading') return this.load(this.data.pageNum - 1);
  },
  retry() {
    if (!session.token()) return navigation.guard('archives');
    return this.load(this._pendingPage || 1);
  },
  async load(pageNum) {
    if (!this._query || !navigation.guard('archives')) return;
    const id = ++this._requestId;
    this._pendingPage = pageNum;
    this.setData({ status: 'loading', message: '', rows: [] });
    try {
      const data = await request.get(api.archiveSearch, { ...this._query, pageNum, pageSize: PAGE_SIZE });
      if (this._closed || id !== this._requestId) return;
      const categories = [{ id: '', name: '全部分类' }, ...data.categories];
      const selected = this.data.categories[this.data.categoryIndex].id;
      const categoryIndex = Math.max(0, categories.findIndex(item => item.id === selected));
      const rows = data.rows.map(row => ({
        ...row,
        statusText: ({ DRAFT: '草稿', SUBMITTED: '已提交', ARCHIVED: '已归档' })[row.archiveStatus] || row.archiveStatus
      }));
      this.setData({
        rows, categories, categoryIndex, pageNum, total: data.total,
        hasMore: pageNum * PAGE_SIZE < data.total, status: rows.length ? 'success' : 'empty'
      });
    } catch (error) {
      if (this._closed) return;
      if (!session.token()) return navigation.guard('archives');
      if (id !== this._requestId) return;
      this.setData({ status: 'error', message: error.message });
    }
  }
});
