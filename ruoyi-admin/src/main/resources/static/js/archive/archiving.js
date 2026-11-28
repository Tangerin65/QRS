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
        if (row.archiveStatus === "SUBMITTED") {
            html.push('<a class="btn btn-primary btn-xs" onclick="archiving.checkOne(' + row.archiveId + ')"><i class="fa fa-check"></i>校验</a> ');
        }
        html.push('<a class="btn btn-default btn-xs" onclick="archiving.showReport(' + row.archiveId + ')"><i class="fa fa-file-text-o"></i>报告</a>');
        if (row.archiveStatus === "SUBMITTED" && typeof archivingFlags !== "undefined" && archivingFlags.canReturn) {
            html.push(' <a class="btn btn-danger btn-xs" onclick="archiving.openReturn(' + row.archiveId + ',' + row.version + ')"><i class="fa fa-reply"></i>退回</a>');
        }
        return html.join("");
    }

    function statusCell(value, row) {
        if (value === "ARCHIVED") return '<span class="label label-success">已归档</span>';
        if (row.checkState === "FAIL") return '<span class="label label-warning">待补正</span>';
        return "待归档";
    }

    // 阶段2 字段（批次号/介质/电子存储位置/归档人）空值统一「未登记」，与契约 §7 和小程序一致。
    function stage2Cell(value) {
        return value == null || value === "" ? "未登记" : escape(value);
    }

    // 校验失败但未归档的档案标「待补正」：按最新一份报告判定，不改档案状态。
    function decorateCheckStates(rows) {
        var ids = $.map(rows || [], function (row) { return row.archiveId; });
        if (!ids.length) return;
        postJson(prefix + "/checkstates", { archiveIds: ids }, function (res) {
            var states = res.data || {};
            var data = $("#bootstrap-table").bootstrapTable("getData");
            $.each(data || [], function (index, row) {
                var state = states[row.archiveId];
                if (!state) return;
                row.checkState = state;
                $("#bootstrap-table").bootstrapTable("updateRow", { index: index, row: row });
            });
        });
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
                { field: "archiveStatus", title: "状态", formatter: statusCell },
                { field: "batchNo", title: "批次号", formatter: stage2Cell },
                { field: "mediumLabel", title: "介质", formatter: stage2Cell },
                { field: "electronicLocation", title: "电子存储位置", formatter: stage2Cell },
                { field: "archivedBy", title: "归档人", formatter: stage2Cell },
                { field: "createBy", title: "登记人" },
                { field: "createTime", title: "登记时间" },
                { title: "操作", align: "center", formatter: function (_, row) { return rowActions(row); } }
            ],
            onLoadSuccess: function (data) {
                $("#summary").text("当前页 " + (data.rows || []).length + " 条");
                decorateCheckStates(data.rows || []);
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

    function itemList(items) {
        var html = '<ul class="m10-check-list">';
        $.each(items || [], function (_, item) {
            var mark = item.passed ? '<i class="fa fa-check m10-pass"></i>' : '<i class="fa fa-times m10-fail"></i>';
            html += '<li>' + mark + " " + escape(item.label) + "：" + escape(item.detail) + '</li>';
        });
        return html + '</ul>';
    }

    function renderReport(data) {
        var latest = data.latest;
        if (!latest) {
            $("#report-body").html(data.reportEnabled === false
                ? '<p class="text-warning">校验报告未启用，请在数据库执行 sql/setup_qrs.sql 的 M10 段（arc_archive_check_report）后重试。</p>'
                : '<p class="text-muted">该档案还没有校验报告，点「校验」生成第一份报告。</p>');
            $("#report-modal").modal("show");
            return;
        }
        var state = latest.reportResult === "PASS"
            ? '<span class="label label-success">通过</span>'
            : '<span class="label label-danger">不通过</span>';
        var stale = data.stale ? ' <span class="label label-warning">档案已改版，需复检</span>' : "";
        var html = '<h4>报告 #' + escape(latest.reportId) + "（" + escape(latest.reportSource) + "） " + state + stale + '</h4>';
        html += '<p class="text-muted">校验时间 ' + escape(latest.checkTime) + '，校验人 ' + escape(latest.checkedBy)
            + '，校验时档案版本 ' + escape(latest.archiveVersion)
            + (data.archiveVersion != null ? '（当前版本 ' + escape(data.archiveVersion) + '）' : '')
            + '，失败项 ' + escape(latest.failCount) + '/' + escape(latest.itemTotal) + '</p>';
        html += itemList(latest.items);
        var history = data.history || [];
        if (history.length > 1) {
            html += '<h5>历史报告</h5><table class="table table-condensed"><thead><tr><th>报告</th><th>来源</th><th>结论</th><th>档案版本</th><th>时间</th><th>校验人</th></tr></thead><tbody>';
            $.each(history, function (_, row) {
                html += '<tr><td>#' + escape(row.reportId) + '</td><td>' + escape(row.reportSource) + '</td><td>'
                    + (row.reportResult === "PASS" ? "通过" : "不通过") + '</td><td>' + escape(row.archiveVersion)
                    + '</td><td>' + escape(row.checkTime) + '</td><td>' + escape(row.checkedBy) + '</td></tr>';
            });
            html += '</tbody></table>';
        }
        $("#report-body").html(html);
        $("#report-modal").modal("show");
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
            $("#submit-medium").val("PAPER");
            $("#submit-electronic").val("");
            archiving.toggleStorage();
            $("#submit-modal").modal("show");
        },
        // 介质为电子或混合时，电子存储位置必填（后端 STORAGE_UNBOUND 同一口径）。
        toggleStorage: function () {
            var medium = $("#submit-medium").val();
            var required = medium === "ELECTRONIC" || medium === "MIXED";
            $("#submit-electronic-group").toggle(required);
            if (!required) $("#submit-electronic").val("");
        },
        checkSelected: function (fromModal) {
            var ids = fromModal ? selected : selectedIds();
            if (!ids.length) ids = selected;
            if (!ids.length) return $.modal.alertWarning("请先勾选待归档档案");
            postJson(prefix + "/check", { archiveIds: ids }, function (res) {
                if (fromModal) $("#submit-modal").modal("hide");
                renderChecks(res.data || []);
                $.table.refresh();
                loadStatistics();
            });
        },
        checkOne: function (id) {
            postJson(prefix + "/check", { archiveIds: [id] }, function (res) {
                renderChecks(res.data || []);
                $.table.refresh();
            });
        },
        showReport: function (id) {
            $.get(prefix + "/report/" + id, function (res) {
                if (res.code !== 0) return $.modal.alertWarning(res.msg || "读取失败");
                renderReport(res.data || {});
            });
        },
        doSubmit: function () {
            var ids = selected.length ? selected : selectedIds();
            if (!ids.length) return $.modal.alertWarning("请先勾选待归档档案");
            var locationId = $("#submit-location").val() || null;
            var storageMedia = $("#submit-medium").val() || "";
            var electronicLocation = $.trim($("#submit-electronic").val() || "");
            if ((storageMedia === "ELECTRONIC" || storageMedia === "MIXED") && !electronicLocation) {
                return $.modal.alertWarning("存储介质为电子或混合时，必须填写电子存储位置");
            }
            postJson(prefix + "/submit", {
                archiveIds: ids,
                locationId: locationId,
                storageMedia: storageMedia,
                electronicLocation: electronicLocation
            }, function (res) {
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
                var facts = res.data.facts || {};
                var fields = ["archiveCode", "title", "formingDate", "categoryName", "secretName", "retentionName", "summary", "archiveStatus"];
                var labels = ["档号", "题名", "形成日期", "分类", "密级", "保管期限", "摘要", "状态"];
                var lines = fields.map(function (field, index) { return labels[index] + "：" + (item[field] == null ? "" : item[field]); });
                lines.push("归档批次号：" + (facts.batchNo || "未登记"));
                lines.push("存储介质：" + (res.data.mediumLabel || "未登记"));
                lines.push("电子存储位置：" + (facts.electronicLocation || "未登记"));
                lines.push("归档人：" + (facts.archivedBy || "未登记"));
                lines.push("库位：" + (facts.locationPath || "未上架"));
                lines.push("归档日期：" + (facts.archiveDate || "未归档"));
                $("#detail-text").text(lines.join("\n"));
                var checks = "";
                $.each(res.data.items || [], function (_, check) {
                    var mark = check.passed ? '<i class="fa fa-check m10-pass"></i>' : '<i class="fa fa-times m10-fail"></i>';
                    checks += '<li>' + mark + " " + escape(check.label) + "：" + escape(check.detail) + '</li>';
                });
                $("#detail-checks").html(checks);
                var report = res.data.report;
                $("#detail-report").html(report
                    ? '<p class="' + (res.data.reportStale ? "m10-fail" : "text-muted") + '">最近校验：'
                        + escape(report.checkTime) + " " + (report.reportResult === "PASS" ? "通过" : "不通过")
                        + (res.data.reportStale ? "（档案已改版，需复检）" : "") + '</p>'
                    : '<p class="text-muted">尚未校验。</p>');
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
