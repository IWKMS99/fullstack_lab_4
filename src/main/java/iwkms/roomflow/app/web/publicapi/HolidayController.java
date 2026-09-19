package iwkms.roomflow.app.web.publicapi;

import iwkms.roomflow.modules.integration.holiday.dto.HolidayDto;
import iwkms.roomflow.modules.integration.holiday.service.HolidayService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidayService;

    @GetMapping
    public ResponseEntity<List<HolidayDto>> getHolidays(
            @RequestParam int year, @RequestParam(required = false, defaultValue = "RU") String country) {
        if (year < 1970 || year > 2100 || !country.matches("(?i)[a-z]{2}")) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(holidayService.getHolidays(year, country));
    }
}
