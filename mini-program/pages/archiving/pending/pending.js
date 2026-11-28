const createListPage = require('../list-page');
const api = require('../api');

// 存储介质（契约 §4.4 storageMedia）：与 PC 提交弹窗同一组枚举，末项留给「暂不登记」
const MEDIUMS = [
  { value: 'PAPER', label: '纸质' },
  { value: 'ELECTRONIC', label: '电子' },
  { value: 'MIXED', label: '混合' },
  { value: '', label: '暂不登记' }
];

Page(createListPage({
  fetch: (query) => api.pending(query),
  // 加入批次 = 单件组批并立即归档（契约 §4.4）：只传 archiveCode，不传 version；
  // 介质、电子存储位置、库位按页面选择带上，未选则不传（后端给「未登记」）。
  action: (archiveCode, name, page) => {
    const payload = { archiveCode };
    const data = page.data;
    if (data.storageMedia) payload.storageMedia = data.storageMedia;
    if (data.needsElectronic && data.electronicLocation) payload.electronicLocation = data.electronicLocation;
    if (data.locationId) payload.locationId = data.locationId;
    return api.joinBatch(payload);
  },
  confirm: () => ({
    title: '提交归档',
    content: '提交后该档案立即归档并生成归档批次，流程不可撤销，确认提交？',
    confirmText: '提交归档',
    cancelText: '再想想'
  }),
  query() {
    return { filter: this.data.filter };
  },
  initial: {
    filter: 'ALL',
    filters: [
      { value: 'ALL', label: '全部' },
      { value: 'NEED_FIX', label: '需补正' },
      { value: 'UNKNOWN', label: '期限无法识别' }
    ],
    mediums: MEDIUMS,
    storageMedia: '',
    needsElectronic: false,
    electronicLocation: '',
    locations: [],
    locationId: ''
  },
  methods: {
    // 展开时重置介质/库位选择，并按需拉一次库位候选（契约 §4.9）
    toggle(event) {
      const id = String(event.currentTarget.dataset.id);
      const opening = this.data.expandedId !== id;
      this.setData({
        expandedId: opening ? id : '',
        actionError: '',
        storageMedia: '',
        needsElectronic: false,
        electronicLocation: '',
        locationId: ''
      });
      if (opening && !this.data.locations.length) this.loadLocations();
    },

    loadLocations() {
      api.location({ pageNum: 1, pageSize: 6 }).then((result) => {
        if (this._closed) return;
        const locations = (result.rows || []).filter((row) => Number(row.status) === 0);
        this.setData({ locations });
      }).catch(() => {
        // 库位只是可选加成，拉不到不影响归档
      });
    },

    pickMedium(event) {
      const value = String(event.currentTarget.dataset.value || '');
      this.setData({
        storageMedia: value,
        needsElectronic: value === 'ELECTRONIC' || value === 'MIXED'
      });
    },

    onElectronic(event) {
      this.setData({ electronicLocation: event.detail.value });
    },

    pickLocation(event) {
      const value = String(event.currentTarget.dataset.value || '');
      this.setData({ locationId: this.data.locationId === value ? '' : value });
    }
  }
}));
