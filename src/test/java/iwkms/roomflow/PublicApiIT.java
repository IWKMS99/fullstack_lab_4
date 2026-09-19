package iwkms.roomflow;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import iwkms.roomflow.modules.integration.holiday.dto.HolidayDto;
import iwkms.roomflow.modules.integration.holiday.service.HolidayService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PublicApiIT {

    @Test
    void shouldRenderMetadataWithoutJavascript() throws Exception {
        mockMvc.perform(get("/web/schedule/room/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("rel=\"canonical\"")))
                .andExpect(content().string(containsString("application/ld+json")))
                .andExpect(content().string(containsString("og:description")));
    }

    @Test
    void shouldReturnReal404ForUnknownPublicRoom() throws Exception {
        mockMvc.perform(get("/web/schedule/room/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("noindex,nofollow")));
    }

    @Test
    void shouldExposeCalendarFailureInsteadOfEmptySuccess() throws Exception {
        when(holidayService.getHolidays(2026, "RU"))
                .thenThrow(
                        new iwkms.roomflow.exception.HolidayUnavailableException(new IllegalStateException("offline")));
        mockMvc.perform(get("/api/v1/holidays").param("year", "2026").param("country", "RU"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void shouldGenerateRobotsForConfiguredOrigin() throws Exception {
        mockMvc.perform(get("/web/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Disallow: /admin")))
                .andExpect(content().string(containsString("Sitemap: http://localhost:8080/sitemap.xml")));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HolidayService holidayService;

    @Test
    void shouldReturnPublicRoomById() throws Exception {
        UUID roomId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        mockMvc.perform(get("/api/v1/rooms/{id}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(roomId.toString()))
                .andExpect(jsonPath("$.name").exists());
    }

    @Test
    void shouldReturn404ForUnknownRoom() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnSitemapXml() throws Exception {
        mockMvc.perform(get("/api/v1/sitemap"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("/schedule")))
                .andExpect(content().string(containsString("/schedule/room/")));
    }

    @Test
    void shouldReturnHolidays() throws Exception {
        when(holidayService.getHolidays(2026, "RU"))
                .thenReturn(List.of(new HolidayDto(LocalDate.of(2026, 1, 1), "Новый год", "New Year", "RU")));

        mockMvc.perform(get("/api/v1/holidays").param("year", "2026").param("country", "RU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-01-01"))
                .andExpect(jsonPath("$[0].countryCode").value("RU"));
    }
}
