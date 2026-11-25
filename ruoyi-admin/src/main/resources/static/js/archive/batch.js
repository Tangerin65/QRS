var batch = (function () {
    var prefix = ctx + "records/batch";

    function escape(value) {
        return $('<div>').text(value == null ? "" : value).html();
    }

    function statusText(status) {
        return { SUCCESS: "成功", PARTIAL: "部分成功", FAILED: "失败", RUNNING: "执行中" }[status] || status;
    }

    function loadStatistics() {
        $.post(ctx + "records/archiving/statistics", {}, function (res) {
            if (res.code !== 0) return;
            var data = res.data || {};
            var html = "";
            html += '<div class="m10-metric"><span>批次总数</span><strong>' + (data.batchTotal || 0) + '</strong></div>';
            html += '<div class="m10-metric"><span>部分失败批次</span><strong>' + (data.batchPartial || 0) + '</strong></div>';
            html += '<div class="m10-metric"><span>已归档</span><strong>' + (data.archived || 0) + '</strong></div>';
            $("#metrics").html(html);
        });
    }

    function rowActions(row) {
        return '<a class="btn btn-info btn-xs" onclick="batch.detail(' + row.batchId + ')"><i class="fa fa-eye"></i>详情</a> '
            + '<a class="btn btn-warning btn-xs" href="' + prefix + '/export/' + row.batchId + '"><i class="fa fa-download"></i>导出</a>';
    }

    function initTable() {
        $.table.init({
            url: prefix + "/list",
            method: "post",
            formId: "batch-form",
            pageSize: 20,
            columns: [
                { field: "batchNo", title: "批次号" },
                { field: "applicantName", title: "申请人" },
                { field: "totalCount", title: "总数" },
                { field: "successCount", title: "成功" },
                { field: "failCount", title: "失败" },
                { field: "batchStatus", title: "状态", formatter: function (value) { return statusText(value); } },
                { field: "locationId", title: "库位编号", formatter: function (value) { return value == null ? "未指定" : escape(value); } },
                { field: "submitTime", title: "提交时间" },
                { field: "finishTime", title: "完成时间" },
                { title: "操作", align: "center", formatter: function (_, row) { return rowActions(row); } }
            ],
            onLoadSuccess: function (data) {
                $("#summary").text("当前页 " + (data.rows || []).length + " 条");
            }
        });
    }

    return {
        init: function () {
            initTable();
            loadStatistics();
        },
        search: function () {
            $.table.search();
        },
        detail: function (batchId) {
            $.get(prefix + "/detail/" + batchId, function (res) {
                if (res.code !== 0) return $.modal.alertWarning(res.msg || "读取失败");
                var item = res.data.batch;
                $("#batch-summary").text("批次 " + item.batchNo + "　申请人 " + item.applicantName
                    + "　共 " + item.totalCount + " 条，成功 " + item.successCount + " 条，失败 " + item.failCount + " 条");
                var rows = "";
                $.each(res.data.items || [], function (_, row) {
                    rows += '<tr><td>' + escape(row.archiveCode) + '</td><td>' + escape(row.title) + '</td><td>'
                        + escape(row.itemResult) + '</td><td>' + escape(row.reason) + '</td></tr>';
                });
                $("#batch-items").html(rows || '<tr><td colspan="4">无明细</td></tr>');
                $("#detail-modal").modal("show");
            });
        },
        printRows: function () {
            var table = $("#bootstrap-table").bootstrapTable("getData");
            var html = '<html><head><meta charset="utf-8"><title>归档批次台账</title><style>body{font-family:SimSun,serif;padding:20px}table{border-collapse:collapse;width:100%}th,td{border:1px solid #333;padding:4px 6px;font-size:12px}h2{text-align:center}</style></head><body><h2>归档批次台账</h2><table><thead><tr><th>批次号</th><th>申请人</th><th>总数</th><th>成功</th><th>失败</th><th>状态</th><th>提交时间</th><th>完成时间</th></tr></thead><tbody>';
            $.each(table, function (_, row) {
                html += '<tr><td>' + escape(row.batchNo) + '</td><td>' + escape(row.applicantName) + '</td><td>' + row.totalCount
                    + '</td><td>' + row.successCount + '</td><td>' + row.failCount + '</td><td>' + statusText(row.batchStatus)
                    + '</td><td>' + escape(row.submitTime) + '</td><td>' + escape(row.finishTime) + '</td></tr>';
            });
            html += '</tbody></table><p style="text-align:right;font-size:12px">打印时间：' + new Date().toLocaleString() + '</p></body></html>';
            var win = window.open("", "_blank");
            win.document.write(html);
            win.document.close();
            win.print();
        }
    };
})();

$(function () { batch.init(); });
