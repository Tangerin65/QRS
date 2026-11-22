//档案登记
const app = getApp();
const navigation = require('../../utils/navigation');
const api = require('../../utils/archive-api');

const P = navigation.draftPermissions;//权限常量

function emptyForm() {
  return {
    archiveId: '',
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

Page({
  data: {
    mode: 'list',
    loading: true,
    saving: false,
    uploading: false,
    error: '',
    rows: [],
    categories: [],
    secretLevels: [],
    retentions: [],
    form: emptyForm(),
    files: [],
    uploaded: [],
    page: 1,
    hasMore: false
  },

  onLoad() {
    this._closed = false;

    this._unsubscribe = app.subscribeSession((state) => {
      if (state.status === 'authenticated' && navigation.can(P.view)) {
        this.loadList(1);
      } else if (state.status !== 'loading') {
        this.setData({
          loading: false,
          rows: [],
          error: '没有权限打开此页面'
        });
      }
    });

    app.refreshSession().catch(() => {});//登陆状态刷新
  },

  onUnload() {
    this._closed = true;

    if (this._unsubscribe) {
      this._unsubscribe();
    }
  },

  async loadList(page) {
    //超高列表加载
    if (!navigation.can(P.view)) return;

    this.setData({
      loading: true,
      error: ''
    });

    try {
      const result = await api.list(page);

      if (this._closed) return;

      this.setData({
        rows: result.rows || result.items || [],
        page,
        hasMore: Boolean(result.hasMore),
        loading: false
      });
    } catch (error) {
      if (!this._closed) {
        this.setData({
          loading: false,
          error: error.message
        });
      }
    }
  },

  loadOptions() {
    //加载表单
    return Promise.all([
      api.categories(),
      api.retentions()
    ]).then(([categories, retentions]) => {
      this.setData({
        categories: categories || [],
        retentions: retentions || [],
        secretLevels: [
          { value: 0, label: '公开' },
          { value: 1, label: '内部' },
          { value: 2, label: '秘密' },
          { value: 3, label: '机密' }
        ]
      });
    });
  },

  newDraft() {
    //新建档案
    if (!navigation.can(P.add)) return;

    this.setData({
      mode: 'editor',
      form: emptyForm(),
      files: [],
      uploaded: [],
      error: ''
    });

    this.loadOptions().catch((error) => {
      this.setData({ error: error.message });
    });
  },

  openDraft(event) {
    const archiveId = String(event.currentTarget.dataset.id);

    this.setData({
      loading: true,
      error: ''
    });

    Promise.all([
      api.detail(archiveId),
      this.loadOptions()
    ]).then(([record]) => {
      this.setData({
        mode: 'editor',
        form: Object.assign(emptyForm(), record),
        uploaded: record.files || [],
        loading: false
      });
    }).catch((error) => {
      this.setData({
        loading: false,
        error: error.message
      });
    });
  },

  setInput(event) {
    const field = event.currentTarget.dataset.field;

    this.setData({
      ['form.' + field]: event.detail.value
    });
  },

  setPicker(event) {
    //下拉框
    const field = event.currentTarget.dataset.field;
    const index = Number(event.detail.value);

    const values = {
      categoryId: this.data.categories,
      secretLevel: this.data.secretLevels,
      retentionCode: this.data.retentions
    };

    const selected = values[field][index];

    const valueField = field === 'categoryId'
      ? 'categoryId'
      : field === 'secretLevel'
        ? 'value'
        : 'code';

    this.setData({
      ['form.' + field]: selected[valueField]
    });
  },

  setDate(event) {
    this.setData({
      'form.formingDate': event.detail.value
    });
  },

  chooseFiles() {
    if (!navigation.can(P.upload)) return;

    wx.chooseMessageFile({
      count: 9,
      type: 'all',
      success: (result) => {
        this.setData({
          files: this.data.files.concat(result.tempFiles || [])
        });
      }
    });
  },

  takePhoto() {
    //拍照上传
    if (!navigation.can(P.upload)) return;

    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera'],
      success: (result) => {
        const file = (result.tempFiles || [])[0];

        if (file) {
          this.setData({
            files: this.data.files.concat([{
              path: file.tempFilePath,
              name: '照片.jpg',
              size: file.size
            }])
          });
        }
      }
    });
  },

  async saveDraft() {
    if (this.data.saving) return;

    const hasArchive = Boolean(this.data.form.archiveId);
    const permission = hasArchive ? P.edit : P.add;
    //有ID编辑，没有ID新建

    if (!navigation.can(permission)) return;

    const form = Object.assign({}, this.data.form);

    if (!form.title ||
        !form.categoryId ||
        !form.formingDate ||
        !form.retentionCode ||
        form.secretLevel === '') {
      wx.showToast({
        title: '请填写必填项目',
        icon: 'none'
      });
      return;
    }

    this.setData({
      saving: true,
      error: ''
    });

    try {
      const result = hasArchive
        ? await api.update(form)
        : await api.create(form);

      const record = result.data || result;

      form.archiveId = String(record.archiveId);
      form.version = record.version || form.version + 1;

      this.setData({
        form,
        saving: false
      });

      if (this.data.files.length) {
        await this.uploadFiles();
      } else {
        wx.showToast({
          title: '草稿已保存',
          icon: 'success'
        });
      }
    } catch (error) {
      this.setData({
        saving: false,
        error: error.message
      });
    }
  },

  async uploadFiles() {
    //上传附件
    if (!this.data.form.archiveId) return;
    if (!navigation.can(P.upload)) return;

    this.setData({
      uploading: true
    });

    const pending = this.data.files.slice();//待上传
    const uploaded = this.data.uploaded.slice();//已上传文件列表

    for (const file of pending) {
      try {
        const result = await api.upload(
          this.data.form.archiveId,
          file.path || file.tempFilePath,
          file.name || '附件',
          file.name || '附件',
          (progress) => {
            this.setData({
              uploadProgress: progress//显示进度
            });
          }
        );

        uploaded.push(result.data || result);

        this.setData({
          uploaded
        });
      } catch (error) {
        this.setData({
          uploading: false,
          uploaded,
          error: error.message
        });

        return;
      }
    }

    this.setData({
      files: [],
      uploading: false,
      uploaded
    });

    wx.showToast({
      title: '档案和附件已保存',
      icon: 'success'
    });
  },

  backToList() {
    this.setData({
      mode: 'list',
      files: [],
      error: ''
    });

    this.loadList(1);
  },

  refresh() {
    this.loadList(this.data.page);
  }
});