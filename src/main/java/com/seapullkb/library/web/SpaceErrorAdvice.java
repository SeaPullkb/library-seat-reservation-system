package com.seapullkb.library.web;

import com.seapullkb.library.service.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

@ControllerAdvice
public class SpaceErrorAdvice {
    private static final Logger LOG=LoggerFactory.getLogger(SpaceErrorAdvice.class);
    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound(ResourceNotFoundException ex) { return error(HttpStatus.NOT_FOUND,ex.getMessage()); }
    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView unknownPage(NoResourceFoundException ex) { return error(HttpStatus.NOT_FOUND,"页面不存在，请从导航进入所需功能。"); }
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class,BindException.class,ServletRequestBindingException.class})
    public ModelAndView invalidParameter(Exception ex) { return error(HttpStatus.BAD_REQUEST,"查询或操作参数无效，请返回后重新填写。"); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ModelAndView method(HttpRequestMethodNotSupportedException ex,HttpServletResponse response) {
        if(ex.getSupportedMethods()!=null) response.setHeader("Allow",String.join(", ",ex.getSupportedMethods()));
        return error(HttpStatus.METHOD_NOT_ALLOWED,"此操作不支持当前请求方式，请使用页面提供的按钮。");
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ModelAndView media(HttpMediaTypeNotSupportedException ex) { return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"请通过页面表单提交此操作。"); }
    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView denied(AccessDeniedException ex) { return error(HttpStatus.FORBIDDEN,"当前账号无此操作权限，请重新登录或返回首页。"); }
    @ExceptionHandler({MaintenanceConflictException.class,ReservationConflictException.class})
    public ModelAndView conflict(RuntimeException ex) { return error(HttpStatus.CONFLICT,ex.getMessage()); }
    @ExceptionHandler({org.apache.catalina.connector.ClientAbortException.class,
            org.springframework.web.context.request.async.AsyncRequestNotUsableException.class})
    public void disconnected(HttpServletResponse response) {
        // The client has left. Do not render HTML into a closed binary/async response.
        LOG.debug("Client disconnected before response delivery completed");
    }
    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception ex,HttpServletResponse response) {
        // Never log bound request values or SQL text that might contain credentials.
        LOG.error("Unexpected web failure: {}",ex.getClass().getName());
        if(response.isCommitted()) return null;
        return error(HttpStatus.INTERNAL_SERVER_ERROR,"系统暂时无法完成操作，请稍后重试。");
    }
    private ModelAndView error(HttpStatus status,String message) {
        ModelAndView result=new ModelAndView("error/space");result.setStatus(status);
        result.addObject("status",status.value());result.addObject("message",message);return result;
    }
}
