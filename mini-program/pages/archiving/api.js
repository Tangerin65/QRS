// M10 归档接口层（阶段 1：接口桩）
// 后端 /api/mobile/archiving/** 目前不存在，本文件先用 MOCK 假数据把页面和交互跑通。
// 接入真实接口：把 MOCK 改成 false，并对照「归档」后端的最终契约校对 PATH。
// 请求约定沿用 utils/request.js：Bearer 令牌、{code,msg,data} 响应、字符串业务码优先于 HTTP 状态码。

const request = require('../../utils/request');

const MOCK = true;

const PATH = {
  hub: '/api/mobile/archiving/hub/summary',
  pending: '/api/mobile/archiving/pending/list',
  batch: '/api/mobile/archiving/batch/list',
  location: '/api/mobile/archiving/location/list',
  status: '/api/mobile/archiving/status/search',
  joinBatch: '/api/mobile/archiving/pending/join-batch',
  retryBatch: '/api/mobile/archiving/batch/retry',
  recheck: '/api/mobile/archiving/status/recheck'
};

// M10 业务错误码 → 中文文案。后端契约确定后可并入 utils/request.js 的 messages。
const ERRORS = {
  RETENTION_UNRECOGNIZED: '保管期限无法识别，请先补正期限再组批',
  RETENTION_MISMATCH: '保管期限与目录规则不一致，请核对后重试',
  CHECK_NOT_PASSED: '仍有检查项未通过，补正后才能加入批次',
  BATCH_LOCKED: '批次正在审核中，暂不能重新组批',
  LOCATION_FULL: '该库位已满，请先调整库位',
  STORAGE_UNBOUND: '电子存储位置未绑定，请先配置存储节点'
};

function fail(code) {
  return Promise.reject(new request.RequestError(ERRORS[code] || '操作未完成，请稍后重试', code));
}

const RETENTION = {
  PERMANENT: { label: '永久', tone: 'permanent' },
  '30Y': { label: '30年', tone: 'fixed' },
  '10Y': { label: '10年', tone: 'fixed' },
  UNKNOWN: { label: '无法识别', tone: 'unknown' }
};

const MEDIUM = { PAPER: '纸质', ELECTRONIC: '电子', MIXED: '混合' };

const CHECK = {
  PASS: { label: '通过', tone: 'pass' },
  WARN: { label: '待确认', tone: 'warn' },
  FAIL: { label: '未通过', tone: 'fail' }
};

const BATCH_STATUS = {
  RUNNING: { label: '进行中', tone: 'running' },
  REVIEW: { label: '待审核', tone: 'review' },
  RETURNED: { label: '已退回', tone: 'returned' },
  DONE: { label: '已完成', tone: 'done' }
};

const BATCH_SOURCE = {
  TRANSFER: '移交归档',
  AUTO: '自动汇集',
  MANUAL: '手动组批'
};

const LOCATION_STATUS = {
  AVAILABLE: { label: '可用', tone: 'available' },
  FULL: { label: '已满', tone: 'full' },
  DISABLED: { label: '停用', tone: 'disabled' }
};

const ARCHIVE_STATUS = {
  PENDING: { label: '待归档', tone: 'pending' },
  ARCHIVED: { label: '已归档', tone: 'archived' },
  RETURNED: { label: '已退回', tone: 'returned' },
  LOCATED: { label: '已上架', tone: 'located' }
};

// ---------------------------------------------------------------- 假数据

const PENDING_RAW = [
  {
    archiveCode: 'QRS-2025-WS-0107', title: '2025年第三季度办公会会议纪要',
    categoryName: '文书档案', formingDate: '2025-09-30', retention: 'PERMANENT',
    medium: 'PAPER', storage: '三号库房 A2-03-05', batchNo: 'BATCH-2025-012',
    checks: [
      { label: '保管期限识别', result: 'PASS', message: '目录规则判定为永久' },
      { label: '元数据完整性', result: 'PASS', message: '题名、责任者、日期齐全' },
      { label: '档号唯一性', result: 'PASS', message: '与馆藏无重复' },
      { label: '密级与权限', result: 'WARN', message: '密级待档案室复核' }
    ]
  },
  {
    archiveCode: 'QRS-2025-WS-0112', title: '2025年度财务决算报告',
    categoryName: '会计档案', formingDate: '2025-09-28', retention: '30Y',
    medium: 'MIXED', storage: '三号库房 A2-03-06；电子 NAS-01 /vault/2025/finance',
    batchNo: 'BATCH-2025-011',
    checks: [
      { label: '保管期限识别', result: 'PASS', message: '判定为 30 年' },
      { label: '元数据完整性', result: 'PASS', message: '报表与说明齐全' },
      { label: '电子文件可读性', result: 'PASS', message: '12 份 PDF 校验通过' },
      { label: '纸质电子一致性', result: 'PASS', message: '页数与目录一致' }
    ]
  },
  {
    archiveCode: 'QRS-2025-DZ-0031', title: '智慧档案平台上线验收材料',
    categoryName: '科技档案', formingDate: '2025-09-25', retention: 'UNKNOWN',
    medium: 'ELECTRONIC', storage: '电子档案库房 E-01-02 /vault/2025/permanent',
    batchNo: 'BATCH-2025-013',
    checks: [
      { label: '保管期限识别', result: 'FAIL', message: '期限代码 T-OLD 不在目录规则内，需人工补正' },
      { label: '电子文件可读性', result: 'FAIL', message: '2 份 PDF 缺少数字签名' },
      { label: '元数据完整性', result: 'PASS', message: '载体形态、文件格式已登记' },
      { label: '档号唯一性', result: 'PASS', message: '与馆藏无重复' }
    ]
  },
  {
    archiveCode: 'QRS-2025-WS-0119', title: '职工代表大会材料汇编',
    categoryName: '文书档案', formingDate: '2025-09-22', retention: '30Y',
    medium: 'MIXED', storage: '三号库房 A2-03-05；电子 NAS-01 /vault/2025/ws',
    batchNo: 'BATCH-2025-012',
    checks: [
      { label: '保管期限识别', result: 'PASS', message: '判定为 30 年' },
      { label: '元数据完整性', result: 'PASS', message: '件内目录完整' },
      { label: '纸质电子一致性', result: 'PASS', message: '扫描件 218 页与纸质一致' }
    ]
  },
  {
    archiveCode: 'QRS-2025-KY-0008', title: '馆藏数字化项目结项报告',
    categoryName: '科技档案', formingDate: '2025-09-18', retention: '10Y',
    medium: 'ELECTRONIC', storage: '电子档案库房 E-01-01 /vault/2025/digital',
    batchNo: 'BATCH-2025-013',
    checks: [
      { label: '保管期限识别', result: 'PASS', message: '判定为 10 年' },
      { label: '电子文件可读性', result: 'WARN', message: '1 份 TIFF 缺校验值' },
      { label: '元数据完整性', result: 'PASS', message: '项目编号已关联' }
    ]
  },
  {
    archiveCode: 'QRS-2025-WS-0123', title: '2025年档案工作要点',
    categoryName: '文书档案', formingDate: '2025-09-15', retention: '30Y',
    medium: 'PAPER', storage: '三号库房 A2-03-06', batchNo: 'BATCH-2025-013',
    checks: [
      { label: '保管期限识别', result: 'PASS', message: '判定为 30 年' },
      { label: '元数据完整性', result: 'PASS', message: '发文号、签发人齐全' },
      { label: '档号唯一性', result: 'PASS', message: '与馆藏无重复' }
    ]
  },
  {
    archiveCode: 'QRS-2025-WS-0126', title: '设备采购合同（2025-07）',
    categoryName: '合同档案', formingDate: '2025-08-30', retention: 'UNKNOWN',
    medium: 'MIXED', storage: '三号库房 A2-03-06；电子 NAS-01 /vault/2025/contract',
    batchNo: '',
    checks: [
      { label: '保管期限识别', result: 'FAIL', message: '合同类目录未匹配期限，判定失败' },
      { label: '元数据完整性', result: 'PASS', message: '合同双方、金额已登记' },
      { label: '电子文件可读性', result: 'PASS', message: '4 份 PDF 校验通过' }
    ]
  },
  {
    archiveCode: 'QRS-2025-WS-0131', title: '2025年上半年信访件办理卷',
    categoryName: '文书档案', formingDate: '2025-08-20', retention: '30Y',
    medium: 'PAPER', storage: '三号库房 A2-03-05', batchNo: 'BATCH-2025-013',
    checks: [
      { label: '保管期限识别', result: 'PASS', message: '判定为 30 年' },
      { label: '元数据完整性', result: 'PASS', message: '办理单与反馈单齐全' },
      { label: '密级与权限', result: 'PASS', message: '涉私内容已限权' }
    ]
  },
  {
    archiveCode: 'QRS-2025-DZ-0044', title: '网站信息发布审批单台账',
    categoryName: '电子档案', formingDate: '2025-08-12', retention: 'PERMANENT',
    medium: 'ELECTRONIC', storage: '电子档案库房 E-01-02 /vault/2025/permanent',
    batchNo: 'BATCH-2025-013',
    checks: [
      { label: '保管期限识别', result: 'PASS', message: '目录规则判定为永久' },
      { label: '电子文件可读性', result: 'PASS', message: '台账 XLSX 可正常解析' },
      { label: '元数据完整性', result: 'PASS', message: '责任部门与发布时间齐全' }
    ]
  },
  {
    archiveCode: 'QRS-2025-WS-0140', title: '2025年节能改造工程图纸',
    categoryName: '基建档案', formingDate: '2025-08-05', retention: 'PERMANENT',
    medium: 'MIXED', storage: '二号库房 C3-02-01；电子 NAS-02 /vault/2025/project',
    batchNo: '',
    checks: [
      { label: '保管期限识别', result: 'PASS', message: '目录规则判定为永久' },
      { label: '元数据完整性', result: 'WARN', message: '缺少竣工图签章日期' },
      { label: '电子文件可读性', result: 'PASS', message: 'DWG 已转 PDF 归档' }
    ]
  },
  {
    archiveCode: 'QRS-2025-WS-0144', title: '2025年干部教育培训记录',
    categoryName: '文书档案', formingDate: '2025-07-28', retention: '10Y',
    medium: 'PAPER', storage: '三号库房 A2-03-06', batchNo: '',
    checks: [
      { label: '保管期限识别', result: 'PASS', message: '判定为 10 年' },
      { label: '元数据完整性', result: 'PASS', message: '培训名单与签到表齐全' }
    ]
  },
  {
    archiveCode: 'QRS-2025-DZ-0051', title: '2025年度网站群内容备份说明',
    categoryName: '电子档案', formingDate: '2025-07-15', retention: '10Y',
    medium: 'ELECTRONIC', storage: '电子档案库房 E-01-01 /vault/2025/digital',
    batchNo: 'BATCH-2025-013',
    checks: [
      { label: '保管期限识别', result: 'PASS', message: '判定为 10 年' },
      { label: '电子文件可读性', result: 'PASS', message: '备份包可解压，哈希一致' },
      { label: '元数据完整性', result: 'PASS', message: '备份周期与责任人已登记' }
    ]
  }
];

const BATCH_RAW = [
  {
    batchNo: 'BATCH-2025-012', name: '综合办公室 2025 年三季度归档批次',
    source: 'TRANSFER', sourceDetail: '综合办公室 2025-10-08 移交清单（12 件）',
    count: 12, retentionText: '永久 3 / 30年 7 / 10年 2',
    status: 'REVIEW', handledBy: '张敏', createdAt: '2025-10-08 09:20', updatedAt: '2025-10-09 15:02'
  },
  {
    batchNo: 'BATCH-2025-011', name: '财务处 2025 年度会计档案批次',
    source: 'TRANSFER', sourceDetail: '财务处 2025-10-06 移交清单（9 件）',
    count: 9, retentionText: '永久 0 / 30年 9',
    status: 'RETURNED', handledBy: '李强', createdAt: '2025-10-06 10:05', updatedAt: '2025-10-07 16:40',
    returnedReason: '第 4 件保管期限代码 T-OLD 无法识别，未通过期限校验，整批退回补正。',
    returnedBy: '王主任', returnedAt: '2025-10-07 16:40'
  },
  {
    batchNo: 'BATCH-2025-010', name: '系统自动汇集：OA 发文 2025-09',
    source: 'AUTO', sourceDetail: 'OA 系统 2025-09 月结归档（自动重试 1 次）',
    count: 26, retentionText: '永久 8 / 30年 12 / 10年 6',
    status: 'DONE', handledBy: '系统', createdAt: '2025-10-01 02:00', updatedAt: '2025-10-01 02:14'
  },
  {
    batchNo: 'BATCH-2025-013', name: '馆藏数字化项目电子档案批次',
    source: 'AUTO', sourceDetail: '数字化平台 2025-10 自动汇集（检查中 6/12）',
    count: 12, retentionText: '永久 5 / 30年 4 / 10年 3',
    status: 'RUNNING', handledBy: '系统', createdAt: '2025-10-10 08:30', updatedAt: '2025-10-10 09:05'
  },
  {
    batchNo: 'BATCH-2025-009', name: '人事处 2024 年岗位聘任材料',
    source: 'MANUAL', sourceDetail: '档案室手工组批（检索条件：人事类 2024）',
    count: 34, retentionText: '永久 0 / 30年 34',
    status: 'RETURNED', handledBy: '张敏', createdAt: '2025-09-26 14:10', updatedAt: '2025-09-27 11:22',
    returnedReason: '组批范围包含 2 份未完成密级标定的材料，请补充密级后再提交。',
    returnedBy: '王主任', returnedAt: '2025-09-27 11:22'
  },
  {
    batchNo: 'BATCH-2025-008', name: '2024 年度归档总批次（补录）',
    source: 'MANUAL', sourceDetail: '档案室手工组批（补录 2024 年遗漏件）',
    count: 7, retentionText: '永久 1 / 30年 4 / 10年 2',
    status: 'DONE', handledBy: '张敏', createdAt: '2025-09-18 09:40', updatedAt: '2025-09-19 10:05'
  }
];

const LOCATION_RAW = [
  {
    code: 'A2-03-05', house: '三号库房', medium: 'PAPER',
    capacity: 30, used: 18, unit: '盒', status: 'AVAILABLE',
    position: '托架 A2-03-05 第 2 层', note: '恒温恒湿',
    archives: ['QRS-2025-WS-0107', 'QRS-2025-WS-0119', 'QRS-2025-WS-0131']
  },
  {
    code: 'A2-03-06', house: '三号库房', medium: 'PAPER',
    capacity: 30, used: 30, unit: '盒', status: 'FULL',
    position: '托架 A2-03-06 第 1 层', note: '已满，新进档案请调配',
    archives: ['QRS-2025-WS-0112', 'QRS-2025-WS-0123', 'QRS-2025-WS-0126']
  },
  {
    code: 'B1-01-02', house: '一号库房', medium: 'MIXED',
    capacity: 40, used: 22, unit: '盒', status: 'AVAILABLE',
    position: '托架 B1-01-02 第 3 层',
    electronicPath: 'NAS-01 /vault/2025/ws', note: '纸质与电子对照存放',
    archives: ['QRS-2025-WS-0119']
  },
  {
    code: 'E-01-01', house: '电子档案库房', medium: 'ELECTRONIC',
    capacity: 2048, used: 1536, unit: 'GB', status: 'AVAILABLE',
    position: '存储节点 NAS-01', electronicPath: '/vault/2025/digital',
    note: '每 24 小时增量备份',
    archives: ['QRS-2025-KY-0008', 'QRS-2025-DZ-0051']
  },
  {
    code: 'E-01-02', house: '电子档案库房', medium: 'ELECTRONIC',
    capacity: 5120, used: 860, unit: 'GB', status: 'AVAILABLE',
    position: '存储节点 NAS-01', electronicPath: '/vault/2025/permanent',
    note: '永久保管电子档案专用',
    archives: ['QRS-2025-DZ-0031', 'QRS-2025-DZ-0044']
  },
  {
    code: 'C3-02-01', house: '二号库房', medium: 'PAPER',
    capacity: 40, used: 35, unit: '盒', status: 'DISABLED',
    position: '托架 C3-02-01 第 1 层', note: '除湿设备检修，暂停上架',
    archives: ['QRS-2025-WS-0140']
  },
  {
    code: 'E-02-01', house: '电子档案库房', medium: 'MIXED',
    capacity: 1024, used: 128, unit: 'GB', status: 'AVAILABLE',
    position: '存储节点 NAS-02', electronicPath: '/vault/2025/project',
    note: '工程类电子文件与图纸',
    archives: ['QRS-2025-WS-0140']
  }
];

const STATUS_RAW = [
  {
    archiveCode: 'QRS-2025-WS-0107', title: '2025年第三季度办公会会议纪要',
    retention: 'PERMANENT', medium: 'PAPER', archiveStatus: 'PENDING',
    batchNo: 'BATCH-2025-012', locationCode: 'A2-03-05', archivedAt: '',
    lastCheckAt: '2025-10-08 09:32', checks: [
      { label: '保管期限识别', result: 'PASS', message: '永久' },
      { label: '密级与权限', result: 'WARN', message: '密级待复核' }
    ]
  },
  {
    archiveCode: 'QRS-2025-WS-0112', title: '2025年度财务决算报告',
    retention: '30Y', medium: 'MIXED', archiveStatus: 'ARCHIVED',
    batchNo: 'BATCH-2025-011', locationCode: 'A2-03-06', archivedAt: '2025-10-06 11:20',
    lastCheckAt: '2025-10-06 10:48', checks: [
      { label: '保管期限识别', result: 'PASS', message: '30年' },
      { label: '纸质电子一致性', result: 'PASS', message: '页数一致' }
    ]
  },
  {
    archiveCode: 'QRS-2025-DZ-0031', title: '智慧档案平台上线验收材料',
    retention: 'UNKNOWN', medium: 'ELECTRONIC', archiveStatus: 'RETURNED',
    batchNo: 'BATCH-2025-013', locationCode: 'E-01-02', archivedAt: '',
    lastCheckAt: '2025-10-10 09:05', checks: [
      { label: '保管期限识别', result: 'FAIL', message: '期限代码 T-OLD 无法识别，需补正' },
      { label: '电子文件可读性', result: 'FAIL', message: '2 份 PDF 缺数字签名' }
    ]
  },
  {
    archiveCode: 'QRS-2025-KY-0008', title: '馆藏数字化项目结项报告',
    retention: '10Y', medium: 'ELECTRONIC', archiveStatus: 'LOCATED',
    batchNo: 'BATCH-2025-010', locationCode: 'E-01-01', archivedAt: '2025-10-01 02:14',
    lastCheckAt: '2025-10-01 02:10', checks: [
      { label: '保管期限识别', result: 'PASS', message: '10年' },
      { label: '电子文件可读性', result: 'WARN', message: '1 份 TIFF 缺校验值' }
    ]
  },
  {
    archiveCode: 'QRS-2025-WS-0126', title: '设备采购合同（2025-07）',
    retention: 'UNKNOWN', medium: 'MIXED', archiveStatus: 'PENDING',
    batchNo: '', locationCode: 'A2-03-06', archivedAt: '',
    lastCheckAt: '2025-10-09 10:15', checks: [
      { label: '保管期限识别', result: 'FAIL', message: '合同类目录未匹配期限' }
    ]
  },
  {
    archiveCode: 'QRS-2025-DZ-0044', title: '网站信息发布审批单台账',
    retention: 'PERMANENT', medium: 'ELECTRONIC', archiveStatus: 'ARCHIVED',
    batchNo: 'BATCH-2025-013', locationCode: 'E-01-02', archivedAt: '2025-10-10 09:05',
    lastCheckAt: '2025-10-10 09:02', checks: [
      { label: '保管期限识别', result: 'PASS', message: '永久' },
      { label: '电子文件可读性', result: 'PASS', message: 'XLSX 可解析' }
    ]
  },
  {
    archiveCode: 'QRS-2025-WS-0140', title: '2025年节能改造工程图纸',
    retention: 'PERMANENT', medium: 'MIXED', archiveStatus: 'PENDING',
    batchNo: '', locationCode: 'C3-02-01', archivedAt: '',
    lastCheckAt: '2025-10-09 16:20', checks: [
      { label: '元数据完整性', result: 'WARN', message: '缺少竣工图签章日期' }
    ]
  },
  {
    archiveCode: 'QRS-2025-WS-0131', title: '2025年上半年信访件办理卷',
    retention: '30Y', medium: 'PAPER', archiveStatus: 'ARCHIVED',
    batchNo: 'BATCH-2025-012', locationCode: 'A2-03-05', archivedAt: '2025-10-09 15:02',
    lastCheckAt: '2025-10-09 14:40', checks: [
      { label: '保管期限识别', result: 'PASS', message: '30年' },
      { label: '密级与权限', result: 'PASS', message: '涉私内容已限权' }
    ]
  },
  {
    archiveCode: 'QRS-2025-DZ-0051', title: '2025年度网站群内容备份说明',
    retention: '10Y', medium: 'ELECTRONIC', archiveStatus: 'LOCATED',
    batchNo: 'BATCH-2025-013', locationCode: 'E-01-01', archivedAt: '2025-10-10 09:05',
    lastCheckAt: '2025-10-10 09:00', checks: [
      { label: '保管期限识别', result: 'PASS', message: '10年' },
      { label: '电子文件可读性', result: 'PASS', message: '备份包哈希一致' }
    ]
  }
];

// ---------------------------------------------------------------- 数据整形

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
  const pageSize = Math.max(1, Number(query && query.pageSize) || 6);
  const start = (pageNum - 1) * pageSize;
  const slice = rows.slice(start, start + pageSize);
  return { rows: slice, total: rows.length, pageNum, pageSize, hasMore: start + slice.length < rows.length };
}

function checkSummary(checks) {
  const total = checks.length;
  const passed = checks.filter((item) => item.result === 'PASS').length;
  const failed = checks.some((item) => item.result === 'FAIL');
  const warned = checks.some((item) => item.result === 'WARN');
  return {
    total, passed,
    tone: failed ? 'fail' : warned ? 'warn' : 'pass',
    label: failed ? '需补正' : warned ? '待确认' : '检查通过'
  };
}

function presentChecks(checks) {
  return checks.map((item) => ({
    label: item.label,
    result: item.result,
    resultLabel: (CHECK[item.result] || { label: item.result }).label,
    tone: (CHECK[item.result] || { tone: 'pass' }).tone,
    message: item.message
  }));
}

function presentPending(row) {
  const retention = RETENTION[row.retention] || RETENTION.UNKNOWN;
  const summary = checkSummary(row.checks);
  return {
    id: row.archiveCode,
    archiveCode: row.archiveCode,
    title: row.title,
    categoryName: row.categoryName,
    formingDate: row.formingDate,
    batchNo: row.batchNo,
    batchText: row.batchNo ? '批次 ' + row.batchNo : '尚未组批',
    medium: row.medium,
    mediumLabel: MEDIUM[row.medium] || row.medium,
    storage: row.storage || '存储位置待定',
    retention: { code: row.retention, label: retention.label, tone: retention.tone },
    needFix: retention.tone === 'unknown' || summary.tone === 'fail',
    check: { label: summary.label, tone: summary.tone, passed: summary.passed, total: summary.total },
    checks: presentChecks(row.checks)
  };
}

function presentBatch(row) {
  const status = BATCH_STATUS[row.status] || { label: row.status, tone: 'running' };
  return {
    id: row.batchNo,
    batchNo: row.batchNo,
    name: row.name,
    sourceLabel: BATCH_SOURCE[row.source] || row.source,
    source: row.source,
    sourceDetail: row.sourceDetail,
    count: row.count,
    retentionText: row.retentionText,
    status: row.status,
    statusLabel: status.label,
    statusTone: status.tone,
    canRetry: row.status === 'RETURNED',
    handledBy: row.handledBy,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
    returnedReason: row.returnedReason || '',
    returnedBy: row.returnedBy || '',
    returnedAt: row.returnedAt || ''
  };
}

function presentLocation(row) {
  const status = LOCATION_STATUS[row.status] || { label: row.status, tone: 'available' };
  const capacity = Math.max(1, row.capacity);
  return {
    id: row.code,
    code: row.code,
    house: row.house,
    medium: row.medium,
    mediumLabel: MEDIUM[row.medium] || row.medium,
    capacity: row.capacity,
    used: row.used,
    unit: row.unit,
    usageText: row.used + ' / ' + row.capacity + ' ' + row.unit,
    percent: Math.min(100, Math.round((row.used / capacity) * 100)),
    status: row.status,
    statusLabel: status.label,
    statusTone: status.tone,
    position: row.position,
    electronicPath: row.electronicPath || '',
    note: row.note || '',
    archives: (row.archives || []).map((code) => ({ code }))
  };
}

function presentStatus(row) {
  const retention = RETENTION[row.retention] || RETENTION.UNKNOWN;
  const status = ARCHIVE_STATUS[row.archiveStatus] || { label: row.archiveStatus, tone: 'pending' };
  const summary = checkSummary(row.checks);
  return {
    id: row.archiveCode,
    archiveCode: row.archiveCode,
    title: row.title,
    retention: { code: row.retention, label: retention.label, tone: retention.tone },
    medium: row.medium,
    mediumLabel: MEDIUM[row.medium] || row.medium,
    statusLabel: status.label,
    statusTone: status.tone,
    batchNo: row.batchNo || '尚未组批',
    locationCode: row.locationCode || '未上架',
    archivedAt: row.archivedAt || '未归档',
    lastCheckAt: row.lastCheckAt,
    check: { label: summary.label, tone: summary.tone, passed: summary.passed, total: summary.total },
    checks: presentChecks(row.checks)
  };
}

// ---------------------------------------------------------------- 对外接口

function hubSummary() {
  if (!MOCK) return request.get(PATH.hub, {});
  const pending = PENDING_RAW.map(presentPending);
  const batches = BATCH_RAW.map(presentBatch);
  const locations = LOCATION_RAW.map(presentLocation);
  return delay({
    pending: pending.length,
    needFix: pending.filter((row) => row.needFix).length,
    unknownRetention: pending.filter((row) => row.retention.tone === 'unknown').length,
    batchOpen: batches.filter((row) => row.status === 'RUNNING' || row.status === 'REVIEW').length,
    batchReturned: batches.filter((row) => row.status === 'RETURNED').length,
    locationAvailable: locations.filter((row) => row.status === 'AVAILABLE').length,
    locationTotal: locations.length
  });
}

function pendingList(query) {
  if (!MOCK) return request.post(PATH.pending, query);
  const filter = (query && query.filter) || 'ALL';
  let rows = PENDING_RAW.map(presentPending);
  if (filter === 'NEED_FIX') rows = rows.filter((row) => row.needFix);
  if (filter === 'UNKNOWN') rows = rows.filter((row) => row.retention.tone === 'unknown');
  return delay(paginate(rows, query));
}

function batchList(query) {
  if (!MOCK) return request.post(PATH.batch, query);
  const filter = (query && query.filter) || 'ALL';
  let rows = BATCH_RAW.map(presentBatch);
  if (filter === 'OPEN') rows = rows.filter((row) => row.status === 'RUNNING' || row.status === 'REVIEW');
  if (filter === 'RETURNED') rows = rows.filter((row) => row.status === 'RETURNED');
  return delay(paginate(rows, query));
}

function locationList(query) {
  if (!MOCK) return request.post(PATH.location, query);
  const filter = (query && query.filter) || 'ALL';
  let rows = LOCATION_RAW.map(presentLocation);
  if (filter !== 'ALL') rows = rows.filter((row) => row.medium === filter);
  return delay(paginate(rows, query));
}

function statusSearch(query) {
  if (!MOCK) return request.post(PATH.status, query);
  const keyword = String((query && query.keyword) || '').trim();
  const filter = (query && query.filter) || 'ALL';
  let rows = STATUS_RAW.map(presentStatus);
  if (filter === 'PERMANENT') rows = rows.filter((row) => row.retention.code === 'PERMANENT');
  if (filter === 'FIXED') rows = rows.filter((row) => row.retention.tone === 'fixed');
  if (filter === 'UNKNOWN') rows = rows.filter((row) => row.retention.tone === 'unknown');
  if (keyword) {
    rows = rows.filter((row) =>
      row.archiveCode.toUpperCase().indexOf(keyword.toUpperCase()) >= 0 || row.title.indexOf(keyword) >= 0);
  }
  return delay(paginate(rows, query));
}

// 加入批次：期限无法识别 / 检查未通过时按契约返回业务错误码，页面展示中文文案。
function joinBatch(archiveCode) {
  if (!MOCK) return request.post(PATH.joinBatch, { archiveCode });
  const row = PENDING_RAW.find((item) => item.archiveCode === archiveCode);
  if (!row) return fail('ARCHIVE_NOT_FOUND');
  if (row.retention === 'UNKNOWN') return fail('RETENTION_UNRECOGNIZED');
  const summary = checkSummary(row.checks);
  if (summary.tone === 'fail') return fail('CHECK_NOT_PASSED');
  row.batchNo = 'BATCH-2025-014';
  return delay({ archiveCode, batchNo: row.batchNo, message: '已加入批次 BATCH-2025-014' });
}

// 重新组批：只有「已退回」批次可重试。
function retryBatch(batchNo) {
  if (!MOCK) return request.post(PATH.retryBatch, { batchNo });
  const row = BATCH_RAW.find((item) => item.batchNo === batchNo);
  if (!row) return fail('BATCH_NOT_FOUND');
  if (row.status !== 'RETURNED') return fail('BATCH_LOCKED');
  row.status = 'RUNNING';
  row.returnedReason = '';
  row.returnedBy = '';
  row.returnedAt = '';
  row.updatedAt = nowText();
  return delay({ batchNo, message: '已重新提交批次 ' + batchNo });
}

// 重新检查：刷新最近检查时间。
function recheck(archiveCode) {
  if (!MOCK) return request.post(PATH.recheck, { archiveCode });
  const row = STATUS_RAW.find((item) => item.archiveCode === archiveCode);
  if (!row) return fail('ARCHIVE_NOT_FOUND');
  row.lastCheckAt = nowText();
  return delay({ archiveCode, message: '已提交重新检查' });
}

module.exports = {
  MOCK,
  PATH,
  ERRORS,
  hub: hubSummary,
  pending: pendingList,
  batch: batchList,
  location: locationList,
  status: statusSearch,
  joinBatch,
  retryBatch,
  recheck
};
