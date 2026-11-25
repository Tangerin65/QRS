var borrow = (function () {
    var prefix = ctx + "borrow";
    var flags = window.borrowFlags || {};
    var LABELS = {
        PENDING: "待审批", APPROVED: "已批准", REJECTED: "已驳回",
        WITHDRAWN: "已撤回", LENT: "已借出", RETURNED: "已归还"
    };
    var rowsById = {};

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
                $("#detail-modal").modal("show");
            });
        }
    };
})();

$(function () {
    borrow.init();
});
