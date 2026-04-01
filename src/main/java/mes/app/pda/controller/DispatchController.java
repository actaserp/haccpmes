package mes.app.pda.controller;

import mes.app.inventory.service.MaterialMoveService;
import mes.app.pda.service.DisaptchService;
import mes.domain.entity.MatLotCons;
import mes.domain.entity.MaterialConsume;
import mes.domain.entity.MaterialInout;
import mes.domain.entity.User;
import mes.domain.model.AjaxResult;
import mes.domain.repository.MatInoutRepository;
import mes.domain.repository.MatLotConsRepository;
import mes.domain.services.CommonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.DispatcherServlet;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/pda/inventory/dispatch")
public class DispatchController {

    @Autowired
    DisaptchService disaptchService;

    @Autowired
    MatInoutRepository matInoutRepository;

    @Autowired
    MatLotConsRepository matLotConsRepository;

    @GetMapping("/read")
    public AjaxResult getLotListByBox(@RequestParam String barcode){

        List<Map<String, Object>> data = disaptchService.getLotListByBoxBarcode(barcode);

        AjaxResult result = new AjaxResult();
        result.success = true;
        result.data = data;
        return result;
    }

    @PostMapping("/lot_move")
    @Transactional
    public AjaxResult PdaLotMove(
            @RequestBody List<Map<String, Object>> lotList,
            Authentication auth
    )
    {
        User user = (User) auth.getPrincipal();
        AjaxResult result = new AjaxResult();

        LocalDate nowDate = LocalDate.now();
        LocalTime nowTime = LocalTime.now();

        Integer to_storehouse_id = (Integer) lotList.get(0).get("store_house_id");

        String spjangcd = Optional.ofNullable(lotList.get(0).get("spjangcd"))
                .map(Object::toString)
                .orElse("ZZ");

        Timestamp today_timestamp = new Timestamp(System.currentTimeMillis());


        //todo; lot만 소진시키고 재고는 차감되면 안됨.
        if(to_storehouse_id == 999){  /// 창고이동이 아니라 그냥 lot재고 삭제하는 불출 의미
            for(Map<String, Object> map : lotList){
                int mat_id = (int) map.get("mat_id");
                int mat_lot_id = (int) map.get("mat_lot_id");
                int from_storehouse_id = (int) map.get("storehouse_id");

                String lotnumber = map.get("LotNumber").toString();
                float currentStock = Float.parseFloat(map.get("CurrentStock").toString());

//                MaterialInout mio_out = new MaterialInout();
//                mio_out.setMaterialId(mat_id);
//                mio_out.setStoreHouseId(from_storehouse_id);
//                mio_out.setInOut("out");
//                mio_out.setLotNumber(lotnumber);
//                mio_out.setOutputType("mobile_release"); //모바일로 불출한것.
//                mio_out.setOutputQty(currentStock);
//                mio_out.setInoutDate(nowDate);
//                mio_out.setInoutTime(nowTime);
//                mio_out.setDescription("모바일자재불출");
//                mio_out.setState("confirmed");
//                mio_out.set_status("a");
//                mio_out.set_audit(user);
//                mio_out.setSpjangcd(spjangcd);
//                this.matInoutRepository.save(mio_out);

                MatLotCons mlc = new MatLotCons();
                mlc.setOutputDateTime(today_timestamp);
                mlc.setOutputQty(currentStock);
                mlc.setDescription("모바일자재불출");
                mlc.setSourceDataPk(mat_lot_id);
                mlc.setSourceTableName("mat_lot");
                mlc.setSpjangcd(spjangcd);
                mlc.set_created(today_timestamp);
                mlc.setMaterialLotId(mat_lot_id);
                this.matLotConsRepository.save(mlc);
            }
        }else{
            for(Map<String, Object> map : lotList){
                int mat_id = (int) map.get("mat_id");
                int mat_lot_id = (int) map.get("mat_lot_id");
                int from_storehouse_id = (int) map.get("storehouse_id");

                String lotnumber = map.get("LotNumber").toString();
                float currentStock = Float.parseFloat(map.get("CurrentStock").toString());
                if(from_storehouse_id==to_storehouse_id){
                    continue;
                }

                MaterialInout mio_out = new MaterialInout();
                mio_out.setMaterialId(mat_id);
                mio_out.setStoreHouseId(from_storehouse_id);
                mio_out.setInOut("out");
                mio_out.setLotNumber(lotnumber);
                mio_out.setOutputType("move_out");
                mio_out.setOutputQty(currentStock);
                mio_out.setInoutDate(nowDate);
                mio_out.setInoutTime(nowTime);
                mio_out.setDescription("로트재고이동");
                mio_out.setState("confirmed");
                mio_out.set_status("a");
                mio_out.set_audit(user);
                mio_out.setSpjangcd(spjangcd);
                this.matInoutRepository.save(mio_out);

                MaterialInout mio_in = new MaterialInout();
                mio_in.setMaterialId(mat_id);
                mio_in.setStoreHouseId(to_storehouse_id);
                mio_in.setInOut("in");
                mio_in.setInputType("move_in");
                mio_in.setInputQty(currentStock);
                mio_in.setInoutDate(nowDate);
                mio_in.setInoutTime(nowTime);
                mio_in.setDescription("로트재고이동");
                mio_in.setState("confirmed");
                mio_in.set_status("a");
                mio_in.setLotNumber(lotnumber);
                mio_in.set_audit(user);
                mio_in.setSpjangcd(spjangcd);
                this.matInoutRepository.save(mio_in);

                this.disaptchService.updateMaterialLotStorehouse(mat_lot_id, to_storehouse_id);
            }
        }
        return result;
    }
}
