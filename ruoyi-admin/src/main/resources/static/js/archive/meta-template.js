"use strict";

const byId = id => document.getElementById(id);
const category = byId("template-category");
const version = byId("template-version");
const reload = byId("template-reload");
const editor = byId("template-editor");
const fieldList = byId("template-fields");
const previewFields = byId("preview-fields");
const templateName = byId("template-name");
const add = byId("template-add");
const save = byId("template-save");
const previewButton = byId("preview-validate");
const content = byId("template-content");
const canManage = document.body.dataset.manage === "true";
const fieldTypes = {text: "文本", number: "数字", date: "日期"};

let categoryId = "";
let latestVersion = 0;
let shownVersion = 0;
let busy = false;
let dirty = false;

function node(tag, text, className) {
    const element = document.createElement(tag);
    if (text) element.textContent = text;
    if (className) element.className = className;
    return element;
}

function label(text, input, className) {
    const wrapper = node("label", text, className);
    wrapper.append(input);
    return wrapper;
}

function button(text, onClick) {
    const control = node("button", text, "button small");
    control.type = "button";
    control.addEventListener("click", onClick);
    return control;
}

function feedback(id, text = "", failed = false) {
    const message = byId(id);
    message.hidden = !text;
    message.textContent = text;
    message.classList.toggle("error", failed);
}

async function request(path, data) {
    const headers = {"X-Requested-With": "XMLHttpRequest"};
    const options = {headers};
    if (data !== undefined) {
        options.method = "POST";
        headers["Content-Type"] = "application/json";
        headers["X-CSRF-Token"] = document.querySelector('meta[name="csrf-token"]').content;
        options.body = JSON.stringify(data);
    }
    const response = await fetch(document.body.dataset.base + path, options);
    if (!response.headers.get("Content-Type")?.includes("application/json")) {
        throw new Error("无法读取模板，请确认登录状态和操作权限");
    }
    const result = await response.json();
    if (!response.ok || result.code !== 0) throw new Error(result.msg || "操作失败");
    return result.data;
}

function updateControls() {
    const editable = canManage && shownVersion === latestVersion;
    category.disabled = busy;
    version.disabled = busy || latestVersion === 0;
    reload.disabled = busy || !categoryId;
    byId("template-editable").disabled = busy || !editable;
    content.classList.toggle("template-readonly", !editable);
    if (add) add.disabled = fieldList.children.length >= 100;
    if (save) {
        save.hidden = !editable;
        save.disabled = busy;
    }
    previewButton.disabled = busy;
}

async function perform(action, feedbackId = "template-feedback") {
    if (busy) return;
    busy = true;
    feedback(feedbackId);
    updateControls();
    try {
        await action();
    } catch (error) {
        feedback(feedbackId, error.message, true);
    } finally {
        busy = false;
        category.value = categoryId;
        version.value = String(shownVersion);
        updateControls();
    }
}

function collectFields() {
    return Array.from(fieldList.children, row => ({
        key: row.querySelector('[name="key"]').value.trim(),
        label: row.querySelector('[name="label"]').value.trim(),
        type: row.querySelector('[name="type"]').value,
        required: row.querySelector('[name="required"]').checked
    }));
}

function buildField(field) {
    const row = node("div", "", "template-field");
    const heading = node("div", "", "template-field-head");
    const controls = node("div", "", "template-field-controls");
    const key = node("input");
    key.name = "key";
    key.value = field.key;
    key.placeholder = "如 contract_no";
    key.pattern = "[A-Za-z][A-Za-z0-9_]{0,31}";
    key.title = "以字母开头，最多 32 位字母、数字或下划线";
    key.maxLength = 32;
    key.required = true;
    const fieldLabel = node("input");
    fieldLabel.name = "label";
    fieldLabel.value = field.label;
    fieldLabel.maxLength = 120;
    fieldLabel.required = true;
    const type = node("select");
    type.name = "type";
    for (const [value, text] of Object.entries(fieldTypes)) type.add(new Option(text, value));
    type.value = field.type;
    const required = node("input");
    required.name = "required";
    required.type = "checkbox";
    required.checked = field.required;
    controls.append(label("字段键", key), label("显示名称", fieldLabel), label("类型", type),
        label("必填", required, "template-required"));
    heading.append(node("strong", "", "template-field-number"));
    if (canManage) {
        const actions = node("div", "", "actions template-field-actions");
        const up = button("上移", () => {
            row.previousElementSibling?.before(row);
            markEdited();
        });
        up.dataset.move = "up";
        const down = button("下移", () => {
            row.nextElementSibling?.after(row);
            markEdited();
        });
        down.dataset.move = "down";
        actions.append(up, down, button("移除", () => {
            row.remove();
            markEdited();
        }));
        heading.append(actions);
    }
    row.append(heading, controls);
    return row;
}

function renderPreview() {
    const fields = collectFields();
    previewFields.replaceChildren();
    for (const [index, field] of fields.entries()) {
        const row = fieldList.children[index];
        row.querySelector(".template-field-number").textContent = "字段 " + (index + 1);
        if (canManage) {
            row.querySelector('[data-move="up"]').disabled = index === 0;
            row.querySelector('[data-move="down"]').disabled = index === fields.length - 1;
        }
        const input = node("input");
        input.type = field.type;
        input.dataset.key = field.key;
        input.required = field.required;
        if (field.type === "text") input.maxLength = 1000;
        if (field.type === "number") input.step = "any";
        const wrapper = node("div");
        const title = label("", input);
        const caption = node("span", field.label || "字段 " + (index + 1));
        if (field.required) caption.append(" ", node("span", "必填", "preview-required"));
        title.prepend(caption);
        const error = node("p", "", "preview-error");
        error.id = "field-error-" + index;
        error.hidden = true;
        input.setAttribute("aria-describedby", error.id);
        wrapper.append(title, error);
        previewFields.append(wrapper);
    }
    byId("preview-empty").textContent = fields.length ? "" : "暂无配置字段。";
    byId("preview-empty").hidden = fields.length > 0;
    previewButton.hidden = fields.length === 0;
    feedback("preview-feedback");
}

function markEdited(event) {
    dirty = true;
    if (event?.target !== templateName) renderPreview();
    updateControls();
}

function show(template, newest) {
    categoryId = String(template.categoryId);
    latestVersion = newest;
    shownVersion = template.version;
    version.replaceChildren();
    if (newest === 0) version.add(new Option("未保存", "0"));
    for (let i = newest; i >= 1; i--) {
        version.add(new Option("第 " + i + " 版" + (i === newest ? "（当前）" : ""), String(i)));
    }
    templateName.value = template.name;
    fieldList.replaceChildren(...template.fields.map(buildField));
    content.hidden = false;
    byId("template-empty").hidden = true;
    dirty = false;
    renderPreview();
}

function discardChanges() {
    return !dirty || window.confirm("当前修改尚未保存，放弃修改？");
}

function loadLatest(selected) {
    return perform(async () => {
        if (!selected) {
            categoryId = "";
            latestVersion = shownVersion = 0;
            dirty = false;
            content.hidden = true;
            byId("template-empty").hidden = false;
            version.replaceChildren(new Option("未保存", "0"));
            return;
        }
        const template = await request("categories/" + selected);
        show(template, template.version);
    });
}

category.addEventListener("change", () => {
    if (discardChanges()) void loadLatest(category.value);
    else category.value = categoryId;
});

version.addEventListener("change", () => {
    if (!discardChanges()) {
        version.value = String(shownVersion);
        return;
    }
    const selected = version.value;
    void perform(async () => {
        const template = await request("categories/" + categoryId + "?version=" + selected);
        show(template, latestVersion);
    });
});

reload.addEventListener("click", () => {
    if (discardChanges()) void loadLatest(categoryId);
});

if (canManage) {
    add.addEventListener("click", () => {
        const row = buildField({key: "", label: "", type: "text", required: false});
        fieldList.append(row);
        markEdited();
        row.querySelector("input").focus();
    });
    editor.addEventListener("input", markEdited);
    editor.addEventListener("submit", event => {
        event.preventDefault();
        if (shownVersion !== latestVersion) return;
        void perform(async () => {
            const template = await request("categories/" + categoryId, {
                expectedVersion: latestVersion,
                name: templateName.value,
                fields: collectFields()
            });
            show(template, template.version);
            feedback("template-feedback", "已保存第 " + template.version + " 版");
        });
    });
}

byId("template-preview").addEventListener("submit", event => {
    event.preventDefault();
    void perform(async () => {
        const inputs = Array.from(previewFields.querySelectorAll("input"));
        const values = Object.fromEntries(inputs.map(input => [input.dataset.key, input.value]));
        const errors = await request("categories/" + categoryId + "/validate", {fields: collectFields(), values});
        let failed = false;
        for (const input of inputs) {
            const message = byId(input.getAttribute("aria-describedby"));
            message.textContent = Object.hasOwn(errors, input.dataset.key) ? errors[input.dataset.key] : "";
            // 浏览器可能把无法解析的输入读成空字符串，用 badInput 区分无效输入和留空。
            if (input.validity.badInput) {
                message.textContent = input.type === "date" ? "请输入有效日期" : "请输入有效数字";
            }
            message.hidden = !message.textContent;
            failed ||= !message.hidden;
            input.setAttribute("aria-invalid", String(!message.hidden));
        }
        feedback("preview-feedback", failed ? "请检查标出的字段" : "校验通过", failed);
    }, "preview-feedback");
});
