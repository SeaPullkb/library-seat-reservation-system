package com.seapullkb.library.service;

import com.seapullkb.library.config.DemoConfiguration;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Synthetic defense fixtures. Refuses any populated database; never resets real accounts. */
@Service @Profile("demo")
public class DemoDataset {
    private final JdbcTemplate jdbc;
    private final InitialDataService initial;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final Environment environment;
    public DemoDataset(JdbcTemplate jdbc,InitialDataService initial,PasswordEncoder passwords,Clock clock,Environment environment) {
        this.jdbc=jdbc;this.initial=initial;this.passwords=passwords;this.clock=clock;this.environment=environment;
    }
    @Transactional
    public void seed(String password) {
        DemoConfiguration.requireIsolated(environment,jdbc.queryForObject("SELECT DATABASE()",String.class));
        for(String table:List.of("user","reading_room","seat","reserve_record"))
            if(jdbc.queryForObject("SELECT COUNT(*) FROM `"+table+"`",Long.class)!=0)
                throw new IllegalStateException("演示生成器只接受四张业务表均为空的新库，不覆盖已有数据。");
        LocalDate day=LocalDate.now(clock);
        if(!LocalTime.now(clock).equals(LocalTime.of(9,5))) throw new IllegalStateException("请使用演示专用的09:05固定业务时钟。");
        initial.initialize(true,password);
        long first=jdbc.queryForObject("SELECT MIN(id) FROM reading_room",Long.class);
        jdbc.update("UPDATE reading_room SET room_name='人文阅览室',location='图书馆二层 · 东侧',description='独立演示样本：安静自习与人文阅读区' WHERE id=?",first);
        List<Long> rooms=new ArrayList<>();rooms.add(first);
        String[] names={"理工阅览室","静思自习室","设备维护室"};int[] sizes={30,40,24};
        for(int i=0;i<3;i++) {
            jdbc.update("INSERT INTO reading_room(room_name,location,description,status) VALUES (?,?,?,?)",names[i],"图书馆"+(i+3)+"层","独立演示样本："+(i==2?"维护暂停开放":"专注学习空间"),i==2?0:1);
            long room=jdbc.queryForObject("SELECT MAX(id) FROM reading_room",Long.class);rooms.add(room);
            for(int n=1;n<=sizes[i];n++) jdbc.update("INSERT INTO seat(room_id,seat_no) VALUES (?,?)",room,"%c%02d".formatted('B'+i,n));
        }
        int[] last={20,30,40,24};
        for(int i=0;i<4;i++) jdbc.update("UPDATE seat SET status=0 WHERE room_id=? AND seat_no=?",rooms.get(i),"%c%02d".formatted('A'+i,last[i]));
        long student=account("student");List<Long> students=new ArrayList<>();
        for(int i=1;i<=24;i++) {
            String username="demo_student_%02d".formatted(i);
            jdbc.update("INSERT INTO `user`(username,password,real_name,class_name,phone,role,created_at) VALUES (?,?,?,?,?,'STUDENT',?)",
                    username,passwords.encode(password),"演示同学%02d".formatted(i),"演示班级 "+(1+(i-1)/8),"13800000000",day.minusDays(14).atStartOfDay());
            students.add(account(username));
        }
        jdbc.update("UPDATE `user` SET created_at=?",day.minusDays(14).atStartOfDay());
        jdbc.update("UPDATE reading_room SET created_at=?",day.minusDays(14).atStartOfDay());
        jdbc.update("UPDATE seat SET created_at=?",day.minusDays(14).atStartOfDay());

        add(student,first,"A01",day,1,"PENDING",day.atTime(9,0),day.atTime(9,10),null,null,null,null,null,null);
        add(students.get(0),first,"A02",day,1,"USING",day.atTime(7,30),day.atTime(8,15),day.atTime(8,4),null,null,null,null,null);
        add(students.get(1),first,"A03",day,1,"COMPLETED",day.atTime(7,30),day.atTime(8,15),day.atTime(8,5),day.atTime(8,50),null,null,null,null);
        add(student,first,"A04",day,2,"CANCELLED",day.atTime(7,30),day.atTime(14,15),null,null,null,day.atTime(8,10),student,"演示样本：学生主动取消");
        add(students.get(2),first,"A05",day,1,"EXPIRED",day.atTime(7,30),day.atTime(8,15),null,null,day.atTime(8,15,1),null,null,null);
        add(students.get(3),first,"A05",day,1,"PENDING",day.atTime(9,0),day.atTime(9,10),null,null,null,null,null,null);
        add(student,first,"A06",day,1,"EXPIRED",day.atTime(8,30),day.atTime(8,40),null,null,day.atTime(8,40,1),null,null,null);
        add(students.get(4),rooms.get(1),"B01",day,1,"USING",day.atTime(7,30),day.atTime(8,15),day.atTime(8,10),null,null,null,null,null);
        add(students.get(5),rooms.get(1),"B02",day,1,"COMPLETED",day.atTime(7,30),day.atTime(8,15),day.atTime(8,10),day.atTime(8,40),null,null,null,null);
        add(students.get(6),rooms.get(2),"C01",day,1,"PENDING",day.atTime(8,59),day.atTime(9,9),null,null,null,null,null,null);
        add(students.get(7),first,"A08",day,2,"PENDING",day.atTime(9,1),day.atTime(14,15),null,null,null,null,null,null);
        add(student,first,"A07",day.plusDays(1),2,"PENDING",day.atTime(9,0),day.plusDays(1).atTime(14,15),null,null,null,null,null,null);
        for(int offset=1;offset<=6;offset++) {
            LocalDate past=day.minusDays(offset);
            for(int n=0;n<2+offset%5;n++) add(n==0?student:students.get((offset*3+n)%24),first,"A%02d".formatted(8+n),past,1,"COMPLETED",
                    past.atTime(7,30),past.atTime(8,15),past.atTime(8,0),past.atTime(10,0),null,null,null,null);
        }
    }
    private long account(String username) { return jdbc.queryForObject("SELECT id FROM `user` WHERE username=?",Long.class,username); }
    private void add(long user,long room,String number,LocalDate date,int slot,String state,LocalDateTime created,LocalDateTime deadline,
                     LocalDateTime in,LocalDateTime out,LocalDateTime released,LocalDateTime cancelled,Long actor,String reason) {
        long seat=jdbc.queryForObject("SELECT id FROM seat WHERE room_id=? AND seat_no=?",Long.class,room,number);
        LocalDateTime updated=out!=null?out:released!=null?released:cancelled!=null?cancelled:in!=null?in:created;
        jdbc.update("INSERT INTO reserve_record(user_id,seat_id,reserve_date,time_slot,status,checkin_deadline,checkin_at,checkout_at,released_at,cancelled_at,cancelled_by,cancel_reason,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                user,seat,date,slot,state,deadline,in,out,released,cancelled,actor,reason,created,updated);
    }
}
