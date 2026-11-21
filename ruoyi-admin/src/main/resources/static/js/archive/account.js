"use strict";

// M12-A 账号与组织底座页面脚本。
// 约定：统计、图表、明细、导出、打印全部基于同一份查询条件 state.params，
// 不允许出现「页面查一套、图表查另一套」的位置，否则数字会不一致。

const section = document.body;
const base = section.dataset.base;
const canManage = section.dataset.manage === "true";
const csrf = document.querySelector('meta[name="csrf-token"]').content;
const byId = id => document.getElementById(id);
const form = byId("filters");
const dialog = byId("editor-dialog");
const content = byId("account-content");
const statuses = { "0": "正常", "1": "停用" };
const state = {
  rows: [], params: "", page: 1, request: 0, onSave: null,
  busy: false, loading: false, depts: [], roles: [], charts: {}
};
const pageSize = 15;

function element(tag, text, css) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (css) node.className = css;
  return node;
}

function button(text, click, css = "") {
  const node = element("button", text, "button " + css);
  node.type = "button";
  node.addEventListener("click", click);
  return node;
}

// 把页面 base（形如 /archive/account/）与相对路径拼成完整地址。
//
// 必须消掉末尾斜杠：Spring Boot 3 起默认关闭了「尾斜杠匹配」，
// 新增/编辑的路径为空时若拼成 /archive/account/，既匹配不上 @PostMapping("/archive/account")，
// 也不会被当成静态资源，最终返回
// "No static resource archive/account for request '/archive/account/'"。
function url(path) {
  const root = base.replace(/\/+$/, "");
  const suffix = String(path || "").replace(/^\/+/, "");
  return suffix ? root + "/" + suffix : root;
}

// 底层请求：返回解析后的完整响应体，GET 无 body，POST/PUT 走 JSON，
// 并带 CSRF 头以通过若依的 CsrfValidateFilter（该过滤器只校验 POST）。
async function fetchJson(method, path, data) {
  const headers = { "X-Requested-With": "XMLHttpRequest" };
  if (csrf) headers["X-CSRF-Token"] = csrf;
  const options = { method, headers };
  if (data !== undefined) {
    headers["Content-Type"] = "application/json";
    options.body = JSON.stringify(data);
  }
  const response = await fetch(url(path), options);
  const answer = await response.json().catch(() => null);
  if (response.redirected || response.status === 401) throw new Error("会话已过期，请重新登录。");
  if (!response.ok || !answer || typeof answer !== "object") throw new Error("请求失败，请重试。");
  if (answer.code !== 0) throw new Error(answer.msg || "请求失败，请重试。");
  return answer;
}

// AjaxResult 接口：业务数据在 data 字段（详情、统计、部门树、角色等）。
async function request(path, data) {
  const answer = await fetchJson(data === undefined ? "GET" : "POST", path, data);
  return answer.data ?? answer.msg;
}

// 分页接口（TableDataInfo）：数据直接在 rows / total 上，响应体里**没有** data 字段。
// 若用上面的 request() 去读，会拿到 null，页面随后抛
// "Cannot read properties of null (reading 'rows')"。
async function requestTable(path) {
  const answer = await fetchJson("GET", path);
  if (!Array.isArray(answer.rows)) throw new Error("清单返回格式不正确，请联系管理员。");
  return { rows: answer.rows, total: Number(answer.total) || 0 };
}

// 写操作：新增 POST、修改与状态变更 PUT，符合 REST 语义。
async function send(method, path, data) {
  const answer = await fetchJson(method, path, data);
  return answer.data ?? answer.msg;
}

function feedback(text, failed = false) {
  const output = byId("feedback");
  output.textContent = text;
  output.hidden = !text;
  output.classList.toggle("error", failed);
}

async function loadOptions() {
  // 部门树与角色下拉只加载一次，失败不影响主列表
  try {
    const [tree, roles] = await Promise.all([request("deptTree"), request("roles")]);
    state.depts = flattenDepts(tree || []);
    state.roles = Array.isArray(roles) ? roles : [];
    for (const dept of state.depts) {
      byId("dept-filter").add(new Option(dept.label, String(dept.id)));
    }
    for (const role of state.roles) {
      byId("role-filter").add(new Option(role.roleName, String(role.roleId)));
    }
  } catch (error) {
    feedback("部门或角色下拉加载失败：" + error.message, true);
  }
}

function flattenDepts(nodes, depth = 0, prefix = "") {
  const flat = [];
  for (const node of nodes || []) {
    const label = (prefix ? prefix + " / " : "") + node.name;
    flat.push({ id: node.id, label, depth, status: node.status });
    if (node.children) flat.push(...flattenDepts(node.children, depth + 1, label));
  }
  return flat;
}

async function load(params = state.params) {
  const sequence = ++state.request;
  state.loading = true;
  content.replaceChildren(element("p", "正在读取账号清单…", "empty"));
  byId("pagination").hidden = true;
  byId("export").disabled = true;
  byId("print").disabled = true;
  try {
    // 明细（分页）与统计并行取，两者使用同一 params，保证图表数字与表格数字一致
    const [page, stats] = await Promise.all([
      requestTable("list?" + normalizeParams(params)),
      request("statistics" + (params ? "?" + params : ""))
    ]);
    if (sequence !== state.request) return;
    state.rows = page.rows;
    state.total = page.total;
    state.params = params;
    state.page = 1;
    state.loading = false;
    byId("export").disabled = state.total === 0;
    byId("print").disabled = state.total === 0;
    renderMetrics(stats);
    renderCharts(stats);
    render();
  } catch (error) {
    if (sequence !== state.request) return;
    state.loading = false;
    const message = element("p", error.message, "empty");
    message.append(" ", button("重试", () => load(params), "small"));
    content.replaceChildren(message);
    for (const key of ["total", "active", "disabled", "archivist", "bound"]) {
      byId("metric-" + key).textContent = "—";
    }
  }
}

// 分页参数与表格排序参数不应参与统计，否则统计口径会随翻页漂移
function normalizeParams(params) {
  const search = new URLSearchParams(params || "");
  search.delete("pageNum");
  search.delete("pageSize");
  search.delete("orderByColumn");
  search.delete("isAsc");
  const query = search.toString();
  return (query ? query + "&" : "") + "pageNum=1&pageSize=" + pageSize;
}

function renderMetrics(stats) {
  if (!stats) return;
  byId("metric-total").textContent = stats.total ?? 0;
  byId("metric-active").textContent = stats.active ?? 0;
  byId("metric-disabled").textContent = stats.disabled ?? 0;
  byId("metric-archivist").textContent = stats.archivist ?? 0;
  byId("metric-bound").textContent = stats.wxBound ?? 0;
}

function renderCharts(stats) {
  if (!stats) return;
  if (typeof echarts === "undefined") {
    // 图表库没加载成功时，别让用户只看到三块空白区域而不知道为什么
    for (const id of ["chart-dept", "chart-role", "chart-status"]) {
      const node = byId(id);
      if (node) node.replaceChildren(element("p", "图表组件未加载，账号清单与导出不受影响。", "empty"));
    }
    return;
  }
  drawBar("chart-dept", "部门", stats.byDept);
  drawBar("chart-role", "角色", stats.byRole);
  drawPie("chart-status", "状态", stats.byStatus);
}

// ECharts 的 canvas 是它自己插进容器里的。如果直接 replaceChildren() 清空容器，
// canvas 会被删掉，但 state.charts 里的实例还在、getDom() 仍等于容器，
// 于是「复用旧实例」的判断会通过，结果画的是一块已经脱离文档的 canvas —— 图表空白。
// 所以这里约定：有数据时只移除「无数据」提示，绝不整体清空容器；
// 确实要清空（无数据）时，先把实例 dispose 掉，避免留下失效引用。
function chartFor(id, node) {
  const existing = state.charts[id];
  if (existing && !existing.isDisposed() && existing.getDom() === node) return existing;
  if (existing && !existing.isDisposed()) existing.dispose();
  const created = echarts.init(node);
  state.charts[id] = created;
  return created;
}

function clearEmptyHint(node) {
  const hint = node.querySelector(".empty");
  if (hint) hint.remove();
}

function showNoData(id, node) {
  const existing = state.charts[id];
  if (existing && !existing.isDisposed()) existing.dispose();
  delete state.charts[id];
  node.replaceChildren(element("p", "当前条件下没有数据。", "empty"));
}

function drawBar(id, name, entries) {
  const node = byId(id);
  if (!node) return;
  const list = Array.isArray(entries) ? entries : [];
  if (!list.length) {
    showNoData(id, node);
    return;
  }
  clearEmptyHint(node);
  const chart = chartFor(id, node);
  chart.setOption({
    tooltip: { trigger: "axis" },
    grid: { left: 8, right: 12, top: 16, bottom: 8, containLabel: true },
    xAxis: { type: "value", minInterval: 1 },
    yAxis: { type: "category", data: list.map(item => item.name), axisLabel: { width: 96, overflow: "truncate" } },
    series: [{ name, type: "bar", data: list.map(item => item.value), barMaxWidth: 22 }]
  }, true);
  chart.resize();
}

function drawPie(id, name, entries) {
  const node = byId(id);
  if (!node) return;
  const list = (Array.isArray(entries) ? entries : []).filter(item => item.value > 0);
  if (!list.length) {
    showNoData(id, node);
    return;
  }
  clearEmptyHint(node);
  const chart = chartFor(id, node);
  chart.setOption({
    tooltip: { trigger: "item" },
    series: [{ name, type: "pie", radius: ["42%", "68%"], data: list.map(item => ({ name: item.name, value: item.value })) }]
  }, true);
  chart.resize();
}

function render() {
  if (state.loading) return;
  const navigation = byId("pagination");
  navigation.replaceChildren();
  navigation.hidden = true;
  if (!state.rows.length) {
    content.replaceChildren(element("p", state.params ? "没有符合条件的账号。" : "暂无账号。", "empty"));
    return;
  }
  content.replaceChildren(makeTable(state.rows));
  const pages = Math.ceil(state.total / pageSize);
  if (pages < 2) return;
  const previous = button("上一页", () => { state.page--; loadPage(); }, "small");
  const next = button("下一页", () => { state.page++; loadPage(); }, "small");
  previous.disabled = state.page === 1;
  next.disabled = state.page === pages;
  const actions = element("div", undefined, "actions");
  actions.append(previous, next);
  navigation.append(element("span", "第 " + state.page + " / " + pages + " 页，共 " + state.total + " 条"), actions);
  navigation.hidden = false;
}

// 翻页时只刷新明细，统计数字按当前条件保持稳定
async function loadPage() {
  const sequence = ++state.request;
  try {
    const page = await requestTable("list?" + (state.params ? state.params + "&" : "")
      + "pageNum=" + state.page + "&pageSize=" + pageSize);
    if (sequence !== state.request) return;
    state.rows = page.rows;
    state.total = page.total;
    render();
  } catch (error) {
    if (sequence === state.request) content.replaceChildren(element("p", error.message, "empty"));
  }
}

function makeTable(rows, interactive = true) {
  const table = element("table");
  const head = table.createTHead().insertRow();
  const headers = ["用户编号", "登录账号", "姓名", "部门", "角色", "状态", "档案员", "微信绑定", "最近登录"];
  if (interactive) headers.push("操作");
  for (const label of headers) {
    const header = element("th", label);
    header.scope = "col";
    head.append(header);
  }
  const body = table.createTBody();
  for (const row of rows) {
    const item = body.insertRow();
    const roleNames = (row.roles || []).map(role => role.roleName).filter(Boolean).join("、") || "—";
    const values = [
      row.userNo || "—", row.loginName || "", row.userName || "", row.dept?.deptName || "—",
      roleNames, statuses[row.status] || row.status,
      row.arcArchivist === "1" ? "是" : "否",
      row.wxBound === "1" ? "已绑定" : "未绑定",
      row.loginDate || "—"
    ];
    for (const value of values) item.insertCell().textContent = value;
    // 状态列加颜色标签，便于一眼区分正常与停用
    const statusCell = item.cells[5];
    statusCell.replaceChildren(element("span", statuses[row.status] || String(row.status),
      "status " + (row.status === "0" ? "active" : "disabled")));
    if (interactive) item.insertCell().append(button("详情", () => openDetails(row.userId), "small"));
  }
  return table;
}

function openDialog(title, parts, submit, label = "保存") {
  byId("dialog-title").textContent = title;
  byId("dialog-content").replaceChildren(...parts);
  byId("dialog-error").hidden = true;
  byId("save").hidden = !submit;
  byId("save").textContent = label;
  state.onSave = submit;
  if (!dialog.open) dialog.showModal();
}

async function openDetails(userId) {
  const loading = element("p", "正在读取详情…");
  openDialog("账号详情", [loading]);
  try {
    const detail = await request(String(userId));
    if (!loading.isConnected || !dialog.open) return;
    const list = element("dl");
    const entries = [
      ["用户编号", detail.userNo || "—"], ["登录账号", detail.loginName || ""], ["姓名", detail.userName || ""],
      ["部门", detail.deptName || "—"], ["角色", detail.roleNames || "—"], ["手机号码", detail.phonenumber || "—"],
      ["邮箱", detail.email || "—"], ["状态", detail.statusLabel || statuses[detail.status] || ""],
      ["档案员", detail.arcArchivist === "1" ? "是" : "否"],
      ["密级上限", detail.arcSecretLevel || "—（由 M13 密级规则判定）"],
      ["微信绑定", detail.wxBound ? "已绑定" : "未绑定"],
      ["最近登录", detail.loginDate ? detail.loginDate + "（" + (detail.loginIp || "未知IP") + "）" : "从未登录"],
      ["在线会话", detail.onlineSessions ?? 0],
      ["创建", (detail.createBy || "") + " " + (detail.createTime || "")],
      ["备注", detail.remark || "—"]
    ];
    for (const [label, value] of entries) list.append(element("dt", label), element("dd", String(value)));

    const items = [list];
    if (canManage) {
      const actions = element("div", undefined, "actions");
      actions.append(button("编辑", () => openEditor(detail)));
      actions.append(button(detail.status === "0" ? "停用" : "启用", () => {
        const disabling = detail.status === "0";
        openDialog(disabling ? "停用账号" : "启用账号", [
          element("p", (detail.loginName || "") + "。" + (disabling
            ? "停用后该账号立即无法登录，微信小程序会话同时失效；记录与审计保留，可随时恢复。"
            : "启用后该账号可正常登录，微信绑定关系保持不变。"))
        ], async () => {
          await send("PUT", "status", { userId: detail.userId, status: disabling ? "1" : "0" });
          return disabling ? "账号已停用。" : "账号已启用。";
        }, disabling ? "确认停用" : "确认启用");
      }));
      if (detail.wxBound) {
        actions.append(button("解除微信绑定", () => {
          openDialog("解除微信绑定", [
            element("p", "解除后该账号的小程序令牌立即失效，用户需要重新扫码绑定。是否继续？")
          ], async () => {
            await send("POST", "unbind/" + detail.userId);
            return "已解除该账号的微信绑定。";
          }, "确认解绑");
        }));
      }
      // 绑定二维码：内容只有用户编号，扫码工具解析结果可肉眼核对
      actions.append(button("绑定二维码", () => openQrCode(detail)));
      items.push(actions);
    }
    openDialog("账号详情", items);
  } catch (error) {
    loading.textContent = error.message;
  }
}

// 绑定二维码预览：后端返回 PNG，内容为纯文本用户编号（保留前导零）
async function openQrCode(detail) {
  const holder = element("div", "正在生成二维码…", "qr-holder");
  openDialog("账号绑定二维码", [
    holder,
    element("p", "二维码内容仅为用户编号「" + (detail.userNo || "") + "」，不含姓名、手机号等个人资料。", "hint"),
    element("p", "该二维码交给本人，用于小程序首次绑定；绑定仍需校验平台账号口令。", "hint")
  ]);
  try {
    const headers = { "X-Requested-With": "XMLHttpRequest" };
    if (csrf) headers["X-CSRF-Token"] = csrf;
    const response = await fetch(url("bindqrcode?userNo=" + encodeURIComponent(detail.userNo)), { headers });
    if (!response.ok) throw new Error("二维码生成失败，请检查用户编号。");
    const blob = await response.blob();
    const image = document.createElement("img");
    image.alt = "用户编号 " + detail.userNo + " 的绑定二维码";
    image.src = URL.createObjectURL(blob);
    holder.replaceChildren(image);
    holder.classList.remove("empty");
  } catch (error) {
    holder.textContent = error.message;
  }
}

function field(label, control) {
  const wrapper = element("label", label);
  wrapper.append(control);
  return wrapper;
}

function input(type, value, maxLength) {
  const control = element("input");
  control.type = type;
  control.value = value ?? "";
  if (maxLength) control.maxLength = maxLength;
  return control;
}

function openEditor(row) {
  const creating = !row;
  const userNo = input("text", row?.userNo || "", 64);
  userNo.required = true;
  userNo.pattern = "[A-Za-z0-9_\\-]+";
  userNo.title = "1-64位字母、数字、下划线或连字符；绑定二维码只包含此编号";
  const loginName = input("text", row?.loginName || "", 30);
  loginName.required = true;
  loginName.disabled = !creating; // 登录账号创建后不允许改，避免历史数据与审计断链
  const userName = input("text", row?.userName || "", 30);
  userName.required = true;
  const phonenumber = input("text", row?.phonenumber || "", 11);
  const email = input("email", row?.email || "", 50);
  const archivist = element("select");
  archivist.add(new Option("否", "0"));
  archivist.add(new Option("是", "1"));
  archivist.value = row?.arcArchivist === "1" ? "1" : "0";
  const secretLevel = input("text", row?.arcSecretLevel || "", 20);
  secretLevel.placeholder = "留空表示不限；具体判定规则由 M13 维护";
  const dept = element("select");
  dept.add(new Option("请选择部门", ""));
  for (const item of state.depts) {
    dept.add(new Option(item.label + (item.status === "1" ? "（已停用）" : ""), String(item.id)));
  }
  dept.value = row?.deptId ? String(row.deptId) : "";
  dept.required = true;
  const roleBoxes = element("div", undefined, "role-boxes");
  const selected = new Set(creating ? [] : (row.roleIds || []));
  for (const role of state.roles) {
    if (role.roleKey === "admin" && !row?.userId) continue; // 新增时不直接给超管角色，避免误授
    const label = element("label", undefined, "role-chip");
    const box = document.createElement("input");
    box.type = "checkbox";
    box.value = String(role.roleId);
    box.checked = selected.has(role.roleId) || (row?.roles || []).some(item => item.roleId === role.roleId);
    label.append(box, document.createTextNode(role.roleName));
    roleBoxes.append(label);
  }
  const remark = element("textarea");
  remark.value = row?.remark || "";
  remark.maxLength = 500;

  openDialog(creating ? "新增账号" : "编辑账号",
    [field("用户编号", userNo), field("登录账号", loginName), field("姓名", userName),
      field("部门", dept), field("角色", roleBoxes), field("手机号码", phonenumber),
      field("邮箱", email), field("档案员", archivist), field("密级上限引用", secretLevel),
      field("备注", remark)],
    async () => {
      const roleIds = Array.from(roleBoxes.querySelectorAll("input:checked")).map(box => Number(box.value));
      const payload = {
        userId: row?.userId, userNo: userNo.value.trim(), userName: userName.value.trim(),
        deptId: dept.value ? Number(dept.value) : null, roleIds,
        phonenumber: phonenumber.value.trim(), email: email.value.trim(),
        arcArchivist: archivist.value, arcSecretLevel: secretLevel.value.trim(), remark: remark.value
      };
      if (creating) {
        payload.loginName = loginName.value.trim();
        await send("POST", "", payload);
        return "账号已新增，初始口令与登录账号一致，请提醒本人首次登录后修改。";
      }
      await send("PUT", "", payload);
      return "账号已更新。";
    });
}

function openImport() {
  const file = input("file", "");
  file.accept = ".xlsx";
  file.required = true;
  openDialog("Excel 导入", [
    element("p", "请使用页面下载的模板。逐行校验，导入完成后显示成功数与每行失败原因。"),
    field("选择账号工作簿", file),
    element("p", "导入不修改口令与密级上限；已存在的登录账号按「修改基础资料」处理。", "hint")
  ], async () => {
    const body = new FormData();
    body.append("file", file.files[0]);
    // 导入是 multipart 上传，不能走 JSON 封装，这里复用 url() 与同一套错误处理
    const headers = { "X-Requested-With": "XMLHttpRequest" };
    if (csrf) headers["X-CSRF-Token"] = csrf;
    const response = await fetch(url("import"), { method: "POST", headers, body });
    const answer = await response.json().catch(() => null);
    if (response.redirected || response.status === 401) throw new Error("会话已过期，请重新登录。");
    if (!response.ok || !answer || answer.code !== 0) throw new Error(answer?.msg || "导入失败，请重试。");
    const summary = answer.data || {};
    const failed = (summary.failures || []).map(item => "第" + item.row + "行（" + (item.userNo || "—") + "）：" + item.reason);
    return "导入完成：成功 " + (summary.success ?? 0) + " 条（新增 " + (summary.created ?? 0)
      + "，更新 " + (summary.updated ?? 0) + "），失败 " + (summary.failed ?? 0) + " 条。"
      + (failed.length ? "\n" + failed.join("\n") : "");
  }, "导入");
}

form.addEventListener("submit", event => {
  event.preventDefault();
  feedback("");
  const params = new URLSearchParams();
  for (const [key, value] of new FormData(form)) {
    if (String(value).trim()) params.set(key, String(value).trim());
  }
  void load(params.toString());
});
byId("clear").addEventListener("click", () => { form.reset(); feedback(""); void load(""); });
byId("export").addEventListener("click", () => {
  location.href = url("export") + (state.params ? "?" + state.params : "");
});
byId("print").addEventListener("click", () => {
  const heading = element("h1", "档案账号清单");
  const meta = element("p", "共 " + state.total + " 条 · " + new Date().toLocaleString("zh-CN"));
  byId("print-view").replaceChildren(heading, meta, makeTable(state.rows, false));
  window.print();
});
if (canManage) {
  byId("add").addEventListener("click", () => openEditor(null));
  byId("import").addEventListener("click", openImport);
}
for (const id of ["cancel-dialog", "close-dialog"]) byId(id).addEventListener("click", () => dialog.close());
dialog.addEventListener("cancel", event => { if (state.busy) event.preventDefault(); });
byId("editor").addEventListener("submit", async event => {
  event.preventDefault();
  if (state.busy || !state.onSave) return;
  state.busy = true;
  const controls = ["save", "cancel-dialog", "close-dialog"].map(byId);
  for (const control of controls) control.disabled = true;
  const errorBox = byId("dialog-error");
  errorBox.hidden = true;
  try {
    const done = await state.onSave();
    dialog.close();
    feedback(done);
    await load();
  } catch (error) {
    errorBox.textContent = error.message;
    errorBox.hidden = false;
  } finally {
    state.busy = false;
    for (const control of controls) control.disabled = false;
  }
});
window.addEventListener("resize", () => {
  for (const chart of Object.values(state.charts)) {
    if (chart && chart.resize) chart.resize();
  }
});
void loadOptions().then(() => load());
