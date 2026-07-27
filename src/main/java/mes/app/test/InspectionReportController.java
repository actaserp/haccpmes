package mes.app.test;

import lombok.extern.slf4j.Slf4j;
import mes.app.test.service.InspectionReportService;
import mes.domain.entity.User;
import mes.domain.model.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/quality/inspection-report")
public class InspectionReportController {

    @Autowired
    InspectionReportService inspectionReportService;

    // ─────────────────────────────────────────────────────────
    // 검사일보 목록 조회
    // ─────────────────────────────────────────────────────────
    @GetMapping("/read")
    public AjaxResult getList(
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end",   required = false) String end,
            @RequestParam(value = "spjangcd") String spjangcd,
            @RequestParam(value = "workcenter_id", required = false) Integer workcenterId,
            HttpServletRequest request) {

        List<Map<String, Object>> items =
                inspectionReportService.getList(start, end, spjangcd, workcenterId);

        AjaxResult result = new AjaxResult();
        result.data = items;
        return result;
    }

    // ─────────────────────────────────────────────────────────
    // 검사일보 상세 조회
    // ─────────────────────────────────────────────────────────
    @GetMapping("/detail")
    public AjaxResult getDetail(
            @RequestParam("id") int id,
            HttpServletRequest request) {

        Map<String, Object> item = inspectionReportService.getDetail(id);
        AjaxResult result = new AjaxResult();
        result.data = item;
        return result;
    }

    // ─────────────────────────────────────────────────────────
    // 검사일보 저장 (등록 / 수정)
    // ─────────────────────────────────────────────────────────
    @PostMapping("/save")
    @Transactional
    public AjaxResult save(
            @RequestBody Map<String, Object> payload,
            Authentication auth) {

        User user = (User) auth.getPrincipal();

        try {
            inspectionReportService.save(payload, user);
            AjaxResult result = new AjaxResult();
            result.success = true;
            return result;
        } catch (Exception e) {
            log.error("검사일보 저장 실패", e);
            AjaxResult result = new AjaxResult();
            result.success = false;
            result.message = e.getMessage();
            return result;
        }
    }

    // ─────────────────────────────────────────────────────────
    // 검사일보 삭제
    // ─────────────────────────────────────────────────────────
    @PostMapping("/delete")
    @Transactional
    public AjaxResult delete(
            @RequestParam("id") Integer id,
            Authentication auth) {

        try {
            inspectionReportService.delete(id);
            AjaxResult result = new AjaxResult();
            result.success = true;
            return result;
        } catch (Exception e) {
            log.error("검사일보 삭제 실패", e);
            AjaxResult result = new AjaxResult();
            result.success = false;
            result.message = e.getMessage();
            return result;
        }
    }
}