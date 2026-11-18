var archive = (function () {
    var prefix = ctx + "records";
    var importRows = [];

    function postJson(url, data, done) {
        $.ajax({
            url: prefix + url,
            type: "POST",
            contentType: "application/json;charset=UTF-8",
            data: JSON.stringify(data),
            success: done
        });
    }

    function escape(value) {
        return $('<div>').text(value == null ? "" : value).html();
    }

    function loadCategories() {
        $.get(prefix + "/categories", function (res) {
            if (res.code !== 0) return;
            var formOptions = '<option value="">请选择分类</option>';
            var filterOptions = '<option value="">全部分类</option>';
            $.each(res.data || [], function (_, item) {
                var label = escape(item.path + " [" + item.code + "]");
                formOptions += '<option value="' + item.id + '">' + label + '</option>';
                filterOptions += '<option value="' + item.id + '">' + label + '</option>';
            });
            $('#archive-editor [name="categoryId"]').html(formOptions);
            $('#filter-category').html(filterOptions);
        });
    }

    function showResult(res, onSuccess) {
        if (res.code === 0) onSuccess();
        else $.modal.alertWarning(res.msg || "操作失败");
    }

    function rowActions(row) {
        //按权限生成档案行操作入口
        var html = [];
        if (archiveFlags.canFiles) html.push('<a class="btn btn-default btn-xs" onclick="archive.files(' + row.archiveId + ')"><i class="fa fa-paperclip"></i>附件</a> ');
        if (archiveFlags.canView) html.push('<a class="btn btn-info btn-xs" onclick="archive.detail(' + row.archiveId + ')"><i class="fa fa-eye"></i>详情</a> ');
        if (archiveFlags.canEdit && row.archiveStatus === "DRAFT") html.push('<a class="btn btn-primary btn-xs" onclick="archive.edit(' + row.archiveId + ')"><i class="fa fa-edit"></i>编辑</a> ');
        if (archiveFlags.canSubmit && row.archiveStatus === "DRAFT") html.push('<a class="btn btn-success btn-xs" onclick="archive.submit(' + row.archiveId + ',' + row.version + ')"><i class="fa fa-check"></i>提交</a> ');
        if (archiveFlags.canDelete && row.archiveStatus === "DRAFT") html.push('<a class="btn btn-danger btn-xs" onclick="archive.remove(' + row.archiveId + ',' + row.version + ')"><i class="fa fa-remove"></i>撤销</a>');
        return html.join("");
    }

    function editorValues() {
        var data = {};
        $.each($("#archive-editor").serializeArray(), function (_, item) { data[item.name] = item.value; });
        ["archiveId", "version", "categoryId", "secretLevel"].forEach(function (key) {
            if (data[key] !== "") data[key] = Number(data[key]);
        });
        if (!data.metadataJson) data.metadataJson = "{}";
        return data;
    }

    function loadStatistics() {
        $.post(prefix + "/statistics", $.common.formToJSON("archive-form"), function (res) {
            if (res.code !== 0) return;
            var totals = { DRAFT: 0 };
            $.each(res.data || [], function (_, item) { totals[item.status] = item.total; });
            var text = "草稿 " + (totals.DRAFT || 0);
            $.each(totals, function (status, count) {
                if (status !== "DRAFT") text += " / " + status + " " + count;
            });
            $("#statistics").text("匹配统计：" + text);
        });
    }

    function initDates() {
        //统一日历语言和日期格式，避免浏览器原生日期占位混排
        $(".archive-date").datetimepicker({
            language: "zh-cn",
            format: "yyyy-mm-dd",
            minView: "month",
            autoclose: true,
            todayHighlight: true,
            clearBtn: true,
            forceParse: false
        });
        $("#archive-modal").on("shown.bs.modal", function () {
            $('#archive-editor [name="formingDate"]').datetimepicker("update");
        });
    }

    function initTable() {
        $.table.init({
            url: prefix + "/list",
            method: "post",
            formId: "archive-form",
            sortName: "createTime",
            sortOrder: "desc",
            pageSize: 20,
            columns: [
                { checkbox: true },
                { field: "archiveCode", title: "档号", sortable: true },
                { field: "title", title: "题名", sortable: true },
                { field: "formingDate", title: "形成日期", sortable: true },
                { field: "categoryName", title: "分类" },
                { field: "secretName", title: "密级", formatter: function (value) { return escape(value); } },
                { field: "retentionName", title: "保管期限" },
                { field: "archiveStatus", title: "状态", formatter: function (value) { return value === "DRAFT" ? "草稿" : value; } },
                { field: "createBy", title: "登记人" },
                { field: "createTime", title: "登记时间" },
                { title: "操作", align: "center", formatter: function (_, row) { return rowActions(row); } }
            ],
            onLoadSuccess: function (data) {
                $("#summary").text("当前页 " + (data.rows || []).length + " 条");
            }
        });
    }

    return {
        files: function (id) {
            //打开当前档案的附件页面
            $.modal.openTab("档案附件", prefix + "/files/" + id);
        },
        init: function () {
            initDates();
            initTable();
            loadCategories();
            loadStatistics();
        },
        search: function () {
            $.table.search();
            loadStatistics();
        },
        openAdd: function () {
            $("#archive-editor")[0].reset();
            $('#archive-editor [name="archiveId"], #archive-editor [name="version"]').val("");
            $("#archive-code-preview").val("");
            $("#archive-modal-title").text("新建档案草稿");
            $("#archive-modal").modal("show");
        },
        previewCode: function () {
            var categoryId = $('#archive-editor [name="categoryId"]').val();
            var formingDate = $('#archive-editor [name="formingDate"]').val();
            if (!categoryId) {
                $.modal.alertWarning("请先选择分类");
                $('#archive-editor [name="categoryId"]').focus();
                return;
            }
            if (!formingDate) {
                $.modal.alertWarning("请先填写形成日期");
                $('#archive-editor [name="formingDate"]').focus();
                return;
            }
            $.get(prefix + "/code-preview", { categoryId: categoryId, formingDate: formingDate }, function (res) {
                if (res.code === 0) $("#archive-code-preview").val(res.data);
                else $.modal.alertWarning(res.msg || "档号预览失败");
            }).fail(function (xhr) {
                var message = xhr.responseJSON && xhr.responseJSON.msg;
                $.modal.alertWarning(message || "档号预览请求失败，请确认当前角色有新增权限后重试");
            });
        },
        save: function () {
            var form = $("#archive-editor")[0];
            if (!form.reportValidity()) return;
            var data = editorValues();
            postJson(data.archiveId ? "/edit" : "/add", data, function (res) {
                showResult(res, function () {
                    $("#archive-modal").modal("hide");
                    $.table.refresh();
                    loadStatistics();
                });
            });
        },
        edit: function (id) {
            $.get(prefix + "/edit/" + id, function (res) {
                if (res.code !== 0) return $.modal.alertWarning(res.msg || "无法读取档案");
                $("#archive-editor")[0].reset();
                $.each(res.data, function (key, value) {
                    var input = $('#archive-editor [name="' + key + '"]');
                    if (input.length && value != null) input.val(value);
                });
                $("#archive-code-preview").val(res.data.archiveCode || "");
                $("#archive-modal-title").text("修改档案草稿");
                $("#archive-modal").modal("show");
            });
        },
        detail: function (id) {
            $.get(prefix + "/detail/" + id, function (res) {
                if (res.code !== 0) return $.modal.alertWarning(res.msg || "无法读取档案");
                var item = res.data;
                var fields = ["archiveCode", "title", "formingDate", "categoryName", "secretName", "retentionName", "summary", "archiveStatus", "metadataJson"];
                var labels = ["档号", "题名", "形成日期", "分类", "密级", "保管期限", "摘要", "状态", "扩展元数据"];
                var lines = fields.map(function (field, index) { return labels[index] + "：" + (item[field] == null ? "" : item[field]); });
                $("#archive-detail").text(lines.join("\n"));
                var auditHtml = "";
                $.each(item.auditEvents || [], function (_, event) {
                    auditHtml += "<tr><td>" + escape(event.eventType) + "</td><td>" + escape(event.actorName) + "</td><td>" + escape(event.eventTime) + "</td><td>" + escape(event.detail) + "</td></tr>";
                });
                $("#audit-events").html(auditHtml || '<tr><td colspan="4">暂无记录</td></tr>');
                $("#detail-modal").modal("show");
            });
        },
        submit: function (id, version) {
            $.modal.confirm("确认提交该档案？提交后将不能再按草稿修改或撤销。", function () {
                $.ajax({ url: prefix + "/submit", type: "POST", data: { archiveId: id, version: version }, success: function (res) {
                    showResult(res, function () { $.table.refresh(); loadStatistics(); });
                }});
            });
        },
        remove: function (id, version) {
            $.modal.confirm("确认撤销该档案草稿？", function () {
                $.ajax({ url: prefix + "/remove", type: "POST", data: { archiveId: id, version: version }, success: function (res) {
                    showResult(res, function () { $.table.refresh(); });
                }});
            });
        },
        openBatch: function () {
            var drafts = $("#bootstrap-table").bootstrapTable("getSelections").filter(function (row) { return row.archiveStatus === "DRAFT"; });
            if (!drafts.length) return $.modal.alertWarning("请选择草稿档案");
            $("#batch-secret").val("");
            $("#batch-retention").val("");
            $("#batch-modal").modal("show");
        },
        saveBatch: function () {
            var selected = $("#bootstrap-table").bootstrapTable("getSelections").filter(function (row) { return row.archiveStatus === "DRAFT"; });
            var updates = selected.map(function (row) { return { archiveId: row.archiveId, version: row.version }; });
            var secret = $("#batch-secret").val();
            var retention = $("#batch-retention").val();
            if (!updates.length) return $.modal.alertWarning("请选择草稿档案");
            if (secret === "" && retention === "") return $.modal.alertWarning("请至少填写一个批量修改项");
            postJson("/batch-edit", { updates: updates, secretLevel: secret === "" ? null : Number(secret), retentionCode: retention === "" ? null : retention }, function (res) {
                showResult(res, function () {
                    $("#batch-modal").modal("hide");
                    $.table.refresh();
                    loadStatistics();
                });
            });
        },
        openImport: function () {
            importRows = [];
            $("#import-file").val("");
            $("#import-rows").empty();
            $("#import-confirm").prop("disabled", true);
            $("#import-modal").modal("show");
        },
        previewImport: function () {
            var file = $("#import-file")[0].files[0];
            if (!file) return $.modal.alertWarning("请选择Excel文件");
            var data = new FormData();
            data.append("file", file);
            $.ajax({ url: prefix + "/import/preview", type: "POST", data: data, processData: false, contentType: false, success: function (res) {
                if (res.code !== 0) return $.modal.alertWarning(res.msg || "文件校验失败");
                importRows = res.data || [];
                var html = "";
                var valid = importRows.length > 0;
                $.each(importRows, function (_, line) {
                    var record = line.record || {};
                    var error = line.error || "";
                    if (error) valid = false;
                    html += "<tr><td>" + line.row + "</td><td>" + escape(record.title) + "</td><td>" + escape(record.categoryCode) + "</td><td class='" + (error ? "text-danger" : "text-success") + "'>" + escape(error || "通过") + "</td></tr>";
                });
                $("#import-rows").html(html);
                $("#import-confirm").prop("disabled", !valid);
            }});
        },
        confirmImport: function () {
            var records = importRows.map(function (line) { return line.record; });
            postJson("/import/confirm", records, function (res) {
                showResult(res, function () {
                    $("#import-modal").modal("hide");
                    $.table.refresh();
                    loadStatistics();
                    $.modal.msgSuccess(res.msg);
                });
            });
        },
        export: function () {
            window.location.href = prefix + "/export?" + $.param($.common.formToJSON("archive-form"));
        }
    };
}());

$(function () {
    archive.init();
});
