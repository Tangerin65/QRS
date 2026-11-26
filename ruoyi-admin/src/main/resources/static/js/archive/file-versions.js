var archiveVersions = (function () {
    var prefix = ctx + "records/files/" + versionOptions.archiveId + "/file/" + versionOptions.fileId;
    var rollbackPending = false;
    function escape(value) { return $("<div>").text(value == null ? "" : value).html(); }
    function requestError(xhr) { $.modal.alertWarning((xhr.responseJSON && xhr.responseJSON.msg) || "请求失败，请稍后重试"); }
    function button(label, action, version) {
        return '<button type="button" class="btn btn-default btn-xs" data-action="' + action
            + '" data-version="' + version + '">' + label + '</button> ';
    }
    function refresh() {
        $("#compare-result").empty();
        return $.get(prefix + "/versions", function (res) {
            if (res.code !== 0) return $.modal.alertWarning(res.msg || "版本查询失败");
            var rows = res.data || [];
            // 后端按版本号倒序返回，第一条就是当前版本。
            var current = rows.length ? rows[0].versionNo : 0;
            $("#version-rows").html(rows.map(function (row) {
                var version = row.versionNo, actions = "";
                if (versionOptions.canPreview) actions += button("预览", "preview", version);
                if (versionOptions.canDownload) actions += button("下载", "download", version);
                if (versionOptions.canPrint) actions += button("打印", "print", version);
                if (versionOptions.canRollback && version !== current) actions += button("回退至此版本", "rollback", version);
                return '<tr><td><input type="checkbox" name="compareVersion" value="' + version + '" aria-label="选择 V' + version
                    + '"></td><td>V' + version + (version === current ? '（当前）' : '') + '</td><td>' + escape(row.originalName)
                    + '</td><td>' + row.fileSize + ' B</td><td style="word-break:break-all">' + escape(row.sha256)
                    + '</td><td>' + escape(row.createBy) + '</td><td>' + escape(row.createTime)
                    + '</td><td>' + (row.sourceVersionNo ? 'V' + row.sourceVersionNo : '上传') + '</td><td>' + actions + '</td></tr>';
            }).join("") || '<tr><td colspan="9">暂无版本</td></tr>');
        }).fail(requestError);
    }
    function compare() {
        var selected = $("#version-rows input[name=compareVersion]:checked").map(function () { return Number(this.value); }).get();
        if (selected.length !== 2) return $.modal.alertWarning("请选择两个版本");
        selected.sort(function (a, b) { return a - b; });
        $.get(prefix + "/compare", {leftVersionNo: selected[0], rightVersionNo: selected[1]}, function (res) {
            if (res.code !== 0) return $.modal.alertWarning(res.msg);
            var value = res.data;
            $("#compare-result").empty().append($("<p>").text(
                'V' + value.left.versionNo + ' → V' + value.right.versionNo + '：内容'
                + (value.sameContent ? '相同' : '不同') + '；文件名' + (value.sameName ? '相同' : '不同')
                + '；大小变化 ' + value.sizeDelta + ' B；类型' + (value.typeChanged ? '已变化' : '未变化')));
        }).fail(requestError);
    }
    function rollback(version) {
        if (rollbackPending) return;
        $.modal.confirm("确认使用 V" + version + " 的内容创建一个新版本？", function () {
            if (rollbackPending) return;
            rollbackPending = true;
            $("#version-rows button[data-action=rollback]").prop("disabled", true);
            $.post(prefix + "/rollback/" + version, function (res) {
                if (res.code === 0) refresh(); else $.modal.alertWarning(res.msg);
            }).fail(requestError).always(function () {
                rollbackPending = false;
                $("#version-rows button[data-action=rollback]").prop("disabled", false);
            });
        });
    }
    return {init: function () {
        $("#compare-versions").on("click", compare);
        $("#refresh-versions").on("click", refresh);
        $("#version-rows").on("change", "input[name=compareVersion]", function () {
            if ($("#version-rows input[name=compareVersion]:checked").length > 2) {
                this.checked = false; $.modal.alertWarning("最多选择两个版本");
            }
        });
        $("#version-rows").on("click", "button[data-action]", function () {
            var action = $(this).data("action"), version = $(this).data("version");
            if (action === "rollback") rollback(version);
            else if (["preview", "download", "print"].indexOf(action) >= 0) {
                window.open(prefix + "/version/" + version + "/" + action, "_blank", "noopener");
            }
        });
        refresh();
    }};
})();
$(archiveVersions.init);
