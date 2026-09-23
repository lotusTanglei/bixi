package com.lotus.bixi.upms.demo.leave.service;

import com.lotus.bixi.upms.demo.leave.entity.LeaveBooking;
import com.lotus.bixi.upms.demo.leave.mapper.LeaveBookingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveBookingServiceTest {
    private static final long LEAVE_ID = 7L;
    private static final int ROUND = 1;
    private static final String HASH = "a".repeat(64);

    @Mock
    private LeaveBookingMapper mapper;
    private LeaveBookingService service;

    @BeforeEach
    void setUp() {
        service = new LeaveBookingService(mapper);
    }

    @Test
    void firstRequestCreatesOneStableBooking() {
        String operationId = UUID.randomUUID().toString();
        when(mapper.selectByOperationIdForUpdate(operationId)).thenReturn(null);
        when(mapper.selectByLeaveRoundForUpdate(LEAVE_ID, ROUND)).thenReturn(null);
        when(mapper.insert(any(LeaveBooking.class))).thenReturn(1);

        LeaveBookingService.Result result = service.request(operationId, LEAVE_ID, ROUND, HASH);

        assertThat(result.state()).isEqualTo("BOOKED");
        assertThat(result.bookingReference()).isEqualTo("booking-" + operationId.replace("-", ""));
        ArgumentCaptor<LeaveBooking> inserted = ArgumentCaptor.forClass(LeaveBooking.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getBookingState()).isEqualTo("BOOKED");
        assertThat(inserted.getValue().getBookingReference()).isEqualTo(result.bookingReference());
    }

    @Test
    void sameOperationAndHashReplaysWithoutAnotherInsert() {
        String operationId = UUID.randomUUID().toString();
        LeaveBooking existing = booking(operationId, "BOOKED", "booking-original", null, HASH);
        when(mapper.selectByOperationIdForUpdate(operationId)).thenReturn(existing);

        LeaveBookingService.Result result = service.request(operationId, LEAVE_ID, ROUND, HASH);

        assertThat(result.state()).isEqualTo("BOOKED");
        assertThat(result.bookingReference()).isEqualTo("booking-original");
        verify(mapper, never()).insert(any(LeaveBooking.class));
        verify(mapper, never()).updateById(any(LeaveBooking.class));
    }

    @Test
    void sameOperationWithDifferentHashIsPermanentConflict() {
        String operationId = UUID.randomUUID().toString();
        when(mapper.selectByOperationIdForUpdate(operationId))
                .thenReturn(booking(operationId, "BOOKED", "booking-original", null, HASH));

        assertThatThrownBy(() -> service.request(operationId, LEAVE_ID, ROUND, "b".repeat(64)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("摘要冲突");
        verify(mapper, never()).insert(any(LeaveBooking.class));
    }

    @Test
    void compensationTombstoneWinsOverLaterRequest() {
        String operationId = UUID.randomUUID().toString();
        String compensationId = UUID.randomUUID().toString();
        when(mapper.selectByOperationIdForUpdate(operationId)).thenReturn(null,
                booking(operationId, "CANCELED", null, compensationId, HASH));
        when(mapper.selectByLeaveRoundForUpdate(LEAVE_ID, ROUND)).thenReturn(null);
        when(mapper.insert(any(LeaveBooking.class))).thenReturn(1);

        LeaveBookingService.Result canceled = service.compensate(operationId, LEAVE_ID, ROUND, HASH, compensationId);
        LeaveBookingService.Result replay = service.request(operationId, LEAVE_ID, ROUND, HASH);

        assertThat(canceled.state()).isEqualTo("CANCELED");
        assertThat(replay.state()).isEqualTo("CANCELED");
        verify(mapper, times(1)).insert(any(LeaveBooking.class));
    }

    @Test
    void compensationAfterBookingIsIdempotent() {
        String operationId = UUID.randomUUID().toString();
        String compensationId = UUID.randomUUID().toString();
        LeaveBooking existing = booking(operationId, "BOOKED", "booking-original", null, HASH);
        when(mapper.selectByOperationIdForUpdate(operationId)).thenReturn(existing, existing);
        when(mapper.updateById(existing)).thenReturn(1);

        LeaveBookingService.Result canceled = service.compensate(operationId, LEAVE_ID, ROUND, HASH, compensationId);
        existing.setBookingState("CANCELED");
        existing.setCompensationId(compensationId);
        LeaveBookingService.Result replay = service.compensate(operationId, LEAVE_ID, ROUND, HASH, compensationId);

        assertThat(canceled.state()).isEqualTo("CANCELED");
        assertThat(replay.bookingReference()).isEqualTo("booking-original");
        verify(mapper, times(1)).updateById(existing);
    }

    private static LeaveBooking booking(String operationId, String state, String reference,
                                        String compensationId, String hash) {
        LeaveBooking booking = new LeaveBooking();
        booking.setOperationId(operationId);
        booking.setLeaveId(LEAVE_ID);
        booking.setRound(ROUND);
        booking.setRequestHash(hash);
        booking.setBookingState(state);
        booking.setBookingReference(reference);
        booking.setCompensationId(compensationId);
        return booking;
    }
}
