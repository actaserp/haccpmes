package mes.app.pda.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import mes.domain.model.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@Slf4j
public class PdaHomeController {

    @Autowired
    private ObjectMapper jacksonObjectMapper;

    private static final String version_file = "C:/Temp/mes21/dongyoungpda/version.json";

    @GetMapping("/pda/app/version")
    public AjaxResult getVersion() {
        AjaxResult result = new AjaxResult();

        File file = new File(version_file);
        if(!file.exists()){
            result.success = false;
            result.message = "버전파일이 존재하지 않습니다.";
            return result;
        }

        try (FileInputStream fis = new FileInputStream(file)){

            Map<String, Object> jsonMap = jacksonObjectMapper.readValue(fis, Map.class);
            String version = jsonMap.get("version").toString();

            result.success = true;
            result.data = version;
        }catch (Exception e){
            result.success = false;
            result.message = "버전파일을 읽는중 에러가 발생하였습니다.";
            log.error("endpoint : /pda/app/version , content= {}" , e.getMessage());
        }

        return result;
    }

    @GetMapping("/pda/app/version/dongyoungpda_latest.apk")
    public ResponseEntity<Resource> downloadApk(@RequestParam String version){
        try{
            String apkPath = "C:/Temp/mes21/dongyoungpda/" + "app_" + version + ".apk";
            File file = new File(apkPath);

            if(!file.exists()){
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(null);
            }

            Resource resource = new FileSystemResource(file);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(file.getName(), StandardCharsets.UTF_8)
                    .build()
            );
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(file.length())
                    .body(resource);
        }catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

}
