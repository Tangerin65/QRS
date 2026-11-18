var archiveFiles = (function () {
    //管理档案附件上传队列与列表
    var prefix = ctx + "records/files/" + archiveFileOptions.archiveId;
    var uploading = false;

    function escape(value) {
        //转义文件名和路径中的HTML字符
        return $("<div>").text(value == null ? "" : value).html();
    }

    function sizeText(bytes) {
        //格式化文件大小
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
        return (bytes / 1024 / 1024).toFixed(1) + " MB";
    }

    function refresh() {
        //刷新已上传附件清单
        $.get(prefix + "/list", function (res) {
            if (res.code !== 0) return $.modal.alertWarning(res.msg || "附件查询失败");
            var html = (res.data || []).map(function (file) {
                //生成附件信息行
                var action = archiveFileOptions.canDeleteFiles
                    ? '<td><button class="btn btn-danger btn-xs" onclick="archiveFiles.remove(' + file.fileId + ')"><i class="fa fa-trash"></i> 删除记录</button></td>' : "";
                return "<tr><td>" + escape(file.displayName) + "</td><td>" + escape(file.relativePath)
                    + "</td><td>" + escape(file.storageName) + "</td><td>" + escape(file.fileType)
                    + "</td><td>" + sizeText(file.fileSize) + "</td><td>" + escape(file.createBy)
                    + "</td><td>" + escape(file.createTime) + "</td>" + action + "</tr>";
            }).join("");
            var columns = archiveFileOptions.canDeleteFiles ? 8 : 7;
            $("#attachment-rows").html(html || '<tr><td colspan="' + columns + '" class="text-center text-muted">暂无附件</td></tr>');
        });
    }

    function uploadQueue(selectedFiles) {
        //依次上传所选文件并保留相对目录
        var queue = Array.from(selectedFiles);
        if (!queue.length || uploading) return;
        uploading = true;
        $("#upload-panel button").prop("disabled", true);
        $("#upload-queue").show();
        $("#upload-rows").html(queue.map(function (file, index) {
            //显示待上传文件与初始进度
            return '<tr id="upload-row-' + index + '"><td>' + escape(file.webkitRelativePath || file.name)
                + "</td><td>" + sizeText(file.size) + '</td><td class="upload-progress">0%</td><td class="upload-result">等待上传</td></tr>';
        }).join(""));
        var success = 0;
        var failed = 0;

        function next(index) {
            //处理下一文件并汇总上传结果
            $("#upload-summary").text("共 " + queue.length + " 个，成功 " + success + " 个，失败 " + failed + " 个");
            if (index === queue.length) {
                uploading = false;
                $("#upload-panel button").prop("disabled", false);
                refresh();
                return;
            }
            var file = queue[index];
            var row = $("#upload-row-" + index);
            if (file.size === 0 || file.size > archiveFileOptions.maxFileSize) {
                row.find(".upload-result").addClass("text-danger").text(file.size === 0 ? "空文件" : "超过单文件大小限制");
                failed++;
                next(index + 1);
                return;
            }
            var data = new FormData();
            data.append("file", file, file.name);
            data.append("relativePath", file.webkitRelativePath || file.name);
            row.find(".upload-result").text("正在上传");
            $.ajax({
                url: prefix + "/upload",
                type: "POST",
                data: data,
                processData: false,
                contentType: false,
                xhr: function () {
                    //更新当前文件上传进度
                    var xhr = $.ajaxSettings.xhr();
                    xhr.upload.addEventListener("progress", function (event) {
                        if (event.lengthComputable) row.find(".upload-progress").text(Math.round(event.loaded * 100 / event.total) + "%");
                    });
                    return xhr;
                },
                success: function (res) {
                    //记录单个文件的服务端处理结果
                    if (res.code === 0) {
                        success++;
                        row.find(".upload-progress").text("100%");
                        row.find(".upload-result").addClass("text-success").text("上传成功");
                    } else {
                        failed++;
                        row.find(".upload-result").addClass("text-danger").text(res.msg || "上传失败");
                    }
                },
                error: function (xhr) {
                    //显示请求失败结果并继续队列
                    failed++;
                    row.find(".upload-result").addClass("text-danger").text((xhr.responseJSON && xhr.responseJSON.msg) || "上传请求失败");
                },
                complete: function () {
                    //继续上传队列
                    next(index + 1);
                }
            });
        }
        next(0);
    }

    return {
        init: function () {
            //初始化附件列表与文件选择事件
            var folderInput = document.getElementById("folder-files");
            if (folderInput) folderInput.setAttribute("webkitdirectory", "");
            $("#file-size-limit").text(sizeText(archiveFileOptions.maxFileSize));
            $("#single-file, #multiple-files, #folder-files").on("change", function () {
                //读取所选文件并启动上传
                uploadQueue(this.files);
                this.value = "";
            });
            refresh();
        },
        pick: function (inputId) {
            //打开单文件多文件或文件夹选择器
            if (!uploading) document.getElementById(inputId).click();
        },
        remove: function (fileId) {
            //确认后只删除附件数据库记录
            $.modal.confirm("删除后附件记录将移除，但磁盘文件会保留，确认继续？", function () {
                $.ajax({
                    url: prefix + "/delete/" + fileId,
                    type: "POST",
                    success: function (res) {
                        if (res.code === 0) refresh();
                        else $.modal.alertWarning(res.msg || "附件记录删除失败");
                    }
                });
            });
        },
        refresh: refresh
    };
})();
$(archiveFiles.init);
