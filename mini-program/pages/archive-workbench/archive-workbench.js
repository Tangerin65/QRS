const app = getApp();
const navigation = require('../../utils/navigation');
const session = require('../../utils/session');
const api = require('../../utils/archive-api');

const P = navigation.draftPermissions;

function emptyForm() {
  return {
    archiveId: '',
    archiveCode: '',
    version: 1,
    title: '',
    formingDate: '',
    categoryId: '',
    secretLevel: '',
    retentionCode: '',
    summary: '',
    metadataJson: '{}'
  };
}

function emptyEditor() {
  return { form: emptyForm(), files: [], uploaded: [], categories: [], secretLevels: [], retentions: [] };
}

Page({
  data: {
    ...emptyEditor(),
    mode: 'list',
    loading: true,
    saving: false,
    uploading: false,
    uploadProgress: 0,
    error: '',
    rows: [],
    page: 1,
    hasMore: false,
    canAdd: false,
    canEdit: false,
    canDelete: false,
    canUpload: false
  },

  onLoad() {
    this._closed = false;
    app.refreshSession().catch(() => {});
    this._unsubscribe = app.subscribeSession((state) => {
      const canView = state.status === 'authenticated' && navigation.can(P.view);
      this.setData({
        ...emptyEditor(),
        mode: 'list',
        loading: state.status === 'loading' || canView,
        saving: false,
        uploading: false,
        uploadProgress: 0,
        error: canView || state.status === 'loading' ? '' : '请登录并确认档案登记权限',
        rows: [],
        page: 1,
        hasMore: false,
        canAdd: canView && navigation.can(P.add),
        canEdit: canView && navigation.can(P.edit),
        canDelete: canView && navigation.can(P.delete),
        canUpload: canView && navigation.can(P.fileView) && navigation.can(P.upload)
      });
      if (canView) this.loadList(1);
    });
  },

  onUnload() {
    this._closed = true;
    if (this._unsubscribe) this._unsubscribe();
  },

  isCurrent(revision) {
    return !this._closed && revision === session.revision() && navigation.can(P.view);
  },

  busy() {
    return this.data.loading || this.data.saving || this.data.uploading;
  },

  async loadList(page) {
    if (!navigation.can(P.view)) return;
    const revision = session.revision();
    this.setData({ loading: true, error: '' });
    try {
      const { rows, hasMore } = await api.list(page);
      if (this.isCurrent(revision)) {
        this.setData({ rows: page === 1 ? rows : this.data.rows.concat(rows), page, hasMore });
      }
    } catch (error) {
      if (this.isCurrent(revision)) this.setData({ error: error.message });
    } finally {
      if (this.isCurrent(revision)) this.setData({ loading: false });
    }
  },

  refresh() {
    if (!this.busy()) this.loadList(1);
  },

  nextPage() {
    if (!this.busy() && this.data.hasMore) this.loadList(this.data.page + 1);
  },

  newDraft() {
    if (this.data.canAdd) return this.openEditor();
  },

  openDraft(event) {
    return this.openEditor(String(event.currentTarget.dataset.id));
  },

  async openEditor(archiveId) {
    if (this.busy() || !navigation.can(P.view)) return;
    const revision = session.revision();
    this.setData({ loading: true, error: '' });
    try {
      const [options, record] = await Promise.all([
        api.options(),
        archiveId ? api.detail(archiveId) : null
      ]);
      if (!this.isCurrent(revision)) return;
      const { files, ...fields } = record || {};
      this.setData({
        ...emptyEditor(),
        ...options,
        mode: 'editor',
        form: { ...emptyForm(), ...fields },
        uploaded: files || []
      });
    } catch (error) {
      if (this.isCurrent(revision)) this.setData({ error: error.message });
    } finally {
      if (this.isCurrent(revision)) this.setData({ loading: false });
    }
  },

  backToList() {
    if (this.busy()) return;
    this.setData({ ...emptyEditor(), mode: 'list', error: '' });
    this.loadList(1);
  },

  setInput(event) {
    this.setData({ ['form.' + event.currentTarget.dataset.field]: event.detail.value });
  },

  setPicker(event) {
    const field = event.currentTarget.dataset.field;
    const choices = {
      categoryId: [this.data.categories, 'categoryId'],
      secretLevel: [this.data.secretLevels, 'value'],
      retentionCode: [this.data.retentions, 'code']
    };
    const [items, key] = choices[field];
    const selected = items[Number(event.detail.value)];
    if (selected) this.setData({ ['form.' + field]: selected[key] });
  },

  setDate(event) {
    this.setData({ 'form.formingDate': event.detail.value });
  },

  chooseFiles() {
    if (this.busy() || !this.data.canUpload) return;
    const revision = session.revision();
    wx.chooseMessageFile({
      count: 9,
      type: 'all',
      success: ({ tempFiles }) => this.queueFiles(tempFiles.map(({ path, name }) => ({ path, name })), revision),
      fail: (error) => this.pickerError(error, revision, '无法选择文件')
    });
  },

  takePhoto() {
    if (this.busy() || !this.data.canUpload) return;
    const revision = session.revision();
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera'],
      success: ({ tempFiles }) => this.queueFiles(tempFiles.map(({ tempFilePath }) => {
        const extension = /\.[a-z0-9]+$/i.exec(tempFilePath);
        return { path: tempFilePath, name: '照片' + (extension ? extension[0] : '.jpg') };
      }), revision),
      fail: (error) => this.pickerError(error, revision, '无法拍照，请检查相机授权')
    });
  },

  queueFiles(files, revision) {
    if (this.isCurrent(revision) && this.data.mode === 'editor' && !this.busy()) {
      this.setData({ files: this.data.files.concat(files) });
    }
  },

  pickerError(error, revision, message) {
    if (this.isCurrent(revision) && this.data.mode === 'editor' && !/cancel/i.test(error.errMsg || '')) {
      this.setData({ error: message });
    }
  },

  removePending(event) {
    if (this.busy()) return;
    const index = Number(event.currentTarget.dataset.index);
    this.setData({ files: this.data.files.filter((_, i) => i !== index), error: '' });
  },

  async saveDraft() {
    if (this.busy() || !navigation.can(P.view)) return;
    const form = { ...this.data.form, title: this.data.form.title.trim() };
    if (!navigation.can(form.archiveId ? P.edit : P.add)) return;
    if (!form.title || !form.categoryId || !form.formingDate || !form.retentionCode || form.secretLevel === '') {
      this.setData({ error: '请填写必填项目' });
      return;
    }

    const revision = session.revision();
    this.setData({ saving: true, error: '' });
    try {
      const saved = form.archiveId ? await api.update(form) : await api.create(form);
      if (!this.isCurrent(revision)) return;
      this.setData({ form: { ...form, ...saved } });
    } catch (error) {
      if (this.isCurrent(revision)) this.setData({ error: error.message });
      return;
    } finally {
      if (this.isCurrent(revision)) this.setData({ saving: false });
    }
    if (this.data.files.length && this.data.canUpload) {
      await this.uploadFiles();
    } else {
      wx.showToast({ title: '草稿已保存', icon: 'success' });
    }
  },

  async uploadFiles() {
    const { archiveId } = this.data.form;
    if (this.busy() || !archiveId || !this.data.canUpload || !this.data.files.length) return;
    const revision = session.revision();
    this.setData({ uploading: true, uploadProgress: 0, error: '' });
    try {
      for (const file of this.data.files.slice()) {
        this.setData({ uploadProgress: 0 });
        const saved = await api.upload(archiveId, file, (progress) => {
          if (this.isCurrent(revision)) this.setData({ uploadProgress: progress });
        });
        if (!this.isCurrent(revision)) return;
        this.setData({ files: this.data.files.slice(1), uploaded: this.data.uploaded.concat(saved) });
      }
      wx.showToast({ title: '附件已上传', icon: 'success' });
    } catch (error) {
      if (this.isCurrent(revision)) {
        this.setData({ error: '草稿已保存，附件上传失败。' + error.message });
      }
    } finally {
      if (this.isCurrent(revision)) this.setData({ uploading: false });
    }
  },

  removeDraft() {
    if (this.busy() || !this.data.form.archiveId || !this.data.canDelete) return;
    const { archiveId, version } = this.data.form;
    const revision = session.revision();
    wx.showModal({
      title: '删除草稿',
      content: '确认删除这份草稿？',
      success: async ({ confirm }) => {
        if (!confirm || !this.isCurrent(revision) || this.busy()) return;
        this.setData({ saving: true, error: '' });
        try {
          await api.remove(archiveId, version);
        } catch (error) {
          if (this.isCurrent(revision)) this.setData({ error: error.message });
          return;
        } finally {
          if (this.isCurrent(revision)) this.setData({ saving: false });
        }
        if (this.isCurrent(revision)) this.backToList();
      }
    });
  }
});
