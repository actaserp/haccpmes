package mes.app.pda.controller;


import mes.app.pda.service.LotHistoryService;
import mes.domain.model.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pda/lot/history")
public class LotHistoryController {

    @Autowired
    LotHistoryService lotHistoryService;

    // LOT 목록 조회
    @GetMapping("/read")
    public AjaxResult getLotHistory(
            @RequestParam(required = false) String lotNumber,
            @RequestParam(required = false) String boxBarcode,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String spjangcd
    ) {
        AjaxResult result = new AjaxResult();
        result.success = true;
        result.data = lotHistoryService.getLotHistory(lotNumber, boxBarcode, startDate, endDate, spjangcd);
        return result;
    }

    // LOT 소비이력 조회
    @GetMapping("/cons")
    public AjaxResult getLotConsHistory(
            @RequestParam Integer lotId,
            @RequestParam String spjangcd
    ) {
        AjaxResult result = new AjaxResult();
        result.success = true;
        result.data = lotHistoryService.getLotConsHistory(lotId, spjangcd);
        return result;
    }
}
