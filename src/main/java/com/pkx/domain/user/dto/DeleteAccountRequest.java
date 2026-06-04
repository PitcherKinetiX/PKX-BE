package com.pkx.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for deleting (withdrawing) the current user account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Delete account request")
public class DeleteAccountRequest {

    @NotBlank(message = "Password is required")
    @Schema(description = "Current password for verification", example = "Password123!", required = true)
    private String password;
}
