package com.ruoyi.web.controller.archive;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.service.ArchiveLocationService;
import com.ruoyi.system.service.ArchiveLocationService.ImportLine;
import com.ruoyi.system.service.ArchiveLocationService.LocationRow;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

/** M10 库位管理：库房/区域/柜架三级维护、导入导出、打印统计。 */
@Controller
@RequestMapping("/records/location")
public class ArchiveLocationController extends BaseController {
    private static final String QUERY = "archiveLocationQuery";
    private static final String MANAGE = "archiveLocationManage";

    private final ArchiveLocationService locations;

    public ArchiveLocationController(ArchiveLocationService locations) {
        this.locations = locations;
    }

    public static class SaveRequest {
        private Long locationId;
        private Long parentId;
        private String locationLevel;
        private String locationCode;
        private String locationName;
        private Integer capacity;
        private Integer status;
        private Integer sortNo;
        private String remark;
        public Long getLocationId() { return locationId; }
        public void setLocationId(Long locationId) { this.locationId = locationId; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public String getLocationLevel() { return locationLevel; }
        public void setLocationLevel(String locationLevel) { this.locationLevel = locationLevel; }
        public String getLocationCode() { return locationCode; }
        public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
        public String getLocationName() { return locationName; }
        public void setLocationName(String locationName) { this.locationName = locationName; }
        public Integer getCapacity() { return capacity; }
        public void setCapacity(Integer capacity) { this.capacity = capacity; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public Integer getSortNo() { return sortNo; }
        public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    @GetMapping
    @RequiresPermissions(QUERY)
    public String page(org.springframework.ui.Model model) {
        model.addAttribute("canManage", org.apache.shiro.SecurityUtils.getSubject().isPermitted(MANAGE));
        return "archive/location";
    }

    @PostMapping("/list")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult list(String keyword, String level, Integer status) {
        return AjaxResult.success(locations.tree(keyword, level, status));
    }

    @GetMapping("/choices")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult choices() {
        return AjaxResult.success(locations.choices());
    }

    @PostMapping("/save")
    @ResponseBody
    @RequiresPermissions(MANAGE)
    @Log(title = "库位管理", businessType = BusinessType.UPDATE)
    public AjaxResult save(@RequestBody SaveRequest request) {
        return AjaxResult.success(locations.save(request.getLocationId(), request.getParentId(),
            request.getLocationLevel(), request.getLocationCode(), request.getLocationName(), request.getCapacity(),
            request.getStatus() == null ? 0 : request.getStatus(), request.getSortNo() == null ? 0 : request.getSortNo(),
            request.getRemark(), getLoginName()));
    }

    @PostMapping("/remove")
    @ResponseBody
    @RequiresPermissions(MANAGE)
    @Log(title = "库位管理", businessType = BusinessType.DELETE)
    public AjaxResult remove(long locationId) {
        locations.remove(locationId);
        return success();
    }

    @PostMapping("/statistics")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult statistics() {
        return AjaxResult.success(locations.statistics());
    }

    @GetMapping("/export")
    @RequiresPermissions(QUERY)
    @Log(title = "库位管理", businessType = BusinessType.EXPORT)
    public void export(String keyword, String level, Integer status, HttpServletResponse response) throws IOException {
        List<LocationRow> rows = locations.tree(keyword, level, status);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=locations.xlsx");
        ArchiveM10Workbook.writeLocations(response.getOutputStream(), rows);
    }

    @GetMapping("/template")
    @RequiresPermissions(QUERY)
    public void template(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=location-template.xlsx");
        ArchiveM10Workbook.writeLocationTemplate(response.getOutputStream());
    }

    @PostMapping("/import")
    @ResponseBody
    @RequiresPermissions(MANAGE)
    @Log(title = "库位管理", businessType = BusinessType.IMPORT)
    public AjaxResult importRows(@RequestParam MultipartFile file) {
        List<ImportLine> lines = locations.importRows(file);
        return AjaxResult.success("已导入 " + lines.size() + " 个库位", lines);
    }

    @GetMapping("/detail/{locationId}")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult detail(@PathVariable long locationId) {
        return AjaxResult.success(locations.require(locationId));
    }
}
