package mes.app.pda.controller;

import mes.app.inventory.service.LotService;
import mes.app.pda.service.ProductionService;
import mes.app.production.service.ProductionResultService;
import mes.domain.entity.*;
import mes.domain.model.AjaxResult;
import mes.domain.repository.*;
import mes.domain.services.CommonUtil;
import mes.domain.services.DateUtil;
import mes.domain.services.SqlRunner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/pda/production")
public class ProductionController {

    @Autowired
    ProductionService productionService;

    @Autowired
    JobResRepository jobResRepository;

    @Autowired
    EquRunRepository equRunRepository;

    @Autowired
    MatConsuRepository matConsuRepository;

    @Autowired
    MatProcInputReqRepository matProcInputReqRepository;

    @Autowired
    MatProduceRepository matProduceRepository;

    @Autowired
    ProductionResultService productionResultService;

    @Autowired
    MatInoutRepository matInoutRepository;

    @Autowired
    MatLotConsRepository matLotConsRepository;

    @Autowired
    MatLotRepository matLotRepository;

    @Autowired
    MatProcInputRepository matProcInputRepository;

    @Autowired
    SqlRunner sqlRunner;

    @Autowired
    MaterialRepository materialRepository;

    @Autowired
    WorkcenterRepository workcenterRepository;

    @Autowired
    LotService lotService;

    @Autowired
    MaterialGroupRepository materialGroupRepository;

    @Autowired
    JobResDefectRepository jobResDefectRepository;

    @GetMapping("/list")
    public AjaxResult getProductionList(
            @RequestParam(value = "startDate") String startDate,
            @RequestParam(value = "endDate") String endDate,
            @RequestParam(value = "matType", required = false) String matType,
            @RequestParam(value = "spjangcd", defaultValue = "ZZ") String spjangcd
    ) {
        List<Map<String, Object>> data = productionService.getProductionList(startDate, endDate, matType, spjangcd);

        AjaxResult result = new AjaxResult();
        result.success = true;
        result.data = data;
        return result;
    }

    @GetMapping("/detail")
    public AjaxResult getProductionDetail(@RequestParam(value = "jrPk") Integer jrPk) {
        Map<String, Object> data = productionService.getProductionDetail(jrPk);
        AjaxResult result = new AjaxResult();
        result.success = true;
        result.data = data;
        return result;
    }

    // ProductionController.java
    @PostMapping("/save")
    public AjaxResult saveEquipmentPda(
            @RequestParam("id") Integer jrPk,
            @RequestParam("equipment_id") Integer equipmentId,
            Authentication auth) {

        User user = (User) auth.getPrincipal();

        // 1. 기존 데이터 조회
        JobRes jr = this.jobResRepository.getJobResById(jrPk);

        if (jr != null) {
            // 2. 설비 정보만 교체
            jr.setEquipment_id(equipmentId);
            jr.set_audit(user); // 수정자 정보 기록

            this.jobResRepository.save(jr);
        }

        AjaxResult result = new AjaxResult();
        result.success = true;
        return result;
    }

    /**
     * 생산 실적 삭제 (PDA용)
     * 경로: /pda/production/del (또는 기존 설정에 맞게 변경)
     */
    @PostMapping("/del")
    @Transactional
    public AjaxResult prodResultDel(
            @RequestParam(value = "id", required = false) Integer jobresId,
            @RequestParam(value = "order_num", required = false) String order_num,
            @RequestParam(value = "equipment_id", required = false) Integer equipmentId,
            HttpServletRequest request,
            Authentication auth) {

        AjaxResult result = new AjaxResult();
        User user = (User) auth.getPrincipal();
        Timestamp now = DateUtil.getNowTimeStamp();

        // 1. 소모 내역 확인 (소모 내역이 있으면 삭제 불가)
        List<MaterialConsume> mcList = this.matConsuRepository.findByJobResponseId(jobresId);
        if (mcList.size() > 0) {
            result.success = false;
            result.message = "등록된 차수가(소모내역) 있어 삭제 할 수 없습니다.";
            return result;
        }

        // 2. 설비 가동 정보 업데이트 (진행 중인 경우 정지 처리)
        Optional<EquRun> runningRunOpt = equRunRepository.findLatestRunningByEquipmentAndOrder(equipmentId, order_num);
        if (runningRunOpt.isPresent()) {
            EquRun equ = runningRunOpt.get();
            if (equ.getEndDate() == null) {
                equ.setEndDate(now); // 중지 시각
                equ.setRunState("stop");
            }
            equ.setDescription("PDA 작지 취소");
            equ.set_audit(user);
            equRunRepository.save(equ);
        }

        // 3. 작업 지시 상태 변경 (삭제 대신 canceled 상태로 변경)
        JobRes jr = this.jobResRepository.getJobResById(jobresId);
        if (jr != null) {
            jr.setState("canceled");
            jr.set_audit(user);
            jobResRepository.save(jr);
            result.success = true;
            result.message = "정상적으로 취소(삭제) 되었습니다.";
        } else {
            result.success = false;
            result.message = "존재하지 않는 작업 지시입니다.";
        }

        return result;
    }

    /**
     * 생산 작업 시작 (PDA용)
     * 경로: /pda/production/work_start
     */
    @PostMapping("/work_start")
    @Transactional
    public AjaxResult workStart(
            @RequestParam(value = "id", required = false) Integer jrPk,
            @RequestParam(value = "prod_date", required = false) String prodDate,
            @RequestParam(value = "end_date", required = false) String endDate,
            @RequestParam(value = "start_time", required = false) String startTime,
            @RequestParam(value = "end_time", required = false) String endTime,
            @RequestParam(value = "good_qty", required = false) String goodQty,
            @RequestParam(value = "defect_qty", required = false) String defectQty,
            @RequestParam(value = "loss_qty", required = false) String lossQty,
            @RequestParam(value = "scrap_qty", required = false) String scrapQty,
            @RequestParam(value = "shift_code", required = false) String shiftCode,
            @RequestParam(value = "workcenter_id", required = false) Integer workcenterId,
            @RequestParam(value = "equipment_id", required = false) Integer equipmentId,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "mat_pk", required = false) Integer matPk,
            @RequestParam(value = "order_num", required = false) String order_num,
            @RequestParam(value = "spjangcd", defaultValue = "ZZ") String spjangcd,
            HttpServletRequest request,
            Authentication auth) {

        AjaxResult result = new AjaxResult();
        User user = (User) auth.getPrincipal();

        Timestamp start_time = Timestamp.valueOf(prodDate + " " + startTime + ":00");
        Timestamp end_time = null;

        // 1. 해당 설비가 이미 가동 중인지 확인
        long runningCount = this.equRunRepository.countByEquipmentIdAndRunState(equipmentId, "run");
        if (runningCount > 0) {
            result.success = false;
            result.message = "해당 설비는 이미 작업 중입니다.";
            return result;
        }

        if (endTime != null && !endTime.equals("")) {
            end_time = Timestamp.valueOf(prodDate + " " + endTime + ":00");
        }

        Timestamp prod_date = CommonUtil.tryTimestamp(prodDate);
        Timestamp now = DateUtil.getNowTimeStamp();

        // 2. 작업 지시 정보(JobRes) 조회
        JobRes jr = this.jobResRepository.getJobResById(jrPk);
        if (jr == null) {
            result.success = false;
            result.message = "존재하지 않는 작업 지시입니다.";
            return result;
        }

        // 3. 자재 투입 요청 정보 생성 (필요 시)
        MatProcInputReq mir = null;
        if (jr.getMaterialProcessInputRequestId() == null) {
            mir = new MatProcInputReq();
            mir.setRequestDate(now);
            mir.setRequesterId(user.getId());
            mir.set_audit(user);
            mir = this.matProcInputReqRepository.save(mir);

            jr.setMaterialProcessInputRequestId(mir.getId());
        }

        // 4. 작업 지시 정보(JobRes) 업데이트
        jr.setState("working"); // 상태를 작업중으로 변경
        jr.setProductionDate(prod_date);
        jr.setStartTime(start_time);

        // 임시로 추가 (기존 로직 유지)
        if (jr.getOrderQty() == null) jr.setOrderQty((float) 0);
        if (jr.getFirstWorkCenter_id() == null) jr.setFirstWorkCenter_id(workcenterId);
        if (jr.getProductionPlanDate() == null) jr.setProductionPlanDate(prod_date);
        if (jr.getMaterialId() == null) jr.setMaterialId(matPk);

        jr.setEndTime(end_time);
        if (endDate != null && !endDate.equals("")) {
            jr.setEndDate(Date.valueOf(endDate));
        }
        jr.setShiftCode(shiftCode);
        jr.setWorkCenter_id(workcenterId);
        jr.setEquipment_id(equipmentId);
        jr.setDescription(description);
        jr.set_audit(user);
        jr = this.jobResRepository.save(jr);

        // 5. 설비 가동 이력(EquRun) 추가
        EquRun er = new EquRun();
        er.setEquipmentId(equipmentId);
        er.setStartDate(start_time);
        er.setWorkOrderNumber(order_num);
        er.setRunState("run");
        er.set_audit(user);
        er.setSpjangcd(spjangcd);

        this.equRunRepository.save(er);

        result.success = true;
        result.message = "작업이 시작되었습니다.";
        result.data = jr;

        return result;
    }

    /**
     * 생산 중지 및 재가동 (PDA용)
     * 경로: /pda/production/stop_save (또는 기존 설정에 맞게 변경)
     */
    @PostMapping("/stop_save")
    @Transactional
    public AjaxResult stopSave(
            @RequestParam(value = "jr_pk", required = false) Integer jr_pk,
            @RequestParam(value = "Equipment_id", required = false) Integer Equipment_id,
            @RequestParam(value = "WorkOrderNumber", required = false) String WorkOrderNumber,
            @RequestParam(value = "stop_date", required = false) String stop_date,
            @RequestParam(value = "stopTime", required = false) String stopTime,
            @RequestParam(value = "Description", required = false) String Description,
            @RequestParam(value = "StopCause_id", required = false) Integer StopCause_id,
            @RequestParam(value = "spjangcd", defaultValue = "ZZ") String spjangcd,
            HttpServletRequest request,
            Authentication auth) {

        AjaxResult result = new AjaxResult();
        User user = (User) auth.getPrincipal();
        Timestamp now = DateUtil.getNowTimeStamp();

        // 1. 진행 중인 가동 정보 조회
        Optional<EquRun> runningRunOpt = equRunRepository.findLatestRunningByEquipmentAndOrder(Equipment_id, WorkOrderNumber);

        if (runningRunOpt.isPresent()) {
            // --- 중지 로직 ---
            EquRun equ = runningRunOpt.get();

            // 현재 시간의 초를 구함
            int currentSecond = LocalDateTime.now().getSecond();
            // stopTime (예: "10:32")에 초를 붙임 → "10:32:47"
            String fullStopTime = stopTime + ":" + String.format("%02d", currentSecond);
            // 최종 Timestamp 생성
            Timestamp stop_time = Timestamp.valueOf(stop_date + " " + fullStopTime);

            equ.setEndDate(stop_time); // 중지 시각 설정
            equ.setRunState("stop");
            equ.setStopCauseId(StopCause_id);
            equ.setDescription(Description);
            equ.set_audit(user);
            equ.setSpjangcd(spjangcd);

            equRunRepository.save(equ);

            // 작업 지시 상태를 '중지됨'으로 변경
            jobResRepository.updateStateById(jr_pk, "stopped");

            result.success = true;
            result.message = "작업이 일시중지 되었습니다.";
        } else {
            // --- 재가동 로직 ---
            // 이미 다른 작업이 해당 설비에서 가동 중인지 체크
            long runningCount = equRunRepository.countByEquipmentIdAndRunState(Equipment_id, "run");
            if (runningCount > 0) {
                result.success = false;
                result.message = "해당 설비는 이미 다른 작업이 진행 중입니다.";
                return result;
            }

            EquRun er = new EquRun();
            er.setEquipmentId(Equipment_id);
            er.setStartDate(now);
            er.setWorkOrderNumber(WorkOrderNumber);
            er.setRunState("run");
            er.set_audit(user);
            er.setSpjangcd(spjangcd);

            this.equRunRepository.save(er);

            // 작업 지시 상태를 '작업중'으로 변경
            jobResRepository.updateStateById(jr_pk, "working");

            result.success = true;
            result.message = "작업이 재개 되었습니다.";
        }

        return result;
    }

    /**
     * 생산 작업 완료 (PDA용)
     * 경로: /pda/production/work_finish
     */
    @PostMapping("/work_finish")
    @Transactional
    public AjaxResult workFinish(
            @RequestParam(value = "id", required = false) Integer jrPk,
            @RequestParam(value = "lot_num", required = false) String lotNum,
            @RequestParam(value = "order_qty", required = false) Float orderQty,
            @RequestParam(value = "good_qty", required = false) Float goodQty,
            @RequestParam(value = "defect_qty", required = false) Float defectQty,
            @RequestParam(value = "loss_qty", required = false) Float lossQty,
            @RequestParam(value = "scrap_qty", required = false) Float scrapQty,
            @RequestParam(value = "shift_code", required = false) String shiftCode,
            @RequestParam(value = "mat_pk", required = false) Integer materialId,
            @RequestParam(value = "workcenter_id", required = false) Integer workcenterId,
            @RequestParam(value = "equipment_id", required = false) Integer equipmentId,
            @RequestParam(value = "prod_date", required = false) String prodDate,
            @RequestParam(value = "start_time", required = false) String startTime,
            @RequestParam(value = "end_date", required = false) String endDate,
            @RequestParam(value = "end_time", required = false) String endTime,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "order_num", required = false) String order_num,
            HttpServletRequest request,
            Authentication auth) {

        AjaxResult result = new AjaxResult();
        User user = (User) auth.getPrincipal();

        // 현재 시간의 초를 가져옴
        int currentSecond = LocalDateTime.now().getSecond();
        String secondStr = String.format(":%02d", currentSecond);

        // start_time 및 end_time 조합
        Timestamp start_time = Timestamp.valueOf(prodDate + " " + startTime + secondStr);
        Timestamp end_time = Timestamp.valueOf(endDate + " " + endTime + secondStr);
        Timestamp prod_date = CommonUtil.tryTimestamp(prodDate);

        // 1. 투입 내역 확인
        List<MaterialConsume> mcList = this.matConsuRepository.findByJobResponseId(jrPk);
        if (mcList.size() == 0) {
            result.success = false;
            result.message = "저장된 투입내역이 없습니다. \n 투입내역을 저장해주세요.";
            return result;
        }

        // 2. 차수 내역 확인
        List<MaterialProduce> mp = this.matProduceRepository.findByJobResponseIdAndMaterialId(jrPk, materialId);
        if (mp.size() == 0) {
            result.success = false;
            result.message = "저장된 차수내역이 없습니다. \n 차수내역을 저장해주세요.";
            return result;
        }

        // 3. 부적합 내역 검증 (추가된 부분)
        float chasu_defect_qty = this.productionResultService.getChasuDefectQty(jrPk);
        Map<String, Object> defectSumMap = this.productionResultService.getJobResDefectSum(jrPk);
        float defect_detail_sum = defectSumMap.get("defect_qty") != null ? Float.parseFloat(defectSumMap.get("defect_qty").toString()) : 0;

        if (Float.compare(chasu_defect_qty, defect_detail_sum) != 0) {
            result.success = false;
            result.message = "차수별 생산의 부적합량 합계(" + chasu_defect_qty + ")와 " +
                    "부적합 정보 탭에 등록된 합계(" + defect_detail_sum + ")가 일치하지 않습니다.\n" +
                    "부적합 정보를 먼저 저장해주세요.";
            return result;
        }

        // 4. 작업 지시 정보 업데이트
        JobRes jr = this.jobResRepository.getJobResById(jrPk);
        if (jr == null) {
            result.success = false;
            result.message = "존재하지 않는 작업 지시입니다.";
            return result;
        }

        jr.set_audit(user);
        jr.setLotNumber(lotNum);
        jr.setGoodQty(goodQty);
        jr.setDefectQty(defectQty);
        jr.setLossQty(lossQty);
        jr.setScrapQty(scrapQty);
        jr.setProductionDate(prod_date);
        jr.setEndDate(Date.valueOf(endDate));
        jr.setStartTime(start_time);
        jr.setEndTime(end_time);
        jr.setShiftCode(shiftCode);
        jr.setWorkCenter_id(workcenterId);
        jr.setEquipment_id(equipmentId);
        jr.setDescription(description);
        jr.setState("finished");

        // 불량 처리 로직 (필요 시)
        this.productionResultService.add_jobres_defectqty_inout(jrPk, user.getId());

        jr = this.jobResRepository.save(jr);

        // 4. 설비 가동 정보 종료 처리
        Optional<EquRun> runningRunOpt = equRunRepository.findLatestRunningByEquipmentAndOrder(equipmentId, order_num);
        if (runningRunOpt.isPresent()) {
            EquRun equ = runningRunOpt.get();
            equ.setEndDate(end_time);
            equ.setRunState("complete");
            equ.set_audit(user);
            equRunRepository.save(equ);
        }

        Map<String, Object> item = new HashMap<String, Object>();
        item.put("jr_pk", jrPk);

        result.success = true;
        result.message = "작업이 완료되었습니다.";
        result.data = item;

        return result;
    }


    /**
     * 생산 작업 완료 취소 (PDA용)
     * 경로: /pda/production/finish_cancel
     */
    @PostMapping("/finish_cancel")
    @Transactional
    public AjaxResult finishCancel(
            @RequestParam(value = "jr_pk", required = false) Integer jrPk,
            @RequestParam(value = "spjangcd", defaultValue = "ZZ") String spjangcd,
            HttpServletRequest request,
            Authentication auth) {

        AjaxResult result = new AjaxResult();
        User user = (User) auth.getPrincipal();

        // 1. 작업 지시 정보 조회 및 상태 변경
        JobRes jr = this.jobResRepository.getJobResById(jrPk);
        if (jr == null) {
            result.success = false;
            result.message = "존재하지 않는 작업 지시입니다.";
            return result;
        }

        jr.setEndTime(null);
        jr.setState("working"); // 다시 작업중으로 변경
        jr.set_audit(user);
        jr = this.jobResRepository.save(jr);

        // 불량 처리 취소 로직
        this.productionResultService.delete_jobres_defectqty_inout(jrPk);

        // 2. 가동 정보 업데이트 및 재시작
        Optional<EquRun> latestComplete = equRunRepository.findLatestCompleteByEquipmentAndOrder(
                jr.getEquipment_id(), jr.getWorkOrderNumber());

        if (latestComplete.isPresent()) {
            EquRun equ = latestComplete.get();
            equ.setRunState("complete_cancel"); // 기존 완료 기록 취소 상태로 변경
            equ.set_audit(user);
            equ.setDescription("완료 취소 (PDA)");
            equ.setSpjangcd(spjangcd);
            equRunRepository.save(equ);

            // 새로운 가동 시작 기록 생성 (다시 작업 중이므로)
            Timestamp now = DateUtil.getNowTimeStamp();
            EquRun newRun = new EquRun();
            newRun.setEquipmentId(jr.getEquipment_id());
            newRun.setWorkOrderNumber(jr.getWorkOrderNumber());
            newRun.setStartDate(now);
            newRun.setRunState("run");
            newRun.setSpjangcd(spjangcd);
            newRun.set_audit(user);

            equRunRepository.save(newRun);
        }

        Map<String, Object> item = new HashMap<String, Object>();
        item.put("jr_pk", jrPk);

        result.success = true;
        result.message = "완료 취소가 정상적으로 처리되었습니다.";
        result.data = item;

        return result;
    }

    /**
     * 차수별 생산 리스트 조회 (PDA용)
     * 경로: /pda/production/chasu_list
     */
    @GetMapping("/chasu_list")
    public AjaxResult getChasuList(
            @RequestParam(value = "jr_pk", required = false) Integer jrPk) {

        AjaxResult result = new AjaxResult();

        // SQL 쿼리 (제공된 텍스트 파일 기준)
        String sql = "SELECT id " +
                "     , \"LotIndex\" as chasu " +
                "     , \"LotNumber\" as lot_no " +
                "     , \"GoodQty\" as good_qty " +
                "     , \"DefectQty\" as defect_qty " +
                "     , \"LossQty\" as loss_qty " +
                "     , \"ScrapQty\" as scrap_qty " +
                "     , to_char(\"EndTime\", 'YYYY-MM-DD HH24:MI') as end_time " +
                "     , to_char(\"StartTime\", 'YYYY-MM-DD HH24:MI') as start_time " +
                "     , case " +
                "         when \"_modified\" is null then to_char(\"_created\", 'YYYY-MM-DD HH24:MI') " +
                "         else to_char(\"_modified\", 'YYYY-MM-DD HH24:MI') " +
                "       end as input_time " +
                " FROM mat_produce " +
                " WHERE \"JobResponse_id\" = :jrPk " +
                " ORDER BY \"LotIndex\"";

        MapSqlParameterSource dicParam = new MapSqlParameterSource();
        dicParam.addValue("jrPk", jrPk);

        try {
            // 기존에 사용하시던 sqlRunner 또는 jdbcTemplate 등을 사용하여 조회
            List<Map<String, Object>> items = this.sqlRunner.getRows(sql, dicParam);

            result.success = true;
            result.data = items;
        } catch (Exception e) {
            result.success = false;
            result.message = "데이터 조회 중 오류가 발생했습니다: " + e.getMessage();
        }

        return result;
    }


    /**
     * 차수별 생산 삭제 (PDA용)
     * 경로: /pda/production/chasu_del
     * 설명: 선택한 차수(들)을 삭제합니다.
     *       웹 API 로직을 기반으로 특정 ID를 삭제하도록 조정했습니다.
     */
    @PostMapping("/chasu_del")
    @Transactional
    public AjaxResult chasuDel(
            @RequestParam(value = "jr_pk") Integer jrPk,
            @RequestParam(value = "ids") List<Integer> chasuIds,
            HttpServletRequest request,
            Authentication auth) {

        AjaxResult result = new AjaxResult();
        User user = (User) auth.getPrincipal();

        try {
            for (Integer chasuId : chasuIds) {
                MaterialProduce mp = this.matProduceRepository.findById(chasuId).orElse(null);
                if (mp == null) continue;

                String lotNumber = mp.getLotNumber();

                // 1. 상태 체크 (투입요청 또는 사용 중인지)
                MaterialLot ml = this.matLotRepository.getByLotNumber(lotNumber);
                if (ml != null) {
                    List<MatProcInput> mpiList = this.matProcInputRepository.findByMaterialLotId(ml.getId());
                    List<MatLotCons> mlcList = this.matLotConsRepository.findByMaterialLotId(ml.getId());

                    if (mpiList.size() > 0 || mlcList.size() > 0) {
                        result.success = false;
                        result.message = "생산LOT(" + lotNumber + ")이 사용 중이거나 요청 중이라 삭제할 수 없습니다.";
                        return result;
                    }
                    // mat_lot 삭제
                    this.matLotRepository.deleteById(ml.getId());
                }

                // 2. 관련 이력 삭제
                // mat_lot_cons 삭제
                this.matLotConsRepository.deleteBySourceTableNameAndSourceDataPk("mat_produce", mp.getId());
                // mat_inout 삭제 (생산입고)
                this.matInoutRepository.deleteBySourceTableNameAndSourceDataPkAndInOutAndInputType("mat_produce", mp.getId(), "in", "produced_in");

                // 3. 소모 내역 삭제
                List<MaterialConsume> mcList = this.matConsuRepository.findByJobResponseIdAndProcessOrderAndLotIndex(jrPk, mp.getProcessOrder(), mp.getLotIndex());
                for (MaterialConsume mc : mcList) {
                    this.matInoutRepository.deleteBySourceTableNameAndSourceDataPkAndInOutAndOutputType("mat_consu", mc.getId(), "out", "consumed_out");
                    this.matConsuRepository.deleteById(mc.getId());
                }

                // 4. mat_produce 삭제
                this.matProduceRepository.deleteById(mp.getId());
            }

            // 5. 전체 합계 재계산 및 JobRes 업데이트
            this.productionResultService.calculate_balance_mat_lot_with_job_res(jrPk);

            Map<String, Object> mapSum = this.productionResultService.getJobResponseGoodDefectQty(jrPk);
            float goodQtySum = mapSum.get("good_qty") != null ? Float.parseFloat(mapSum.get("good_qty").toString()) : 0;
            float defectQtySum = mapSum.get("defect_qty") != null ? Float.parseFloat(mapSum.get("defect_qty").toString()) : 0;

            JobRes jr = this.jobResRepository.getJobResById(jrPk);
            if (jr != null) {
                jr.setGoodQty(goodQtySum);
                jr.setDefectQty(defectQtySum);
                jr.set_audit(user);
                this.jobResRepository.save(jr);
            }

            result.success = true;
            result.message = "선택한 차수가 삭제되었습니다.";

            Map<String, Object> data = new HashMap<>();
            data.put("jr_pk", jrPk);
            data.put("good_qty_sum", goodQtySum);
            data.put("defect_qty_sum", defectQtySum);
            result.data = data;

        } catch (Exception e) {
            result.success = false;
            result.message = "삭제 중 오류 발생: " + e.getMessage();
        }

        return result;
    }

    //chasu_add ===> web api 와 동일하므로 web api로 태음. 이라고 할려했는데 csrf 문제때문에 여기에 그냥 실음
    /**
     * 차수별 생산 추가 (PDA용)
     * 경로: /pda/production/chasu_add
     * 설명: 입력한 양품량(good_qty)만큼 새로운 차수를 생성하고 BOM에 따라 재고를 차감합니다.
     *       웹 API (/api/production/prod_result/chasu_add) 로직을 PDA 환경에 맞춰 구성했습니다.
     */
    @PostMapping("/chasu_add")
    @Transactional
    public AjaxResult chasuAdd(
            @RequestParam(value = "jr_pk") Integer jrPk,
            @RequestParam(value = "good_qty") float goodQty,
            @RequestParam(value = "spjangcd", defaultValue = "ZZ") String spjangcd,
            HttpServletRequest request,
            Authentication auth) {

        AjaxResult result = new AjaxResult();
        User user = (User) auth.getPrincipal();
        Timestamp now = DateUtil.getNowTimeStamp();
        LocalDate date = LocalDate.now();
        LocalTime time = LocalTime.now();

        try {
            // 1. 기초 정보 조회 및 검증
            JobRes jr = this.jobResRepository.getJobResById(jrPk);
            if (jr == null) {
                result.success = false;
                result.message = "존재하지 않는 작업 지시입니다.";
                return result;
            }

            if (jr.getWorkCenter_id() == null) {
                result.success = false;
                result.message = "워크센터가 지정되지 않았습니다.";
                return result;
            }

            Material m = this.materialRepository.getMaterialById(jr.getMaterialId());
            if (m == null || m.getStoreHouseId() == null) {
                result.success = false;
                result.message = "생산제품 정보가 없거나 기본 창고가 설정되어 있지 않습니다.";
                return result;
            }

            // 2. 차수 정보 계산
            List<MaterialProduce> mpList = this.matProduceRepository.findByJobResponseId(jr.getId());
            Integer chasu = mpList.size() + 1;

            Workcenter wc = this.workcenterRepository.getWorkcenterById(jr.getWorkCenter_id());
            Integer processId = wc.getProcessId();

            // 3. 로트번호 생성
            String lotPrefix = "product".equals(this.materialGroupRepository.getMatGrpById(m.getMaterialGroupId()).getMaterialType()) ? "P" : "B";
            String lotNumber = this.lotService.make_production_lot_in_number(lotPrefix);

            // 4. mat_produce 생성 (차수 실적)
            MaterialProduce mp = new MaterialProduce();
            mp.setJobResponseId(jr.getId());
            mp.setMaterialId(m.getId());
            mp.setProcessId(processId);
            mp.setProcessOrder(1);
            mp.setLotIndex(chasu);
            mp.setState("finished");
            mp.set_status("a");
            mp.setStoreHouseId(m.getStoreHouseId());
            mp.setProductionDate(jr.getProductionDate());
            mp.setStartTime(jr.getStartTime());
            mp.setEndTime(now);
            mp.setShiftCode(jr.getShiftCode());
            mp.setWorkCenterId(jr.getWorkCenter_id());
            mp.setEquipmentId(jr.getEquipment_id());
            mp.setGoodQty(goodQty);
            mp.setDescription("PDA 차수생산");
            mp.setActorId(user.getId());
            mp.set_audit(user);
            mp.setLastProcessYN("Y");
            mp.setLotNumber(lotNumber);
            mp.setSpjangcd(spjangcd);
            mp = this.matProduceRepository.save(mp);

            // 5. mat_lot 생성 (생산된 재고 로트)
            MaterialLot ml = new MaterialLot();
            ml.setLotNumber(lotNumber);
            ml.setMaterialId(m.getId());
            ml.setInputDateTime(now);
            ml.setInputQty(goodQty);
            ml.setCurrentStock(goodQty);
            ml.setDescription(chasu + "차수생산 (PDA)");
            ml.setSourceDataPk(mp.getId());
            ml.setSourceTableName("mat_produce");
            ml.setStoreHouseId(mp.getStoreHouseId());
            ml.set_audit(user);
            ml.setSpjangcd(spjangcd);
            this.matLotRepository.save(ml);

            // 6. BOM 기반 자재 소모 처리
            List<Map<String, Object>> bomMatItems = this.productionResultService.get_chasu_bom_mat_qty_list(mp.getId());
            if (bomMatItems.isEmpty()) {
                result.success = false;
                result.message = "BOM구성이 없습니다.";
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return result;
            }

            for (Map<String, Object> bomMap : bomMatItems) {
                float chasuBomQty = Float.parseFloat(bomMap.get("chasu_bom_qty").toString());
                int consumeMatPk = (int) bomMap.get("mat_pk");
                String matName = bomMap.get("mat_name").toString();
                Material consMat = this.materialRepository.getMaterialById(consumeMatPk);
                String lotUseYn = bomMap.get("lotUseYn").toString();

                if ("Y".equals(lotUseYn)) {
                    List<Map<String, Object>> mpiList = this.productionResultService.getMaterialProcessInputList(jr.getId(), consumeMatPk);
                    float totalLotQty = (float) mpiList.stream().mapToDouble(i -> Double.parseDouble(i.get("curr_qty").toString())).sum();

                    if (totalLotQty < chasuBomQty) {
                        result.success = false;
                        result.message = "가용한 LOT 재고가 부족합니다. (" + matName + ")";
                        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                        return result;
                    }

                    float remainQty = chasuBomQty;
                    for (Map<String, Object> mpiMap : mpiList) {
                        if (remainQty <= 0) break;
                        float currentStock = Float.parseFloat(mpiMap.get("curr_qty").toString());
                        if (currentStock <= 0) continue;

                        MatLotCons mlc = new MatLotCons();
                        mlc.setMaterialLotId((int) mpiMap.get("ml_id"));
                        mlc.setOutputDateTime(now);
                        mlc.setSourceDataPk(mp.getId());
                        mlc.setSourceTableName("mat_produce");
                        mlc.set_audit(user);
                        mlc.setSpjangcd(spjangcd);

                        if (currentStock >= remainQty) {
                            mlc.setOutputQty(remainQty);
                            remainQty = 0;
                        } else {
                            mlc.setOutputQty(currentStock);
                            remainQty -= currentStock;
                        }
                        this.matLotConsRepository.save(mlc);
                    }
                } else {
                    // 일반 품목 재고 체크
                    if ("1".equals(consMat.getUseyn())) {
                        result.success = false;
                        result.message = "사용 불가능한 품목이 BOM에 있습니다. (" + matName + ")";
                        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                        return result;
                    }
                    if (!"0".equals(consMat.getMtyn())) {
                        if (consMat.getCurrentStock() == null || consMat.getCurrentStock() < chasuBomQty) {
                            result.success = false;
                            result.message = "품목 재고가 부족합니다. (" + matName + ")";
                            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                            return result;
                        }
                    }
                }

                // mat_consume 및 mat_inout(출고) 생성
                MaterialConsume mc = new MaterialConsume();
                mc.setJobResponseId(jr.getId());
                mc.setMaterialId(consumeMatPk);
                mc.setProcessOrder(mp.getProcessOrder());
                mc.setLotIndex(mp.getLotIndex());
                mc.setStartTime(now);
                mc.setEndTime(now);
                mc.setConsumedQty(chasuBomQty);
                mc.set_audit(user);
                mc.setState("finished");
                mc.setSpjangcd(spjangcd);
                mc = this.matConsuRepository.save(mc);

                MaterialInout mic = new MaterialInout();
                mic.setMaterialId(mc.getMaterialId());
                mic.setStoreHouseId(consMat.getStoreHouseId());
                mic.setLotNumber(mp.getLotNumber());
                mic.setInoutDate(date);
                mic.setInoutTime(time);
                mic.setInOut("out");
                mic.setOutputType("consumed_out");
                mic.setOutputQty(chasuBomQty);
                mic.setSourceDataPk(mc.getId());
                mic.setSourceTableName("mat_consu");
                mic.setState("confirmed");
                mic.set_audit(user);
                mic.setSpjangcd(spjangcd);
                this.matInoutRepository.save(mic);
            }

            // 7. 생산 입고 이력(mat_inout) 생성
            MaterialInout mip = new MaterialInout();
            mip.setMaterialId(m.getId());
            mip.setStoreHouseId(m.getStoreHouseId());
            mip.setLotNumber(mp.getLotNumber());
            mip.setInoutDate(date);
            mip.setInoutTime(time);
            mip.setInOut("in");
            mip.setInputType("produced_in");
            mip.setInputQty(goodQty);
            mip.setSourceDataPk(mp.getId());
            mip.setSourceTableName("mat_produce");
            mip.setState("confirmed");
            mip.set_audit(user);
            mip.setSpjangcd(spjangcd);
            this.matInoutRepository.save(mip);

            // 8. 최종 합계 업데이트
            this.productionResultService.calculate_balance_mat_lot_with_job_res(jrPk);
            Map<String, Object> mapSum = this.productionResultService.getJobResponseGoodDefectQty(jrPk);
            jr.setGoodQty(mapSum.get("good_qty") != null ? Float.parseFloat(mapSum.get("good_qty").toString()) : 0);
            jr.setDefectQty(mapSum.get("defect_qty") != null ? Float.parseFloat(mapSum.get("defect_qty").toString()) : 0);
            this.jobResRepository.save(jr);

            result.success = true;
            result.message = "차수가 추가되었습니다.";
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("jr_pk", jrPk);
            responseData.put("lot_number", lotNumber);
            responseData.put("good_qty_sum", jr.getGoodQty());
            result.data = responseData;

        } catch (Exception e) {
            result.success = false;
            result.message = "차수 추가 중 오류: " + e.getMessage();
        }

        return result;
    }

    /**
     * 차수별 생산 실적 선택 저장 (PDA용)
     * 경로: /pda/production/chasu_save
     * 설명: 선택한 차수들의 양품량 및 부적합량을 저장합니다.
     */
    @PostMapping("/chasu_save")
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public AjaxResult chasuSave(
            @RequestBody List<Map<String, Object>> chasuList,
            Authentication auth) {

        AjaxResult result = new AjaxResult();
        List<Map<String, Object>> resultList = new ArrayList<>();

        try {
            for (Map<String, Object> chasu : chasuList) {
                Integer jrPk = Integer.parseInt(chasu.get("jr_pk").toString());
                Integer mpId = Integer.parseInt(chasu.get("mp_id").toString());
                Float goodQty = Float.parseFloat(chasu.get("good_qty").toString());
                Float defectQty = Float.parseFloat(chasu.get("defect_qty").toString());

                // saveSingleChasu는 기존 웹 서비스 로직을 그대로 사용하거나
                // 아래와 같이 내부에서 구현합니다.
                AjaxResult singleResult = saveSingleChasu(jrPk, mpId, goodQty, defectQty, auth);

                if (!singleResult.success) {
                    // 실패 시 롤백 + 에러 메시지 반환
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    return singleResult;
                }

                resultList.add((Map<String, Object>) singleResult.data);
            }

            result.success = true;
            result.message = "선택한 차수 정보가 저장되었습니다.";
            result.data = resultList;
        } catch (Exception e) {
            result.success = false;
            result.message = "저장 중 오류 발생: " + e.getMessage();
        }

        return result;
    }

    /**
     * 단일 차수 저장 로직 (참고용)
     */
    private AjaxResult saveSingleChasu(Integer jrPk, Integer mpId, Float goodQty, Float defectQty, Authentication auth) {
        AjaxResult result = new AjaxResult();
        User user = (User) auth.getPrincipal();

        MaterialProduce mp = this.matProduceRepository.findById(mpId).orElse(null);
        if (mp == null) {
            result.success = false;
            result.message = "존재하지 않는 차수 정보입니다. (ID: " + mpId + ")";
            return result;
        }

        mp.setGoodQty(goodQty);
        mp.setDefectQty(defectQty);
        mp.set_audit(user);
        this.matProduceRepository.save(mp);

        // 연관된 mat_lot 수량 업데이트
        MaterialLot ml = this.matLotRepository.findBySourceTableNameAndSourceDataPk("mat_produce", mpId);
        if (ml != null) {
            ml.setInputQty(goodQty);
            ml.setCurrentStock(goodQty);
            ml.set_audit(user);
            this.matLotRepository.save(ml);
        }

        // 연관된 mat_inout 수량 업데이트
        this.matInoutRepository.updateQtyBySource("mat_produce", mpId, "in", goodQty);

        // JobRes 합계 갱신 로직 등 추가 필요 (전체 루프 종료 후 한 번만 호출하는 것이 효율적)

        Map<String, Object> data = new HashMap<>();
        data.put("mp_id", mpId);
        result.success = true;
        result.data = data;
        return result;
    }

    /**
     * 부적합 리스트 조회 (PDA용)
     * 경로: /pda/production/defect_list
     */
    @GetMapping("/defect_list")
    public AjaxResult getDefectList(
            @RequestParam(value = "jr_pk", required = false) Integer jrPk,
            @RequestParam(value = "workcenter_id", required = false) Integer workcenterId) {

        AjaxResult result = new AjaxResult();

        // SQL 쿼리 (제공된 텍스트 파일 기준)
        String sql = """
             with TOT as (
                      select jrd.id as jrd_id
                      , jrd."DefectQty" as defect_qty
                      , jrd."DefectType_id"  as defect_id
                      , jrd."Description" as defect_remark
                      from job_res_defect jrd 
                      where jrd."JobResponse_id" = :jrPk
                   ), a as(
                     select 
                     jr."WorkCenter_id"
                     , wc."Process_id"
                     , pdt."DefectType_id" as defect_id
                     , dt."Name" as defect_type
                     , coalesce( TOT.defect_qty,0) as defect_qty
                     , TOT.jrd_id
                     , TOT.defect_remark
                     from job_res jr 
                     left join work_center wc on wc.id=jr."WorkCenter_id"  
                     left join proc_defect_type pdt on pdt."Process_id" =wc."Process_id" 
                     inner join defect_type dt on dt.id = pdt."DefectType_id" 
                     left join TOT on TOT.defect_id=dt.id
                     where jr.id = :jrPk
                     )
                     select * from a
            """;

        MapSqlParameterSource dicParam = new MapSqlParameterSource();
        dicParam.addValue("jrPk", jrPk);
        dicParam.addValue("workcenterId", workcenterId);

        try {
            List<Map<String, Object>> items = this.sqlRunner.getRows(sql, dicParam);
            result.success = true;
            result.data = items;
        } catch (Exception e) {
            result.success = false;
            result.message = "부적합 데이터 조회 중 오류가 발생했습니다: " + e.getMessage();
        }

        return result;
    }


    /**
     * 부적합 정보 저장 (PDA용)
     * 경로: /pda/production/defect_save
     */
    @PostMapping("/defect_save")
    @Transactional
    public AjaxResult defectSave(
            @RequestParam(value = "jr_pk") Integer jrPk,
            @RequestParam(value = "spjangcd", defaultValue = "ZZ") String spjangcd,
            @RequestBody List<Map<String, Object>> defectList, // JSON Body 직접 수신
            HttpServletRequest request,
            Authentication auth) {

        AjaxResult result = new AjaxResult();
        User user = (User) auth.getPrincipal();

        try {
            JobRes jr = this.jobResRepository.getJobResById(jrPk);
            if (jr == null) {
                result.success = false;
                result.message = "존재하지 않는 작업 지시입니다.";
                return result;
            }

            // 기존 부적합 정보 삭제 (웹 API 로직 동일)
            jobResDefectRepository.deleteByJobResponseId(jrPk);

            float jobresTotalDefectQty = 0;

            for (Map<String, Object> item : defectList) {
                Integer defectId = Integer.parseInt(item.get("defect_id").toString());
                Float defectQty = Float.parseFloat(item.get("defect_qty").toString());
                String defectRemark = item.get("defect_remark") != null ? item.get("defect_remark").toString() : "";

                if (defectQty <= 0) continue; // 수량이 0 이하인 것은 저장하지 않음

                JobResDefect jrd = new JobResDefect();
                jrd.setJobResponseId(jrPk);
                jrd.setDefectTypeId(defectId);
                jrd.setDefectQty(defectQty);
                jrd.setDescription(defectRemark);
                jrd.setProcessOrder(1); // 기본값 설정
                jrd.setLotIndex(0);
                jrd.set_audit(user);
                jrd.setSpjangcd(spjangcd);

                this.jobResDefectRepository.save(jrd);
                jobresTotalDefectQty += defectQty;
            }

            // 1. 차수별 생산 탭에서 집계된 전체 부적합량 조회
            // (mat_produce 테이블의 sum(DefectQty)를 가져오는 기존 서비스 활용)
            float chasu_defect_qty = this.productionResultService.getChasuDefectQty(jrPk);

            // 2. 검증: 차수별 부적합 합계 vs 부적합 탭 입력 합계
            if (Float.compare(chasu_defect_qty, jobresTotalDefectQty) != 0) {
                result.success = false;
                result.message = "차수별 생산의 부적합량 합계(" + chasu_defect_qty + ")와 " +
                        "부적합 탭의 상세 합계(" + jobresTotalDefectQty + ")가 일치하지 않습니다.";
                // 롤백 강제
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return result;
            }

            // 3. JobRes 마스터 부적합량 업데이트
            jr.setDefectQty(jobresTotalDefectQty);
            jr.set_audit(user);
            this.jobResRepository.save(jr);

            result.success = true;
            result.message = "부적합 정보가 저장되었습니다.";
            Map<String, Object> resData = new HashMap<>();
            resData.put("jr_pk", jrPk);
            resData.put("total_defect", jobresTotalDefectQty);
            result.data = resData;

        } catch (Exception e) {
            result.success = false;
            result.message = "부적합 정보 저장 중 오류: " + e.getMessage();
        }

        return result;
    }

}