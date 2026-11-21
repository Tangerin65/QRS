$(function () {
    var prefix = ctx + "records/search";
    var $form = $("#search-form");
    var $table = $("#bootstrap-table");
    var activeQuery = {};

    function display(value) {
        return $("<span>").text(value == null || value === "" ? "—" : value).html();
    }

    function status(value) {
        if (value === "DRAFT") return "草稿";
        if (value === "SUBMITTED") return "已提交";
        return value;
    }

    function summary(value) {
        return display(value && value.length > 90 ? value.slice(0, 90) + "…" : value);
    }

    function showDetail(event, value, row) {
        $.getJSON(ctx + "records/detail/" + encodeURIComponent(row.archiveId), function (res) {
            if (res.code !== 0) {
                $.modal.alertWarning(res.msg || "无法查看此档案");
                return;
            }
            var record = res.data;
            var fields = [
                ["档号", record.archiveCode], ["题名", record.title],
                ["形成日期", record.formingDate], ["分类", record.categoryName],
                ["密级", record.secretName], ["保管期限", record.retentionName],
                ["状态", status(record.archiveStatus)], ["登记人", record.createBy],
                ["摘要", record.summary]
            ];
            var $fields = $("#search-detail-fields").empty();
            fields.forEach(function (field) {
                $("<tr>").append($("<th>").text(field[0]), $("<td>").text(field[1] || "—")).appendTo($fields);
            });
            $("#search-detail").modal("show");
        });
    }

    var columns = [
        { field: "archiveCode", title: "档号", sortable: true, formatter: display },
        { field: "title", title: "题名", sortable: true, formatter: display },
        { field: "categoryName", title: "分类", formatter: display },
        { field: "formingDate", title: "形成日期", sortable: true, formatter: display },
        { field: "secretName", title: "密级", formatter: display },
        { field: "archiveStatus", title: "状态", formatter: function (value) { return display(status(value)); } },
        { field: "createBy", title: "登记人", formatter: display },
        { field: "summary", title: "摘要", formatter: summary }
    ];
    if (canViewSearchDetail) {
        columns.push({
            title: "操作",
            align: "center",
            formatter: function () {
                return '<button type="button" class="btn btn-info btn-xs view-detail"><i class="fa fa-eye"></i> 详情</button>';
            },
            events: { "click .view-detail": showDetail }
        });
    }

    $.table.init({
        url: prefix + "/list",
        method: "post",
        sortName: "createTime",
        sortOrder: "desc",
        pageSize: 20,
        queryParams: function (params) {
            return $.extend({
                pageSize: params.limit,
                pageNum: params.offset / params.limit + 1,
                orderByColumn: params.sort,
                isAsc: params.order
            }, activeQuery);
        },
        columns: columns
    });

    function search() {
        var values = $.common.formToJSON("search-form");
        if (values.dateFrom && values.dateTo && values.dateFrom > values.dateTo) {
            $.modal.alertWarning("起始日期不能晚于结束日期");
            return;
        }
        activeQuery = values;
        $table.bootstrapTable("refresh", { pageNumber: 1 });
    }

    $form.on("submit", function (event) {
        event.preventDefault();
        search();
    });
    $("#reset-search").on("click", function () {
        $form[0].reset();
        search();
    });
    $("#export-search").on("click", function () {
        var tableOptions = $table.bootstrapTable("getOptions");
        var query = $.extend({}, activeQuery, { orderByColumn: tableOptions.sortName, isAsc: tableOptions.sortOrder });
        window.location.href = prefix + "/export?" + $.param(query);
    });
});
