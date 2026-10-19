package com.seapullkb.library.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SeatForm(
        @NotBlank(message = "请输入座位编号") @Size(max = 20, message = "编号不能超过20个字符") String seatNo) {
}
