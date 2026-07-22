package mes.app.production;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.core.Authentication;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mes.app.production.service.EquipmentRunChartService;
import mes.domain.entity.EquRun;
import mes.domain.entity.User;
import mes.domain.model.AjaxResult;
import mes.domain.repository.EquRunRepository;
import mes.domain.services.SqlRunner;

@RestController
@RequestMapping("/api/production/equipment_run_chart")
public class EquipmentRunChartController {

	@Autowired
	SqlRunner sqlRunner;

	@Autowired
	EquipmentRunChartService equipmentRunChartService;

	@Autowired
	EquRunRepository equRunRepository;

	// 차트 searchMainData
	@GetMapping("/read")
	public AjaxResult getEquipmentRunChart(
			@RequestParam(value="date_from", required=false) String date_from,
			@RequestParam(value="date_to", required=false) String date_to,
			@RequestParam(value="id", required=false) Integer id,
			@RequestParam(value="runType", required=false) String runType,
			@RequestParam String spjangcd) {

		List<Map<String, Object>> items = this.equipmentRunChartService.getEquipmentRunChart(date_from, date_to, id, runType, spjangcd);
		List<Map<String, Object>> result = new ArrayList<>();

		Map<String, List<Map<String, Object>>> GroupByNameItems = items.stream()
				.filter(item -> item.get("Name") != null)
				.collect(Collectors.groupingBy(item -> item.get("Name").toString()));

		for(Map.Entry<String, List<Map<String, Object>>> entry : GroupByNameItems.entrySet()){

			List<Map<String, Object>> groupItems = entry.getValue();

			// 비가동(GapTime) 계산은 '앞 가동의 종료 → 다음 가동의 시작' 순서에 의존하므로
			// StartDate 오름차순 정렬 필수. 정렬이 없으면 종료<시작이 되어 음수(-)가 나옴.
			groupItems.sort(Comparator.comparing(
					m -> (Timestamp) m.get("StartDate"),
					Comparator.nullsLast(Comparator.naturalOrder())));

			for (int i = 0; i < groupItems.size(); i++) {
				Map<String, Object> uptime = groupItems.get(i);

				Map<String, Object> Downtime = new HashMap<>(); //비가동시간

				//uptime.get("")
				Object endDate = uptime.get("end_date");
				Object endTime = uptime.get("EndTime");
				Object EquipmentName = uptime.get("Name");
				Object Equipment_id = uptime.get("Equipment_id");
				Object StopCause = uptime.get("StopCauseName");

				Timestamp StartDate = (Timestamp) uptime.get("StartDate");
				Timestamp EndDate = (Timestamp) uptime.get("EndDate");

				if(EndDate != null){
					long diffMillis = EndDate.getTime() - StartDate.getTime();
					long diffMinutes = (diffMillis / 1000) / 60;
					uptime.put("Runtime", String.valueOf(diffMinutes));
				}

				uptime.put("RunState", "run");
				uptime.put("StopCauseName", "");

				Map<String, Object> nextItem = (i + 1 < groupItems.size()) ? groupItems.get(i + 1) : null;

				result.add(uptime);
				if (EndDate == null) continue;

				Downtime.put("RunState", "stop");
				Downtime.put("Name", EquipmentName);
				Downtime.put("Equipment_id", Equipment_id);
				Downtime.put("start_date", endDate);
				Downtime.put("StartTime", endTime);
				Downtime.put("StopCauseName", StopCause);
				if(nextItem == null){
					Downtime.put("end_date", "");
					Downtime.put("EndTime", "");
					Downtime.put("Runtime", "");
				}else{
					Downtime.put("end_date", nextItem.get("start_date"));
					Downtime.put("EndTime", nextItem.get("StartTime"));

					Timestamp DownTimeEndDate = (Timestamp) nextItem.get("StartDate"); //비가동의 종료시간
					long diffMillis = DownTimeEndDate.getTime() - EndDate.getTime(); //가동되지 않았던 시간
					long diffMinutes = (diffMillis / 1000) / 60;

					Downtime.put("GapTime", diffMinutes);
				}
				result.add(Downtime);
			}
		}

		AjaxResult result2 = new AjaxResult();
		result2.data = result;
		return result2;
	}

	/*// 차트 fillData
	@GetMapping("/readData")
	public AjaxResult getEquipmentRunChart(
    		@RequestParam(value="id", required=false) Integer id,
    		@RequestParam(value="runType", required=false) String runType,
			HttpServletRequest request) {

		List<Map<String, Object>> items = this.equipmentRunChartService.getEquipmentRunChart(null, null, id, runType);
        AjaxResult result = new AjaxResult();
        result.data = items;
		return result;
	}*/

	// 선택 작지의 공정별 설비 수집 (equ_collect, work_order_no 기준)
	// 실제 PLC 연동 시와 동일 구조: 작지 1건 → 그 공정 통과 설비별 수집.
	// job_res는 읽지 않음(작지번호만 파라미터로 받음).
	@GetMapping("/collect_by_wo")
	public AjaxResult getCollectByWo(
			@RequestParam(value="work_order_no", required=false) String work_order_no,
			@RequestParam String spjangcd) {

		MapSqlParameterSource p = new MapSqlParameterSource();
		p.addValue("work_order_no", work_order_no);
		p.addValue("spjangcd", spjangcd);

		String sql =
				"SELECT c.equipment_code, e.\"Name\" AS equipment_name, c.process_seq, " +
						"       c.input_qty, c.prod_qty, c.defect_qty, c.place_count, " +
						"       c.part_loss_rate, c.temp_c, c.n2_ppm, c.run_min, c.stop_min " +
						"FROM equ_collect c " +
						"JOIN equ e ON e.id = c.equipment_id " +
						"WHERE c.work_order_no = :work_order_no AND c.spjangcd = :spjangcd " +
						"ORDER BY c.process_seq, c.equipment_code";

		List<Map<String, Object>> data = this.sqlRunner.getRows(sql, p);
		AjaxResult result = new AjaxResult();
		result.data = data;
		return result;
	}

	// 설비 로우데이터: 생산일(수집일) 목록 - 조회 드롭다운용
	@GetMapping("/raw_dates")
	public AjaxResult getRawDates(
			@RequestParam(value="equipment_code", required=false) String equipment_code) {
		MapSqlParameterSource p = new MapSqlParameterSource();
		p.addValue("equipment_code", equipment_code);
		String sql =
				"SELECT DISTINCT to_char(collect_dt, 'yyyy-mm-dd') AS collect_date " +
						"FROM equ_raw " +
						"WHERE (:equipment_code IS NULL OR equipment_code = :equipment_code) " +
						"ORDER BY 1 DESC";
		AjaxResult r = new AjaxResult();
		r.data = this.sqlRunner.getRows(sql, p);
		return r;
	}

	// 설비 로우데이터: 특정 설비 + 기간(date_from~date_to)의 1분주기 원시 로그
	@GetMapping("/raw")
	public AjaxResult getRaw(
			@RequestParam(value="equipment_code", required=false) String equipment_code,
			@RequestParam(value="date_from", required=false) String date_from,
			@RequestParam(value="date_to", required=false) String date_to) {
		MapSqlParameterSource p = new MapSqlParameterSource();
		p.addValue("equipment_code", equipment_code);
		p.addValue("date_from", date_from);
		p.addValue("date_to", date_to);
		String sql =
				"SELECT equipment_code, to_char(collect_dt,'yyyy-mm-dd') AS collect_date, " +
						"       to_char(collect_dt,'HH24:MI') AS collect_time, collect_dt, " +
						"       eq_mode, eq_status, job_name, pcb_in, pcb_out, temp_c, n2_ppm, " +
						"       place_count, defect_qty, part_loss_rate, cv_speed " +
						"FROM equ_raw " +
						"WHERE equipment_code = :equipment_code " +
						"  AND collect_dt >= CAST(:date_from AS date) " +
						"  AND collect_dt <  CAST(:date_to AS date) + interval '1 day' " +
						"ORDER BY collect_dt";
		AjaxResult r = new AjaxResult();
		r.data = this.sqlRunner.getRows(sql, p);
		return r;
	}

	// saveData
	@PostMapping("/addData")
	public AjaxResult addDataEquipmentRunChart (
			@RequestParam(value="id", required=false) Integer id,
			@RequestParam(value="spjangcd") String spjangcd,
			@RequestParam(value="Equipment_id", required=false) Integer Equipment_id,
			@RequestParam(value="start_date", required=false) String start_date,
			@RequestParam(value="StartTime", required=false) String StartTime,
			@RequestParam(value="end_date", required=false) String end_date,
			@RequestParam(value="EndTime", required=false) String EndTime,
			@RequestParam(value="RunState", required=false) String RunState,
			@RequestParam(value="Description", required=false) String Description,
			@RequestParam(value="StopCause_id", required=false) Integer StopCause_id,
			HttpServletRequest request,
			Authentication auth) {

		AjaxResult result = new AjaxResult();

		User user = (User)auth.getPrincipal();

		Timestamp startDate = Timestamp.valueOf(start_date + ' ' + StartTime + ":59");
		Timestamp endDate = Timestamp.valueOf(end_date + ' ' + EndTime + ":59");

		EquRun er = null;

		List<Map<String, Object>> overlappinged = equipmentRunChartService.OverlappingTimeQuery(startDate, endDate, Equipment_id, spjangcd);//현재 겹치는 시간은 안됨. 설비가 겹치는 시간대로 가동되는건 말이 안되기 때문


		if(!overlappinged.isEmpty()){
			boolean hasMultipleRecords = overlappinged.size() != 1;
			boolean hasEndDate = overlappinged.get(0).get("EndDate") != null;

			if(hasMultipleRecords || hasEndDate){
				result.success = false;
				result.message = "해당 시간대에 이미 가동 중인 기록이 있습니다.";
				return result;
			}
		}

		result.success = true;
		result.message = "성공!";
		return result;

		/*if (id==null) {
			er = new EquRun();
		} else {
			er = this.equRunRepository.getEquRunById(id);
		}

		er.setEquipmentId(Equipment_id);
		er.setStartDate(startDate);
		er.setEndDate(endDate);
		er.setRunState("run");
		er.setDescription(Description);
		er.setStopCauseId(StopCause_id);
		er.set_audit(user);
		er.setSpjangcd(spjangcd);

		this.equRunRepository.save(er);

		result.success = true;
		result.message = "저장하였습니다.";
		result.data = er.getId();
	    return result;*/
	}

	// delDataBind
	@PostMapping("/delData")
	public AjaxResult deleteEquipmentRunChart(
			@RequestParam("id") Integer id) {

		this.equRunRepository.deleteById(id);
		AjaxResult result = new AjaxResult();
		return result;
	}
}