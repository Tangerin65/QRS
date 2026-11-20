var archiving = (function () {
    var prefix = ctx + "records/archiving";
    var selected = [];

    function escape(value) {
        return $('<div>').text(value == null ? "" : value).html();
    }

    function postJson(url, data, done) {
        $.ajax({
            url: url,
            type: "POST",
            contentType: "application/json;charset=UTF-8",
            data: JSON.stringify(data),
            success: function (res) {
                if (res.code === 0) done(res);
                else $.modal.alertWarning(res.msg || "操作失败");
            },
            error: function (xhr) {
                var message = xhr.responseJSON && xhr.responseJSON.msg;
                $.modal.alertWarning(message || "请求失败");
            }
        });
    }

    function loadCategories() {
        $.get(ctx + "records/categories", function (res) {
            if (res.code !== 0) return;
            var options = '<option value="">全部分类</option>';
            $.each(res.data || [], function (_, item) {
                options += '<option value="' + item.id + '">' + escape(item.path + " [" + item.code + "]") + '</option>';
            });
            $("#filter-category").html(options);
        });
    }

    function loadLocations() {
        $.get(ctx + "records/location/choices", function (res) {
            if (res.code !== 0) return;
            var options = '<option value="">不指定库位</option>';
            $.each(res.data || [], function (_, item) {
                options += '<option value="' + item.locationId + '">' + escape(item.levelPath + " " + item.locationName) + '</option>';
            });
            $("#submit-location").html(options);
        });
    }

    function loadStatistics() {
        $.post(prefix + "/statistics", $.common.formToJSON("archiving-form"), function (res) {
            if (res.code !== 0) return;
            var data = res.data || {};
            var html = "";
            html += '<div class="m10-metric"><span>待归档</span><strong>' + (data.pending || 0) + '</strong></div>';
            html += '<div class="m10-metric"><span>已归档</span><strong>' + (data.archived || 0) + '</strong></div>';
            html += '<div class="m10-metric"><span>本月归档</span><strong>' + (data.monthArchived || 0) + '</strong></div>';
            html += '<div class="m10-metric"><span>归档批次</span><strong>' + (data.batchTotal || 0) + '</strong></div>';
            html += '<div class="m10-metric"><span>部分失败批次</span><strong>' + (data.batchPartial || 0) + '</strong></div>';
            $("#metrics").html(html);
        });
    }

    function rowActions(row) {
        var html = [];
        html.push('<a class="btn btn-info btn-xs" onclick="archiving.detail(' + row.archiveId + ')"><i class="fa fa-eye"></i>详情</a> ');
        html.push('<a class="btn btn-primary btn-xs" onclick="archiving.checkOne(' + row.archiveId + ')"><i class="fa fa-check"></i>校验</a>');
        if (typeof archivingFlags !== "undefined" && archivingFlags.canReturn) html.push(' <a class="btn btn-danger btn-xs" onclick="archiving.openReturn(' + row.archiveId + ',' + row.version + ')"><i class="fa fa-reply"></i>退回</a>');
        return html.join("");
    }

    function initTable() {
        $.table.init({
            url: prefix + "/list",
            method: "post",
            formId: "archiving-form",
            sortName: "archiveCode",
            sortOrder: "asc",
            pageSize: 20,
            columns: [
                { checkbox: true },
                { field: "archiveCode", title: "档号", sortable: true },
                { field: "title", title: "题名", sortable: true },
                { field: "formingDate", title: "形成日期", sortable: true },
                { field: "categoryName", title: "分类" },
                { field: "secretName", title: "密级", formatter: function (value) { return escape(value); } },
                { field: "retentionName", title: "保管期限" },
                { field: "archiveStatus", title: "状态", formatter: function () { return "待归档"; } },
                { field: "createBy", title: "登记人" },
                { field: "createTime", title: "登记时间" },
                { title: "操作", align: "center", formatter: function (_, row) { return rowActions(row); } }
            ],
            onLoadSuccess: function (data) {
                $("#summary").text("当前页 " + (data.rows || []).length + " 条");
            }
        });
    }

    function renderChecks(results) {
        var html = "";
        $.each(results, function (_, result) {
            var state = result.pass ? '<span class="label label-success">通过</span>' : '<span class="label label-danger">不通过</span>';
            html += '<h4>' + escape(result.archiveCode) + " " + escape(result.title) + " " + state + '</h4><ul class="m10-check-list">';
            $.each(result.items || [], function (_, item) {
                var mark = item.passed ? '<i class="fa fa-check m10-pass"></i>' : '<i class="fa fa-times m10-fail"></i>';
                html += '<li>' + mark + " " + escape(item.label) + "：" + escape(item.detail) + '</li>';
            });
            html += '</ul><hr>';
        });
        $("#check-body").html(html || '<p class="text-muted">没有可校验的数据</p>');
        $("#check-modal").modal("show");
    }

    function selectedIds() {
        return $.map($("#bootstrap-table").bootstrapTable("getSelections"), function (row) { return row.archiveId; });
    }

    return {
        init: function () {
            loadCategories();
            loadLocations();
            initTable();
            loadStatistics();
        },
        search: function () {
            $.table.search();
            loadStatistics();
        },
        openSubmit: function () {
            selected = selectedIds();
            if (!selected.length) return $.modal.alertWarning("请先勾选待归档档案");
            $("#submit-count").text("已选择 " + selected.length + " 条待归档档案");
            $("#submit-modal").modal("show");
        },
        checkSelected: function (fromModal) {
            var ids = fromModal ? selected : selectedIds();
            if (!ids.length) ids = selected;
            if (!ids.length) return $.modal.alertWarning("请先勾选待归档档案");
            postJson(prefix + "/check", { archiveIds: ids }, function (res) {
                if (fromModal) $("#submit-modal").modal("hide");
                renderChecks(res.data || []);
            });
        },
        checkOne: function (id) {
            postJson(prefix + "/check", { archiveIds: [id] }, function (res) {
                renderChecks(res.data || []);
            });
        },
        doSubmit: function () {
            var ids = selected.length ? selected : selectedIds();
            if (!ids.length) return $.modal.alertWarning("请先勾选待归档档案");
            var locationId = $("#submit-location").val() || null;
            postJson(prefix + "/submit", { archiveIds: ids, locationId: locationId }, function (res) {
                var data = res.data || {};
                $("#submit-modal").modal("hide");
                selected = [];
                var html = '<p>批次 ' + escape(data.batchNo) + '：共 ' + data.total + ' 条，成功 ' + data.success + ' 条，失败 ' + data.fail + ' 条</p>';
                $.each(data.items || [], function (_, item) {
                    if (item.result !== "OK") html += '<p class="m10-fail">' + escape(item.archiveCode || item.archiveId) + "：" + escape(item.reason) + '</p>';
                });
                $("#check-body").html(html);
                $("#check-modal").modal("show");
                $.table.refresh();
                loadStatistics();
            });
        },
        openReturn: function (id, version) {
            $("#return-id").val(id);
            $("#return-version").val(version);
            $("#return-reason").val("");
            $("#return-modal").modal("show");
        },
        doReturn: function () {
            var reason = $("#return-reason").val();
            if (!reason) return $.modal.alertWarning("请填写退回原因");
            postJson(prefix + "/return", {
                archiveId: Number($("#return-id").val()),
                version: Number($("#return-version").val()),
                reason: reason
            }, function () {
                $("#return-modal").modal("hide");
                $.table.refresh();
                loadStatistics();
            });
        },
        detail: function (id) {
            $.get(prefix + "/detail/" + id, function (res) {
                if (res.code !== 0) return $.modal.alertWarning(res.msg || "读取失败");
                var item = res.data.archive;
                var fields = ["archiveCode", "title", "formingDate", "categoryName", "secretName", "retentionName", "summary", "archiveStatus"];
                var labels = ["档号", "题名", "形成日期", "分类", "密级", "保管期限", "摘要", "状态"];
                var lines = fields.map(function (field, index) { return labels[index] + "：" + (item[field] == null ? "" : item[field]); });
                $("#detail-text").text(lines.join("\n"));
                var checks = "";
                $.each(res.data.items || [], function (_, check) {
                    var mark = check.passed ? '<i class="fa fa-check m10-pass"></i>' : '<i class="fa fa-times m10-fail"></i>';
                    checks += '<li>' + mark + " " + escape(check.label) + "：" + escape(check.detail) + '</li>';
                });
                $("#detail-checks").html(checks);
                $("#detail-modal").modal("show");
            });
        },
        exportRows: function () {
            window.location.href = prefix + "/export?" + $.param($.common.formToJSON("archiving-form"));
        },
        printRows: function () {
            var table = $("#bootstrap-table").bootstrapTable("getData");
            var html = '<html><head><meta charset="utf-8"><title>待归档档案</title><style>body{font-family:SimSun,serif;padding:20px}table{border-collapse:collapse;width:100%}th,td{border:1px solid #333;padding:4px 6px;font-size:12px}h2{text-align:center}</style></head><body><h2>待归档档案清单</h2><table><thead><tr><th>档号</th><th>题名</th><th>形成日期</th><th>分类</th><th>密级</th><th>保管期限</th><th>登记人</th></tr></thead><tbody>';
            $.each(table, function (_, row) {
                html += '<tr><td>' + escape(row.archiveCode) + '</td><td>' + escape(row.title) + '</td><td>' + escape(row.formingDate) + '</td><td>' + escape(row.categoryName) + '</td><td>' + escape(row.secretName) + '</td><td>' + escape(row.retentionName) + '</td><td>' + escape(row.createBy) + '</td></tr>';
            });
            html += '</tbody></table><p style="text-align:right;font-size:12px">打印时间：' + new Date().toLocaleString() + '</p></body></html>';
            var win = window.open("", "_blank");
            win.document.write(html);
            win.document.close();
            win.print();
        }
    };
})();

$(function () { archiving.init(); });
