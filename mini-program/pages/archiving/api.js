// M10 归档接口层（阶段 2：契约与后端字段已对齐，MOCK 待切 false）
// 字段与文案按 doc/m10-mobile-api.md 对齐；只保留 arc_archive / arc_archive_batch / arc_location 里真实存在的列。
// 阶段 2 列已随 sql/setup_qrs.sql 迁移落地：storage_media、electronic_location、archived_by、
// archive_batch_id、arc_archive_batch.source_type/source_ref，因此页面按契约渲染 medium/mediumLabel、
// electronicLocation、batchNo、archivedBy、source/sourceLabel/sourceDetail。
// 接入真实接口：把 MOCK 改成 false 即可（第二轮联调）。

const request = require('../../utils/request');

const MOCK = false;

// 契约 §4 的 9 个接口
const PATH = {
  hub: '/api/mobile/archiving/hub/summary',
  pending: '/api/mobile/archiving/pending/list',
  check: '/api/mobile/archiving/check',
  joinBatch: '/api/mobile/archiving/pending/join-batch',
  status: '/api/mobile/archiving/status/search',
  batchList: '/api/mobile/archiving/batch/list',
  batchDetail: '/api/mobile/archiving/batch/detail',
  batchRetry: '/api/mobile/archiving/batch/retry',
  location: '/api/mobile/archiving/location/list'
};

// 契约 §5 的 11 个业务码，文案与后端一致（业务失败 HTTP 400 + 字符串 code）
const ERRORS = {
  ARCHIVE_NOT_FOUND: '档案不存在或无权限访问',
  ARCHIVE_STATUS_CONFLICT: '档案当前状态为{status}，只有「待归档」档案可以归档',
  RETENTION_UNRECOGNIZED: '保管期限无法识别：{message}，请先补正期限',
  CHECK_NOT_PASSED: '仍有检查项未通过：{label}（{detail}）；补正后才能归档',
  VERSION_CONFLICT: '档案已被修改，请刷新后重试',
  LOCATION_NOT_FOUND: '库位不存在',
  LOCATION_DISABLED: '库位已停用',
  STORAGE_UNBOUND: '存储介质为电子或混合时，必须填写电子存储位置',
  BATCH_NOT_FOUND: '批次不存在',
  BATCH_NOT_RETRYABLE: '该批次没有可重新组批的失败项',
  PARAM_INVALID: '参数不合法：{field}'
};

function text(code, params) {
  const template = ERRORS[code] || '操作未完成，请稍后重试';
  return template.replace(/\{(\w+)\}/g, (whole, key) =>
    params && params[key] !== undefined ? String(params[key]) : whole);
}

function fail(code, params) {
  return Promise.reject(new request.RequestError(text(code, params), code));
}

// 契约 §3：期限三态，years 仅 FIXED 有值，message 仅 UNKNOWN 有值
function retention(code, label, state, years, message) {
  return {
    code,
    label,
    state,
    years: state === 'FIXED' ? Number(years || 0) : null,
    message: message || ''
  };
}

// 契约 §3：检查项只有这 7 个 code，label 由后端给
const CHECK_LABELS = {
  STATUS: '档案处于待归档状态',
  CODE: '档号已生成',
  TITLE: '题名完整',
  CATEGORY: '分类为启用末级',
  RETENTION: '保管期限可识别',
  FILE: '至少一个附件',
  DESTROY: '无未结销毁申请'
};

function chk(code, passed, detail) {
  return { code, label: CHECK_LABELS[code], passed: Boolean(passed), detail: detail || '' };
}

// 批次状态（契约 §4.6）：RUNNING/SUCCESS/PARTIAL/FAILED
const BATCH_STATUS = {
  RUNNING: '进行中',
  SUCCESS: '已完成',
  PARTIAL: '部分失败',
  FAILED: '全部失败'
};

// 档案状态（契约 §4.5）
const ARCHIVE_STATUS = {
  DRAFT: '草稿',
  SUBMITTED: '待归档',
  ARCHIVED: '已归档'
};

// ---------------------------------------------------------------- 假数据
// 与本地库同规模：6 条档案（ARCHIVED 2 / SUBMITTED 3 / DRAFT 1）。
// 只放 arc_archive 存在的列；介质、电子存储位置、批次来源属阶段 2，这里不造。

const ARCHIVES = [
  {
    archiveId: '10241', archiveCode: 'QRS-2026-WS-0107', title: '2026年第三季度办公会会议纪要',
    categoryName: '文书档案', formingDate: '2026-09-30', version: 3,
    retention: retention('PERMANENT', '永久', 'PERMANENT'),
    archiveStatus: 'SUBMITTED', batchNo: 'BATCH-2026-013',
    locationCode: 'A2-03-05', locationPath: '三号库房 / A2 排 / 03 架 / 05 层',
    checkedAt: '2026-10-08 09:32', archivedAt: '',
    medium: 'PAPER', mediumLabel: '纸质', electronicLocation: '', archivedBy: '',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true), chk('CATEGORY', true),
      chk('RETENTION', true), chk('FILE', true), chk('DESTROY', true)
    ]
  },
  {
    archiveId: '10253', archiveCode: 'QRS-2026-DZ-0031', title: '智慧档案平台上线验收材料',
    categoryName: '科技档案', formingDate: '2026-09-25', version: 1,
    retention: retention('X99', '无法识别', 'UNKNOWN', null,
      '字典 arc_retention_period 中不存在该编码 X99'),
    archiveStatus: 'SUBMITTED', batchNo: 'BATCH-2026-012',
    locationCode: 'E-01-02', locationPath: '电子档案库房 / 节点 E-01-02',
    checkedAt: '2026-10-10 09:05', archivedAt: '',
    medium: 'ELECTRONIC', mediumLabel: '电子',
    electronicLocation: '/data/archive/2026/qrs/QRS-2026-DZ-0031', archivedBy: '',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true), chk('CATEGORY', true),
      chk('RETENTION', false, '字典 arc_retention_period 中不存在该编码 X99'),
      chk('FILE', true), chk('DESTROY', true)
    ]
  },
  {
    archiveId: '10260', archiveCode: 'QRS-2026-WS-0119', title: '职工代表大会材料汇编',
    categoryName: '文书档案', formingDate: '2026-09-22', version: 1,
    retention: retention('Y30', '30年', 'FIXED', 30),
    archiveStatus: 'SUBMITTED', batchNo: 'BATCH-2026-012',
    locationCode: 'A2-03-05', locationPath: '三号库房 / A2 排 / 03 架 / 05 层',
    checkedAt: '2026-10-09 15:02', archivedAt: '',
    medium: 'PAPER', mediumLabel: '纸质', electronicLocation: '', archivedBy: '',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true), chk('CATEGORY', true),
      chk('RETENTION', true), chk('FILE', false, '附件 0 个'), chk('DESTROY', true)
    ]
  },
  {
    archiveId: '10248', archiveCode: 'QRS-2026-WS-0112', title: '2026年度财务决算报告',
    categoryName: '会计档案', formingDate: '2026-09-28', version: 2,
    retention: retention('Y30', '30年', 'FIXED', 30),
    archiveStatus: 'ARCHIVED', batchNo: 'BATCH-2026-011',
    locationCode: 'A2-03-06', locationPath: '三号库房 / A2 排 / 03 架 / 06 层',
    checkedAt: '2026-10-06 10:48', archivedAt: '2026-10-06 11:20',
    medium: 'PAPER', mediumLabel: '纸质', electronicLocation: '', archivedBy: '李强',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true), chk('CATEGORY', true),
      chk('RETENTION', true), chk('FILE', true), chk('DESTROY', true)
    ]
  },
  {
    archiveId: '10266', archiveCode: 'QRS-2026-KY-0008', title: '馆藏数字化项目结项报告',
    categoryName: '科技档案', formingDate: '2026-09-18', version: 4,
    retention: retention('Y10', '10年', 'FIXED', 10),
    archiveStatus: 'ARCHIVED', batchNo: 'BATCH-2026-011',
    locationCode: 'E-01-01', locationPath: '电子档案库房 / 节点 E-01-01',
    checkedAt: '2026-10-01 02:10', archivedAt: '2026-10-01 02:14',
    medium: 'ELECTRONIC', mediumLabel: '电子',
    electronicLocation: '/data/archive/2026/qrs/QRS-2026-KY-0008', archivedBy: '李强',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true), chk('CATEGORY', true),
      chk('RETENTION', true), chk('FILE', true), chk('DESTROY', true)
    ]
  },
  {
    archiveId: '10301', archiveCode: 'QRS-2026-WS-0144', title: '2026年干部教育培训记录',
    categoryName: '文书档案', formingDate: '2026-07-28', version: 1,
    retention: retention('Y10', '10年', 'FIXED', 10),
    archiveStatus: 'DRAFT', batchNo: '',
    locationCode: '', locationPath: '',
    checkedAt: '2026-10-08 11:20', archivedAt: '',
    medium: '', mediumLabel: '', electronicLocation: '', archivedBy: '',
    checks: [
      chk('STATUS', false, '当前状态 DRAFT'), chk('CODE', true), chk('TITLE', true),
      chk('CATEGORY', true), chk('RETENTION', true), chk('FILE', true), chk('DESTROY', true)
    ]
  }
];

// 批次只放 arc_archive_batch 的列（batch_no/remark/total_count/success_count/fail_count/batch_status/submit_time/finish_time/…）
const BATCH_RAW = [
  {
    batchId: '9003', batchNo: 'BATCH-2026-013', name: '馆藏数字化项目电子档案批次',
    total: 1, success: 0, fail: 0,
    retentionText: '永久 2 / 30年 3 / 10年 1',
    status: 'RUNNING', applicantName: '张敏', locationCode: 'A2-03-05',
    source: 'MOBILE', sourceLabel: '小程序归档', sourceDetail: '',
    createdAt: '2026-10-10 08:30', updatedAt: '2026-10-10 09:05',
    returnedReason: ''
  },
  {
    batchId: '9002', batchNo: 'BATCH-2026-012', name: '2026 年三季度文书档案批次',
    total: 2, success: 0, fail: 2,
    retentionText: '30年 1 / 无法识别 1',
    status: 'PARTIAL', applicantName: '张敏', locationCode: 'A2-03-05',
    source: 'MANUAL', sourceLabel: '手工归档', sourceDetail: '',
    createdAt: '2026-10-09 14:10', updatedAt: '2026-10-09 15:02',
    returnedReason: '保管期限无法识别：字典 arc_retention_period 中不存在该编码 X99；附件 0 个'
  },
  {
    batchId: '9001', batchNo: 'BATCH-2026-011', name: '2026 年度会计与科技档案批次',
    total: 2, success: 2, fail: 0,
    retentionText: '30年 1 / 10年 1',
    status: 'SUCCESS', applicantName: '李强', locationCode: 'A2-03-06',
    source: 'REBATCH', sourceLabel: '重新组批', sourceDetail: '原批次 BATCH-2026-010',
    createdAt: '2026-10-06 10:05', updatedAt: '2026-10-06 11:20',
    returnedReason: ''
  }
];

// 批次明细（契约 §4.7）：items 带 medium/mediumLabel/archivedBy（与 PC 同一 batchItemFacts 口径）
const BATCH_ITEMS = {
  'BATCH-2026-013': [
    { archiveId: '10241', archiveCode: 'QRS-2026-WS-0107', title: '2026年第三季度办公会会议纪要', itemResult: 'OK', reasonCode: '', reason: '', medium: 'PAPER', mediumLabel: '纸质', archivedBy: '' }
  ],
  'BATCH-2026-012': [
    { archiveId: '10253', archiveCode: 'QRS-2026-DZ-0031', title: '智慧档案平台上线验收材料', itemResult: 'FAIL', reasonCode: 'RETENTION', reason: '字典 arc_retention_period 中不存在该编码 X99', medium: 'ELECTRONIC', mediumLabel: '电子', archivedBy: '' },
    { archiveId: '10260', archiveCode: 'QRS-2026-WS-0119', title: '职工代表大会材料汇编', itemResult: 'FAIL', reasonCode: 'CHECK', reason: '附件 0 个', medium: 'PAPER', mediumLabel: '纸质', archivedBy: '' }
  ],
  'BATCH-2026-011': [
    { archiveId: '10248', archiveCode: 'QRS-2026-WS-0112', title: '2026年度财务决算报告', itemResult: 'OK', reasonCode: '', reason: '', medium: 'PAPER', mediumLabel: '纸质', archivedBy: '李强' },
    { archiveId: '10266', archiveCode: 'QRS-2026-KY-0008', title: '馆藏数字化项目结项报告', itemResult: 'OK', reasonCode: '', reason: '', medium: 'ELECTRONIC', mediumLabel: '电子', archivedBy: '李强' }
  ]
};

// 库位只放 arc_location 的列（location_code/location_name/level/level_path/capacity/used/unit/status/…）
const LOCATION_RAW = [
  {
    locationId: '501', locationCode: 'A2-03-05', locationName: '三号库房 A2 排 03 架 05 层',
    level: 'RACK', levelName: '柜架', levelPath: '三号库房 / A2 排 / 03 架 / 05 层',
    house: '三号库房', capacity: 30, used: 2, unit: '件', percent: 7,
    medium: 'PAPER', mediumLabel: '纸质', electronicPath: '',
    status: 0, statusLabel: '启用', remark: '恒温恒湿',
    archives: [
      { archiveCode: 'QRS-2026-WS-0107', title: '2026年第三季度办公会会议纪要' },
      { archiveCode: 'QRS-2026-WS-0119', title: '职工代表大会材料汇编' }
    ]
  },
  {
    locationId: '502', locationCode: 'A2-03-06', locationName: '三号库房 A2 排 03 架 06 层',
    level: 'RACK', levelName: '柜架', levelPath: '三号库房 / A2 排 / 03 架 / 06 层',
    house: '三号库房', capacity: 30, used: 1, unit: '件', percent: 3,
    medium: 'PAPER', mediumLabel: '纸质', electronicPath: '',
    status: 0, statusLabel: '启用', remark: '会计档案专架',
    archives: [
      { archiveCode: 'QRS-2026-WS-0112', title: '2026年度财务决算报告' }
    ]
  },
  {
    locationId: '503', locationCode: 'B1-01-02', locationName: '一号库房 B1 排 01 架 02 层',
    level: 'RACK', levelName: '柜架', levelPath: '一号库房 / B1 排 / 01 架 / 02 层',
    house: '一号库房', capacity: 40, used: 0, unit: '件', percent: 0,
    medium: '', mediumLabel: '', electronicPath: '',
    status: 0, statusLabel: '启用', remark: '',
    archives: []
  },
  {
    locationId: '601', locationCode: 'E-01-01', locationName: '电子档案库房 节点 E-01-01',
    level: 'RACK', levelName: '存储节点', levelPath: '电子档案库房 / 节点 E-01-01',
    house: '电子档案库房', capacity: 2048, used: 1, unit: '件', percent: 1,
    medium: 'ELECTRONIC', mediumLabel: '电子',
    electronicPath: '/data/archive/2026/qrs/QRS-2026-KY-0008',
    status: 0, statusLabel: '启用', remark: '每 24 小时增量备份',
    archives: [
      { archiveCode: 'QRS-2026-KY-0008', title: '馆藏数字化项目结项报告' }
    ]
  },
  {
    locationId: '602', locationCode: 'E-01-02', locationName: '电子档案库房 节点 E-01-02',
    level: 'RACK', levelName: '存储节点', levelPath: '电子档案库房 / 节点 E-01-02',
    house: '电子档案库房', capacity: 5120, used: 0, unit: '件', percent: 0,
    medium: '', mediumLabel: '', electronicPath: '',
    status: 0, statusLabel: '启用', remark: '永久保管电子档案专用',
    archives: []
  },
  {
    locationId: '504', locationCode: 'C3-02-01', locationName: '二号库房 C3 排 02 架 01 层',
    level: 'RACK', levelName: '柜架', levelPath: '二号库房 / C3 排 / 02 架 / 01 层',
    house: '二号库房', capacity: 40, used: 0, unit: '件', percent: 0,
    medium: '', mediumLabel: '', electronicPath: '',
    status: 1, statusLabel: '停用', remark: '除湿设备检修，暂停上架',
    archives: []
  }
];

// ---------------------------------------------------------------- 工具

function delay(data, ms) {
  return new Promise((resolve) => setTimeout(() => resolve(data), ms || 220));
}

function nowText() {
  const date = new Date();
  const pad = (value) => String(value).padStart(2, '0');
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) +
    ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes());
}

function paginate(rows, query) {
  const pageNum = Math.max(1, Number(query && query.pageNum) || 1);
  const pageSize = Math.min(50, Math.max(1, Number(query && query.pageSize) || 6));
  const start = (pageNum - 1) * pageSize;
  const slice = rows.slice(start, start + pageSize);
  return { rows: slice, total: rows.length, pageNum, pageSize, hasMore: start + slice.length < rows.length };
}

// 汇总检查结果（契约：check = {passed,total,tone,label,checkedAt}）
function summarize(checks, checkedAt) {
  const total = checks.length;
  const passed = checks.filter((item) => item.passed).length;
  return {
    passed,
    total,
    tone: passed === total ? 'pass' : 'fail',
    label: passed === total ? '检查通过' : '需补正',
    checkedAt: checkedAt || ''
  };
}

// 空值统一「未登记」（契约 §7：列/字段为空时前端显示「未登记」），label 一律用后端返回值
const BLANK = '未登记';

function presentArchive(row) {
  return Object.assign({}, row, {
    statusLabel: ARCHIVE_STATUS[row.archiveStatus] || row.archiveStatus,
    mediumText: row.mediumLabel || BLANK,
    mediumTone: row.medium || '',
    electronicText: row.electronicLocation || BLANK,
    archivedByText: row.archivedBy || BLANK,
    batchText: row.batchNo || BLANK,
    locationText: row.locationCode || '未上架',
    archivedText: row.archivedAt || '未归档',
    lastCheckAt: row.checkedAt || '',
    check: summarize(row.checks, row.checkedAt)
  });
}

function presentBatch(row) {
  // 与 PC 同口径：sourceLabel + （原批次 X），空来源显示「未登记」（PC batch.js sourceText）
  const label = row.sourceLabel || BLANK;
  return Object.assign({}, row, {
    statusLabel: BATCH_STATUS[row.status] || row.status,
    sourceLabelText: label,
    sourceText: row.sourceDetail ? label + '（' + row.sourceDetail + '）' : label,
    canRetry: Number(row.fail || 0) > 0,
    hasReturnedReason: Boolean(row.returnedReason)
  });
}

function presentLocation(row) {
  return Object.assign({}, row, {
    capacityText: row.capacity ? row.used + ' / ' + row.capacity + ' ' + row.unit : row.used + ' 件（不限容量）',
    mediumText: row.mediumLabel || BLANK,
    mediumTone: row.medium || '',
    electronicText: row.electronicPath || BLANK,
    statusTone: Number(row.status) === 0 ? 'available' : 'disabled',
    percentClamped: Math.min(100, Math.max(0, Number(row.percent) || 0)),
    isOverCapacity: Boolean(row.capacity) && Number(row.used) > Number(row.capacity)
  });
}

// 批次明细行（契约 §4.7 items）：介质、归档人由后端 batchItemFacts() 给出，空值「未登记」
function presentItem(item) {
  return Object.assign({}, item, {
    mediumText: item.mediumLabel || BLANK,
    mediumTone: item.medium || '',
    archivedByText: item.archivedBy || BLANK
  });
}

// 真接口与桩走同一套展示映射：页面只认 present* 产出的字段，切 MOCK=false 不用改页面。
function presentRows(data, mapper) {
  const result = Object.assign({}, data || {});
  result.rows = ((data && data.rows) || []).map(mapper);
  return result;
}

// ---------------------------------------------------------------- 对外接口

function hubSummary() {
  if (!MOCK) return request.get(PATH.hub, {});
  const pending = ARCHIVES.filter((row) => row.archiveStatus === 'SUBMITTED').map(presentArchive);
  const batches = BATCH_RAW.map(presentBatch);
  const locations = LOCATION_RAW.map(presentLocation);
  return delay({
    pending: pending.length,
    needFix: pending.filter((row) => row.retention.state === 'UNKNOWN' || row.check.tone === 'fail').length,
    unknownRetention: pending.filter((row) => row.retention.state === 'UNKNOWN').length,
    batchOpen: batches.filter((row) => row.status === 'RUNNING').length,
    batchReturned: batches.filter((row) => row.status === 'PARTIAL' || row.status === 'FAILED').length,
    locationAvailable: locations.filter((row) => Number(row.status) === 0 &&
      (!row.capacity || row.used < row.capacity)).length,
    locationTotal: locations.length
  });
}

function pendingList(query) {
  if (!MOCK) return request.post(PATH.pending, query).then((data) => presentRows(data, presentArchive));
  const filter = (query && query.filter) || 'ALL';
  const keyword = String((query && query.keyword) || '').trim().toUpperCase();
  let rows = ARCHIVES.filter((row) => row.archiveStatus === 'SUBMITTED').map(presentArchive);
  if (filter === 'NEED_FIX') rows = rows.filter((row) => row.check.tone === 'fail');
  if (filter === 'UNKNOWN') rows = rows.filter((row) => row.retention.state === 'UNKNOWN');
  if (keyword) {
    rows = rows.filter((row) =>
      row.archiveCode.toUpperCase().indexOf(keyword) >= 0 || row.title.indexOf(keyword) >= 0);
  }
  return delay(paginate(rows, query));
}

// 校验报告开关（契约 §6）：本地未建 arc_archive_check_report 表时后端返回 reportEnabled:false，
// 页面据此区分「报告未启用」与「暂无报告」。桩里默认启用，联调前可用 setReportEnabled(false) 复现未建表态。
let reportEnabled = true;

function setReportEnabled(value) {
  reportEnabled = Boolean(value);
}

// 通用校验（契约 §4.3）：两个页面共用，真写 CHECK 报告由后端做，桩里刷新检查时间
function check(query) {
  if (!MOCK) return request.post(PATH.check, query);
  const code = (query && query.archiveCode) || '';
  const row = ARCHIVES.find((item) => item.archiveCode === code);
  if (!row) return fail('ARCHIVE_NOT_FOUND');
  const checks = row.checks.map((item) => Object.assign({}, item));
  const passed = checks.every((item) => item.passed);
  const checkedAt = nowText();
  row.checkedAt = checkedAt;
  return delay({
    archiveId: row.archiveId,
    archiveCode: row.archiveCode,
    pass: passed,
    retention: row.retention,
    reportId: reportEnabled ? '8802' : '',
    reportEnabled,
    checkedAt,
    checks,
    message: passed ? '重新检查完成：全部通过' : '重新检查完成：仍有未通过项'
  });
}

// 加入批次 = 单件组批并立即归档（契约 §4.4，幂等）
function joinBatch(query) {
  if (!MOCK) return request.post(PATH.joinBatch, query);
  const code = (query && (query.archiveCode || query.archiveId)) || '';
  const row = ARCHIVES.find((item) => item.archiveCode === code || item.archiveId === String(code));
  if (!row) return fail('ARCHIVE_NOT_FOUND');
  if (row.archiveStatus === 'ARCHIVED') {
    return delay({ archiveCode: row.archiveCode, batchNo: row.batchNo, message: '已归档，批次号 ' + row.batchNo });
  }
  if (row.archiveStatus !== 'SUBMITTED') {
    return fail('ARCHIVE_STATUS_CONFLICT', { status: ARCHIVE_STATUS[row.archiveStatus] || row.archiveStatus });
  }
  // 契约 §4.4：入参不带 version，服务端在事务内按当时读到的版本做乐观锁
  const retentionFailed = row.checks.find((item) => item.code === 'RETENTION' && !item.passed);
  if (retentionFailed) return fail('RETENTION_UNRECOGNIZED', { message: retentionFailed.detail });
  const otherFailed = row.checks.find((item) => !item.passed);
  if (otherFailed) {
    return fail('CHECK_NOT_PASSED', { label: otherFailed.label, detail: otherFailed.detail });
  }
  const batchNo = 'BATCH-2026-' + String(900 + ARCHIVES.indexOf(row));
  row.archiveStatus = 'ARCHIVED';
  row.batchNo = batchNo;
  row.archivedAt = nowText();
  row.version += 1;
  return delay({
    batchNo,
    archiveId: row.archiveId,
    archiveCode: row.archiveCode,
    archiveStatus: 'ARCHIVED',
    locationCode: row.locationCode,
    archivedAt: row.archivedAt,
    message: '已归档，批次号 ' + batchNo
  });
}

function statusSearch(query) {
  if (!MOCK) return request.post(PATH.status, query).then((data) => presentRows(data, presentArchive));
  const keyword = String((query && query.keyword) || '').trim().toUpperCase();
  const filter = (query && query.filter) || 'ALL';
  let rows = ARCHIVES.map(presentArchive);
  if (filter === 'PERMANENT') rows = rows.filter((row) => row.retention.state === 'PERMANENT');
  if (filter === 'FIXED') rows = rows.filter((row) => row.retention.state === 'FIXED');
  if (filter === 'UNKNOWN') rows = rows.filter((row) => row.retention.state === 'UNKNOWN');
  if (keyword) {
    rows = rows.filter((row) =>
      row.archiveCode.toUpperCase().indexOf(keyword) >= 0 || row.title.indexOf(keyword) >= 0);
  }
  return delay(paginate(rows, query));
}

function batchList(query) {
  if (!MOCK) return request.post(PATH.batchList, query).then((data) => presentRows(data, presentBatch));
  const filter = (query && query.filter) || 'ALL';
  const keyword = String((query && query.keyword) || '').trim().toUpperCase();
  let rows = BATCH_RAW.map(presentBatch);
  if (filter === 'OPEN') rows = rows.filter((row) => row.status === 'RUNNING');
  if (filter === 'RETURNED') rows = rows.filter((row) => row.status === 'PARTIAL' || row.status === 'FAILED');
  if (keyword) {
    rows = rows.filter((row) =>
      row.batchNo.toUpperCase().indexOf(keyword) >= 0 ||
      String(row.name).indexOf(keyword) >= 0 ||
      String(row.applicantName).toUpperCase().indexOf(keyword) >= 0);
  }
  return delay(paginate(rows, query));
}

function batchDetail(batchNo) {
  if (!MOCK) {
    return request.get(PATH.batchDetail, { batchNo }).then((data) => ({
      batch: presentBatch((data && data.batch) || {}),
      items: ((data && data.items) || []).map(presentItem)
    }));
  }
  const row = BATCH_RAW.find((item) => item.batchNo === batchNo);
  if (!row) return fail('BATCH_NOT_FOUND');
  const items = (BATCH_ITEMS[batchNo] || []).map(presentItem);
  return delay({ batch: presentBatch(row), items });
}

// 重新组批（契约 §4.8）：按失败项重校验并生成新批次，原批次不改写
function batchRetry(batchNo) {
  if (!MOCK) return request.post(PATH.batchRetry, { batchNo });
  const row = BATCH_RAW.find((item) => item.batchNo === batchNo);
  if (!row) return fail('BATCH_NOT_FOUND');
  if (Number(row.fail || 0) === 0) return fail('BATCH_NOT_RETRYABLE');
  const newNo = 'BATCH-2026-' + String(950 + BATCH_RAW.length);
  BATCH_RAW.unshift({
    batchId: String(9100 + BATCH_RAW.length),
    batchNo: newNo,
    name: '重新组批：' + row.batchNo,
    total: row.total, success: row.total, fail: 0,
    retentionText: row.retentionText,
    status: 'SUCCESS', applicantName: row.applicantName, locationCode: row.locationCode,
    createdAt: nowText(), updatedAt: nowText(),
    returnedReason: ''
  });
  BATCH_ITEMS[newNo] = (BATCH_ITEMS[batchNo] || []).map((item) => Object.assign({}, item, {
    itemResult: 'OK', reasonCode: '', reason: ''
  }));
  return delay({
    sourceBatchNo: row.batchNo, batchNo: newNo, total: row.total, success: row.total, fail: 0,
    message: '已重新组批：' + newNo
  });
}

function locationList(query) {
  if (!MOCK) return request.post(PATH.location, query).then((data) => presentRows(data, presentLocation));
  const keyword = String((query && query.keyword) || '').trim().toUpperCase();
  let rows = LOCATION_RAW.map(presentLocation);
  if (keyword) {
    rows = rows.filter((row) =>
      row.locationCode.toUpperCase().indexOf(keyword) >= 0 ||
      String(row.locationName).indexOf(keyword) >= 0);
  }
  return delay(paginate(rows, query));
}

module.exports = {
  MOCK,
  PATH,
  ERRORS,
  setReportEnabled,
  hub: hubSummary,
  pending: pendingList,
  check,
  joinBatch,
  status: statusSearch,
  batch: batchList,
  batchDetail,
  retryBatch: batchRetry,
  location: locationList
};
