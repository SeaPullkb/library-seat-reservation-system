package com.seapullkb.library.web;

import com.seapullkb.library.service.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(assignableTypes = {AdminSpaceController.class, StudentSpaceController.class, ReservationController.class, AdminReservationController.class, UserAdminController.class, StatisticsController.class})
public class SpaceErrorAdvice {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ModelAndView invalidParameter(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "日期、时段或操作参数无效，请返回后重新选择。");
    }
    private ModelAndView error(HttpStatus status, String message) {
        ModelAndView result = new ModelAndView("error/space");
        result.setStatus(status); result.addObject("status", status.value()); result.addObject("message", message);
        return result;
    }
}
