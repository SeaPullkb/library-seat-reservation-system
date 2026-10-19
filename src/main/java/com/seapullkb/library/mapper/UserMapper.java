package com.seapullkb.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seapullkb.library.entity.UserAccount;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<UserAccount> {
    @Select("SELECT * FROM `user` WHERE username = #{username} FOR UPDATE")
    UserAccount lockByUsername(String username);
    @Select("SELECT * FROM `user` WHERE id = #{id} FOR UPDATE")
    UserAccount lockById(Long id);
}
