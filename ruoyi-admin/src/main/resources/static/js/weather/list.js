var weather = (function () {
    var prefix = ctx + "weather";

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

    function queryParams() {
        return $.common.formToJSON("weather-form");
    }

    function loadCities() {
        $.get(prefix + "/cities", function (res) {
            if (res.code !== 0) return;
            var filter = '<option value="">全部城市</option>';
            var record = '';
            (res.data || []).forEach(function (city) {
                filter += '<option value="' + city.cityCode + '">' + escape(city.cityName) + '</option>';
                record += '<option value="' + city.cityCode + '">' + escape(city.cityName) + '（' + city.cityCode + '）</option>';
            });
            $("#filter-city").html(filter);
            $("#record-city").html(record);
        });
    }

    function loadSummary() {
        $.post(prefix + "/statistics", queryParams(), function (res) {
            if (res.code !== 0) return;
            var d = res.data || {};
            $("#summary").text("合计 " + (d.total || 0) + " 条　城市 " + (d.cityCount || 0) +
                " 个　未来预报 " + (d.upcoming || 0) + " 条　已撤销 " + (d.revoked || 0) + " 条");
        });
    }

    function initTable() {
        $.table.init({
            url: prefix + "/list",
            method: "post",
            formId: "weather-form",
            sortName: "forecastTime",
            sortOrder: "desc",
            pageSize: 10,
            columns: [
                { field: "cityCode", title: "城市", sortable: true,
                  formatter: function (v, row) { return escape(row.cityName || v); } },
                { field: "forecastTime", title: "预报时间", sortable: true,
                  formatter: function (v) { return dateTime(v); } },
                { field: "weatherText", title: "天气", sortable: true,
                  formatter: function (v) { return escape(v); } },
                { field: "tempMin", title: "最低温(℃)", sortable: true },
                { field: "tempMax", title: "最高温(℃)", sortable: true },
                { field: "humidity", title: "湿度(%)" },
                { field: "windLevel", title: "风力" },
                { field: "dataSource", title: "来源" },
                { field: "collectTime", title: "采集时间", sortable: true,
                  formatter: function (v) { return dateTime(v); } },
                { field: "revoked", title: "状态", formatter: function (v) {
                    return v === 1 ? '<span class="label label-default">已撤销</span>'
                        : '<span class="label label-success">有效</span>';
                } },
                { title: "操作", align: "center", formatter: function (v, row) {
                    var flags = window.weatherFlags || {};
                    var buttons = ['<a class="btn btn-info btn-xs" onclick="weather.detail(' + row.forecastId + ')">详情</a>'];
                    if (flags.manage && row.revoked !== 1) {
                        buttons.push('<a class="btn btn-primary btn-xs" onclick="weather.openEdit(' + row.forecastId + ')">修改</a>');
                        buttons.push('<a class="btn btn-danger btn-xs" onclick="weather.revoke(' + row.forecastId + ')">撤销</a>');
                    }
                    return buttons.join(" ");
                } }
            ]
        });
    }

    function openAdd() {
        $("#record-id").val("");
        $("#record-version").val("");
        $("#record-time").val("");
        $("#record-text").val("");
        $("#record-min").val("");
        $("#record-max").val("");
        $("#record-humidity").val("");
        $("#record-wind").val("");
        $("#record-source").val("manual");
        $("#record-modal .modal-title").text("新增气象记录");
        $("#record-modal").modal("show");
    }

    function openEdit(id) {
        $.get(prefix + "/detail/" + id, function (res) {
            if (res.code !== 0) return $.modal.alertWarning(res.msg);
            var r = res.data;
            $("#record-id").val(r.forecastId);
            $("#record-version").val(r.version);
            $("#record-city").val(r.cityCode);
            $("#record-time").val(dateTime(r.forecastTime));
            $("#record-text").val(r.weatherText);
            $("#record-min").val(r.tempMin);
            $("#record-max").val(r.tempMax);
            $("#record-humidity").val(r.humidity);
            $("#record-wind").val(r.windLevel);
            $("#record-source").val(r.dataSource);
            $("#record-modal .modal-title").text("修改气象记录");
            $("#record-modal").modal("show");
        });
    }

    function submitRecord() {
        var data = {
            forecastId: $("#record-id").val() || null,
            version: $("#record-version").val() || null,
            cityCode: $("#record-city").val(),
            forecastTime: $("#record-time").val(),
            weatherText: $.trim($("#record-text").val()),
            tempMin: $("#record-min").val(),
            tempMax: $("#record-max").val(),
            humidity: $("#record-humidity").val() || null,
            windLevel: $("#record-wind").val(),
            dataSource: $("#record-source").val() || "manual"
        };
        if (!data.cityCode) return $.modal.alertWarning("请选择城市");
        if (!data.forecastTime) return $.modal.alertWarning("请填写预报时间");
        if (!data.weatherText) return $.modal.alertWarning("请填写天气现象");
        var isEdit = !!data.forecastId;
        $.ajax({
            url: prefix + "/record",
            type: isEdit ? "PUT" : "POST",
            contentType: "application/json",
            data: JSON.stringify(data),
            success: function (res) {
                if (res.code !== 0) return $.modal.alertWarning(res.msg);
                $("#record-modal").modal("hide");
                $.modal.msgSuccess(isEdit ? "修改成功" : "新增成功");
                refreshAll();
            }
        });
    }

    function revoke(id) {
        $.modal.confirm("确认撤销该条气象记录？撤销后默认查询不再显示，但保留审计。", function () {
            $.ajax({
                url: prefix + "/record/" + id,
                type: "DELETE",
                success: function (res) {
                    if (res.code !== 0) return $.modal.alertWarning(res.msg);
                    $.modal.msgSuccess("已撤销");
                    refreshAll();
                }
            });
        });
    }

    function detail(id) {
        $.get(prefix + "/detail/" + id, function (res) {
            if (res.code !== 0) return $.modal.alertWarning(res.msg);
            var r = res.data;
            var rows = [
                ["城市", (r.cityName || "") + "（" + r.cityCode + "）"],
                ["预报时间", dateTime(r.forecastTime)],
                ["天气现象", r.weatherText],
                ["温度", (r.tempMin == null ? "-" : r.tempMin) + " ~ " + (r.tempMax == null ? "-" : r.tempMax) + " ℃"],
                ["湿度", r.humidity == null ? "-" : r.humidity + "%"],
                ["风力", r.windLevel || "-"],
                ["数据来源", r.dataSource || "-"],
                ["采集时间", dateTime(r.collectTime)],
                ["状态", r.revoked === 1
                    ? "已撤销（" + (r.revokeReason || "") + "，" + dateTime(r.revokedAt) + "，" + (r.revokedBy || "") + "）"
                    : "有效"],
                ["创建时间", dateTime(r.createdAt)]
            ];
            $("#detail-body").html(rows.map(function (row) {
                return '<tr><th style="width:140px">' + row[0] + '</th><td>' + escape(row[1]) + '</td></tr>';
            }).join(""));
            $("#detail-modal").modal("show");
        });
    }

    function loadCityTable() {
        $.get(prefix + "/cities", function (res) {
            if (res.code !== 0) return;
            $("#cities-body").html((res.data || []).map(function (c) {
                return "<tr><td>" + c.cityCode + "</td><td>" + escape(c.cityName) + "</td><td>" +
                    (c.latitude || "-") + "</td><td>" + (c.longitude || "-") + "</td><td>" +
                    (c.enabled === 1 ? "是" : "否") +
                    '</td><td><a class="btn btn-primary btn-xs" onclick="weather.fillCity(\'' + c.cityCode + '\')">编辑</a></td></tr>';
            }).join(""));
            weather._cities = res.data || [];
        });
    }

    function fillCity(code) {
        var city = (weather._cities || []).filter(function (c) { return c.cityCode === code; })[0] || {};
        $("#city-code").val(city.cityCode);
        $("#city-name").val(city.cityName);
        $("#city-lat").val(city.latitude);
        $("#city-lng").val(city.longitude);
    }

    function saveCity() {
        var data = {
            cityCode: $.trim($("#city-code").val()),
            cityName: $.trim($("#city-name").val()),
            latitude: $("#city-lat").val() || null,
            longitude: $("#city-lng").val() || null,
            enabled: 1
        };
        if (!data.cityCode || !data.cityName) return $.modal.alertWarning("城市编码和名称不能为空");
        $.ajax({
            url: prefix + "/city",
            type: "POST",
            contentType: "application/json",
            data: JSON.stringify(data),
            success: function (res) {
                if (res.code !== 0) return $.modal.alertWarning(res.msg);
                $.modal.msgSuccess("已保存");
                loadCityTable();
                loadCities();
            }
        });
    }

    function openStatistics() {
        $.post(prefix + "/statistics", queryParams(), function (res) {
            if (res.code !== 0) return $.modal.alertWarning(res.msg);
            var d = res.data || {};
            var rows = [
                ["记录总数", d.total || 0],
                ["涉及城市", d.cityCount || 0],
                ["未来预报", d.upcoming || 0],
                ["已撤销", d.revoked || 0]
            ];
            $("#statistics-body").html(rows.map(function (r) {
                return '<tr><th style="width:160px">' + r[0] + "</th><td>" + r[1] + "</td></tr>";
            }).join(""));
            $("#statistics-modal").modal("show");
        });
    }

    function exportExcel() {
        window.location.href = prefix + "/export?" + $.param(queryParams());
    }

    function print() {
        window.open(prefix + "/print?" + $.param(queryParams()));
    }

    function openImport() {
        $("#import-result").text("");
        $("#import-file").val("");
        $("#import-modal").modal("show");
    }

    function downloadTemplate() {
        window.location.href = prefix + "/import/template";
    }

    function doImport() {
        var file = $("#import-file")[0].files[0];
        if (!file) return $.modal.alertWarning("请选择文件");
        var formData = new FormData();
        formData.append("file", file);
        $.ajax({
            url: prefix + "/import",
            type: "POST",
            data: formData,
            processData: false,
            contentType: false,
            success: function (res) {
                if (res.code !== 0) return $.modal.alertWarning(res.msg);
                var d = res.data || {};
                $("#import-result").html("导入完成：成功 " + (d.success || 0) + " 行，失败 " + (d.failed || 0) + " 行" +
                    (d.errors && d.errors.length ? "<br>" + d.errors.map(escape).join("<br>") : ""));
                refreshAll();
            }
        });
    }

    function refreshAll() {
        $.table.refresh();
        loadSummary();
    }

    function initDates() {
        if (typeof laydate !== "undefined") {
            laydate.render({ elem: 'input[name="dateFrom"]', type: "date" });
            laydate.render({ elem: 'input[name="dateTo"]', type: "date" });
        }
    }

    return {
        init: function () {
            initDates();
            initTable();
            loadCities();
            loadSummary();
        },
        openAdd: openAdd,
        openEdit: openEdit,
        submitRecord: submitRecord,
        revoke: revoke,
        detail: detail,
        openCities: function () { loadCityTable(); $("#cities-modal").modal("show"); },
        fillCity: fillCity,
        saveCity: saveCity,
        openStatistics: openStatistics,
        exportExcel: exportExcel,
        print: print,
        openImport: openImport,
        downloadTemplate: downloadTemplate,
        doImport: doImport
    };
})();

$(function () {
    weather.init();
});
