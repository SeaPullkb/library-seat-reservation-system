package com.seapullkb.library.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoomForm(
        @NotBlank(message = "请输入阅览室名称") @Size(max = 100, message = "名称不能超过100个字符") String name,
        @NotBlank(message = "请输入位置") @Size(max = 200, message = "位置不能超过200个字符") String location,
        @Size(max = 255, message = "介绍不能超过255个字符") String description) {
}
