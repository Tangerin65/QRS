var borrow = (function () {
    var prefix = ctx + "borrow";
    var flags = window.borrowFlags || {};
    var LABELS = {
        PENDING: "待审批", APPROVED: "已批准", REJECTED: "已驳回",
        WITHDRAWN: "已撤回", LENT: "已借出", RETURNED: "已归还"
    };
    var rowsById = {};
    var RENEW_LABELS = { PENDING: "待审批", APPROVED: "已批准", REJECTED: "已驳回", WITHDRAWN: "已关闭" };
    var AUDIT_LABELS = {
        BORROW_APPLY: "申请", BORROW_APPROVE: "审批通过", BORROW_REJECT: "审批驳回",
        BORROW_LEND: "借出", BORROW_RETURN: "归还", BORROW_WITHDRAW: "撤回",
        BORROW_RENEW: "续借申请", BORROW_RENEW_APPROVE: "续借批准", BORROW_RENEW_REJECT: "续借驳回",
        BORROW_IMPORT: "导入"
    };

    function escape(value) {
        return $('<div>').text(value == null ? "" : value).html();
    }

    function dateTime(value) {
        return (value == null ? "" : String(value)).replace("T", " ").replace(/\.\d+$/, "");
    }

    function post(url, data, done) {
        $.post(prefix + url, data, function (res) {
            if (res.code === 0) {
                if (done) done(res);
            } else {
                $.modal.alertWarning(res.msg || "操作失败");
            }
        });
    }

    function rowActions(row) {
        var id = row.borrowId;
        rowsById[id] = row;
        var buttons = ['<a class="btn btn-info btn-xs" onclick="borrow.detail(' + id + ')">详情</a>'];
        if (row.borrowStatus === "PENDING" && flags.canApprove) {
            buttons.push('<a class="btn btn-primary btn-xs" onclick="borrow.openApprove(' + id + ')">审批</a>');
        }
        if (row.borrowStatus === "PENDING" && flags.canApply) {
            buttons.push('<a class="btn btn-warning btn-xs" onclick="borrow.withdraw(' + id + ')">撤回</a>');
        }
        if (row.borrowStatus === "APPROVED" && flags.canLend) {
            buttons.push('<a class="btn btn-success btn-xs" onclick="borrow.lend(' + id + ')">登记借出</a>');
        }
        if (row.borrowStatus === "LENT" && flags.canReturn) {
            buttons.push('<a class="btn btn-danger btn-xs" onclick="borrow.giveBack(' + id + ')">登记归还</a>');
        }
        if (row.borrowStatus === "LENT" && flags.canApply) {
            buttons.push('<a class="btn btn-primary btn-xs" onclick="borrow.openRenew(' + id + ')">续借</a>');
        }
        return buttons.join(' ');
    }

    function queryParams() {
        return $.common.formToJSON("borrow-form");
    }

    function loadStatistics() {
        $.post(prefix + "/statistics", queryParams(), function (res) {
            if (res.code !== 0) return;
            var d = res.data || {};
            $("#statistics").text("待审批 " + (d.pending || 0) + "　已批准 " + (d.approved || 0) +
                "　已借出 " + (d.lent || 0) + "　已归还 " + (d.returned || 0) +
                "　逾期 " + (d.overdue || 0) + "　合计 " + (d.total || 0));
        });
    }

    function loadDepts() {
        $.get(prefix + "/depts", function (res) {
            if (res.code !== 0) return;
            var html = '<option value="">全部部门</option>';
            (res.data || []).forEach(function (item) {
                html += '<option value="' + item.deptId + '">' + escape(item.deptName) + '</option>';
            });
            $("#filter-dept").html(html);
        });
    }

    function initTable() {
        $.table.init({
            url: prefix + "/list",
            method: "post",
            formId: "borrow-form",
            sortName: "createTime",
            sortOrder: "desc",
            pageSize: 20,
            columns: [
                { field: "borrowNo", title: "借阅单号", sortable: true },
                { field: "archiveCode", title: "档号" },
                { field: "archiveTitle", title: "档案题名", sortable: true },
                { field: "applicantName", title: "申请人", sortable: true },
                { field: "startDate", title: "借期开始", sortable: true },
                { field: "endDate", title: "应归还", sortable: true },
                {
                    field: "borrowStatus", title: "状态", sortable: true,
                    formatter: function (value) { return LABELS[value] || escape(value); }
                },
                {
                    field: "overdue", title: "逾期",
                    formatter: function (value) { return value ? '<span class="text-danger">已逾期</span>' : ''; }
                },
                { title: "操作", align: "center", formatter: function (_, row) { return rowActions(row); } }
            ],
            onLoadSuccess: function (data) {
                $("#summary").text("当前页 " + (data.rows || []).length + " 条");
            }
        });
    }

    function initDates() {
        $(".borrow-date").datetimepicker({
            language: "zh-cn",
            format: "yyyy-mm-dd",
            minView: "month",
            autoclose: true,
            todayHighlight: true
        });
    }

    function loadArchives(keyword) {
        $.get(prefix + "/archives", { keyword: keyword || "" }, function (res) {
            if (res.code !== 0) return $.modal.alertWarning(res.msg || "档案加载失败");
            var html = '<option value="">请选择档案</option>';
            (res.data || []).forEach(function (item) {
                html += '<option value="' + item.archive_id + '">' + escape(item.archive_code + " " + item.title) + '</option>';
            });
            $("#apply-archive").html(html);
            if (!(res.data || []).length) $.modal.alertWarning("暂无可借阅档案：需要已提交或已归档的档案");
        });
    }

    return {
        init: function () {
            initDates();
            initTable();
            loadDepts();
            loadStatistics();
        },
        search: function () {
            $.table.search();
            loadStatistics();
        },
        exportExcel: function () {
            window.location.href = prefix + "/export?" + $.param(queryParams());
        },
        print: function () {
            window.open(prefix + "/print?" + $.param(queryParams()));
        },
        openChart: function () {
            $("#chart-modal").modal("show");
            $.post(prefix + "/statistics", queryParams(), function (res) {
                if (res.code !== 0) return;
                var d = res.data || {};
                var chart = echarts.init(document.getElementById("list-chart"));
                chart.setOption({
                    tooltip: { trigger: "item" },
                    legend: { bottom: 0 },
                    series: [{
                        type: "pie", radius: ["40%", "65%"],
                        data: [
                            { name: "待审批", value: d.pending || 0 },
                            { name: "已批准", value: d.approved || 0 },
                            { name: "已借出", value: d.lent || 0 },
                            { name: "已归还", value: d.returned || 0 },
                            { name: "已驳回", value: d.rejected || 0 },
                            { name: "已撤回", value: d.withdrawn || 0 }
                        ].filter(function (item) { return item.value > 0; })
                    }]
                }, true);
            });
        },
        openImport: function () {
            $("#import-file").val("");
            $("#import-rows").html("");
            $("#import-modal").modal("show");
        },
        doImport: function () {
            var file = $("#import-file")[0].files[0];
            if (!file) return $.modal.alertWarning("请选择要导入的 Excel 文件");
            var form = new FormData();
            form.append("file", file);
            $.ajax({
                url: prefix + "/import",
                type: "POST",
                data: form,
                processData: false,
                contentType: false,
                success: function (res) {
                    if (res.code !== 0) return $.modal.alertWarning(res.msg || "导入失败");
                    var rows = res.data || [];
                    $("#import-rows").html(rows.map(function (r) {
                        var text = r.ok ? "<span class='text-success'>" + escape(r.message) + "</span>"
                            : "<span class='text-danger'>" + escape(r.message) + "</span>";
                        return "<tr><td>" + r.row + "</td><td>" + text + "</td></tr>";
                    }).join(""));
                    var ok = rows.filter(function (r) { return r.ok; }).length;
                    $.modal.msgSuccess("导入完成：成功 " + ok + " 条，失败 " + (rows.length - ok) + " 条");
                    $.table.refresh();
                    loadStatistics();
                }
            });
        },
        openApply: function () {
            $("#apply-keyword").val("");
            $("#apply-reason").val("");
            $("#apply-start").val("");
            $("#apply-end").val("");
            $("#apply-archive").html('<option value="">请选择档案</option>');
            $("#apply-modal").modal("show");
            loadArchives("");
        },
        searchArchives: function () {
            loadArchives($("#apply-keyword").val());
        },
        submitApply: function () {
            var data = {
                archiveId: $("#apply-archive").val(),
                reason: $.trim($("#apply-reason").val()),
                startDate: $("#apply-start").val(),
                endDate: $("#apply-end").val()
            };
            if (!data.archiveId) return $.modal.alertWarning("请选择档案");
            if (!data.reason) return $.modal.alertWarning("请填写借阅事由");
            if (!data.startDate || !data.endDate) return $.modal.alertWarning("请填写借期起止日期");
            $.ajax({
                url: prefix + "/apply",
                type: "POST",
                contentType: "application/json;charset=UTF-8",
                data: JSON.stringify(data),
                success: function (res) {
                    if (res.code === 0) {
                        $("#apply-modal").modal("hide");
                        $.modal.msgSuccess("已提交，等待审批");
                        $.table.refresh();
                        loadStatistics();
                    } else {
                        $.modal.alertWarning(res.msg || "提交失败");
                    }
                }
            });
        },
        openApprove: function (id) {
            var row = rowsById[id] || {};
            $("#approve-borrow-id").val(id);
            $("#approve-opinion").val("");
            $("#approve-summary").text("档案：" + (row.archiveCode || "") + " " + (row.archiveTitle || ""));
            $("#approve-modal").modal("show");
        },
        decide: function (result) {
            var id = $("#approve-borrow-id").val();
            var opinion = $.trim($("#approve-opinion").val());
            if (result === "REJECTED" && !opinion) return $.modal.alertWarning("驳回必须填写意见");
            post("/approve", { borrowId: id, result: result, opinion: opinion }, function () {
                $("#approve-modal").modal("hide");
                $.modal.msgSuccess(result === "APPROVED" ? "已批准" : "已驳回");
                $.table.refresh();
                loadStatistics();
            });
        },
        lend: function (id) {
            $.modal.confirm("确认登记借出？", function () {
                post("/lend", { borrowId: id }, function () { $.modal.msgSuccess("已登记借出"); $.table.refresh(); loadStatistics(); });
            });
        },
        giveBack: function (id) {
            $.modal.confirm("确认登记归还？", function () {
                post("/return", { borrowId: id }, function () { $.modal.msgSuccess("已登记归还"); $.table.refresh(); loadStatistics(); });
            });
        },
        withdraw: function (id) {
            $.modal.confirm("确认撤回该借阅申请？", function () {
                post("/withdraw", { borrowId: id }, function () { $.modal.msgSuccess("已撤回"); $.table.refresh(); loadStatistics(); });
            });
        },
        openRenew: function (id) {
            var row = rowsById[id] || {};
            $("#renew-borrow-id").val(id);
            $("#renew-date").val("");
            $("#renew-reason").val("");
            $("#renew-summary").text("借阅单 " + (row.borrowNo || "") + "，当前应归还日 " + (row.endDate || ""));
            $("#renew-modal").modal("show");
        },
        submitRenew: function () {
            var data = {
                borrowId: $("#renew-borrow-id").val(),
                requestedEndDate: $("#renew-date").val(),
                reason: $.trim($("#renew-reason").val())
            };
            if (!data.requestedEndDate) return $.modal.alertWarning("请选择新的应归还日");
            if (!data.reason) return $.modal.alertWarning("请填写续借原因");
            post("/renew", data, function () {
                $("#renew-modal").modal("hide");
                $.modal.msgSuccess("续借申请已提交");
                $.table.refresh();
                loadStatistics();
            });
        },
        openRenewals: function () {
            $("#renewals-rows").html('<tr><td colspan="6" class="text-muted">加载中…</td></tr>');
            $("#renewals-modal").modal("show");
            $.get(prefix + "/renew/pending", function (res) {
                if (res.code !== 0) return $("#renewals-rows").html('<tr><td colspan="6" class="text-danger">' + escape(res.msg) + '</td></tr>');
                var rows = res.data || [];
                if (!rows.length) return $("#renewals-rows").html('<tr><td colspan="6" class="text-muted">暂无待审批续借</td></tr>');
                $("#renewals-rows").html(rows.map(function (r) {
                    return "<tr><td>" + escape(r.borrowNo) + "</td><td>" + escape(r.archiveTitle) + "</td><td>" +
                        escape(r.oldEndDate) + "</td><td>" + escape(r.requestedEndDate) + "</td><td>" + escape(r.reason) + "</td><td>" +
                        '<a class="btn btn-primary btn-xs" onclick="borrow.decideRenew(' + r.renewId + ',\'APPROVED\')">批准</a> ' +
                        '<a class="btn btn-danger btn-xs" onclick="borrow.decideRenew(' + r.renewId + ',\'REJECTED\')">驳回</a></td></tr>';
                }).join(""));
            });
        },
        decideRenew: function (id, result) {
            var opinion = "";
            if (result === "REJECTED") {
                opinion = window.prompt("请输入驳回意见");
                if (opinion === null) return;
                if (!$.trim(opinion)) return $.modal.alertWarning("驳回必须填写意见");
            }
            post("/renew/approve", { renewId: id, result: result, opinion: opinion }, function () {
                $.modal.msgSuccess(result === "APPROVED" ? "已批准续借" : "已驳回续借");
                borrow.openRenewals();
                $.table.refresh();
                loadStatistics();
            });
        },
        detail: function (id) {
            $.get(prefix + "/detail/" + id, function (res) {
                if (res.code !== 0) return $.modal.alertWarning(res.msg || "无法读取借阅单");
                var d = res.data || {};
                var lines = [
                    "借阅单号：" + (d.borrowNo || ""),
                    "档号：" + (d.archiveCode || "") + "　题名：" + (d.archiveTitle || ""),
                    "申请人：" + (d.applicantName || ""),
                    "借期：" + (d.startDate || "") + " 至 " + (d.endDate || ""),
                    "状态：" + (LABELS[d.borrowStatus] || d.borrowStatus || ""),
                    "事由：" + (d.reason || ""),
                    "借出时间：" + dateTime(d.lendAt),
                    "归还时间：" + dateTime(d.returnAt)
                ];
                $("#borrow-detail").text(lines.join("\n"));
                var rows = ((d.params || {}).approvals) || [];
                $("#approval-rows").html(rows.map(function (a) {
                    return "<tr><td>" + escape(a.approverName) + "</td><td>" +
                        (a.result === "APPROVED" ? "批准" : "驳回") + "</td><td>" +
                        escape(a.opinion) + "</td><td>" + escape(dateTime(a.handledAt)) + "</td></tr>";
                }).join(""));
                var renews = ((d.params || {}).renews) || [];
                $("#renew-rows").html(renews.length ? renews.map(function (r) {
                    return "<tr><td>" + escape(r.oldEndDate) + "</td><td>" + escape(r.requestedEndDate) + "</td><td>" +
                        escape(r.reason) + "</td><td>" + (RENEW_LABELS[r.renewStatus] || escape(r.renewStatus)) + "</td><td>" +
                        escape(r.approverName) + "</td><td>" + escape(dateTime(r.handledAt)) + "</td></tr>";
                }).join("") : '<tr><td colspan="6" class="text-muted">暂无续借记录</td></tr>');
                var audits = ((d.params || {}).audits) || [];
                $("#audit-rows").html(audits.length ? audits.map(function (a) {
                    return "<tr><td>" + (AUDIT_LABELS[a.event_type] || escape(a.event_type)) + "</td><td>" +
                        escape(a.actor_name) + "</td><td>" + escape(dateTime(a.event_time)) + "</td><td>" +
                        escape(a.detail) + "</td></tr>";
                }).join("") : '<tr><td colspan="4" class="text-muted">暂无操作日志</td></tr>');
                $("#detail-modal").modal("show");
            });
        }
    };
})();

$(function () {
    borrow.init();
});
