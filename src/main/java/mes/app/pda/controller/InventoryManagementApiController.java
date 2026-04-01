package mes.app.pda.controller;

import mes.app.inventory.service.LotService;
import mes.app.pda.service.InventoryManagementApiService;
import mes.domain.entity.MaterialLot;
import mes.domain.entity.User;
import mes.domain.model.AjaxResult;
import mes.domain.repository.MatLotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pda/inventory/read_receipt")
public class InventoryManagementApiController {

    @Autowired
    private InventoryManagementApiService inventoryManagementApiService;

    @Autowired
    MatLotRepository matLotRepository;

    @Autowired
    LotService lotService;

    @GetMapping("/read")
    public AjaxResult getMaterialInOutList(@RequestParam String srchStartDt,
                                           @RequestParam String srchEndDt,
                                           @RequestParam String inout,
                                           @RequestParam String housePk,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam String spjangcd
                                           ){

        List<Map<String, Object>> matInoutList = inventoryManagementApiService.getMatInoutList(srchStartDt, srchEndDt, inout, housePk, keyword, spjangcd);

        AjaxResult result = new AjaxResult();
        result.success = true;
        result.data = matInoutList;
        return result;
    }
    @GetMapping("/detail")
    public AjaxResult getMaterialInOutDetail(@RequestParam Integer mat_id,
                                             @RequestParam Integer mio_pk,
                                             @RequestParam String spjangcd
    ){

        List<Map<String, Object>> matInoutDetail = inventoryManagementApiService.getMatInoutDetail(mat_id, mio_pk, spjangcd);

        AjaxResult result = new AjaxResult();
        result.success = true;
        result.data = matInoutDetail;
        return result;
    }

    @PostMapping("/receive/save")
    @Transactional
    public AjaxResult ReceiveSave(
            @RequestBody List<Map<String, Object>> data,
            Authentication auth
    ){
        AjaxResult result = new AjaxResult();
        if (data == null || data.isEmpty()) {
            result.success = false;
            result.message = "전송된 데이터가 없습니다.";
            return result;
        }

        User user = (User) auth.getPrincipal();
        Timestamp today = new Timestamp(System.currentTimeMillis());

        // 1. 기본 정보 추출 (리스트의 첫 번째 항목 기준)
        String spjangcd = data.get(0).get("spjangcd").toString();
        Integer materialId = (Integer) data.get(0).get("material_id");
        Integer sourdataPk = Integer.parseInt(data.get(0).get("mio_pk").toString());
        Double baljuQty = Double.parseDouble(data.get(0).get("BaljuQty").toString());

        // 2. 현재 DB에 이미 할당된 Lot 수량 합계 조회
        Float currStock = matLotRepository.sumInputQtyByConditions(materialId, "mat_inout", sourdataPk, spjangcd);
        if (currStock == null) currStock = 0f;

        // 3. 이번 요청(data 리스트)으로 추가될 총 수량 계산
        double requestTotalQty = data.stream()
                .mapToDouble(m -> Double.parseDouble(m.get("qty").toString()))
                .sum();

        // 4. 초과 검증 (기존 할당량 + 새로 들어온 수량 > 발주 수량)
        if ((currStock + requestTotalQty) > baljuQty) {
            result.success = false;
            result.message = String.format("발주 수량을 초과할 수 없습니다. (발주:%s, 기존할당:%s, 요청:%s)",
                    baljuQty, currStock, requestTotalQty);
            return result;
        }



        //todo: 근데 저장로직 for문이랑 합치는 것도 좋음. 근데 어차피 시간복잡도면에서 상수시간 차이라 그냥 분리해서 가독성 높히는게 유리해보임 ㅇㅇ.
        //다른 품목에 등록된 박스 바코드인지 체크
        for(Map<String, Object> item : data){
            String barcode = item.get("barcode").toString();
            boolean isDuplicate = matLotRepository.existsByMaterialBarCodeAndMaterialIdNot(barcode, materialId);
            if(isDuplicate){
                result.success = false;
                result.message = "'" + barcode + "' 바코드는 다른 품목에 이미 등록된 박스입니다.";
                return result;
            }
        }


        // 5. 저장 로직 진행
        for (Map<String, Object> item : data) {
            MaterialLot ml;
            String lotNumber;

            // LotNumber가 있으면 업데이트, 없으면 신규 생성
            if (item.get("LotNumber") != null && !item.get("LotNumber").toString().isEmpty()) {
                lotNumber = item.get("LotNumber").toString();
                ml = this.matLotRepository.getByLotNumber(lotNumber);
                // 기존 로트 수정 시 수량 변경이 있다면 위에서 계산한 로직을 더 정교하게 다듬어야 함(일단 작성하신 흐름 유지)
            } else {
                lotNumber = this.lotService.make_lot_in_number();
                ml = new MaterialLot();
                ml.setLotNumber(lotNumber);
                ml.setMaterialId(materialId);
                ml.setInputDateTime(today);
                ml.setSourceTableName("mat_inout");
                ml.setSourceDataPk(sourdataPk);
                ml.setEffectiveDate(Timestamp.valueOf("2999-12-31 00:00:00"));
                ml.setStoreHouseId(3);
                ml.set_audit(user);
            }

            float inputQty = Float.parseFloat(item.get("qty").toString());
            ml.setInputQty(inputQty);
            ml.setCurrentStock(inputQty);
            ml.setSpjangcd(spjangcd);
            ml.setMaterialBarCode(item.get("barcode").toString());

            if (item.get("Description") != null) {
                ml.setDescription(item.get("Description").toString());
            }

            this.matLotRepository.save(ml);
        }

        result.success = true;
        result.message = "저장이 완료되었습니다.";
        return result;
    }

    @PostMapping("/lot/delete")
    @Transactional
    public AjaxResult deleteLot(
        @RequestBody List<Map<String, Object>> data,
        Authentication auth
    ){
        AjaxResult result = new AjaxResult();

        try{
            Integer lotId = Integer.parseInt(data.get(0).get("id").toString());

            if (lotId == null) {
                result.success = false;
                result.message = "LOT ID가 없습니다.";
                return result;
            }

            // 1. lot 조회
            MaterialLot ml = matLotRepository.findById(lotId).orElse(null);

            if(ml == null){
                result.success = false;
                result.message = "해당 LOT를 찾을 수 없습니다.";
                return result;
            }

            // 2. 이미 출고된 수량 체크 (현재고 != 입고수량이면 출고된 것)
            if (ml.getCurrentStock() < ml.getInputQty()) {
                result.success = false;
                result.message = "이미 출고된 수량이 있어 삭제할 수 없습니다.";
                return result;
            }

            matLotRepository.delete(ml);

            result.success = true;
            result.message = "LOT가 삭제되었습니다.";

        }catch (Exception e){
            result.success = false;
            result.message = "삭제 중 오류가 발생했습니다: " + e.getMessage();
            throw e;
        }
        return result;
    }
}
