package mes.app.definition;

import lombok.extern.slf4j.Slf4j;
import mes.app.definition.service.SOPService;
import mes.domain.entity.User;
import mes.domain.model.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/sop")
public class SOPController {

    @Autowired
    SOPService sopService;

    // 목록 조회
    @GetMapping("/read")
    public AjaxResult getSopList(
            @RequestParam(value = "sop_name", required = false) String sopName,
            @RequestParam(value = "spjangcd", required = false) String spjangcd,
            HttpServletRequest request) {

        AjaxResult result = new AjaxResult();
        result.data = sopService.getSopList(sopName, spjangcd);
        return result;
    }

    // 단건 상세 조회
    @GetMapping("/detail")
    public AjaxResult getSopDetail(
            @RequestParam(value = "id") Long id,
            HttpServletRequest request) {

        AjaxResult result = new AjaxResult();
        result.data = sopService.getSopDetail(id);
        return result;
    }

    // 저장 (신규/수정)
    @PostMapping("/save")
    public AjaxResult saveSop(
            @RequestParam Map<String, Object> params,
            // 사진 8개 (없으면 null)
            @RequestParam(value = "photo_1", required = false) MultipartFile photo1,
            @RequestParam(value = "photo_2", required = false) MultipartFile photo2,
            @RequestParam(value = "photo_3", required = false) MultipartFile photo3,
            @RequestParam(value = "photo_4", required = false) MultipartFile photo4,
            @RequestParam(value = "photo_5", required = false) MultipartFile photo5,
            @RequestParam(value = "photo_6", required = false) MultipartFile photo6,
            @RequestParam(value = "photo_7", required = false) MultipartFile photo7,
            @RequestParam(value = "photo_8", required = false) MultipartFile photo8,
            HttpServletRequest request,
            Authentication auth) {


        User user = (User) auth.getPrincipal();
        params.put("created_by", user.getPersonid());

        MultipartFile[] photos = {photo1, photo2, photo3, photo4, photo5, photo6, photo7, photo8};

        AjaxResult result = new AjaxResult();
        try {
            sopService.saveSop(params, photos);
            result.success = true;
        } catch (Exception e) {
            log.error("SOP 저장 실패", e);
            result.success = false;
            result.message = e.getMessage();
        }
        return result;
    }

    // 삭제
    @PostMapping("/delete")
    public AjaxResult deleteSop(
            @RequestParam(value = "id") Long id,
            HttpServletRequest request) {

        AjaxResult result = new AjaxResult();
        try {
            sopService.deleteSop(id);
            result.success = true;
        } catch (Exception e) {
            log.error("SOP 삭제 실패", e);
            result.success = false;
            result.message = e.getMessage();
        }
        return result;
    }

    // SOPController.java에 추가
    @GetMapping("/photo")
    public ResponseEntity<Resource> getPhoto(
            @RequestParam("path") String path,
            @RequestParam("uuid") String uuid) {

        try {
            File file = new File(path + uuid);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) contentType = "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);

        } catch (Exception e) {
            log.error("사진 조회 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // 작업표준서 다운로드 로직
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadSop(
            @RequestParam(value = "id") Long id,
            HttpServletRequest request) {
        try {
            byte[] excelBytes = sopService.generateSopExcel(id);

            ByteArrayResource resource = new ByteArrayResource(excelBytes);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" +
                                    URLEncoder.encode("작업표준서.xlsx", StandardCharsets.UTF_8))
                    .contentLength(excelBytes.length)
                    .body(resource);
        } catch (Exception e) {
            log.error("SOP 다운로드 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}