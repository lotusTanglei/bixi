package com.lotus.bixi.upms.demo.leave.service;

import com.lotus.bixi.upms.demo.leave.entity.LeaveBooking;
import com.lotus.bixi.upms.demo.leave.mapper.LeaveBookingMapper;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Idempotent local side effect used by the stage-2 automatic-task contract.
 * The row is also the durable tombstone when compensation wins the race.
 */
@Service
@ConditionalOnWorkflowEnabled
public class LeaveBookingService {
    private static final String BOOKED = "BOOKED";
    private static final String CANCELED = "CANCELED";

    private final LeaveBookingMapper mapper;

    public LeaveBookingService(LeaveBookingMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public Result request(String operationId, long leaveId, int round, String requestHash) {
        validate(operationId, leaveId, round, requestHash);
        LeaveBooking current = mapper.selectByOperationIdForUpdate(operationId);
        if (current != null) {
            verifyIdentity(current, operationId, leaveId, round, requestHash);
            return result(current);
        }
        LeaveBooking occupying = mapper.selectByLeaveRoundForUpdate(leaveId, round);
        if (occupying != null) {
            throw new IllegalStateException("请假轮次已被其他自动任务占用");
        }
        LeaveBooking booking = new LeaveBooking();
        booking.setOperationId(operationId);
        booking.setLeaveId(leaveId);
        booking.setRound(round);
        booking.setRequestHash(requestHash);
        booking.setBookingState(BOOKED);
        booking.setBookingReference(reference(operationId));
        booking.setCreatedAt(LocalDateTime.now());
        booking.setUpdatedAt(booking.getCreatedAt());
        requireAffected(mapper.insert(booking), "登记请假执行记录失败");
        return result(booking);
    }

    @Transactional
    public Result compensate(String operationId, long leaveId, int round, String requestHash,
                             String compensationId) {
        validate(operationId, leaveId, round, requestHash);
        requireUuid(compensationId, "compensationId");
        LeaveBooking current = mapper.selectByOperationIdForUpdate(operationId);
        if (current == null) {
            LeaveBooking occupying = mapper.selectByLeaveRoundForUpdate(leaveId, round);
            if (occupying != null) {
                throw new IllegalStateException("请假轮次已被其他自动任务占用");
            }
            LeaveBooking tombstone = new LeaveBooking();
            tombstone.setOperationId(operationId);
            tombstone.setLeaveId(leaveId);
            tombstone.setRound(round);
            tombstone.setRequestHash(requestHash);
            tombstone.setBookingState(CANCELED);
            tombstone.setCompensationId(compensationId);
            tombstone.setCreatedAt(LocalDateTime.now());
            tombstone.setUpdatedAt(tombstone.getCreatedAt());
            requireAffected(mapper.insert(tombstone), "写入请假补偿墓碑失败");
            return result(tombstone);
        }

        verifyIdentity(current, operationId, leaveId, round, requestHash);
        if (current.getCompensationId() != null && !current.getCompensationId().equals(compensationId)) {
            throw new IllegalStateException("补偿标识冲突");
        }
        if (CANCELED.equals(current.getBookingState())) {
            return result(current);
        }
        if (!BOOKED.equals(current.getBookingState())) {
            throw new IllegalStateException("请假登记状态不允许补偿");
        }
        current.setBookingState(CANCELED);
        current.setCompensationId(compensationId);
        current.setUpdatedAt(LocalDateTime.now());
        requireAffected(mapper.updateById(current), "更新请假补偿状态失败");
        return result(current);
    }

    private static Result result(LeaveBooking booking) {
        return new Result(booking.getOperationId(), booking.getBookingState(),
                booking.getBookingReference(), booking.getCompensationId());
    }

    private static void verifyIdentity(LeaveBooking booking, String operationId, long leaveId,
                                       int round, String requestHash) {
        if (!operationId.equals(booking.getOperationId()) || !Long.valueOf(leaveId).equals(booking.getLeaveId())
                || !Integer.valueOf(round).equals(booking.getRound())) {
            throw new IllegalStateException("自动任务业务身份冲突");
        }
        if (!requestHash.equals(booking.getRequestHash())) {
            throw new IllegalStateException("自动任务摘要冲突");
        }
    }

    private static void validate(String operationId, long leaveId, int round, String requestHash) {
        requireUuid(operationId, "operationId");
        if (leaveId <= 0 || round <= 0) throw new IllegalArgumentException("业务标识无效");
        if (requestHash == null || !requestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestHash无效");
        }
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        }
        catch (RuntimeException invalid) {
            throw new IllegalArgumentException(field + "无效", invalid);
        }
    }

    private static String reference(String operationId) {
        return "booking-" + operationId.replace("-", "");
    }

    private static void requireAffected(int affected, String message) {
        if (affected != 1) throw new IllegalStateException(message);
    }

    public record Result(String operationId, String state, String bookingReference, String compensationId) { }
}
