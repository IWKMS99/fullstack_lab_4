package iwkms.roomflow.modules.integration.holiday.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import iwkms.roomflow.exception.HolidayUnavailableException;
import iwkms.roomflow.modules.integration.holiday.dto.HolidayDto;
import iwkms.roomflow.modules.integration.holiday.dto.NagerHolidayDto;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class HolidayGateway {
    private final RestClient holidayRestClient;

    @Cacheable(cacheNames = "holidays", key = "#year + ':' + #country")
    @CircuitBreaker(name = "holidayApi", fallbackMethod = "unavailable")
    @Retry(name = "holidayApi")
    @RateLimiter(name = "holidayApi")
    public List<HolidayDto> fetch(int year, String country) {
        NagerHolidayDto[] response = holidayRestClient
                .get()
                .uri("/api/v3/PublicHolidays/{year}/{country}", year, country)
                .retrieve()
                .body(NagerHolidayDto[].class);
        if (response == null) {
            return List.of();
        }
        return Arrays.stream(response)
                .map(item -> new HolidayDto(item.date(), item.localName(), item.name(), item.countryCode()))
                .toList();
    }

    @SuppressWarnings("unused")
    private List<HolidayDto> unavailable(int year, String country, Throwable cause) {
        throw new HolidayUnavailableException(cause);
    }
}
