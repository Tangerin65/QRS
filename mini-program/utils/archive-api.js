const request = require('./request');
//接口继承于此
const BASE = '/records';
const FILES = '/records/files';

module.exports = {
  list(page) {
    return request.get(BASE + '/list', {
      pageNum: page,
      pageSize: 20,
      archiveStatus: 'DRAFT'
    });
  },

  categories() {
    return request.get(BASE + '/categories');
  },

  retentions() {
    return request.get(BASE + '/retentions');
  },

  detail(archiveId) {
    return request.get(BASE + '/edit/' + archiveId);
  },

  create(record) {
    return request.post(BASE + '/add', record);
  },

  update(record) {
    return request.post(BASE + '/edit', record);
  },

  remove(archiveId, version) {
    return request.post(BASE + '/remove', { archiveId, version });
  },

  upload(archiveId, filePath, fileName, relativePath, onProgress) {
    return request.upload({
      url: FILES + '/' + archiveId + '/upload',
      filePath,
      name: 'file',
      formData: { relativePath: relativePath || fileName },
      onProgress
    });
  }
};