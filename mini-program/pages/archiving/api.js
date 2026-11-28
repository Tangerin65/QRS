// M10 归档接口层（阶段 1：接口桩，展示映射已按 doc/m10-mobile-api.md 对齐）
// 后端 /api/mobile/archiving/** 尚未实现，本文件用 MOCK 假数据把页面跑通。
// 接入真实接口：把 MOCK 改成 false；字段与错误文案都按契约返回，页面无需再改映射。
// 请求约定沿用 utils/request.js：Bearer 令牌、{code,msg,data} 响应、业务失败走 HTTP 400 + 字符串 code。

const request = require('../../utils/request');

const MOCK = true;

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

const MEDIUM = { PAPER: '纸质', ELECTRONIC: '电子', MIXED: '混合' };

// 批次状态（契约 §4.6）：RUNNING/SUCCESS/PARTIAL/FAILED
const BATCH_STATUS = {
  RUNNING: '进行中',
  SUCCESS: '已完成',
  PARTIAL: '部分失败',
  FAILED: '全部失败'
};

const BATCH_SOURCE = {
  TRANSFER: '移交归档',
  AUTO: '自动汇集',
  MANUAL: '手动组批',
  REBATCH: '重新组批',
  MOBILE: '移动端组批'
};

// 档案状态（契约 §4.5）
const ARCHIVE_STATUS = {
  DRAFT: '草稿',
  SUBMITTED: '待归档',
  ARCHIVED: '已归档'
};

// ---------------------------------------------------------------- 假数据

const PENDING_RAW = [
  {
    archiveId: '10241', archiveCode: 'QRS-2025-WS-0107', title: '2025年第三季度办公会会议纪要',
    categoryName: '文书档案', formingDate: '2025-09-30', version: 3,
    retention: retention('PERMANENT', '永久', 'PERMANENT'),
    medium: 'PAPER', mediumLabel: '纸质', storageText: '三号库房 A2-03-05',
    batchNo: '', checkedAt: '2025-10-08 09:32',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true),
      chk('CATEGORY', true), chk('RETENTION', true), chk('FILE', true)
    ]
  },
  {
    archiveId: '10248', archiveCode: 'QRS-2025-WS-0112', title: '2025年度财务决算报告',
    categoryName: '会计档案', formingDate: '2025-09-28', version: 2,
    retention: retention('Y30', '30年', 'FIXED', 30),
    medium: 'MIXED',
    mediumLabel: '混合',
    storageText: '三号库房 A2-03-06；电子 NAS-01 /vault/2025/finance',
    batchNo: '', checkedAt: '2025-10-08 10:05',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true),
      chk('CATEGORY', true), chk('RETENTION', true), chk('FILE', true)
    ]
  },
  {
    archiveId: '10253', archiveCode: 'QRS-2025-DZ-0031', title: '智慧档案平台上线验收材料',
    categoryName: '科技档案', formingDate: '2025-09-25', version: 1,
    retention: retention('X99', '无法识别', 'UNKNOWN', null,
      '字典 arc_retention_period 中不存在该编码 X99'),
    medium: 'ELECTRONIC', mediumLabel: '电子',
    storageText: '电子档案库房 E-01-02 /vault/2025/permanent',
    batchNo: '', checkedAt: '2025-10-10 09:05',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true),
      chk('CATEGORY', true),
      chk('RETENTION', false, '字典 arc_retention_period 中不存在该编码 X99'),
      chk('FILE', true)
    ]
  },
  {
    archiveId: '10260', archiveCode: 'QRS-2025-WS-0119', title: '职工代表大会材料汇编',
    categoryName: '文书档案', formingDate: '2025-09-22', version: 1,
    retention: retention('Y30', '30年', 'FIXED', 30),
    medium: 'MIXED', mediumLabel: '混合',
    storageText: '三号库房 A2-03-05；电子 NAS-01 /vault/2025/ws',
    batchNo: '', checkedAt: '2025-10-09 15:02',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true),
      chk('CATEGORY', true), chk('RETENTION', true), chk('FILE', false, '附件 0 个')
    ]
  },
  {
    archiveId: '10266', archiveCode: 'QRS-2025-KY-0008', title: '馆藏数字化项目结项报告',
    categoryName: '科技档案', formingDate: '2025-09-18', version: 4,
    retention: retention('Y10', '10年', 'FIXED', 10),
    medium: 'ELECTRONIC', mediumLabel: '电子',
    storageText: '电子档案库房 E-01-01 /vault/2025/digital',
    batchNo: '', checkedAt: '2025-10-01 02:10',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true),
      chk('CATEGORY', true), chk('RETENTION', true), chk('FILE', true)
    ]
  },
  {
    archiveId: '10271', archiveCode: 'QRS-2025-WS-0123', title: '2025年档案工作要点',
    categoryName: '文书档案', formingDate: '2025-09-15', version: 1,
    retention: retention('Y30', '30年', 'FIXED', 30),
    medium: 'PAPER', mediumLabel: '纸质', storageText: '三号库房 A2-03-06',
    batchNo: '', checkedAt: '2025-10-09 14:40',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true),
      chk('CATEGORY', false, '分类已停用或不是末级'), chk('RETENTION', true),
      chk('FILE', true)
    ]
  },
  {
    archiveId: '10278', archiveCode: 'QRS-2025-WS-0126', title: '设备采购合同（2025-07）',
    categoryName: '合同档案', formingDate: '2025-08-30', version: 2,
    retention: retention('长期', '无法识别', 'UNKNOWN', null,
      '字典标签无法解析，应为「永久」或阿拉伯数字+年（编码 9，标签 长期）'),
    medium: 'MIXED', mediumLabel: '混合',
    storageText: '三号库房 A2-03-06；电子 NAS-01 /vault/2025/contract',
    batchNo: '', checkedAt: '2025-10-09 10:15',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true),
      chk('CATEGORY', true),
      chk('RETENTION', false, '字典标签无法解析，应为「永久」或阿拉伯数字+年（编码 9，标签 长期）'),
      chk('FILE', true)
    ]
  },
  {
    archiveId: '10284', archiveCode: 'QRS-2025-WS-0131', title: '2025年上半年信访件办理卷',
    categoryName: '文书档案', formingDate: '2025-08-20', version: 1,
    retention: retention('Y30', '30年', 'FIXED', 30),
    medium: 'PAPER', mediumLabel: '纸质', storageText: '三号库房 A2-03-05',
    batchNo: '', checkedAt: '2025-10-09 16:20',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true),
      chk('CATEGORY', true), chk('RETENTION', true), chk('FILE', true)
    ]
  },
  {
    archiveId: '10290', archiveCode: 'QRS-2025-DZ-0044', title: '网站信息发布审批单台账',
    categoryName: '电子档案', formingDate: '2025-08-12', version: 5,
    retention: retention('PERMANENT', '永久', 'PERMANENT'),
    medium: 'ELECTRONIC', mediumLabel: '电子',
    storageText: '电子档案库房 E-01-02 /vault/2025/permanent',
    batchNo: '', checkedAt: '2025-10-10 09:02',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true),
      chk('CATEGORY', true), chk('RETENTION', true), chk('FILE', true),
      chk('DESTROY', false, '未结销毁单 1 条')
    ]
  },
  {
    archiveId: '10296', archiveCode: 'QRS-2025-WS-0140', title: '2025年节能改造工程图纸',
    categoryName: '基建档案', formingDate: '2025-08-05', version: 2,
    retention: retention('PERMANENT', '永久', 'PERMANENT'),
    medium: 'MIXED', mediumLabel: '混合',
    storageText: '二号库房 C3-02-01；电子 NAS-02 /vault/2025/project',
    batchNo: '', checkedAt: '2025-10-09 16:48',
    checks: [
      chk('STATUS', false, '当前状态 ARCHIVED'), chk('CODE', true), chk('TITLE', true),
      chk('CATEGORY', true), chk('RETENTION', true), chk('FILE', true)
    ]
  },
  {
    archiveId: '10301', archiveCode: 'QRS-2025-WS-0144', title: '2025年干部教育培训记录',
    categoryName: '文书档案', formingDate: '2025-07-28', version: 1,
    retention: retention('Y10', '10年', 'FIXED', 10),
    medium: 'PAPER', mediumLabel: '纸质', storageText: '三号库房 A2-03-06',
    batchNo: '', checkedAt: '2025-10-08 11:20',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true),
      chk('CATEGORY', true), chk('RETENTION', true), chk('FILE', true)
    ]
  },
  {
    archiveId: '10308', archiveCode: 'QRS-2025-DZ-0051', title: '2025年度网站群内容备份说明',
    categoryName: '电子档案', formingDate: '2025-07-15', version: 1,
    retention: retention('Y10', '10年', 'FIXED', 10),
    medium: 'ELECTRONIC', mediumLabel: '电子',
    storageText: '电子档案库房 E-01-01 /vault/2025/digital',
    batchNo: '', checkedAt: '2025-10-10 09:00',
    checks: [
      chk('STATUS', true), chk('CODE', true), chk('TITLE', true),
      chk('CATEGORY', true), chk('RETENTION', true), chk('FILE', true)
    ]
  }
];

const BATCH_RAW = [
  {
    batchId: '9001', batchNo: 'BATCH-2025-012', name: '综合办公室 2025 年三季度归档批次',
    source: 'TRANSFER', sourceLabel: '移交归档',
    sourceDetail: '综合办公室 2025-10-08 移交清单（12 件）',
    total: 12, success: 10, fail: 2,
    retentionText: '永久 3 / 30年 7 / 10年 2',
    status: 'PARTIAL', applicantName: '张敏', locationCode: 'A2-03-05',
    createdAt: '2025-10-08 09:20', updatedAt: '2025-10-09 15:02',
    returnedReason: '保管期限无法识别：字典 arc_retention_period 中不存在该编码 X99；附件 0 个',
    returnedBy: '', returnedAt: ''
  },
  {
    batchId: '9002', batchNo: 'BATCH-2025-011', name: '财务处 2025 年度会计档案批次',
    source: 'TRANSFER', sourceLabel: '移交归档',
    sourceDetail: '财务处 2025-10-06 移交清单（9 件）',
    total: 9, success: 9, fail: 0,
    retentionText: '永久 0 / 30年 9',
    status: 'SUCCESS', applicantName: '李强', locationCode: 'A2-03-06',
    createdAt: '2025-10-06 10:05', updatedAt: '2025-10-06 11:20',
    returnedReason: '', returnedBy: '', returnedAt: ''
  },
  {
    batchId: '9003', batchNo: 'BATCH-2025-010', name: '系统自动汇集：OA 发文 2025-09',
    source: 'AUTO', sourceLabel: '自动汇集',
    sourceDetail: 'OA 系统 2025-09 月结归档（自动重试 1 次）',
    total: 26, success: 26, fail: 0,
    retentionText: '永久 8 / 30年 12 / 10年 6',
    status: 'SUCCESS', applicantName: '系统', locationCode: 'A2-03-05',
    createdAt: '2025-10-01 02:00', updatedAt: '2025-10-01 02:14',
    returnedReason: '', returnedBy: '', returnedAt: ''
  },
  {
    batchId: '9004', batchNo: 'BATCH-2025-013', name: '馆藏数字化项目电子档案批次',
    source: 'AUTO', sourceLabel: '自动汇集',
    sourceDetail: '数字化平台 2025-10 自动汇集（进行中 6/12）',
    total: 12, success: 6, fail: 0,
    retentionText: '永久 5 / 30年 4 / 10年 3',
    status: 'RUNNING', applicantName: '系统', locationCode: 'E-01-02',
    createdAt: '2025-10-10 08:30', updatedAt: '2025-10-10 09:05',
    returnedReason: '', returnedBy: '', returnedAt: ''
  },
  {
    batchId: '9005', batchNo: 'BATCH-2025-009', name: '人事处 2024 年岗位聘任材料',
    source: 'MANUAL', sourceLabel: '手动组批',
    sourceDetail: '档案室手工组批（检索条件：人事类 2024）',
    total: 34, success: 0, fail: 34,
    retentionText: '永久 0 / 30年 34',
    status: 'FAILED', applicantName: '张敏', locationCode: 'C3-02-01',
    createdAt: '2025-09-26 14:10', updatedAt: '2025-09-27 11:22',
    returnedReason: '分类已停用或不是末级；档案当前状态为 ARCHIVED，只有「待归档」档案可以归档',
    returnedBy: '', returnedAt: ''
  },
  {
    batchId: '9006', batchNo: 'BATCH-2025-008', name: '2024 年度归档总批次（补录）',
    source: 'MANUAL', sourceLabel: '手动组批',
    sourceDetail: '档案室手工组批（补录 2024 年遗漏件）',
    total: 7, success: 7, fail: 0,
    retentionText: '永久 1 / 30年 4 / 10年 2',
    status: 'SUCCESS', applicantName: '张敏', locationCode: 'A2-03-05',
    createdAt: '2025-09-18 09:40', updatedAt: '2025-09-19 10:05',
    returnedReason: '', returnedBy: '', returnedAt: ''
  }
];

// 批次明细（契约 §4.7）
const BATCH_ITEMS = {
  'BATCH-2025-012': [
    { archiveId: '10241', archiveCode: 'QRS-2025-WS-0107', title: '2025年第三季度办公会会议纪要', itemResult: 'OK', reasonCode: '', reason: '' },
    { archiveId: '10253', archiveCode: 'QRS-2025-DZ-0031', title: '智慧档案平台上线验收材料', itemResult: 'FAIL', reasonCode: 'RETENTION', reason: '字典 arc_retention_period 中不存在该编码 X99' },
    { archiveId: '10260', archiveCode: 'QRS-2025-WS-0119', title: '职工代表大会材料汇编', itemResult: 'FAIL', reasonCode: 'CHECK', reason: '附件 0 个' }
  ],
  'BATCH-2025-011': [
    { archiveId: '10248', archiveCode: 'QRS-2025-WS-0112', title: '2025年度财务决算报告', itemResult: 'OK', reasonCode: '', reason: '' }
  ],
  'BATCH-2025-009': [
    { archiveId: '10296', archiveCode: 'QRS-2025-WS-0140', title: '2025年节能改造工程图纸', itemResult: 'FAIL', reasonCode: 'CONFLICT', reason: '档案当前状态为 ARCHIVED，只有「待归档」档案可以归档' },
    { archiveId: '10278', archiveCode: 'QRS-2025-WS-0126', title: '设备采购合同（2025-07）', itemResult: 'FAIL', reasonCode: 'CHECK', reason: '分类已停用或不是末级' }
  ]
};

const LOCATION_RAW = [
  {
    locationId: '501', locationCode: 'A2-03-05', locationName: '三号库房 A2 排 03 架 05 层',
    level: 'RACK', levelName: '柜架', levelPath: '三号库房 / A2 排 / 03 架 / 05 层',
    house: '三号库房', capacity: 30, used: 18, unit: '件', percent: 60,
    status: 0, statusLabel: '启用', medium: 'PAPER', mediumLabel: '纸质',
    electronicPath: '', note: '恒温恒湿',
    archives: [
      { archiveCode: 'QRS-2025-WS-0107', title: '2025年第三季度办公会会议纪要' },
      { archiveCode: 'QRS-2025-WS-0119', title: '职工代表大会材料汇编' }
    ]
  },
  {
    locationId: '502', locationCode: 'A2-03-06', locationName: '三号库房 A2 排 03 架 06 层',
    level: 'RACK', levelName: '柜架', levelPath: '三号库房 / A2 排 / 03 架 / 06 层',
    house: '三号库房', capacity: 30, used: 30, unit: '件', percent: 100,
    status: 0, statusLabel: '启用', medium: 'MIXED', mediumLabel: '混合',
    electronicPath: 'NAS-01 /vault/2025/finance', note: '纸质与电子对照存放',
    archives: [
      { archiveCode: 'QRS-2025-WS-0112', title: '2025年度财务决算报告' }
    ]
  },
  {
    locationId: '503', locationCode: 'B1-01-02', locationName: '一号库房 B1 排 01 架 02 层',
    level: 'RACK', levelName: '柜架', levelPath: '一号库房 / B1 排 / 01 架 / 02 层',
    house: '一号库房', capacity: 40, used: 22, unit: '件', percent: 55,
    status: 0, statusLabel: '启用', medium: 'MIXED', mediumLabel: '混合',
    electronicPath: 'NAS-01 /vault/2025/ws', note: '纸质与电子对照存放',
    archives: []
  },
  {
    locationId: '601', locationCode: 'E-01-01', locationName: '电子档案库房 节点 E-01-01',
    level: 'RACK', levelName: '存储节点', levelPath: '电子档案库房 / 节点 E-01-01',
    house: '电子档案库房', capacity: 2048, used: 1536, unit: '件', percent: 75,
    status: 0, statusLabel: '启用', medium: 'ELECTRONIC', mediumLabel: '电子',
    electronicPath: '/vault/2025/digital', note: '每 24 小时增量备份',
    archives: [
      { archiveCode: 'QRS-2025-KY-0008', title: '馆藏数字化项目结项报告' },
      { archiveCode: 'QRS-2025-DZ-0051', title: '2025年度网站群内容备份说明' }
    ]
  },
  {
    locationId: '602', locationCode: 'E-01-02', locationName: '电子档案库房 节点 E-01-02',
    level: 'RACK', levelName: '存储节点', levelPath: '电子档案库房 / 节点 E-01-02',
    house: '电子档案库房', capacity: 5120, used: 860, unit: '件', percent: 17,
    status: 0, statusLabel: '启用', medium: 'ELECTRONIC', mediumLabel: '电子',
    electronicPath: '/vault/2025/permanent', note: '永久保管电子档案专用',
    archives: [
      { archiveCode: 'QRS-2025-DZ-0031', title: '智慧档案平台上线验收材料' },
      { archiveCode: 'QRS-2025-DZ-0044', title: '网站信息发布审批单台账' }
    ]
  },
  {
    locationId: '504', locationCode: 'C3-02-01', locationName: '二号库房 C3 排 02 架 01 层',
    level: 'RACK', levelName: '柜架', levelPath: '二号库房 / C3 排 / 02 架 / 01 层',
    house: '二号库房', capacity: 40, used: 35, unit: '件', percent: 88,
    status: 1, statusLabel: '停用', medium: 'PAPER', mediumLabel: '纸质',
    electronicPath: '', note: '除湿设备检修，暂停上架',
    archives: [
      { archiveCode: 'QRS-2025-WS-0140', title: '2025年节能改造工程图纸' }
    ]
  },
  {
    locationId: '603', locationCode: 'E-02-01', locationName: '电子档案库房 节点 E-02-01',
    level: 'RACK', levelName: '存储节点', levelPath: '电子档案库房 / 节点 E-02-01',
    house: '电子档案库房', capacity: 1024, used: 128, unit: '件', percent: 13,
    status: 0, statusLabel: '启用', medium: 'MIXED', mediumLabel: '混合',
    electronicPath: '/vault/2025/project', note: '工程类电子文件与图纸',
    archives: [
      { archiveCode: 'QRS-2025-WS-0140', title: '2025年节能改造工程图纸' }
    ]
  }
];

const STATUS_RAW = [
  {
    archiveId: '10241', archiveCode: 'QRS-2025-WS-0107', title: '2025年第三季度办公会会议纪要',
    retention: retention('PERMANENT', '永久', 'PERMANENT'),
    medium: 'PAPER', mediumLabel: '纸质', archiveStatus: 'SUBMITTED',
    statusLabel: '待归档', batchNo: '', locationCode: 'A2-03-05',
    archivedAt: '', lastCheckAt: '2025-10-08 09:32', version: 3,
    checks: [chk('STATUS', true), chk('RETENTION', true), chk('FILE', true)]
  },
  {
    archiveId: '10248', archiveCode: 'QRS-2025-WS-0112', title: '2025年度财务决算报告',
    retention: retention('Y30', '30年', 'FIXED', 30),
    medium: 'MIXED', mediumLabel: '混合', archiveStatus: 'ARCHIVED',
    statusLabel: '已归档', batchNo: 'BATCH-2025-011', locationCode: 'A2-03-06',
    archivedAt: '2025-10-06 11:20', lastCheckAt: '2025-10-06 10:48', version: 2,
    checks: [chk('STATUS', true), chk('RETENTION', true), chk('FILE', true)]
  },
  {
    archiveId: '10253', archiveCode: 'QRS-2025-DZ-0031', title: '智慧档案平台上线验收材料',
    retention: retention('X99', '无法识别', 'UNKNOWN', null,
      '字典 arc_retention_period 中不存在该编码 X99'),
    medium: 'ELECTRONIC', mediumLabel: '电子', archiveStatus: 'SUBMITTED',
    statusLabel: '待归档', batchNo: '', locationCode: 'E-01-02',
    archivedAt: '', lastCheckAt: '2025-10-10 09:05', version: 1,
    checks: [
      chk('STATUS', true),
      chk('RETENTION', false, '字典 arc_retention_period 中不存在该编码 X99'),
      chk('FILE', true)
    ]
  },
  {
    archiveId: '10266', archiveCode: 'QRS-2025-KY-0008', title: '馆藏数字化项目结项报告',
    retention: retention('Y10', '10年', 'FIXED', 10),
    medium: 'ELECTRONIC', mediumLabel: '电子', archiveStatus: 'ARCHIVED',
    statusLabel: '已归档', batchNo: 'BATCH-2025-010', locationCode: 'E-01-01',
    archivedAt: '2025-10-01 02:14', lastCheckAt: '2025-10-01 02:10', version: 4,
    checks: [chk('STATUS', true), chk('RETENTION', true), chk('FILE', true)]
  },
  {
    archiveId: '10278', archiveCode: 'QRS-2025-WS-0126', title: '设备采购合同（2025-07）',
    retention: retention('长期', '无法识别', 'UNKNOWN', null,
      '字典标签无法解析，应为「永久」或阿拉伯数字+年（编码 9，标签 长期）'),
    medium: 'MIXED', mediumLabel: '混合', archiveStatus: 'SUBMITTED',
    statusLabel: '待归档', batchNo: '', locationCode: 'A2-03-06',
    archivedAt: '', lastCheckAt: '2025-10-09 10:15', version: 2,
    checks: [
      chk('STATUS', true),
      chk('RETENTION', false, '字典标签无法解析，应为「永久」或阿拉伯数字+年（编码 9，标签 长期）'),
      chk('FILE', true)
    ]
  },
  {
    archiveId: '10290', archiveCode: 'QRS-2025-DZ-0044', title: '网站信息发布审批单台账',
    retention: retention('PERMANENT', '永久', 'PERMANENT'),
    medium: 'ELECTRONIC', mediumLabel: '电子', archiveStatus: 'ARCHIVED',
    statusLabel: '已归档', batchNo: 'BATCH-2025-013', locationCode: 'E-01-02',
    archivedAt: '2025-10-10 09:05', lastCheckAt: '2025-10-10 09:02', version: 5,
    checks: [chk('STATUS', true), chk('RETENTION', true), chk('FILE', true), chk('DESTROY', false, '未结销毁单 1 条')]
  },
  {
    archiveId: '10296', archiveCode: 'QRS-2025-WS-0140', title: '2025年节能改造工程图纸',
    retention: retention('PERMANENT', '永久', 'PERMANENT'),
    medium: 'MIXED', mediumLabel: '混合', archiveStatus: 'ARCHIVED',
    statusLabel: '已归档', batchNo: 'BATCH-2025-009', locationCode: 'C3-02-01',
    archivedAt: '2025-09-27 11:22', lastCheckAt: '2025-09-27 11:20', version: 2,
    checks: [chk('STATUS', false, '当前状态 ARCHIVED'), chk('RETENTION', true), chk('FILE', true)]
  },
  {
    archiveId: '10284', archiveCode: 'QRS-2025-WS-0131', title: '2025年上半年信访件办理卷',
    retention: retention('Y30', '30年', 'FIXED', 30),
    medium: 'PAPER', mediumLabel: '纸质', archiveStatus: 'ARCHIVED',
    statusLabel: '已归档', batchNo: 'BATCH-2025-012', locationCode: 'A2-03-05',
    archivedAt: '2025-10-09 15:02', lastCheckAt: '2025-10-09 14:40', version: 1,
    checks: [chk('STATUS', true), chk('RETENTION', true), chk('FILE', true)]
  },
  {
    archiveId: '10301', archiveCode: 'QRS-2025-WS-0144', title: '2025年干部教育培训记录',
    retention: retention('Y10', '10年', 'FIXED', 10),
    medium: 'PAPER', mediumLabel: '纸质', archiveStatus: 'DRAFT',
    statusLabel: '草稿', batchNo: '', locationCode: '',
    archivedAt: '', lastCheckAt: '2025-10-08 11:20', version: 1,
    checks: [chk('STATUS', false, '当前状态 DRAFT'), chk('RETENTION', true)]
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

function pendingRow(row) {
  return Object.assign({}, row, {
    batchText: row.batchNo ? '批次 ' + row.batchNo : '尚未归档',
    check: summarize(row.checks, row.checkedAt),
    archived: Boolean(row.archived)
  });
}

function statusRow(row) {
  return Object.assign({}, row, {
    batchText: row.batchNo || '尚未组批',
    locationText: row.locationCode || '未上架',
    archivedText: row.archivedAt || '未归档',
    check: summarize(row.checks, row.lastCheckAt)
  });
}

function batchRow(row) {
  const statusLabel = BATCH_STATUS[row.status] || row.status;
  return Object.assign({}, row, {
    statusLabel,
    canRetry: Number(row.fail || 0) > 0,
    hasReturnedInfo: Boolean(row.returnedReason || row.returnedBy || row.returnedAt),
    returnedMeta: row.returnedBy || row.returnedAt
      ? (row.returnedBy || '系统') + (row.returnedAt ? ' 于 ' + row.returnedAt : '') + ' 返回'
      : ''
  });
}

function locationRow(row) {
  return Object.assign({}, row, {
    capacityText: row.capacity ? row.used + ' / ' + row.capacity + ' ' + row.unit : row.used + ' 件（不限容量）',
    statusTone: Number(row.status) === 0 ? 'available' : 'disabled',
    hasElectronic: Boolean(row.electronicPath),
    percentClamped: Math.min(100, Math.max(0, Number(row.percent) || 0))
  });
}

// ---------------------------------------------------------------- 对外接口

function hubSummary() {
  if (!MOCK) return request.get(PATH.hub, {});
  const pending = PENDING_RAW.map(pendingRow).filter((row) => !row.archived);
  const batches = BATCH_RAW.map(batchRow);
  const locations = LOCATION_RAW.map(locationRow);
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
  if (!MOCK) return request.post(PATH.pending, query);
  const filter = (query && query.filter) || 'ALL';
  const keyword = String((query && query.keyword) || '').trim().toUpperCase();
  let rows = PENDING_RAW.map(pendingRow).filter((row) => !row.archived);
  if (filter === 'NEED_FIX') rows = rows.filter((row) => row.check.tone === 'fail');
  if (filter === 'UNKNOWN') rows = rows.filter((row) => row.retention.state === 'UNKNOWN');
  if (keyword) {
    rows = rows.filter((row) =>
      row.archiveCode.toUpperCase().indexOf(keyword) >= 0 || row.title.indexOf(keyword) >= 0);
  }
  return delay(paginate(rows, query));
}

// 通用校验（契约 §4.3）：两个页面共用，真写 CHECK 报告由后端做，桩里刷新检查时间与逐项结果
function check(query) {
  if (!MOCK) return request.post(PATH.check, query);
  const code = (query && query.archiveCode) || '';
  const pendingRowData = PENDING_RAW.find((item) => item.archiveCode === code);
  const statusRowData = STATUS_RAW.find((item) => item.archiveCode === code);
  if (!pendingRowData && !statusRowData) return fail('ARCHIVE_NOT_FOUND');
  const source = pendingRowData || statusRowData;
  const checks = source.checks.map((item) => Object.assign({}, item));
  const passed = checks.every((item) => item.passed);
  const checkedAt = nowText();
  // 同一档案可能同时出现在待归档与状态查询两份桩数据里，两边都刷新检查时间
  if (pendingRowData) pendingRowData.checkedAt = checkedAt;
  if (statusRowData) statusRowData.lastCheckAt = checkedAt;
  return delay({
    archiveId: source.archiveId,
    archiveCode: source.archiveCode,
    pass: passed,
    retention: source.retention,
    reportId: '8802',
    reportEnabled: true,
    checkedAt,
    checks,
    message: passed ? '重新检查完成：全部通过' : '重新检查完成：仍有未通过项'
  });
}

// 加入批次 = 单件组批并立即归档（契约 §4.4，幂等）
function joinBatch(query) {
  if (!MOCK) return request.post(PATH.joinBatch, query);
  const code = (query && (query.archiveCode || query.archiveId)) || '';
  const row = PENDING_RAW.find((item) => item.archiveCode === code || item.archiveId === String(code));
  if (!row) return fail('ARCHIVE_NOT_FOUND');
  if (row.archived) {
    return delay({ archiveCode: row.archiveCode, batchNo: row.batchNo, message: '已归档，批次号 ' + row.batchNo });
  }
  if (query && query.version !== undefined && Number(query.version) !== Number(row.version)) {
    return fail('VERSION_CONFLICT');
  }
  const retentionFailed = row.checks.find((item) => item.code === 'RETENTION' && !item.passed);
  if (retentionFailed) {
    return fail('RETENTION_UNRECOGNIZED', { message: retentionFailed.detail });
  }
  const otherFailed = row.checks.find((item) => !item.passed);
  if (otherFailed) {
    return fail('CHECK_NOT_PASSED', { label: otherFailed.label, detail: otherFailed.detail });
  }
  const batchNo = 'BATCH-2025-' + String(900 + PENDING_RAW.indexOf(row));
  row.archived = true;
  row.batchNo = batchNo;
  row.version += 1;
  return delay({
    batchNo,
    archiveId: row.archiveId,
    archiveCode: row.archiveCode,
    archiveStatus: 'ARCHIVED',
    locationCode: 'A2-03-05',
    archivedAt: nowText(),
    message: '已归档，批次号 ' + batchNo
  });
}

function statusSearch(query) {
  if (!MOCK) return request.post(PATH.status, query);
  const keyword = String((query && query.keyword) || '').trim().toUpperCase();
  const filter = (query && query.filter) || 'ALL';
  let rows = STATUS_RAW.map(statusRow);
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
  if (!MOCK) return request.post(PATH.batchList, query);
  const filter = (query && query.filter) || 'ALL';
  const keyword = String((query && query.keyword) || '').trim().toUpperCase();
  let rows = BATCH_RAW.map(batchRow);
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
  if (!MOCK) return request.get(PATH.batchDetail, { batchNo });
  const row = BATCH_RAW.find((item) => item.batchNo === batchNo);
  if (!row) return fail('BATCH_NOT_FOUND');
  const items = BATCH_ITEMS[batchNo] || [
    { archiveId: '', archiveCode: '（明细未登记）', title: '该批次暂无逐件明细', itemResult: 'OK', reasonCode: '', reason: '' }
  ];
  return delay({ batch: batchRow(row), items });
}

// 重新组批（契约 §4.8）：按失败项重校验并生成新批次，原批次不改写
function batchRetry(batchNo) {
  if (!MOCK) return request.post(PATH.batchRetry, { batchNo });
  const row = BATCH_RAW.find((item) => item.batchNo === batchNo);
  if (!row) return fail('BATCH_NOT_FOUND');
  if (Number(row.fail || 0) === 0) return fail('BATCH_NOT_RETRYABLE');
  const newNo = 'BATCH-2025-' + String(950 + BATCH_RAW.length);
  BATCH_RAW.unshift({
    batchId: String(9100 + BATCH_RAW.length),
    batchNo: newNo,
    name: '重新组批：' + row.batchNo,
    source: 'REBATCH', sourceLabel: '重新组批',
    sourceDetail: '由 ' + row.batchNo + ' 的失败项重新校验',
    total: row.total, success: row.total, fail: 0,
    retentionText: row.retentionText,
    status: 'SUCCESS', applicantName: row.applicantName, locationCode: row.locationCode,
    createdAt: nowText(), updatedAt: nowText(),
    returnedReason: '', returnedBy: '', returnedAt: ''
  });
  BATCH_ITEMS[newNo] = (BATCH_ITEMS[batchNo] || []).map((item) => Object.assign({}, item, {
    itemResult: 'OK', reasonCode: '', reason: ''
  }));
  return delay({ sourceBatchNo: row.batchNo, batchNo: newNo, total: row.total, success: row.total, fail: 0,
    message: '已重新组批：' + newNo });
}

function locationList(query) {
  if (!MOCK) return request.post(PATH.location, query);
  const filter = (query && query.filter) || 'ALL';
  const keyword = String((query && query.keyword) || '').trim().toUpperCase();
  let rows = LOCATION_RAW.map(locationRow);
  if (filter !== 'ALL') rows = rows.filter((row) => row.medium === filter);
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
