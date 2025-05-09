package mes.app.PopBill.service;


import com.popbill.api.easyfin.EasyFinBankSearchDetail;
import lombok.extern.slf4j.Slf4j;
import mes.app.PopBill.dto.EasyFinBankAccountFormDto;
import mes.app.transaction.service.TransactionInputService;
import mes.app.util.UtilClass;
import mes.domain.entity.TB_ACCOUNT;
import mes.domain.entity.TB_BANKTRANSIT;
import mes.domain.repository.TB_BANKTRANSITRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EasyFinBankCustomService {
    @Autowired
    private TB_BANKTRANSITRepository tB_BANKTRANSITRepository;

    @Autowired
    private TransactionInputService transactionInputService;

    @Autowired
    private com.popbill.api.EasyFinBankService easyFinBankService;

    @Autowired
    BankTransitTransactionalService bankTransitTransactionalService;

    public void Insert_Tb_Account(EasyFinBankAccountFormDto form){
        TB_ACCOUNT account = new TB_ACCOUNT();

        String bankId_form = form.getBankId();

        Integer BankId = Integer.parseInt(bankId_form);

        //account.setBankid();
    }

    //TODO: tid를 기준으로 중복저장 방지함 , tid 목록을 메모리에 적재한다음 유효성 판단할 것
    /**
     * 비동기나 트랜잭션은 프록시 객체에서 처리하는데 Spring이 만들어주는
     * 프록시는 **Spring 컨테이너에서 관리되는 객체(Bean)**에만 적용
     * 그러므로 외부 클래스에서 호출해야 한다.
     *
     * 저장메서드와 비동기 메서드를 클래스별로 분리한 이유 -> Async랑 Transaction 이랑 같이 쓰면 안먹음
     *
     * **/
    @Async
    public void saveBankDataAsync(List<EasyFinBankSearchDetail> list, String jobID, String accountNumber, Integer accountid, String bankname){


        List<String> tidList = getTidList(list);
        Map<String, Integer> cltCdRelationRemarkList =
        convertToRemarkCltcdMap(transactionInputService.getCltCdRelationRemarkList());


        Map<String, TB_BANKTRANSIT> existing = tB_BANKTRANSITRepository.findByTidIn(tidList)
                .stream()
                .collect(Collectors.toMap(TB_BANKTRANSIT::getTid, Function.identity()));



        List<TB_BANKTRANSIT> tb_banktransitList = new ArrayList<>();

        for(EasyFinBankSearchDetail  map : list){
            TB_BANKTRANSIT entity = new TB_BANKTRANSIT();

            String tid = map.getTid();
            String remark = map.getRemark1();

            if(existing.containsKey(tid)){
                continue;
            }

            Integer accIn = UtilClass.parseInteger(map.getAccIn());
            String inoutFlag = (accIn == 0) ? "1" : "0";

            entity.setTid(tid);
            entity.setTrdate( map.getTrdate());
            entity.setTrserial(UtilClass.parseInteger(map.getTrserial()));
            entity.setTrdt(map.getTrdt());
            entity.setAccin(accIn);
            entity.setAccout(UtilClass.parseInteger(map.getAccOut()));
            entity.setBalance(UtilClass.parseInteger(map.getBalance()));
            entity.setRemark1(map.getRemark1());
            entity.setRemark2(map.getRemark2());
            entity.setRemark3(map.getRemark3());
            entity.setRemark4(map.getRemark4());
            entity.setRegdt( map.getRegDT());
            entity.setJobid(jobID);
            entity.setMemo(map.getMemo());
            entity.setIoflag(inoutFlag);
            entity.setAccnum(accountNumber);
            entity.setAccid(accountid);
            entity.setBanknm(bankname);

            if(remark != null && cltCdRelationRemarkList.containsKey(remark)){
                entity.setCltcd(cltCdRelationRemarkList.get(remark));
            }
            tb_banktransitList.add(entity);

        }
        bankTransitTransactionalService.saveBankDataTransactional(tb_banktransitList);
    }

    public List<String> getTidList(List<EasyFinBankSearchDetail> list){

        return list.stream()
                .map(dto -> dto.getTid())
                .collect(Collectors.toList());

    }

    public List<Map<String, Object>> convertToMapList(List<EasyFinBankSearchDetail> detailList) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (EasyFinBankSearchDetail detail : detailList) {
            Map<String, Object> map = new LinkedHashMap<>(); // 순서 유지

            map.put("tid", detail.getTid());  //거래내역아이디
            map.put("accountID", detail.getAccountID()); //
            map.put("trdate", detail.getTrdate().substring(0, 4) + "-" + detail.getTrdate().substring(4, 6) + "-" + detail.getTrdate().substring(6, 8)); //거래일자
            map.put("trserial", detail.getTrserial()); //거래일련번호
            map.put("trdt", detail.getTrdt()); //거래일시
            map.put("accin",  UtilClass.parseInteger(detail.getAccIn())); //입금액
            map.put("accout", detail.getAccOut()); //출금액
            map.put("balance", detail.getBalance()); //잔액
            map.put("remark1", detail.getRemark1() + detail.getRemark2() + detail.getRemark3() + detail.getRemark4());
            map.put("regdt", detail.getRegDT()); //등록일시
            map.put("memo", detail.getMemo()); //메모

            result.add(map);
        }

        return result;
    }

    public Map<String, Integer> convertToRemarkCltcdMap(List<Map<String, Object>> list){
        Map<String, Integer> result = new HashMap<>();

        for(Map<String, Object> row : list){
            String remark = (String) row.get("remark1");
            Integer cltcdObj = UtilClass.parseInteger(row.get("cltcd"));

            if(remark != null && cltcdObj != null){
                result.put(remark, cltcdObj);

            }
        }
        return result;
    }
}
