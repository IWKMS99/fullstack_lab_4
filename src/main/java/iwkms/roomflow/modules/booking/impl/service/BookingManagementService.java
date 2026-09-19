package iwkms.roomflow.modules.booking.impl.service;

import iwkms.roomflow.exception.BookingConflictException;
import iwkms.roomflow.exception.ResourceNotFoundException;
import iwkms.roomflow.modules.booking.api.dto.BookRoomDto;
import iwkms.roomflow.modules.booking.api.dto.CancelBookingDto;
import iwkms.roomflow.modules.booking.impl.domain.Booking;
import iwkms.roomflow.modules.booking.impl.domain.BookingStatus;
import iwkms.roomflow.modules.booking.impl.domain.Room;
import iwkms.roomflow.modules.booking.impl.repository.BookingRepository;
import iwkms.roomflow.modules.booking.impl.repository.RoomRepository;
import iwkms.roomflow.modules.integration.holiday.dto.HolidayDto;
import iwkms.roomflow.modules.integration.holiday.service.HolidayService;
import iwkms.roomflow.modules.user.impl.domain.Role;
import iwkms.roomflow.modules.user.impl.domain.User;
import iwkms.roomflow.modules.user.impl.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingManagementService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final HolidayService holidayService;

    public Booking bookRoom(BookRoomDto command) {
        LocalDate bookingDate = command.startTime().toLocalDate();
        if (isPublicHoliday(bookingDate)) {
            throw new BookingConflictException("Booking is unavailable on public holidays.");
        }

        Room room = roomRepository
                .findByIdAndActiveTrue(command.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room with id " + command.roomId() + " not found"));

        List<Booking> conflictingBookings =
                bookingRepository.findConflictingBookings(command.roomId(), command.startTime(), command.endTime());

        if (!conflictingBookings.isEmpty()) {
            throw new BookingConflictException("Room is already booked for the selected time period.");
        }

        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .room(room)
                .userId(command.userId())
                .startTime(command.startTime())
                .endTime(command.endTime())
                .status(BookingStatus.CONFIRMED)
                .build();
        return bookingRepository.save(booking);
    }

    public void cancelBooking(CancelBookingDto command) {
        Booking booking = bookingRepository
                .findById(command.bookingId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Booking with id " + command.bookingId() + " not found"));

        User currentUser = userRepository
                .findById(command.currentUserId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("User with id " + command.currentUserId() + " not found"));

        boolean isOwner = booking.getUserId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(Role.ROLE_ADMIN.name()));

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to cancel this booking.");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    private boolean isPublicHoliday(LocalDate date) {
        List<HolidayDto> holidays = holidayService.getHolidays(date.getYear(), "RU");
        return holidays.stream().anyMatch(holiday -> holiday.date().equals(date));
    }
}
