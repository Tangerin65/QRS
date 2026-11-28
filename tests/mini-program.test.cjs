const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../mini-program');
function harness() {
  const modules = new Map();
  const storage = new Map();
  const requests = [];
  const calls = { scan: 0, login: 0, routes: [], toast: [] };
  let app;
  let pageDefinition;
  let componentDefinition;
  let scanHandler;
  const wx = {
    getStorageSync: (key) => storage.get(key),
    setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: (key) => storage.delete(key),
    onNetworkStatusChange() {},
    setNavigationBarTitle() {},
    request: (options) => requests.push(options),
    login: (options) => { calls.login++; options.success({ code: 'new-wechat-code' }); },
    showToast: (options) => calls.toast.push(options.title),
    reLaunch: (options) => calls.routes.push(options.url),
    navigateTo: (options) => calls.routes.push(options.url),
    scanCode: (options) => { calls.scan++; scanHandler = options; },
    showModal: (options) => options.success({ confirm: true })
  };
  const context = vm.createContext({
    wx, console, setTimeout, clearTimeout,
    setInterval: () => 1, clearInterval: () => {},
    App: (definition) => { app = definition; },
    Page: (definition) => { pageDefinition = definition; },
    Component: (definition) => { componentDefinition = definition; },
    getApp: () => app
  });
  function load(relative) {
    let filename = path.resolve(root, relative);
    if (!fs.existsSync(filename)) {
      // 本地 config/index.js 不提交仓库；缺失时回退到模板，保证全新克隆也能运行测试。
      const template = filename.endsWith(path.join('config', 'index.js'))
        ? path.join(path.dirname(filename), 'index.example.js') : '';
      if (template && fs.existsSync(template)) filename = template;
    }
    if (modules.has(filename)) return modules.get(filename).exports;
    const mod = { exports: {} };
    modules.set(filename, mod);
    const source = fs.readFileSync(filename, 'utf8');
    const fn = vm.runInContext('(function(require,module,exports){' + source + '\n})', context, { filename });
    fn((id) => load(path.relative(root, path.resolve(path.dirname(filename), id + '.js'))), mod, mod.exports);
    return mod.exports;
  }
  function page(relative) {
    load(relative);
    const instance = Object.assign({}, pageDefinition);
    instance.data = JSON.parse(JSON.stringify(pageDefinition.data || {}));
    instance.setData = (data) => Object.assign(instance.data, data);
    return instance;
  }
  load('app.js');
  app.onLaunch();
  const config = load('config/index.js');
  config.baseUrl = 'https://api.example.test';
  const session = load('utils/session.js');
  const identity = (id = '3', permissions = []) => ({
    token: 'token-' + id, userId: id, userName: '成员' + id, roleName: '档案员', permissions
  });
  const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };
  const reply = (index, data, code = 200, statusCode = 200) =>
    requests[index].success({ statusCode, data: { code, data } });
  return {
    load, page, app, wx, config, session, identity, requests, calls, storage, flush, reply,
    scan: (result) => scanHandler.success({ result }),
    scanFail: (errMsg) => scanHandler.fail({ errMsg }),
    component: () => componentDefinition
  };
}

test('档案登记先保存草稿，再逐个上传，失败后只重试队列中的附件', async () => {
  const h = harness();
  const uploads = [];
  h.wx.uploadFile = (options) => { uploads.push(options); };
  h.wx.chooseMessageFile = (options) => options.success({ tempFiles: [
    { path: '/tmp/a.pdf', name: '甲.pdf' }, { path: '/tmp/b.txt', name: '乙.txt' }
  ] });
  const identity = h.identity('3', ['archiveQuery', 'archiveAdd', 'archiveFileView', 'archiveFileUpload']);
  const page = h.page('pages/archive-workbench/archive-workbench.js');
  h.session.set(identity);
  page.onLoad();
  h.reply(0, identity);
  await h.flush();
  assert.equal(h.requests[1].url, h.config.baseUrl + '/api/mobile/archives/drafts');
  h.reply(1, { rows: [], hasMore: false });
  await h.flush();
  const editor = page.newDraft();
  h.reply(2, { categories: [], secretLevels: [], retentions: [] });
  await editor;
  page.data.form = { ...page.data.form, title: '测试档案', formingDate: '2026-11-22',
    categoryId: '1', secretLevel: 0, retentionCode: 'Y30' };
  page.chooseFiles();
  const save = page.saveDraft();
  assert.equal(h.requests[3].method, 'POST');
  assert.equal(uploads.length, 0);
  h.reply(3, { archiveId: '93', archiveCode: 'A-2026-000001', version: 1 }, 0);
  await h.flush();
  assert.equal(uploads[0].url, h.config.baseUrl + '/api/mobile/archives/drafts/93/files');
  assert.equal(uploads[0].header.Authorization, 'Bearer token-3');
  assert.equal(uploads[0].formData.fileName, '甲.pdf');
  uploads[0].success({ statusCode: 200, data: JSON.stringify({ code: 0, data: { fileId: '1' } }) });
  await h.flush();
  uploads[1].success({ statusCode: 400, data: JSON.stringify({ code: 400, msg: '文件类型无效' }) });
  await save;
  assert.equal(page.data.form.archiveId, '93');
  assert.equal(page.data.uploaded.length, 1);
  assert.equal(page.data.files.length, 1);
  assert.match(page.data.error, /文件类型无效/);
  const retry = page.uploadFiles();
  assert.equal(uploads[2].formData.fileName, '乙.txt');
  assert.equal(h.requests.length, 4);
  uploads[2].success({ statusCode: 200, data: JSON.stringify({ code: 0, data: { fileId: '2' } }) });
  await retry;
  assert.equal(page.data.uploaded.length, 2);
  assert.equal(page.data.files.length, 0);
  page.onUnload();
});

test('附件请求的旧 401 不会清空当前账号，非 JSON 响应可正常报错', async () => {
  const h = harness();
  let upload;
  h.wx.uploadFile = (options) => { upload = options; };
  h.session.set(h.identity('3'));
  const request = h.load('utils/request.js');
  const pending = request.upload({ url: '/api/mobile/archives/drafts/93/files', filePath: '/tmp/a.pdf' });
  h.session.set(h.identity('4'));
  upload.success({ statusCode: 401, data: '<html/>' });
  await assert.rejects(pending, (error) => error.statusCode === 401);
  assert.equal(h.session.snapshot().user.userId, '4');
});

test('用户编号保留普通数字、前导零、长整数和文本编码', () => {
  const { parseUserNo } = harness().load('utils/qr.js');
  for (const code of ['0', '123', '0003', '900719925474099312345', 'member3-ZhaoBoyu']) {
    assert.equal(parseUserNo(code), code);
  }
  assert.equal(parseUserNo('{"userNo":"0003"}'), '0003');
  assert.equal(parseUserNo('{"userNo":9007199254740993}'), '');
  for (const code of ['', 'https://evil.test', '<script>', 'a'.repeat(65), '[]']) {
    assert.equal(parseUserNo(code), '');
  }
});

test('绑定码与电脑登录码不能混用，电脑凭证必须有明确类型和足够长度', () => {
  const qr = harness().load('utils/qr.js');
  assert.equal(qr.parsePcTicket('123'), '');
  assert.equal(qr.parsePcTicket('{"type":"pc-login","ticket":"123"}'), '');
  assert.equal(qr.parsePcTicket(JSON.stringify({ type: 'pc-login', ticket: 'x'.repeat(32) })), 'x'.repeat(32));
  assert.equal(qr.parseUserNo(JSON.stringify({ type: 'pc-login', ticket: 'x'.repeat(32) })), '');
});

test('角色名不授予权限，只有服务端明确权限才显示业务菜单', () => {
  const h = harness();
  const nav = h.load('utils/navigation.js');
  h.session.set({ ...h.identity(), roleName: '管理员' });
  assert.equal(nav.visible().length, 2);
  nav.go('archives');
  assert.equal(h.calls.routes.length, 0);
  h.session.set(h.identity('3', ['records:search:view']));
  assert.equal(nav.can('records:search:view'), true);
  assert.equal(nav.can('records:message:view'), false);
  h.session.loading();
  assert.equal(nav.can('records:search:view'), false);
  h.session.clear();
  assert.equal(nav.visible().length, 2);
});

test('未配置服务时不发请求，不伪造登录成功', async () => {
  const h = harness();
  h.config.baseUrl = '';
  await assert.rejects(h.load('utils/request.js').get('/api/mobile/auth/session'),
    (error) => error.code === 'NOT_CONFIGURED');
  const state = await h.app.refreshSession();
  assert.equal(state.status, 'anonymous');
  assert.equal(h.requests.length, 0);
});

test('请求拒绝非 HTTPS 服务地址和外部 URL 路径', async () => {
  const h = harness();
  const request = h.load('utils/request.js');
  h.config.baseUrl = 'http://api.example.test';
  await assert.rejects(request.get('/api/mobile/auth/session'), (error) => error.code === 'CONFIG_ERROR');
  h.config.baseUrl = 'https://api.example.test';
  await assert.rejects(request.get('https://evil.test'), (error) => error.code === 'INVALID_PATH');
  assert.equal(h.requests.length, 0);
});

test('微信免登用新 code 换登录态，凭证不写本地也不暴露给页面', async () => {
  const h = harness();
  const promise = h.app.refreshSession();
  await h.flush();
  assert.equal(h.requests[0].method, 'POST');
  assert.equal(h.requests[0].data.code, 'new-wechat-code');
  assert.equal(h.requests[0].header.Authorization, undefined);
  h.reply(0, h.identity('3', ['records:search:view']));
  const state = await promise;
  assert.equal(state.status, 'authenticated');
  assert.equal(state.user.userId, '3');
  assert.equal(state.token, undefined);
  assert.equal(state.user.token, undefined);
  assert.equal(h.storage.has('records_session'), false);
});

test('未绑定响应进入绑定引导，缺少权限数组时默认无业务权限', async () => {
  const h = harness();
  const promise = h.app.refreshSession();
  await h.flush();
  h.reply(0, { bound: false });
  assert.equal((await promise).status, 'unbound');
  h.session.set({ token: 'example', userId: '3' });
  assert.equal(h.load('utils/navigation.js').visible().length, 2);
});

test('认证失败立即撤销旧会话和菜单，后端停用账号同样处理', async () => {
  for (const code of [401, '401', 'ACCOUNT_DISABLED']) {
    const h = harness();
    h.session.set(h.identity('3', ['records:search:view']));
    const promise = h.load('utils/request.js').get('/api/mobile/auth/session');
    h.reply(0, null, code, 200);
    await assert.rejects(promise);
    assert.equal(h.session.token(), '');
    assert.equal(h.load('utils/navigation.js').visible().length, 2);
  }
});

test('旧账号请求延迟返回 401 不会清除新账号', async () => {
  const h = harness();
  h.session.set(h.identity('3'));
  const promise = h.load('utils/request.js').get('/api/mobile/auth/session');
  h.session.set(h.identity('4'));
  h.reply(0, null, 401, 401);
  await assert.rejects(promise);
  assert.equal(h.session.snapshot().user.userId, '4');
});

test('退出后延迟返回的免登成功和未绑定失败都不能恢复或覆盖新账号', async () => {
  for (const succeeds of [true, false]) {
    const h = harness();
    const pending = h.app.refreshSession();
    await h.flush();
    await h.app.logout();
    h.session.set(h.identity('4'));
    if (succeeds) h.reply(0, h.identity('3'));
    else h.reply(0, null, 'ACCOUNT_UNBOUND');
    await pending;
    assert.equal(h.session.snapshot().user.userId, '4');
  }
});

test('退出失败仍清除本机状态，并阻止自动重新登录；主动登录可以恢复', async () => {
  const h = harness();
  h.session.set(h.identity());
  const promise = h.app.logout();
  assert.equal(h.session.token(), '');
  h.requests[0].fail({ errMsg: 'request:fail offline' });
  await assert.rejects(promise);
  assert.equal((await h.app.refreshSession()).status, 'anonymous');
  assert.equal(h.requests.length, 1);
  const explicit = h.app.refreshSession({ explicit: true });
  await h.flush();
  h.reply(1, h.identity());
  assert.equal((await explicit).status, 'authenticated');
});

test('超时、403 和服务端异常有中文提示，不自动重发写入请求或泄露堆栈', async () => {
  const h = harness();
  const request = h.load('utils/request.js');
  const timeout = request.post('/api/mobile/account/bind');
  h.requests[0].fail({ errMsg: 'request:fail timeout' });
  await assert.rejects(timeout, (error) => error.code === 'TIMEOUT');
  const forbidden = request.get('/api/mobile/auth/session');
  h.reply(1, null, 403, 403);
  await assert.rejects(forbidden, /没有.*权限/);
  const failure = request.get('/api/mobile/auth/session');
  h.requests[2].success({ statusCode: 500, data: { code: 500, msg: 'java.lang.stack SECRET' } });
  await assert.rejects(failure, /服务暂时不可用/);
  assert.equal(h.requests.length, 3);
});

test('重新扫码实际调用扫码；无效码清除前一次识别的账号；取消不报错', () => {
  const h = harness();
  const page = h.page('pages/scan/scan.js');
  page.onLoad({ mode: 'bind' });
  page.startScan();
  page.startScan();
  assert.equal(h.calls.scan, 1);
  h.scan('0003');
  assert.equal(page.data.userNo, '0003');
  page.retry();
  assert.equal(h.calls.scan, 2);
  assert.equal(page.data.userNo, '');
  h.scan('https://evil.test');
  assert.equal(page.data.status, 'error');
  page.retry();
  h.scanFail('scanCode:fail cancel');
  assert.equal(page.data.status, 'idle');
  page.onUnload();
});

test('绑定操作防重复点击，成功后令牌不出现在页面 data 中', async () => {
  const h = harness();
  const page = h.page('pages/scan/scan.js');
  page.onLoad({ mode: 'bind' });
  page.startScan();
  h.scan('0003');
  // M12-B：首次绑定需校验平台账号口令（后端强制），未填口令不会发请求
  page.setBindPassword('secret-pass');
  page.confirmBind();
  page.confirmBind();
  await h.flush();
  assert.equal(h.requests.length, 1);
  assert.equal(h.requests[0].data.userNo, '0003');
  assert.equal(h.requests[0].data.password, 'secret-pass');
  h.reply(0, h.identity());
  await h.flush();
  assert.equal(h.session.snapshot().status, 'authenticated');
  assert.equal(JSON.stringify(page.data).includes('token-3'), false);
  assert.equal(JSON.stringify(page.data).includes('secret-pass'), false);
  assert.equal(h.calls.routes.at(-1), '/pages/home/home');
  page.onUnload();
});

async function inspectFixture(h, page, remaining = 60000) {
  page.startScan();
  h.scan(JSON.stringify({ type: 'pc-login', ticket: 'x'.repeat(32) }));
  h.reply(0, { ...h.identity(), permissions: ['records:search:view'] });
  await h.flush();
  h.reply(1, {
    state: 'pending', deviceName: '测试电脑', location: '测试位置',
    serverTime: 1000000, expiresAt: 1000000 + remaining
  });
  await h.flush();
}

test('电脑登录必须先核验请求，显式确认后才发送确认且只能提交一次', async () => {
  const h = harness();
  h.session.set(h.identity());
  const page = h.page('pages/scan/scan.js');
  page.onLoad({ mode: 'pc' });
  await inspectFixture(h, page);
  assert.equal(page.data.pcReady, true);
  assert.equal(h.requests.length, 2);
  assert.equal(page.data.secondsLeft, 60);
  assert.equal(JSON.stringify(page.data).includes('x'.repeat(32)), false);
  page.confirmPc();
  page.confirmPc();
  assert.equal(h.requests.length, 3);
  h.reply(2, { state: 'confirmed' });
  await h.flush();
  assert.equal(page.data.pcDone, true);
  assert.equal(page.data.pcReady, false);
  page.confirmPc();
  assert.equal(h.requests.length, 3);
  page.onUnload();
});

test('电脑登录可拒绝；已过期凭证不能确认', async () => {
  const h = harness();
  h.session.set(h.identity());
  const page = h.page('pages/scan/scan.js');
  page.onLoad({ mode: 'pc' });
  await inspectFixture(h, page);
  page.cancelPc();
  assert.match(h.requests[2].url, /\/pc\/cancel$/);
  h.reply(2, { state: 'cancelled' });
  await h.flush();
  assert.equal(page.data.pcCancelled, true);
  page._ticket = 'x'.repeat(32);
  page._deadline = Date.now() - 1;
  page.setData({ pcReady: true });
  page.confirmPc();
  assert.equal(page.data.pcReady, false);
  assert.equal(h.requests.length, 3);
  page.onUnload();
});

test('网络失败不会显示电脑登录成功，重新扫码不会重发确认请求', async () => {
  const h = harness();
  h.session.set(h.identity());
  const page = h.page('pages/scan/scan.js');
  page.onLoad({ mode: 'pc' });
  await inspectFixture(h, page);
  page.confirmPc();
  h.requests[2].fail({ errMsg: 'timeout' });
  await h.flush();
  assert.equal(page.data.pcDone, false);
  assert.equal(page.data.pcReady, false);
  assert.match(page.data.message, /核对电脑端/);
  page.retry();
  assert.equal(h.requests.length, 3);
  assert.equal(h.calls.scan, 2);
  page.onUnload();
});

test('页面退出后延迟绑定响应不建立会话', async () => {
  const h = harness();
  const page = h.page('pages/scan/scan.js');
  page.onLoad({ mode: 'bind' });
  page.startScan();
  h.scan('3');
  // M12-B：需先填口令才会真正提交绑定请求
  page.setBindPassword('secret-pass');
  page.confirmBind();
  await h.flush();
  page.onUnload();
  h.reply(0, h.identity());
  await h.flush();
  assert.equal(h.session.snapshot().status, 'anonymous');
});

test('页面路由、组件引用、WXML 事件处理函数完整，页面无敏感持久化', () => {
  const appConfig = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'));
  const h = harness();
  for (const page of appConfig.pages) {
    for (const extension of ['js', 'json', 'wxml', 'wxss']) {
      assert.equal(fs.existsSync(path.join(root, page + '.' + extension)), true);
    }
    const cfg = JSON.parse(fs.readFileSync(path.join(root, page + '.json'), 'utf8'));
    for (const ref of Object.values(cfg.usingComponents || {})) {
      assert.equal(fs.existsSync(path.join(root, ref.slice(1) + '.json')), true);
    }
    const definition = h.page(page + '.js');
    const wxml = fs.readFileSync(path.join(root, page + '.wxml'), 'utf8');
    for (const binding of wxml.matchAll(/bind(?::|\w*)[\w-]*="([\w]+)"/g)) {
      assert.equal(typeof definition[binding[1]], 'function', page + ':' + binding[1]);
    }
  }
});
