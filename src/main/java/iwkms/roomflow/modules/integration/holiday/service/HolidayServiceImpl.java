package iwkms.roomflow.modules.integration.holiday.service;

import iwkms.roomflow.modules.integration.holiday.config.HolidayApiProperties;
import iwkms.roomflow.modules.integration.holiday.dto.HolidayDto;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HolidayServiceImpl implements HolidayService {
    private final HolidayGateway gateway;
    private final HolidayApiProperties properties;

    @Override
    public List<HolidayDto> getHolidays(int year, String country) {
        String resolved = country == null || country.isBlank() ? properties.defaultCountry() : country;
        return gateway.fetch(year, resolved.toUpperCase(Locale.ROOT));
    }

    @Override
    public boolean isHoliday(LocalDate date, String country) {
        return getHolidays(date.getYear(), country).stream()
                .anyMatch(holiday -> holiday.date().equals(date));
    }
}
