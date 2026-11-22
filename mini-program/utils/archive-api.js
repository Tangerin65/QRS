const request = require('./request');

const BASE = '/api/mobile/archives/drafts';

function draftBody({ title, formingDate, categoryId, secretLevel, retentionCode, summary, metadataJson }) {
  return { title, formingDate, categoryId, secretLevel, retentionCode, summary, metadataJson };
}

module.exports = {
  list(pageNum) {
    return request.get(BASE, { pageNum });
  },

  options() {
    return request.get(BASE + '/options');
  },

  detail(archiveId) {
    return request.get(BASE + '/' + archiveId);
  },

  create(record) {
    return request.post(BASE, draftBody(record));
  },

  update(record) {
    return request.put(BASE + '/' + record.archiveId, { ...draftBody(record), version: record.version });
  },

  remove(archiveId, version) {
    return request.remove(BASE + '/' + archiveId, { version });
  },

  upload(archiveId, file, onProgress) {
    return request.upload({
      url: BASE + '/' + archiveId + '/files',
      filePath: file.path,
      formData: file.name ? { fileName: file.name } : {},
      onProgress
    });
  }
};
