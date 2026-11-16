package com.vento.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequestDTO(
        @NotBlank @Size(min = 8, max = 72) String nuevaPassword
) {
}
