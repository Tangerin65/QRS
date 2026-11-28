// 归档 hub 的入口配置。M09 分享协作、M11 保管期限鉴定 / 开放 / 销毁
// 以后各自加入口时，只往这个数组追加一项即可，hub 页面本身不用改写。
// valueKey / hintKey 指向 api.hub() 返回的统计字段，hub.js 负责拼出展示文案。

const sections = [
  {
    key: 'pending',
    module: 'M10',
    title: '待归档',
    desc: '待归档档案、保管期限状态与逐项检查结果',
    path: '/pages/archiving/pending/pending',
    glyph: '待',
    tone: 'blue',
    valueKey: 'pending',
    valueUnit: '件',
    hint: '其中 {n} 件需补正',
    hintKey: 'needFix'
  },
  {
    key: 'batch',
    module: 'M10',
    title: '归档批次',
    desc: '批次来源、执行状态与失败原因',
    path: '/pages/archiving/batch/batch',
    glyph: '批',
    tone: 'teal',
    valueKey: 'batchOpen',
    valueUnit: '个进行中',
    hint: '另有 {n} 个有失败项',
    hintKey: 'batchReturned'
  },
  {
    key: 'location',
    module: 'M10',
    title: '库位',
    desc: '库房排架、存储介质与电子存储位置',
    path: '/pages/archiving/location/location',
    glyph: '位',
    tone: 'violet',
    valueKey: 'locationAvailable',
    valueUnit: '个可用',
    hint: '共 {n} 个库位',
    hintKey: 'locationTotal'
  },
  {
    key: 'status',
    module: 'M10',
    title: '归档状态查询',
    desc: '按档号或题名查询归档进度与保管期限状态',
    path: '/pages/archiving/status/status',
    glyph: '查',
    tone: 'amber',
    valueKey: 'pending',
    valueUnit: '件在流程中',
    hint: '含 {n} 件期限无法识别',
    hintKey: 'unknownRetention'
  }
];

module.exports = sections;
