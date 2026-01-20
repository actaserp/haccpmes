package mes.app.dashboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@RestController
@RequestMapping("/api/dashboard/reflow")
public class DashBoardReflowController {

	// ✅ 로그 폴더 (UNC 경로는 \\ 를 두 번)
	private static final String BASE_DIR =
		"\\\\Actas_server\\공유폴더\\HaccpMes\\동영전자\\설비\\Reflow\\PV";

	// ✅ GET /api/dashboard/reflow?mmdd=01-09
	@GetMapping
	public ResponseEntity<Map<String, Object>> read(
		@RequestParam String mmdd,                          // "01-09"
		@RequestParam(required = false) String yy,
		@RequestParam(required = false) String minute       // 예: "오전 07:41" 또는 "07:41"
	) throws IOException {

		String year2 = (yy != null && !yy.isBlank()) ? yy : getCurrentYear2(); // 예: "26"
		String expectedFileName = mmdd + "-" + year2 + ".Log";                 // 예: "01-09-26.Log"

		Path dir = Paths.get(BASE_DIR);
		if (!Files.exists(dir) || !Files.isDirectory(dir)) {
			return ResponseEntity.ok(Map.of(
				"ok", false,
				"message", "로그 경로가 존재하지 않습니다.",
				"path", BASE_DIR
			));
		}

		// 1) 정확히 일치하는 파일 우선
		Path file = dir.resolve(expectedFileName);

		// 2) 혹시 확장자 대소문자/파일명 변형 대비: prefix로 한번 더 찾기
		if (!Files.exists(file)) {
			String prefix = (mmdd + "-" + year2).toLowerCase();
			try (Stream<Path> s = Files.list(dir)) {
				Optional<Path> found = s
																 .filter(p -> p.getFileName().toString().toLowerCase().startsWith(prefix))
																 .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".log"))
																 .findFirst();
				if (found.isPresent()) file = found.get();
			}
		}

		if (!Files.exists(file)) {
			return ResponseEntity.ok(Map.of(
				"ok", false,
				"message", "해당 날짜 로그 파일을 찾지 못했습니다.",
				"expected", expectedFileName
			));
		}

		List<Map<String, Object>> rows = parseTabLog(file);

		Map<String, Object> current = null;

		if (minute != null && !minute.isBlank()) {
			// minute 입력 형태 지원:
			// 1) "오전 07:41"  2) "07:41"
			String minuteNorm = normalizeMinute(minute);

			for (Map<String, Object> row : rows) {
				Object dtObj = row.get("날 짜"); // ✅ 헤더명이 "날 짜" 그대로여야 함
				if (dtObj == null) continue;

				String dtText = dtObj.toString(); // 예: "2025-12-09 오전 07:41:11"
				String rowMinute = extractMinuteKey(dtText); // 예: "오전 07:41"

				if (rowMinute != null && rowMinute.equals(minuteNorm)) {
					current = row;
					break;
				}
			}
		}

// minute을 안 주면 최신 row를 current로 (옵션)
		if (current == null && !rows.isEmpty()) {
			current = rows.get(rows.size() - 1);
		}


		return ResponseEntity.ok(Map.of(
			"ok", true,
			"file", file.getFileName().toString(),
			"count", rows.size(),
			"current", current,
			"data", rows
		));
	}

	private static String getCurrentYear2() {
		int y = LocalDate.now().getYear() % 100;
		return String.format("%02d", y);
	}

	/**
	 * 탭(\t) 구분 로그 파싱
	 * - 첫 줄: 헤더
	 * - 이후: 데이터
	 */
	private static List<Map<String, Object>> parseTabLog(Path file) throws IOException {

		// ✅ UTF-8 BOM 로그에 맞는 charset
		List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		if (lines.isEmpty()) return Collections.emptyList();

		// ✅ 첫 줄 BOM 제거
		lines.set(0, stripUtf8Bom(lines.get(0)));

		String[] headers = lines.get(0).split("\\t", -1);

		List<Map<String, Object>> out = new ArrayList<>();
		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line == null || line.isBlank()) continue;

			String[] cols = line.split("\\t", -1);

			Map<String, Object> row = new LinkedHashMap<>();
			for (int c = 0; c < headers.length; c++) {
				String key = headers[c].trim();
				String val = (c < cols.length) ? cols[c].trim() : "";
				row.put(key, tryParseNumber(val));
			}
			out.add(row);
		}
		return out;
	}

	private static String stripUtf8Bom(String s) {
		if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
			return s.substring(1);
		}
		return s;
	}


	/**
	 * 숫자처럼 보이면 숫자로 변환
	 * - "25 Hz" 같은 건 문자열 유지
	 */
	private static Object tryParseNumber(String v) {
		if (v == null) return "";
		String s = v.replace(",", "").trim();
		if (s.matches("^-?\\d+(\\.\\d+)?$")) {
			if (s.contains(".")) return Double.parseDouble(s);
			return Long.parseLong(s);
		}
		return v;
	}
	// "오전 07:41" / "07:41" 둘 다 "오전 07:41" 형태로 맞추기
	private static String normalizeMinute(String minute) {
		String m = minute.trim();

		// 이미 "오전 07:41" 형태면 그대로
		if (m.matches("^(오전|오후)\\s+\\d{1,2}:\\d{2}$")) {
			String[] parts = m.split("\\s+");
			String ampm = parts[0];
			String hhmm = parts[1];
			String[] t = hhmm.split(":");
			String hh = String.format("%02d", Integer.parseInt(t[0]));
			return ampm + " " + hh + ":" + t[1];
		}

		// "07:41" 형태면 오전/오후를 알 수 없으니
		// ✅ 로그가 항상 오전만 쓰는 게 아니라면 여기 정책이 필요함.
		// 일단 "07:41"은 그대로 "07:41"만 비교하도록 만들 수도 있음.
		if (m.matches("^\\d{1,2}:\\d{2}$")) {
			String[] t = m.split(":");
			String hh = String.format("%02d", Integer.parseInt(t[0]));
			return hh + ":" + t[1]; // "07:41"
		}

		return m;
	}

	// 로그의 "날 짜" 텍스트에서 "오전 07:41" 또는 "07:41" 뽑기
	private static String extractMinuteKey(String dtText) {
		if (dtText == null) return null;
		String s = dtText.trim();

		// 예: "2025-12-09 오전 07:41:11"
		// 그룹: (오전|오후) (HH) :(MM)
		var m = java.util.regex.Pattern
							.compile("^\\d{4}-\\d{2}-\\d{2}\\s+(오전|오후)\\s+(\\d{1,2}):(\\d{2})")
							.matcher(s);

		if (m.find()) {
			String ampm = m.group(1);
			String hh = String.format("%02d", Integer.parseInt(m.group(2)));
			String mm = m.group(3);
			return ampm + " " + hh + ":" + mm; // "오전 07:41"
		}

		return null;
	}


}

