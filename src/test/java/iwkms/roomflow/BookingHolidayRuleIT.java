package iwkms.roomflow;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import iwkms.roomflow.modules.booking.api.dto.CreateBookingRequestDto;
import iwkms.roomflow.modules.integration.holiday.dto.HolidayDto;
import iwkms.roomflow.modules.integration.holiday.service.HolidayService;
import iwkms.roomflow.modules.user.impl.domain.Role;
import iwkms.roomflow.modules.user.impl.domain.User;
import iwkms.roomflow.modules.user.impl.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class BookingHolidayRuleIT {

    @Test
    void shouldNotConfirmBookingWhenHolidayCheckFails() throws Exception {
        when(holidayService.getHolidays(LocalDate.now().plusDays(2).getYear(), "RU"))
                .thenThrow(
                        new iwkms.roomflow.exception.HolidayUnavailableException(new IllegalStateException("offline")));
        LocalDateTime start = LocalDate.now().plusDays(2).atTime(10, 0);
        CreateBookingRequestDto request = new CreateBookingRequestDto(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), start, start.plusHours(1));
        mockMvc.perform(post("/api/v1/bookings")
                        .with(user(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private HolidayService holidayService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("holiday-booking@test.com")
                .password(passwordEncoder.encode("password"))
                .roles(Set.of(Role.ROLE_USER))
                .build();
        userRepository.save(user);

        when(holidayService.getHolidays(LocalDate.now().plusDays(2).getYear(), "RU"))
                .thenReturn(List.of(new HolidayDto(LocalDate.now().plusDays(2), "Holiday", "Holiday", "RU")));
    }

    @Test
    void shouldRejectBookingOnHoliday() throws Exception {
        CreateBookingRequestDto request = new CreateBookingRequestDto(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                LocalDateTime.now()
                        .plusDays(2)
                        .withHour(10)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0),
                LocalDateTime.now()
                        .plusDays(2)
                        .withHour(11)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0));

        mockMvc.perform(post("/api/v1/bookings")
                        .with(user(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
}
