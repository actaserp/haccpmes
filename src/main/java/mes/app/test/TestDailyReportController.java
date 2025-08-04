package mes.app.test;

import lombok.extern.slf4j.Slf4j;
import mes.app.test.service.TestDailyReportServicr;
import mes.domain.model.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/test/test_daily_report")
public class TestDailyReportController { // 검사일보

  @Autowired
  TestDailyReportServicr testDailyReportServicr;

  @GetMapping("/read")
  public AjaxResult getTestDailyReportRead(@RequestParam(value = "WorkCenter_id") Integer workCenterId,
                                           @RequestParam(value = "SearchDate") String searchDate) {
    LocalDate startDate = LocalDate.parse(searchDate + "-01");
    LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth()); // 월의 마지막 일

    // 원하는 포맷으로 문자열 변환 (예: "2025-08-01", "2025-08-31")
    String start = startDate.toString(); // "2025-08-01"
    String end = endDate.toString();     // "2025-08-31"

    List<Map<String, Object>> items = testDailyReportServicr.getList(workCenterId,start,end );
    AjaxResult result = new AjaxResult();
    result.data = items;

    return result;
  }


}
