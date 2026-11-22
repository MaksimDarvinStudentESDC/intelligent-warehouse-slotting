package com.warehouse.slotting.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record CreateBatchRequest(@NotNull Long productId,@NotBlank String batchCode,@NotNull @Positive Integer quantity,
 @NotNull LocalDate receivingDate,LocalDate expiryDate) {}
