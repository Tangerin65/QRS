"use strict";

const section = document.body;
const base = section.dataset.base;
const canManage = section.dataset.manage === "true";
const csrf = document.querySelector('meta[name="csrf-token"]').content;
const byId = id => document.getElementById(id);
const form = byId("filters");
const dialog = byId("editor-dialog");
const content = byId("catalog-content");
const kinds = {fond:"全宗", category:"分类", collection:"档案集", folder:"逻辑文件夹"};
const statuses = {active:"启用", disabled:"停用"};
const state = {rows:[], all:[], mode:"tree", page:1, params:"", request:0, onSave:null, busy:false, loading:false};
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

async function request(path, data) {
    const headers = {"X-Requested-With":"XMLHttpRequest"};
    if (csrf) headers["X-CSRF-Token"] = csrf;
    let body = data;
    if (data !== undefined && !(data instanceof FormData)) {
        headers["Content-Type"] = "application/json";
        body = JSON.stringify(data);
    }
    const response = await fetch(base + path, {method:data === undefined ? "GET" : "POST", headers, body});
    const answer = await response.json().catch(() => null);
    if (response.redirected || response.status === 401) throw new Error("会话已过期，请重新登录。");
    if (!response.ok || !answer || answer.code !== 0) throw new Error(answer?.msg || "请求失败，请重试。");
    return answer.data ?? answer.msg;
}

function feedback(text, failed = false) {
    const output = byId("feedback");
    output.textContent = text;
    output.hidden = !text;
    output.classList.toggle("error", failed);
}

function catalogPath(row, index) {
    const parts = [row.name];
    const seen = new Set([row.id]);
    let ancestor = row.parentId;
    while (ancestor !== null && !seen.has(ancestor) && index.has(ancestor)) {
        const parent = index.get(ancestor);
        parts.unshift(parent.name);
        seen.add(ancestor);
        ancestor = parent.parentId;
    }
    return parts.join(" / ");
}

async function load(params = state.params) {
    const sequence = ++state.request;
    state.loading = true;
    content.replaceChildren(element("p","正在读取目录…","empty"));
    byId("pagination").hidden = true;
    byId("export").disabled = true;
    byId("print").disabled = true;
    byId("mode").disabled = true;
    if (canManage) byId("add").disabled = true;
    try {
        const rows = request("entries" + (params ? "?" + params : ""));
        const [visible, all] = await Promise.all([rows, params ? request("entries") : rows]);
        if (sequence !== state.request) return;
        state.rows = visible;
        state.all = all;
        state.params = params;
        state.page = 1;
        state.loading = false;
        byId("mode").disabled = false;
        byId("export").disabled = visible.length === 0;
        byId("print").disabled = visible.length === 0;
        if (canManage) byId("add").disabled = false;
        render();
    } catch (error) {
        if (sequence !== state.request) return;
        state.loading = false;
        const message = element("p",error.message,"empty");
        message.append(" ",button("重试",() => load(params),"small"));
        content.replaceChildren(message);
        for (const key of ["total","active","disabled"]) byId("metric-" + key).textContent = "—";
    }
}

function render() {
    if (state.loading) return;
    const active = state.rows.filter(row => row.status === "active").length;
    byId("metric-total").textContent = state.rows.length;
    byId("metric-active").textContent = active;
    byId("metric-disabled").textContent = state.rows.length - active;
    const navigation = byId("pagination");
    navigation.replaceChildren();
    navigation.hidden = true;
    if (!state.rows.length) {
        content.replaceChildren(element("p","没有符合条件的目录。可调整筛选条件或新增目录。","empty"));
        return;
    }
    if (state.mode === "tree") {
        content.replaceChildren(renderTree());
        return;
    }
    const pages = Math.ceil(state.rows.length / pageSize);
    const start = (state.page - 1) * pageSize;
    content.replaceChildren(makeTable(state.rows.slice(start, start + pageSize), true));
    if (pages < 2) return;
    const previous = button("上一页",() => { state.page--; render(); },"small");
    const next = button("下一页",() => { state.page++; render(); },"small");
    previous.disabled = state.page === 1;
    next.disabled = state.page === pages;
    const actions = element("div",undefined,"actions");
    actions.append(previous,next);
    navigation.append(element("span","第 " + state.page + " / " + pages + " 页"),actions);
    navigation.hidden = false;
}

function makeTable(rows, interactive) {
    const table = element("table");
    const head = table.createTHead().insertRow();
    for (const label of ["名称","编码","类型","目录路径","状态","显示顺序",...(interactive ? ["操作"] : [])]) {
        const header = element("th",label);
        header.scope = "col";
        head.append(header);
    }
    const data = table.createTBody();
    const index = new Map(state.all.map(row => [row.id,row]));
    for (const row of rows) {
        const item = data.insertRow();
        for (const value of [row.name,row.code,kinds[row.kind],catalogPath(row,index),statuses[row.status],row.sortOrder]) {
            item.insertCell().textContent = value;
        }
        if (interactive) item.insertCell().append(button("详情",() => openDetails(row.id),"small"));
    }
    return table;
}

function renderTree() {
    const visible = new Set(state.rows.map(row => row.id));
    const index = new Map(state.all.map(row => [row.id,row]));
    const byParent = new Map();
    for (const row of state.rows) {
        const parent = visible.has(row.parentId) ? row.parentId : null;
        if (!byParent.has(parent)) byParent.set(parent,[]);
        byParent.get(parent).push(row);
    }
    function branch(parentId) {
        const list = element("ul",undefined,parentId === null ? "tree" : "");
        for (const row of byParent.get(parentId) || []) {
            const item = element("li");
            const hasChildren = byParent.has(row.id);
            const line = element(hasChildren ? "summary" : "div",undefined,"line");
            const status = element("span",statuses[row.status],"status " + row.status);
            line.append(element("strong",row.name),element("span",row.code),element("span",kinds[row.kind]),status,
                button("详情",event => { event.preventDefault(); void openDetails(row.id); },"small"));
            if (hasChildren) {
                const nested = element("details");
                nested.open = true;
                nested.append(line,branch(row.id));
                item.append(nested);
            } else item.append(line);
            if (parentId === null && row.parentId !== null)
                item.append(element("small",catalogPath(row,index),"path"));
            list.append(item);
        }
        return list;
    }
    return branch(null);
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

async function openDetails(id) {
    const loading = element("p","正在读取详情…");
    openDialog("目录详情",[loading]);
    try {
        const row = await request("entries/" + id);
        if (!loading.isConnected || !dialog.open) return;
        const index = new Map(state.all.map(item => [item.id,item]));
        const details = element("dl");
        for (const [label,value] of [["名称",row.name],["编码",row.code],["类型",kinds[row.kind]],
            ["目录路径",catalogPath(row,index)],["状态",statuses[row.status]],["显示顺序",row.sortOrder],["备注",row.remark || "—"]]) {
            details.append(element("dt",label),element("dd",value));
        }
        const items = [details];
        if (canManage) {
            const actions = element("div",undefined,"actions");
            actions.append(button("编辑",() => openEditor(row)));
            const active = row.status === "active";
            if (active) actions.append(button("新增下级",() => openEditor(null,row.id)));
            actions.append(button(active ? "停用" : "启用",() => {
                const title = active ? "停用目录" : "启用目录";
                const description = active ? "停用后从新建档案的可选项中移除，历史目录仍保留。"
                    : "启用后，满足条件的分类可用于新建档案。";
                openDialog(title,[element("p",row.name + "。"+description)], async () => {
                    await request("entries",{...row,status:active ? "disabled" : "active"});
                    return active ? "目录已停用。" : "目录已启用。";
                },active ? "确认停用" : "确认启用");
            }));
            items.push(actions);
        }
        openDialog("目录详情",items);
    } catch (error) { loading.textContent = error.message; }
}

function field(label, control) {
    const wrapper = element("label",label);
    wrapper.append(control);
    return wrapper;
}

function input(type, name, value, maxLength) {
    const control = element("input");
    control.type = type;
    control.name = name;
    control.value = value;
    if (maxLength) control.maxLength = maxLength;
    return control;
}

function openEditor(row, inheritedParent = null) {
    const code = input("text","code",row?.code || "",64);
    code.pattern = "[A-Za-z0-9_.\\-]+";
    code.required = true;
    const name = input("text","name",row?.name || "",120);
    name.required = true;
    const kind = element("select");
    for (const [value,label] of Object.entries(kinds)) kind.add(new Option(label,value));
    const inherited = state.all.find(item => item.id === inheritedParent);
    kind.value = row?.kind || (inherited ? (["fond","category"].includes(inherited.kind) ? "category" : "folder") : "fond");
    kind.disabled = !!row;
    const parent = element("select");
    const parentField = field("上级目录",parent);
    const index = new Map(state.all.map(item => [item.id,item]));
    for (const item of state.all) {
        if (item.id === row?.id) continue;
        let next = item.parentId;
        const seen = new Set();
        while (next !== null && next !== row?.id && !seen.has(next) && index.has(next)) {
            seen.add(next);
            next = index.get(next).parentId;
        }
        if (next === row?.id) continue;
        const choice = new Option(catalogPath(item,index) + "（" + item.code + "）",String(item.id));
        choice.dataset.kind = item.kind;
        parent.add(choice);
    }
    parent.add(new Option("请选择上级目录",""),0);
    parent.value = String(row?.parentId ?? inheritedParent ?? "");
    function updateParent() {
        parentField.hidden = kind.value === "fond";
        parent.required = !parentField.hidden;
        for (const option of parent.options)
            option.disabled = kind.value === "category" && option.value !== "" && !["fond","category"].includes(option.dataset.kind);
        if (parent.selectedOptions[0]?.disabled) parent.value = "";
    }
    kind.addEventListener("change",updateParent);
    updateParent();
    const status = element("select");
    for (const [value,label] of Object.entries(statuses)) status.add(new Option(label,value));
    status.value = row?.status || "active";
    const order = input("number","sortOrder",row?.sortOrder ?? 0);
    order.min = "0";
    order.max = "2147483647";
    order.required = true;
    const remark = element("textarea");
    remark.value = row?.remark || "";
    remark.maxLength = 1000;
    openDialog(row ? "编辑目录" : "新增目录",
        [field("编码",code),field("名称",name),field("类型",kind),parentField,
            field("状态",status),field("显示顺序",order),field("备注",remark)],
        async () => {
            await request("entries",{id:row?.id,revision:row?.revision,code:code.value,name:name.value,
                kind:kind.value,parentId:kind.value === "fond" || !parent.value ? null : Number(parent.value),
                status:status.value,sortOrder:Number(order.value),remark:remark.value});
            return row ? "目录已更新。" : "目录已新增。";
        });
}

function openImport() {
    const file = input("file","file","");
    file.accept = ".xlsx";
    file.required = true;
    openDialog("Excel 导入",[
        element("p","请使用下载的模板。上级记录排在下级之前，遇到错误会撤回本批全部数据。"),
        field("选择目录工作簿",file)
    ],async () => {
        const body = new FormData();
        body.append("file",file.files[0]);
        return request("import",body);
    },"导入");
}

form.addEventListener("submit",event => {
    event.preventDefault();
    feedback("");
    const params = new URLSearchParams();
    for (const [key,value] of new FormData(form)) if (String(value).trim()) params.set(key,String(value).trim());
    void load(params.toString());
});
byId("clear").addEventListener("click",() => { form.reset(); feedback(""); void load(""); });
byId("mode").addEventListener("click",event => {
    state.mode = state.mode === "tree" ? "list" : "tree";
    event.currentTarget.textContent = state.mode === "tree" ? "切换列表" : "切换目录树";
    event.currentTarget.setAttribute("aria-pressed",String(state.mode === "list"));
    render();
});
byId("export").addEventListener("click",() => { location.href = base + "export" + (state.params ? "?" + state.params : ""); });
byId("print").addEventListener("click",() => {
    const heading = element("h1","档案目录");
    const summary = element("p","共 " + state.rows.length + " 条 · " + new Date().toLocaleString("zh-CN"));
    byId("print-view").replaceChildren(heading,summary,makeTable(state.rows,false));
    window.print();
});
if (canManage) {
    byId("add").addEventListener("click",() => openEditor(null));
    byId("import").addEventListener("click",openImport);
}
for (const id of ["cancel-dialog","close-dialog"]) byId(id).addEventListener("click",() => dialog.close());
dialog.addEventListener("cancel",event => { if (state.busy) event.preventDefault(); });
byId("editor").addEventListener("submit", async event => {
    event.preventDefault();
    if (state.busy || !state.onSave) return;
    state.busy = true;
    const controls = ["save","cancel-dialog","close-dialog"].map(byId);
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
void load();
