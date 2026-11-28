var locationPage = (function () {
    var prefix = ctx + "records/location";
    var treeCache = [];

    function escape(value) {
        return $('<div>').text(value == null ? "" : value).html();
    }

    function loadTree() {
        $.post(prefix + "/list", $.common.formToJSON("location-form"), function (res) {
            if (res.code !== 0) return;
            treeCache = res.data || [];
            $("#bootstrap-table").bootstrapTable("load", treeCache);
            $("#summary").text("共 " + treeCache.length + " 个库位");
            loadStatistics();
        });
    }

    function loadStatistics() {
        $.post(prefix + "/statistics", {}, function (res) {
            if (res.code !== 0) return;
            var data = res.data || {};
            var html = "";
            html += '<div class="m10-metric"><span>库房</span><strong>' + (data.WAREHOUSE || 0) + '</strong></div>';
            html += '<div class="m10-metric"><span>区域</span><strong>' + (data.AREA || 0) + '</strong></div>';
            html += '<div class="m10-metric"><span>柜架</span><strong>' + (data.RACK || 0) + '</strong></div>';
            html += '<div class="m10-metric"><span>已挂档案</span><strong>' + (data.linkedArchives || 0) + '</strong></div>';
            $("#metrics").html(html);
        });
    }

    function rowActions(row) {
        if (!locationFlags.canManage) return "";
        return '<a class="btn btn-primary btn-xs" onclick="locationPage.edit(' + row.locationId + ')"><i class="fa fa-edit"></i>编辑</a> '
            + '<a class="btn btn-danger btn-xs" onclick="locationPage.remove(' + row.locationId + ')"><i class="fa fa-trash"></i>删除</a>';
    }

    function initTable() {
        $("#bootstrap-table").bootstrapTable({
            data: [],
            pagination: false,
            striped: true,
            columns: [
                { field: "levelPath", title: "完整路径" },
                { field: "locationCode", title: "编码" },
                { field: "locationName", title: "名称" },
                { field: "levelName", title: "层级" },
                { field: "capacity", title: "容量", formatter: function (v) { return v == null ? "" : v; } },
                { field: "usedCount", title: "已挂档案" },
                { field: "mediumLabel", title: "介质", formatter: function (value, row) {
                    if (!value || value === "未登记") return '<span class="text-muted">未登记</span>';
                    var tone = value === "纸质" ? "primary" : (value === "电子" ? "info" : "warning");
                    return '<span class="label label-' + tone + '">' + escape(value) + '</span>';
                } },
                { field: "electronicPath", title: "电子存储位置", formatter: function (value) {
                    // 阶段2 字段空值统一「未登记」，与契约 §7 和小程序一致。
                    return value ? escape(value) : '<span class="text-muted">未登记</span>';
                } },
                { field: "status", title: "状态", formatter: function (v) { return v === 0 ? '<span class="label label-success">启用</span>' : '<span class="label label-default">停用</span>'; } },
                { field: "sortOrder", title: "排序" },
                { field: "remark", title: "备注" },
                { title: "操作", align: "center", formatter: function (_, row) { return rowActions(row); } }
            ]
        });
    }

    function fillParents(selected) {
        var level = $("#editor-level").val();
        var index = ["WAREHOUSE", "AREA", "RACK"].indexOf(level);
        var upper = index > 0 ? ["WAREHOUSE", "AREA", "RACK"][index - 1] : null;
        var options = upper ? '<option value="">请选择上级</option>' : '<option value="">无（库房）</option>';
        if (upper) {
            $.each(treeCache, function (_, item) {
                if (item.level === upper) options += '<option value="' + item.locationId + '">' + escape(item.levelPath + " " + item.locationName) + '</option>';
            });
        }
        $("#editor-parent").html(options).val(selected == null ? "" : selected);
    }

    return {
        init: function () {
            initTable();
            loadTree();
            $("#editor-level").on("change", function () { fillParents(); });
        },
        search: function () { loadTree(); },
        openAdd: function () {
            $("#location-editor")[0].reset();
            $("#location-editor [name='locationId']").val("");
            $("#editor-level").val("WAREHOUSE");
            fillParents();
            $("#editor-title").text("新增库位");
            $("#editor-modal").modal("show");
        },
        edit: function (id) {
            var row = null;
            $.each(treeCache, function (_, item) { if (item.locationId === id) row = item; });
            if (!row) return;
            $("#location-editor")[0].reset();
            $("#location-editor [name='locationId']").val(row.locationId);
            $("#editor-level").val(row.level);
            fillParents(row.parentId == null ? "" : row.parentId);
            $("#editor-code").val(row.locationCode);
            $("#editor-name").val(row.locationName);
            $("#editor-capacity").val(row.capacity == null ? "" : row.capacity);
            $("#editor-sort").val(row.sortOrder);
            $("#editor-status").val(String(row.status));
            $("#editor-remark").val(row.remark);
            $("#editor-title").text("修改库位");
            $("#editor-modal").modal("show");
        },
        save: function () {
            var form = $("#location-editor")[0];
            if (!form.reportValidity()) return;
            var data = $.common.formToJSON("location-editor");
            ["locationId", "parentId", "capacity", "sortNo", "status"].forEach(function (key) {
                if (data[key] === "" || data[key] == null) data[key] = null; else data[key] = Number(data[key]);
            });
            $.ajax({
                url: prefix + "/save",
                type: "POST",
                contentType: "application/json;charset=UTF-8",
                data: JSON.stringify(data),
                success: function (res) {
                    if (res.code !== 0) return $.modal.alertWarning(res.msg || "保存失败");
                    $("#editor-modal").modal("hide");
                    loadTree();
                },
                error: function (xhr) {
                    var message = xhr.responseJSON && xhr.responseJSON.msg;
                    $.modal.alertWarning(message || "保存失败");
                }
            });
        },
        remove: function (id) {
            $.modal.confirm("确认删除该库位？被档案引用或存在下级时不能删除。", function () {
                $.post(prefix + "/remove", { locationId: id }, function (res) {
                    if (res.code !== 0) return $.modal.alertWarning(res.msg || "删除失败");
                    loadTree();
                }).fail(function (xhr) {
                    var message = xhr.responseJSON && xhr.responseJSON.msg;
                    $.modal.alertWarning(message || "删除失败");
                });
            });
        },
        openImport: function () {
            $("#import-file").val("");
            $("#import-result").html("");
            $("#import-modal").modal("show");
        },
        doImport: function () {
            var file = $("#import-file")[0].files[0];
            if (!file) return $.modal.alertWarning("请选择 .xlsx 文件");
            var data = new FormData();
            data.append("file", file);
            $.ajax({
                url: prefix + "/import",
                type: "POST",
                data: data,
                processData: false,
                contentType: false,
                success: function (res) {
                    if (res.code !== 0) return $.modal.alertWarning(res.msg || "导入失败");
                    var html = "";
                    $.each(res.data || [], function (_, line) {
                        html += "<p>" + line.row + " 行 " + escape(line.code) + "：" + (line.result === "OK" ? "成功" : escape(line.reason)) + "</p>";
                    });
                    $("#import-result").html(html || "导入完成");
                    loadTree();
                },
                error: function (xhr) {
                    var message = xhr.responseJSON && xhr.responseJSON.msg;
                    $.modal.alertWarning(message || "导入失败");
                }
            });
        },
        exportRows: function () {
            window.location.href = prefix + "/export?" + $.param($.common.formToJSON("location-form"));
        },
        printRows: function () {
            var html = '<html><head><meta charset="utf-8"><title>库位清单</title><style>body{font-family:SimSun,serif;padding:20px}table{border-collapse:collapse;width:100%}th,td{border:1px solid #333;padding:4px 6px;font-size:12px}h2{text-align:center}</style></head><body><h2>库位清单</h2><table><thead><tr><th>完整路径</th><th>编码</th><th>名称</th><th>层级</th><th>容量</th><th>已挂档案</th><th>介质</th><th>状态</th></tr></thead><tbody>';
            $.each(treeCache, function (_, row) {
                html += '<tr><td>' + escape(row.levelPath) + '</td><td>' + escape(row.locationCode) + '</td><td>' + escape(row.locationName)
                    + '</td><td>' + escape(row.levelName) + '</td><td>' + (row.capacity == null ? "" : row.capacity)
                    + '</td><td>' + row.usedCount + '</td><td>' + escape(row.mediumLabel || "未登记") + '</td><td>' + (row.status === 0 ? "启用" : "停用") + '</td></tr>';
            });
            html += '</tbody></table><p style="text-align:right;font-size:12px">打印时间：' + new Date().toLocaleString() + '</p></body></html>';
            var win = window.open("", "_blank");
            win.document.write(html);
            win.document.close();
            win.print();
        }
    };
})();

$(function () { locationPage.init(); });