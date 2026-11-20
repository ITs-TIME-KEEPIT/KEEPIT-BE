package com.keepit.rental.dto;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 새로 만든 대여 건의 id.
 */
public record RentalIdResponse(
		@Schema(description = "새로 만들어진 대여 건 id", example = "55") Long rentalId) {
}
