package com.lotus.bixi.upms.demo.leave.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

/** Only applicant-editable fields; identity, process binding and state are server-owned. */
@Data
public class LeaveRequestDTO {
    @NotNull @Positive
    private Long approverId;
    @NotNull
    private LocalDate startDate;
    @NotNull
    private LocalDate endDate;
    @NotBlank @Size(max = 1000)
    private String reason;
}
