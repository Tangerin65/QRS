// M12-B 小程序个人中心页测试。
// 与成员3 的 tests/mini-program.test.cjs 分开维护，避免互相改动对方文件；
// 复用同一套微信 API 替身思路，只覆盖成员6 新增的行为。
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
  const calls = { toast: [], routes: [], modals: [] };
  let app;
  let pageDefinition;
  let scanHandler;
  const wx = {
    getStorageSync: (key) => storage.get(key),
    setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: (key) => storage.delete(key),
    request: (options) => requests.push(options),
    login: (options) => options.success({ code: 'wx-code' }),
    showToast: (options) => calls.toast.push(options.title),
    reLaunch: (options) => calls.routes.push(options.url),
    navigateTo: (options) => calls.routes.push(options.url),
    setNavigationBarTitle() {},
    // 扫码由测试显式触发，便于构造「扫到某个编号」的场景
    scanCode: (options) => { scanHandler = options; },
    showModal: (options) => { calls.modals.push(options); options.success({ confirm: true }); }
  };
  const context = vm.createContext({
    wx, console, setTimeout, clearTimeout, setInterval: () => 1, clearInterval: () => {},
    App: (definition) => { app = definition; },
    Page: (definition) => { pageDefinition = definition; },
    Component() {},
    getApp: () => app
  });
  function load(relative) {
    const filename = path.resolve(root, relative);
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
  const flush = async () => { for (let i = 0; i < 14; i++) await Promise.resolve(); };
  const reply = (index, data, code = 200, statusCode = 200) =>
    requests[index].success({ statusCode, data: { code, data } });
  const identity = (id = '2') => ({
    token: 'token-' + id, userId: id, userName: '若依', roleName: '普通角色', permissions: ['records:search:view']
  });
  return { load, page, app, wx, config, session, requests, calls, storage, flush, reply, identity,
    // 触发一次扫码结果，供绑定流程测试使用
    scan: (result) => scanHandler.success({ result }) };
}

test('profile 接口已登记在 config.api 中', () => {
  const { config } = harness();
  assert.equal(typeof config.api.profile, 'string');
  assert.match(config.api.profile, /^\/api\/mobile\/auth\/profile$/);
});

test('未登录时不请求资料接口，显示登录引导', async () => {
  const h = harness();
  const page = h.page('pages/profile/profile.js');
  page.onLoad();
  page.onUnload();
  assert.equal(h.requests.length, 0);
  assert.equal(page.data.bound, false);
  assert.equal(page.data.profileName, '未登录');
});

test('已登录时读取基础资料，手机号使用服务端掩码值', async () => {
  const h = harness();
  h.session.set(h.identity('2'));
  const page = h.page('pages/profile/profile.js');
  page.onLoad();
  await h.flush();
  assert.equal(h.requests.length, 1);
  assert.equal(h.requests[0].url, 'https://api.example.test/api/mobile/auth/profile');
  assert.equal(h.requests[0].header.Authorization, 'Bearer token-2');
  h.reply(0, {
    userId: '2', userName: '若依', roleName: '普通角色', permissions: ['records:search:view'],
    userNo: '0002', loginName: 'ry', deptName: '测试部门', phonenumber: '156****6666',
    arcArchivist: true, bound: true, boundAt: '2026-11-19 15:34:03'
  });
  await h.flush();
  assert.equal(page.data.profileState, 'success');
  assert.equal(page.data.userNo, '0002');
  assert.equal(page.data.loginName, 'ry');
  assert.equal(page.data.deptName, '测试部门');
  assert.equal(page.data.phonenumber, '156****6666');
  assert.equal(page.data.archivist, true);
  assert.equal(page.data.boundAt, '2026-11-19 15:34:03');
  assert.equal(page.data.permissionCount, 1);
  page.onUnload();
});

test('资料里不出现令牌、openid 等敏感值', async () => {
  const h = harness();
  h.session.set(h.identity('2'));
  const page = h.page('pages/profile/profile.js');
  page.onLoad();
  await h.flush();
  h.reply(0, {
    userId: '2', userName: '若依', userNo: '0002', loginName: 'ry',
    deptName: '测试部门', phonenumber: '156****6666', bound: true, boundAt: '2026-11-19 15:34:03'
  });
  await h.flush();
  const serialized = JSON.stringify(page.data);
  assert.equal(serialized.includes('token-2'), false);
  assert.equal(serialized.includes('openid'), false);
  assert.equal(serialized.includes('session_key'), false);
  assert.equal(h.storage.has('records_session'), false);
  page.onUnload();
});

test('资料读取失败只提示，不伪造空资料，重试后恢复', async () => {
  const h = harness();
  h.session.set(h.identity('2'));
  const page = h.page('pages/profile/profile.js');
  page.onLoad();
  await h.flush();
  h.reply(0, null, 403, 403);
  await h.flush();
  assert.equal(page.data.profileState, 'error');
  assert.match(page.data.profileError, /权限/);
  assert.equal(page.data.userNo, '');
  page.retryProfile();
  await h.flush();
  assert.equal(h.requests.length, 2);
  h.reply(1, {
    userId: '2', userName: '若依', userNo: '0002', loginName: 'ry',
    deptName: '测试部门', phonenumber: '156****6666', bound: true, boundAt: '2026-11-19 15:34:03'
  });
  await h.flush();
  assert.equal(page.data.profileState, 'success');
  assert.equal(page.data.userNo, '0002');
  page.onUnload();
});

test('退出后清空基础资料，不残留上一个账号信息', async () => {
  const h = harness();
  h.session.set(h.identity('2'));
  const page = h.page('pages/profile/profile.js');
  page.onLoad();
  await h.flush();
  h.reply(0, {
    userId: '2', userName: '若依', userNo: '0002', loginName: 'ry',
    deptName: '测试部门', phonenumber: '156****6666', bound: true, boundAt: '2026-11-19 15:34:03'
  });
  await h.flush();
  assert.equal(page.data.userNo, '0002');
  // 退出：本地会话先清，页面资料随之清空
  const pending = h.app.logout();
  await h.flush();
  assert.equal(page.data.bound, false);
  assert.equal(page.data.userNo, '');
  assert.equal(page.data.deptName, '');
  if (h.requests.length > 1) h.requests[1].fail({ errMsg: 'offline' });
  await pending.catch(() => {});
  page.onUnload();
});

test('停用账号返回 ACCOUNT_DISABLED 时清空会话并清空资料', async () => {
  const h = harness();
  h.session.set(h.identity('2'));
  const page = h.page('pages/profile/profile.js');
  page.onLoad();
  await h.flush();
  h.reply(0, null, 'ACCOUNT_DISABLED', 403);
  await h.flush();
  assert.equal(h.session.snapshot().status, 'expired');
  assert.equal(page.data.bound, false);
  assert.equal(page.data.userNo, '');
  page.onUnload();
});

test('profile 页面 WXML 事件与页面方法完整对应', () => {
  const definition = harness().page('pages/profile/profile.js');
  const wxml = fs.readFileSync(path.join(root, 'pages/profile/profile.wxml'), 'utf8');
  const handlers = [...wxml.matchAll(/bind(?::|\w*)[\w-]*="([\w]+)"/g)].map((m) => m[1]);
  assert.ok(handlers.length > 0, '应至少有一个事件绑定');
  for (const handler of handlers) {
    assert.equal(typeof definition[handler], 'function', '缺少方法：' + handler);
  }
});

// ---------------------------------------------------------------------------
// M12-B 绑定口令（模块二关键规则 1：不允许仅凭用户编号绑定）
// ---------------------------------------------------------------------------

test('扫码页未填口令时不提交绑定请求，并给出明确提示', async () => {
  const h = harness();
  const page = h.page('pages/scan/scan.js');
  page.onLoad({ mode: 'bind' });
  page.startScan();
  h.scan('0003');
  page.confirmBind();
  await h.flush();
  assert.equal(h.requests.length, 0, '未填口令不应发出绑定请求');
  assert.equal(page.data.status, 'error');
  assert.match(page.data.message, /口令/);
  page.onUnload();
});

test('扫码页填了口令后请求体带 password，且口令不出现在页面 data 中', async () => {
  const h = harness();
  const page = h.page('pages/scan/scan.js');
  page.onLoad({ mode: 'bind' });
  page.startScan();
  h.scan('0003');
  page.onPasswordInput({ detail: { value: 'my-pass-123' } });
  assert.equal(page.data.passwordFilled, true);
  page.confirmBind();
  await h.flush();
  assert.equal(h.requests.length, 1);
  assert.equal(h.requests[0].data.userNo, '0003');
  assert.equal(h.requests[0].data.password, 'my-pass-123');
  // 口令绝不能进入 data（否则会被 setData 输出到调试器/日志）
  assert.equal(JSON.stringify(page.data).includes('my-pass-123'), false);
  h.reply(0, h.identity());
  await h.flush();
  page.onUnload();
});

test('绑定成功后口令被清空，重新扫码不会复用到另一个账号', async () => {
  const h = harness();
  const page = h.page('pages/scan/scan.js');
  page.onLoad({ mode: 'bind' });
  page.startScan();
  h.scan('0003');
  page.setBindPassword('first-pass');
  page.confirmBind();
  await h.flush();
  h.reply(0, h.identity());
  await h.flush();
  assert.equal(page.data.passwordFilled, false, '绑定成功后应清空口令状态');
  assert.equal(page._bindPassword, '', '内存里的口令也要清空');

  // 已登录状态下再次绑定会被拦下（不依赖口令），并且不会发出第二个请求，
  // 所以「复用旧口令去绑另一个账号」这条路本身也走不通。
  page.startScan();
  h.scan('0009');
  page.confirmBind();
  await h.flush();
  assert.equal(h.requests.length, 1, '登录后不允许再次绑定，不应发第二个请求');
  assert.match(page.data.message, /已登录/);
  page.onUnload();
});

test('切换模式与重新扫码都会清空已输入的口令', () => {
  const h = harness();
  const page = h.page('pages/scan/scan.js');
  page.onLoad({ mode: 'bind' });
  page.setBindPassword('should-be-cleared');
  assert.equal(page.data.passwordFilled, true);
  page.changeMode({ currentTarget: { dataset: { mode: 'pc' } } });
  assert.equal(page.data.passwordFilled, false, '切换模式应清空口令');
  assert.equal(page._bindPassword, '');

  page.changeMode({ currentTarget: { dataset: { mode: 'bind' } } });
  page.setBindPassword('again');
  page.startScan();
  assert.equal(page.data.passwordFilled, false, '重新扫码应清空口令');
  page.onUnload();
});

test('扫码页 WXML 含口令输入框且事件方法存在', () => {
  const definition = harness().page('pages/scan/scan.js');
  const wxml = fs.readFileSync(path.join(root, 'pages/scan/scan.wxml'), 'utf8');
  assert.match(wxml, /bindinput="onPasswordInput"/);
  assert.match(wxml, /password="\{\{true\}\}"/, '口令输入框应使用 password 属性遮蔽显示');
  for (const handler of [...wxml.matchAll(/bind(?::|\w*)[\w-]*="([\w]+)"/g)].map((m) => m[1])) {
    assert.equal(typeof definition[handler], 'function', '缺少方法：' + handler);
  }
});
