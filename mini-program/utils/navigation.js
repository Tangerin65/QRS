const session = require('./session');

const draftPermissions = {
  view: 'archiveQuery',
  add: 'archiveAdd',
  edit: 'archiveEdit',
  delete: 'archiveDelete',
  fileView: 'archiveFileView',
  upload: 'archiveFileUpload'
};

const entries = [
  { key: 'home', label: '工作台', path: '/pages/home/home' },
  { key: 'archives', label: '查档', title: '档案检索', desc: '查询授权范围内的档案',
    path: '/pages/archives/archives', permission: 'records:search:view', serverPermission: 'archiveSearch' },
  { key: 'borrow', label: '借阅', title: '我的借阅', desc: '查看借阅记录并提交申请',
    path: '/pages/borrow/mine/mine', permission: 'borrowView', serverPermission: 'borrowView' },
  {
    key: 'archive-drafts',
    label: '档案登记',
    title: '档案登记',
    icon: 'archives',
    path: '/pages/archive-workbench/archive-workbench',
    permission: draftPermissions.view,
    workbenchOnly: true
  },
  { key: 'messages', label: '待办', title: '我的待办', desc: '查看个人待办与业务消息',
    path: '/pages/messages/messages', permission: 'records:message:view' },
  { key: 'profile', label: '我的', title: '个人中心', desc: '账号状态与登录管理',
    path: '/pages/profile/profile' }
];
function can(permission) {
  if (!permission) return true;
  const state = session.snapshot();
  return state.status === 'authenticated' && state.user &&
    (state.user.permissions.includes(permission) || state.user.permissions.includes('*:*:*'));
}
function canOpen(entry) {
  return can(entry.permission) || Boolean(entry.serverPermission && can(entry.serverPermission));
}
function visible({ workbench = false } = {}) {
  return entries
    .filter((entry) => (!entry.workbenchOnly || workbench) && canOpen(entry))
    .map((entry) => Object.assign({}, entry));
}
function go(key) {
  if (key === 'scan' || key === 'pc') {
    wx.navigateTo({ url: '/pages/scan/scan?mode=' + (key === 'pc' ? 'pc' : 'bind') });
    return;
  }
  const entry = entries.find((item) => item.key === key);
  if (!entry || !canOpen(entry)) {
    wx.showToast({ title: '当前账号暂无此功能权限', icon: 'none' });
    return;
  }
  if (entry.workbenchOnly) {
    wx.navigateTo({ url: entry.path });
    return;
  }
  wx.reLaunch({ url: entry.path });
}
function guard(key) {
  const entry = entries.find((item) => item.key === key);
  if (entry && canOpen(entry)) return true;
  wx.showToast({ title: '请先登录并取得访问权限', icon: 'none' });
  wx.reLaunch({ url: '/pages/home/home' });
  return false;
}
module.exports = { can, visible, go, guard, draftPermissions };
