package mes.app.pda.controller;

import mes.app.pda.service.LotManagementService;
import mes.domain.model.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/pda/lot")
public class LotManagementController {

    @Autowired
    LotManagementService lotManagementService;

    @GetMapping("/list/read")
    public AjaxResult getList(
            @RequestParam(required = false) Integer StoreHouseId,
            @RequestParam String materialName,
            @RequestParam String LotNumber,
            @RequestParam String spjangcd,
            @RequestParam boolean groupYn,
            Authentication auth
    ){
        AjaxResult result = new AjaxResult();


        List<Map<String, Object>> lotList = lotManagementService.getLotList(StoreHouseId, materialName, LotNumber, spjangcd, groupYn);

        result.data = lotList;
        result.success = true;
        return result;
    }
}
