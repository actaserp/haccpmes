package mes.app.support;

import mes.app.support.service.CustomerDefectAnalysisService;
import mes.domain.model.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support/customer/")
public class CustomerDefectAnalysisController {


    @Autowired
    CustomerDefectAnalysisService customerDefectAnalysisService;

    @GetMapping("/read")
    public AjaxResult getList(@RequestParam(required = true) String startDt
                             ,@RequestParam(required = true) String endDt
                             ,@RequestParam(required = false) String company_name
                             ,@RequestParam(required = false) String material_name
                             ,@RequestParam(required = false) Integer defect_type_id
                             ,@RequestParam(required = false) String progress
                             ,@RequestParam(required = true) String spjangcd

    ){
        AjaxResult result = new AjaxResult();

        List<Map<String, Object>> list = customerDefectAnalysisService.getList(startDt, endDt, company_name, material_name, defect_type_id, progress, spjangcd);

        result.success = true;
        result.data = list;

        return result;
    }
}
