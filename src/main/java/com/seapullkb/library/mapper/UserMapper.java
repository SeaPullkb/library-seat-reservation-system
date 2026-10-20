package com.seapullkb.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seapullkb.library.entity.UserAccount;
import org.apache.ibatis.annotations.*;
import com.seapullkb.library.service.UserSummary;
import java.util.List;

public interface UserMapper extends BaseMapper<UserAccount> {
    @Select("SELECT * FROM `user` WHERE id IN (#{actorId},#{targetId}) ORDER BY id FOR UPDATE")
    List<UserAccount> lockPair(@Param("actorId") Long actorId,@Param("targetId") Long targetId);
    String FILTER = " WHERE (#{keyword}='' OR LOCATE(#{keyword},username)>0 OR LOCATE(#{keyword},real_name)>0) AND (#{role} IS NULL OR role=#{role}) AND (#{status} IS NULL OR status=#{status}) ";
    @Select("SELECT id,username,real_name,class_name,phone,role,status,created_at FROM `user`" + FILTER + " ORDER BY id DESC LIMIT 20 OFFSET #{offset}")
    List<UserSummary> search(@Param("keyword") String keyword,@Param("role") String role,@Param("status") Integer status,@Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM `user`" + FILTER)
    long searchCount(@Param("keyword") String keyword,@Param("role") String role,@Param("status") Integer status);
    @Select("SELECT COUNT(*) FROM reserve_record WHERE user_id=#{id} AND status IN ('PENDING','USING')")
    long openReservationCount(Long id);
    @Select("SELECT COUNT(*) FROM `user` WHERE role='SUPER_ADMIN' AND status=1")
    long enabledSuperAdmins();
    @Update("UPDATE `user` SET real_name=#{name},class_name=#{className},phone=#{phone} WHERE id=#{id}")
    int editProfile(@Param("id") Long id,@Param("name") String name,@Param("className") String className,@Param("phone") String phone);
    @Update("UPDATE `user` SET role=#{role} WHERE id=#{id}")
    int changeRole(@Param("id") Long id,@Param("role") String role);
    @Update("UPDATE `user` SET status=#{status} WHERE id=#{id}")
    int changeStatus(@Param("id") Long id,@Param("status") int status);
    @Update("UPDATE `user` SET password=#{hash} WHERE id=#{id}")
    int changePassword(@Param("id") Long id,@Param("hash") String hash);
    @Select("SELECT * FROM `user` WHERE username = #{username} FOR UPDATE")
    UserAccount lockByUsername(String username);
    @Select("SELECT * FROM `user` WHERE id = #{id} FOR UPDATE")
    UserAccount lockById(Long id);
}
