package mes.app.pda.controller;

import lombok.extern.slf4j.Slf4j;
import mes.app.pda.service.ShipmentApiService;
import mes.app.shipment.enums.ShipmentStatus;
import mes.app.util.UtilClass;
import mes.domain.entity.Shipment;
import mes.domain.entity.ShipmentHead;
import mes.domain.model.AjaxResult;
import mes.domain.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/pda/shipment/shipment_order")
public class ShipmentApiController {

    @Autowired
    ShipmentApiService shipmentApiService;

    @Autowired
    MatLotRepository matLotRepository;

    @Autowired
    MatProcInputRepository matProcInputRepository;

    @Autowired
    MatProcInputReqRepository matProcInputReqRepository;

    @Autowired
    ShipmentHeadRepository shipmentHeadRepository;
    @Autowired
    private ShipmentRepository shipmentRepository;

    @PostMapping("/read")
    public AjaxResult getShipmentOrderList(
            @RequestParam(value="srchStartDt", required=false) String date_from,
            @RequestParam(value="srchEndDt", required=false) String date_to,
            @RequestParam(value="chkNotShipped", required=false) String not_ship,
            @RequestParam(value="cboCompany", required=false) Integer comp_pk,
            @RequestParam(value="cboMatGroup", required=false) Integer mat_grp_pk,
            @RequestParam(value="cboMaterial", required=false) Integer mat_pk,
            @RequestParam(value="keyword", required=false) String keyword,
            HttpServletRequest request) {

        String state = "";
        AjaxResult result = new AjaxResult();

        String cookieValue = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("dongyoung_jsessionid".equals(cookie.getName())) {
                    cookieValue = cookie.getValue();
                    break;
                }
            }
        }
        System.out.println("받은 쿠키 dongyoung_jsessionid = " + cookieValue);

        try{
            if("Y".equals(not_ship)) {
                state= "ordered";
            } else {
                state = "";
            }

            if(!date_from.contains("-")){
                date_from = UtilClass.toContainsHyphenDateString(date_from);
            }
            if(!date_to.contains("-")){
                date_to = UtilClass.toContainsHyphenDateString(date_to);
            }

            List<Map<String, Object>> items = this.shipmentApiService.ApigetShipmentOrderList(date_from, date_to, state, comp_pk, mat_grp_pk, mat_pk, keyword);
            //List<Map<String, Object>> test = Collections.emptyList();


            result.data = items;

        }catch(Exception e){
            result.success = false;
            result.data = null;
            result.message = "서버에러 발생";
        }

        return result;
    }

    @GetMapping("/shipment_list")
    public AjaxResult getShipmentList(
            @RequestParam(value = "header_id", required = false) Integer shipment_header_id,
            HttpServletRequest request) {

        List<Map<String, Object>> items = this.shipmentApiService.getShipmentList(shipment_header_id);

        String cookieValue = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("dongyoung_jsessionid".equals(cookie.getName())) {
                    cookieValue = cookie.getValue();
                    break;
                }
            }
        }
        System.out.println("받은 쿠키 dongyoung_jsessionid = " + cookieValue);
        AjaxResult result = new AjaxResult();
        result.data = items;

        return result;
    }

    //lot 바코드 스캔
    @GetMapping("/lot_scan")
    public AjaxResult getLotInfo(@RequestParam String lotNum, Authentication auth){

        AjaxResult result = new AjaxResult();

        //lot번호로 lot 정보 로드
        List<Map<String, Object>> results  = shipmentApiService.getByLotNumber(lotNum);

        try{
            result.data = results.get(0);

        }catch(Exception e){
            result.data = null;
        }
        return result;
    }


    // 출고등록
    @PostMapping("/shipment_save")
    public AjaxResult ShipSave(@RequestBody Map<String, Object> request,
                               Authentication auth){
        //해당 로직은 부분출고는 안되며, 바코드를 찍으며 하나의 출고씩 나가는 로직임 (여러개의 shipment_head x)


        /** init variables **/
        AjaxResult result = new AjaxResult();

        List<Map<String, Object>> BarcodeList = (List<Map<String, Object>>) request.get("results");
        List<Map<String, Object>> shipmentList = (List<Map<String, Object>>) request.get("shipmentList");

        Integer head_id = UtilClass.parseInteger(shipmentList.get(0).get("sh_id"));

        ShipmentHead smh = this.shipmentHeadRepository.getShipmentHeadById(head_id);

        //validation chk
        if(smh == null) {result.success = false; result.message = "해당 출하정보가 없습니다.";}


        // business logic
        if(!smh.getState().equals(ShipmentStatus.SHIPPED.getLabel())){
            List<Shipment> smList = shipmentRepository.findByShipmentHeadId(head_id);

            String sourceData = smList.get(0).getSourceTableName() == null ? "" : smList.get(0).getSourceTableName();
            AjaxResult innerResult = shipmentApiService.ShipmenSaveActionByLot(smList, head_id, BarcodeList, sourceData, auth);

            if(!innerResult.success) return  innerResult;
        }
        return result;
    }

    //출고현황 (내역)
    @GetMapping("/shipped_list")
    public AjaxResult getShipmentHeadList(
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            @RequestParam String keyword,
            @RequestParam String status
    ){

        AjaxResult result = new AjaxResult();

        if(!dateFrom.contains("-")){
            dateFrom = UtilClass.toContainsHyphenDateString(dateFrom);
        }
        if(!dateTo.contains("-")){
            dateTo = UtilClass.toContainsHyphenDateString(dateTo);
        }

        result.data = this.shipmentApiService.getShipmentHeadList(dateFrom, dateTo, keyword, status);

        return result;
    }

    @GetMapping("/shipment_item_list")
    public AjaxResult getShipmentDetailItemList(
            @RequestParam String headId
    ){

        List<Map<String, Object>> items = this.shipmentApiService.getShipmentDetailItemList(headId, null);

        AjaxResult result = new AjaxResult();
        result.data = items;

        return result;
    }
}
