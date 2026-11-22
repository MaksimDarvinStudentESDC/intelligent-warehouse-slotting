package com.warehouse.slotting.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import com.warehouse.slotting.entity.OutboundFlow;
public record CreateProductRequest(@NotNull Long warehouseId,@NotBlank String sku,@NotBlank String name,
 @NotNull @Positive Integer lengthMm,@NotNull @Positive Integer widthMm,@NotNull @Positive Integer heightMm,
 @NotNull @DecimalMin(value="0.0",inclusive=false) BigDecimal unitWeightKg,
 Long compatibilityGroupId,@Positive Integer shelfLifeDays,@NotNull OutboundFlow outboundFlow) {}
