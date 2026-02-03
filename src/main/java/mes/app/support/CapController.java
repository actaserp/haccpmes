package mes.app.support;

import mes.app.support.service.CapService;
import mes.domain.entity.CapIssue;
import mes.domain.model.AjaxResult;
import mes.domain.repository.CapIssueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/support/cap")
public class CapController {

    @Autowired
    CapIssueRepository capIssueRepository;

    @Autowired
    CapService capService;

    @GetMapping("/read")
    public AjaxResult Read(@RequestParam(required = false) String capNo
                          ,@RequestParam String spjangcd
    ){

        AjaxResult result = new AjaxResult();

        List<Map<String, Object>> list = capService.getList(capNo, spjangcd);

        result.success = true;
        result.data = list;

        return result;
    }

    @PostMapping("/save")
    @Transactional
    public AjaxResult Save(
                           @RequestParam String cap_number,
                           @RequestParam(required = true) String  date_occurrence,
                           @RequestParam(required = false) String  date_finish,
                           @RequestParam(required = false) Integer  cap_depart,
                           @RequestParam(required = true) Integer  material_id,
                           @RequestParam(required = true) Integer  cboCompanyHidden,
                           @RequestParam(required = false) Integer  cap_unit,
                           @RequestParam(required = true) String  cap_progress,
                           @RequestParam(required = false) Integer  cap_process,
                           @RequestParam(required = true) Integer  defect_type,
                           @RequestParam(required = false) String  cap_creater_id,
                           @RequestParam(required = false) String  cap_problem_content,
                           @RequestParam String spjangcd
    ){

        AjaxResult result = new AjaxResult();

        Optional<CapIssue> byCapNo = capIssueRepository.findByCapNo(cap_number);

        CapIssue capIssue;

        if (byCapNo.isPresent()) {
            // ✅ 수정
            capIssue = byCapNo.get();
        } else {
            // ✅ 신규 생성
            capIssue = new CapIssue();

            // 오늘 날짜 yyyyMMdd
            String today = LocalDate.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            Optional<String> maxCapNoOpt =
                    capIssueRepository.findMaxCapNoByDate(today);

            String newCapNo;

            if (maxCapNoOpt.isPresent()) {
                String maxCapNo = maxCapNoOpt.get();
                int seq = Integer.parseInt(maxCapNo.substring(8)) + 1;
                newCapNo = today + String.format("%04d", seq);
            } else {
                newCapNo = today + "0001";
            }

            capIssue.setCapNo(newCapNo);
        }



        if (cap_progress != null) {
            switch (cap_progress.trim()) {
                case "접수":
                    // 접수
                    cap_progress = "receipt";
                    break;

                case "진행":
                    // 진행
                    cap_progress = "progress";
                    break;

                case "완료":
                    // 완료
                    cap_progress = "finish";
                    break;

                default:
                    // 알 수 없는 상태
                    cap_progress = null;
                    break;
            }
        }

        // 처리일자 세팅: 진행상태가 finish일 때만
        if ("finish".equals(cap_progress)
                && (date_finish == null || date_finish.trim().isEmpty())) {

            date_finish = LocalDate.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }

// 🔽 공통 필드 세팅
        capIssue.setOccurDate(date_occurrence);
        capIssue.setOccurDepart(cap_depart);
        capIssue.setMaterialId(material_id);
        capIssue.setUnitId(cap_unit);
        capIssue.setStatus(cap_progress);
        capIssue.setProcessId(cap_process);
        capIssue.setDefect_type_id(defect_type);
        capIssue.setWriter(cap_creater_id);

        capIssue.setProcess_date(date_finish);

        capIssue.setIssueSummary(cap_problem_content);
        capIssue.setCompany_id(cboCompanyHidden);

        capIssue.setSpjangcd(spjangcd);

        capIssueRepository.save(capIssue);

        result.success = true;
        result.data = capIssue.getCapNo();

        return result;
    }

    @PostMapping("/delete")
    @Transactional
    public AjaxResult delete(String capNo){
        AjaxResult result = new AjaxResult();

        capIssueRepository.deleteByCapNo(capNo);

        return result;
    }

}
