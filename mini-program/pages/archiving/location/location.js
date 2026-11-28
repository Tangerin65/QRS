const createListPage = require('../list-page');
const api = require('../api');

Page(createListPage({
  fetch: (query) => api.location(query)
}));
