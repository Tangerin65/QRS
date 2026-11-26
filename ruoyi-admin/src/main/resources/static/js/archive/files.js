var archiveFiles = (function () {
    var prefix = ctx + "records/files/" + archiveFileOptions.archiveId;
    var uploading = false;
    var versionUploading = false;
    var chart;
    var statisticsRequest;
    var events = {FILE_UPLOAD: "首次上传", FILE_VERSION_ADD: "新增版本", FILE_VERSION_ROLLBACK: "版本回退",
        FILE_REVOKE: "撤销附件", FILE_PREVIEW: "预览", FILE_DOWNLOAD: "下载", FILE_PRINT: "打印请求"};

    function escape(value) {
        return $("<div>").text(value == null ? "" : value).html();
    }
    function sizeText(bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
        return (bytes / 1024 / 1024).toFixed(1) + " MB";
    }
    function requestError(xhr) {
        $.modal.alertWarning((xhr.responseJSON && xhr.responseJSON.msg) || "请求失败，请稍后重试");
    }
    function button(label, action, fileId) {
        return '<button type="button" class="btn btn-default btn-xs" data-action="' + action
            + '" data-file-id="' + fileId + '">' + label + '</button> ';
    }
    function refresh() {
        return $.get(prefix + "/list", function (res) {
            if (res.code !== 0) return $.modal.alertWarning(res.msg || "附件查询失败");
            var html = (res.data || []).map(function (file) {
                var id = file.fileId, version = file.currentVersionNo;
                var actions = button("版本", "versions", id) + button("操作记录", "operations", id);
                if (version) {
                    if (archiveFileOptions.canPreview) actions += button("预览", "preview", id);
                    if (archiveFileOptions.canDownload) actions += button("下载", "download", id);
                    if (archiveFileOptions.canPrint) actions += button("打印", "print", id);
                    if (archiveFileOptions.canUpload) actions += button("新增版本", "addVersion", id);
                }
                if (archiveFileOptions.canDeleteFiles) actions += button("撤销", "revoke", id);
                return '<tr data-version="' + (version || 0) + '"><td>' + escape(file.displayName)
                    + '</td><td>' + escape(file.relativePath) + '</td><td>' + escape(file.fileType)
                    + '</td><td>' + sizeText(file.fileSize) + '</td><td>' + (version ? 'V' + version : '待迁移')
                    + '</td><td>' + escape(file.createBy) + '</td><td>' + escape(file.createTime)
                    + '</td><td>' + actions + '</td></tr>';
            }).join("");
            $("#attachment-rows").html(html || '<tr><td colspan="8" class="text-center">暂无附件</td></tr>');
        }).fail(requestError);
    }
    function loadStatistics() {
        var from = $("#usage-form [name=dateFrom]").val(), to = $("#usage-form [name=dateTo]").val();
        if (from && to && from > to) return $.modal.alertWarning("开始日期不能晚于结束日期");
        // 新查询替换旧查询，避免慢响应覆盖最新筛选结果。
        if (statisticsRequest) statisticsRequest.abort();
        statisticsRequest = $.ajax({url: prefix + "/statistics", type: "POST", data: $("#usage-form").serialize(),
            success: function (res) {
                if (res.code !== 0) return $.modal.alertWarning(res.msg || "统计失败");
                var rows = res.data || [], total = 0;
                var byEvent = $("#usage-form [name=groupBy]").val() === "eventType";
                var names = [], values = [];
                $("#usage-rows").html(rows.map(function (row) {
                    var name = byEvent ? (events[row.name] || row.name) : row.name;
                    var count = Number(row.total);
                    total += count; names.push(name); values.push(count);
                    return '<tr><td>' + escape(name) + '</td><td>' + count + '</td></tr>';
                }).join("") || '<tr><td colspan="2">暂无操作记录</td></tr>');
                $("#usage-summary").text("符合条件的操作共 " + total + " 次；按档案当前状态及附件当前类型分组。");
                if (window.echarts) {
                    if (!chart) chart = echarts.init(document.getElementById("usage-chart"));
                    chart.setOption({tooltip: {trigger: "axis"}, grid: {x: 60, y: 30, x2: 30, y2: 80},
                        xAxis: [{type: "category", data: names, axisLabel: {rotate: 20}}],
                        yAxis: [{type: "value", min: 0, minInterval: 1}],
                        series: [{name: "操作次数", type: "bar", data: values}]}, true);
                }
            }, error: function (xhr, status) { if (status !== "abort") requestError(xhr); }
        });
    }
    function changed() { refresh(); loadStatistics(); }
    function uploadQueue(selectedFiles) {
        var queue = Array.from(selectedFiles);
        if (!queue.length || uploading) return;
        uploading = true;
        $("#upload-panel button").prop("disabled", true);
        $("#upload-queue").show();
        $("#upload-rows").html(queue.map(function (file, index) {
            return '<tr id="upload-row-' + index + '"><td>' + escape(file.webkitRelativePath || file.name)
                + '</td><td>' + sizeText(file.size) + '</td><td class="upload-progress">0%</td><td class="upload-result">等待上传</td></tr>';
        }).join(""));
        var success = 0, failed = 0;
        function next(index) {
            $("#upload-summary").text("共 " + queue.length + " 个，成功 " + success + " 个，失败 " + failed + " 个");
            if (index === queue.length) {
                uploading = false;
                $("#upload-panel button").prop("disabled", false);
                changed();
                return;
            }
            var file = queue[index], row = $("#upload-row-" + index);
            if (!file.size || file.size > archiveFileOptions.maxFileSize) {
                row.find(".upload-result").addClass("text-danger").text(!file.size ? "空文件" : "超过单文件大小限制");
                failed++;
                // 异步继续，避免大量无效文件造成递归栈过深。
                setTimeout(function () { next(index + 1); }, 0);
                return;
            }
            var data = new FormData();
            data.append("file", file, file.name);
            data.append("relativePath", file.webkitRelativePath || file.name);
            row.find(".upload-result").text("正在上传");
            $.ajax({url: prefix + "/upload", type: "POST", data: data, processData: false, contentType: false,
                xhr: function () {
                    var xhr = $.ajaxSettings.xhr();
                    xhr.upload.addEventListener("progress", function (event) {
                        if (event.lengthComputable) row.find(".upload-progress").text(Math.round(event.loaded * 100 / event.total) + "%");
                    });
                    return xhr;
                }, success: function (res) {
                    if (res.code === 0) {
                        success++; row.find(".upload-progress").text("100%");
                        row.find(".upload-result").addClass("text-success").text("上传成功");
                    } else {
                        failed++; row.find(".upload-result").addClass("text-danger").text(res.msg || "上传失败");
                    }
                }, error: function (xhr) {
                    failed++; row.find(".upload-result").addClass("text-danger").text((xhr.responseJSON && xhr.responseJSON.msg) || "上传请求失败");
                }, complete: function () { next(index + 1); }
            });
        }
        next(0);
    }
    function addVersion(fileId) {
        if (versionUploading) return $.modal.alertWarning("请等待当前版本上传完成");
        var input = $('<input type="file">');
        input.on("change", function () {
            var file = this.files[0];
            if (!file) return;
            if (!file.size || file.size > archiveFileOptions.maxFileSize) return $.modal.alertWarning("文件为空或超过大小限制");
            var data = new FormData(); data.append("file", file, file.name);
            versionUploading = true;
            $.ajax({url: prefix + "/file/" + fileId + "/versions", type: "POST", data: data,
                processData: false, contentType: false,
                success: function (res) { if (res.code === 0) changed(); else $.modal.alertWarning(res.msg); },
                error: requestError, complete: function () { versionUploading = false; }
            });
        });
        input[0].click();
    }
    function revoke(fileId) {
        var reason = window.prompt("请输入撤销原因（可留空）");
        if (reason === null) return;
        $.modal.confirm("撤销后该附件将不再用于预览、下载和打印，确认继续？", function () {
            $.post(prefix + "/file/" + fileId + "/revoke", {reason: reason}, function (res) {
                if (res.code === 0) changed(); else $.modal.alertWarning(res.msg);
            }).fail(requestError);
        });
    }
    function operations(fileId) {
        $("#operation-rows").html('<tr><td colspan="4">加载中…</td></tr>');
        $("#file-operations").modal("show");
        $.get(prefix + "/file/" + fileId + "/operations", function (res) {
            if (res.code !== 0) return $.modal.alertWarning(res.msg);
            $("#operation-rows").html((res.data || []).map(function (row) {
                return '<tr><td>' + escape(events[row.eventType] || row.eventType) + '</td><td>' + escape(row.actorName)
                    + '</td><td>' + escape(row.eventTime) + '</td><td>' + escape(row.detail) + '</td></tr>';
            }).join("") || '<tr><td colspan="4">暂无操作记录</td></tr>');
        }).fail(requestError);
    }
    return {
        init: function () {
            var folderInput = document.getElementById("folder-files");
            if (folderInput) folderInput.setAttribute("webkitdirectory", "");
            $("#file-size-limit").text(sizeText(archiveFileOptions.maxFileSize));
            $("#single-file, #multiple-files, #folder-files").on("change", function () { uploadQueue(this.files); this.value = ""; });
            $("#attachment-rows").on("click", "button[data-action]", function () {
                var id = $(this).data("file-id"), action = $(this).data("action");
                var version = $(this).closest("tr").data("version");
                if (action === "versions") window.open(prefix + "/file/" + id + "/versions/page", "_blank", "noopener");
                else if (action === "addVersion") addVersion(id);
                else if (action === "revoke") revoke(id);
                else if (action === "operations") operations(id);
                else if (["preview", "download", "print"].indexOf(action) >= 0) {
                    window.open(prefix + "/file/" + id + "/version/" + version + "/" + action, "_blank", "noopener");
                }
            });
            $("#usage-form").on("submit", function (event) { event.preventDefault(); loadStatistics(); });
            $(window).on("resize", function () { if (chart) chart.resize(); });
            $(window).on("focus", changed);
            refresh(); loadStatistics();
        },
        pick: function (inputId) { if (!uploading) document.getElementById(inputId).click(); },
        refresh: changed
    };
})();
$(archiveFiles.init);
