package mes.app.pda.controller;

import mes.app.pda.service.ReceiveApiService;
import mes.app.util.UtilClass;
import mes.domain.entity.Balju;
import mes.domain.entity.Material;
import mes.domain.entity.MaterialInout;
import mes.domain.entity.User;
import mes.domain.model.AjaxResult;
import mes.domain.repository.BujuRepository;
import mes.domain.repository.MatInoutRepository;
import mes.domain.repository.MaterialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static javax.xml.bind.DatatypeConverter.parseDecimal;

@RestController
@RequestMapping("/pda/receive/receive_management")
public class ReceiveApiController {

    @Autowired
    private ReceiveApiService receiveApiService;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    BujuRepository bujuRepository;

    @Autowired
    MatInoutRepository matInoutRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/read_balju")
    public AjaxResult getBalJuList(@RequestParam String start_date,
                                   @RequestParam String end_date,
                                   @RequestParam(value = "spjangcd", required = false) String spjangcd,
                                   HttpServletRequest request

    ){

        if(!start_date.contains("-")){
            start_date = UtilClass.toContainsHyphenDateString(start_date);
        }
        if(!end_date.contains("-")){
            end_date = UtilClass.toContainsHyphenDateString(end_date);
        }

        start_date = start_date + " 00:00:00";
        end_date = end_date + " 23:59:59";

        Timestamp start = Timestamp.valueOf(start_date);
        Timestamp end = Timestamp.valueOf(end_date);

        List<Map<String, Object>> items = this.receiveApiService.getPdaBaljuList(start, end, "ZZ");  //지금은 그냥 하드코딩인데 나중에 쓰게 된다면 수정해주셈

        AjaxResult result = new AjaxResult();
        result.data = items;

        return result;
    }

    @PostMapping("/save_balju")
    @Transactional
    public AjaxResult saveBalju_inout(@RequestBody Map<String, Object> request,
                                      Authentication auth){

        User user = (User) auth.getPrincipal();
        AjaxResult result = new AjaxResult();

        List<Map<String, Object>> baljuList = (List<Map<String, Object>>) request.get("results");


        for (Map<String, Object> item : baljuList) {
            try {
                Integer bal_pk = (Integer) item.get("id");
                String description = (String) item.get("Description2");
                if (description == null || description.trim().isEmpty()) {
                    description = "발주 입고";
                }
                String inoutQtyStr = String.valueOf(item.get("inputQty")); // '입고 수량'
                String materialIdStr = String.valueOf(item.get("Material_id"));
                String storeHouseIdStr = String.valueOf(item.get("StoreHouse_id"));

                Integer matPk = Integer.parseInt(materialIdStr);
                BigDecimal qty = parseDecimal(inoutQtyStr);

                MaterialInout mi = new MaterialInout();
                mi.setInoutDate(LocalDate.now());
                mi.setInoutTime(LocalTime.now());
                mi.setMaterialId(matPk);
                mi.setStoreHouseId(Integer.parseInt(storeHouseIdStr));

                Material m = materialRepository.getMaterialById(matPk);
                String testYn = m.getInTestYN() != null ? m.getInTestYN() : "";

                if ("Y".equals(testYn)) {
                    mi.setPotentialInputQty(qty.floatValue());
                    mi.setState("waiting");
                    mi.set_status("t");
                } else {
                    mi.setInputQty(qty.floatValue());
                    mi.setState("confirmed");
                    mi.set_status("a");
                }

                mi.setDescription(description);
                mi.setInOut("in");
                mi.set_audit(user);
                mi.setSourceDataPk(bal_pk);
                mi.setSourceTableName("balju");
                mi.setSpjangcd((String) item.get("spjangcd"));
                mi.setCompanyId((Integer) item.get("Company_id"));

                Balju balju = this.bujuRepository.getBujuById(bal_pk);

                double sujuQty2 = jdbcTemplate.queryForObject("""
					SELECT COALESCE(SUM("InputQty"), 0)
					FROM mat_inout
					WHERE "SourceDataPk" = ? 
					  AND "SourceTableName" = 'balju'
					  AND COALESCE("_status", 'a') = 'a'
				""", Double.class, bal_pk);

                balju.setShipmentState(storeHouseIdStr);
                mi.setInputType("order_in");

                matInoutRepository.save(mi);
                bujuRepository.save(balju);

            } catch (Exception e) {
                result.success = false;
                result.message = "처리 중 오류 발생: " + e.getMessage();
                throw new RuntimeException("오류발생 : " + e);
            }
        }


        return result;

    }
}
