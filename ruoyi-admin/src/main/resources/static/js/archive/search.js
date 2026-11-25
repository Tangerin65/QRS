$(function () {
    var prefix = ctx + "records/search";
    var $form = $("#search-form");
    var $table = $("#bootstrap-table");
    var activeQuery = {};
    var plans = [];

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

    function searchExcerpt(value, row) {
        if (!row.matchedAttachment || !row.attachmentExcerpt) return summary(value);
        return $('<div>').append(
            $('<div class="text-muted">').text(row.matchedAttachment),
            $('<div>').text(row.attachmentExcerpt)
        ).html();
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
        { field: "summary", title: "摘要 / 附件命中", formatter: searchExcerpt }
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
        window.location.href = prefix + "/export?" + $.param(currentQuery());
    });

    function currentQuery() {
        var tableOptions = $table.bootstrapTable("getOptions");
        return $.extend({}, activeQuery, { orderByColumn: tableOptions.sortName, isAsc: tableOptions.sortOrder });
    }

    function withResult(done) {
        return function (res) {
            if (res.code === 0) done(res.data);
            else $.modal.alertWarning(res.msg || "操作未完成");
        };
    }

    $("#print-search").on("click", function () {
        window.open(prefix + "/print?" + $.param(currentQuery()), "_blank", "noopener");
    });
    $("#search-statistics").on("click", function () {
        $.post(prefix + "/statistics", activeQuery, withResult(function (rows) {
            var $rows = $("#statistics-rows").empty();
            var total = 0;
            rows.forEach(function (row) {
                total += Number(row.total);
                $("<tr>").append($("<td>").text(status(row.status)), $("<td>").text(row.total)).appendTo($rows);
            });
            $("<tr>").append($("<th>").text("合计"), $("<td>").text(total)).appendTo($rows);
            $("#statistics-modal").modal("show");
        }));
    });

    var $plans = $("#plan-select");
    var $planName = $("#plan-name");
    function selectedPlan() {
        return plans.find(function (plan) { return plan.id === $plans.val(); });
    }
    function loadPlans() {
        $.getJSON(prefix + "/plans", withResult(function (rows) {
            plans = rows;
            $plans.empty().append($("<option>").val("").text("请选择方案"));
            rows.forEach(function (plan) {
                $plans.append($("<option>").val(plan.id).text(plan.name));
            });
            $planName.val("");
            $plans.trigger("change");
            $("#search-plans").modal("show");
        }));
    }
    $("#open-plans").on("click", loadPlans);
    $plans.on("change", function () {
        var plan = selectedPlan();
        $planName.val(plan ? plan.name : "");
        $("#apply-plan, #update-plan, #disable-plan").prop("disabled", !plan);
    });
    $("#apply-plan").on("click", function () {
        var plan = selectedPlan();
        if (!plan) return;
        var unavailable = $form.find("select").toArray().some(function (field) {
            var value = plan.filters[field.name];
            return value && !Array.from(field.options).some(function (option) { return option.value === value; });
        });
        if (unavailable) return $.modal.alertWarning("方案中的筛选项已不可用，请更新方案");
        $form[0].reset();
        $form.find("[name]").each(function () {
            $(this).val(plan.filters[this.name] || "");
        });
        activeQuery = $.common.formToJSON("search-form");
        $table.bootstrapTable("refreshOptions", {
            pageNumber: 1,
            sortName: plan.filters.orderByColumn || "createTime",
            sortOrder: plan.filters.isAsc === "asc" ? "asc" : "desc"
        });
        $("#search-plans").modal("hide");
    });
    function savePlan(id) {
        var name = $planName.val().trim();
        if (!name) return $.modal.alertWarning("请输入方案名称");
        $.post(prefix + "/plans", $.extend(currentQuery(), { name: name, planId: id }), withResult(loadPlans));
    }
    $("#save-plan").on("click", function () { savePlan(""); });
    $("#update-plan").on("click", function () { savePlan($plans.val()); });
    $("#disable-plan").on("click", function () {
        var id = $plans.val();
        $.modal.confirm("停用此检索方案？", function () {
            $.post(prefix + "/plans/" + encodeURIComponent(id) + "/disable", withResult(loadPlans));
        });
    });
});
