package com.graduate.api.user;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.graduate.api.course.CourseController;
import com.graduate.common.BaseController;
import com.graduate.common.BaseJsonData;
import com.graduate.system.college.model.College;
import com.graduate.system.college.service.CollegeService;
import com.graduate.system.user.model.Role;
import com.graduate.system.user.model.User;
import com.graduate.system.user.model.UserAndRole;
import com.graduate.system.user.service.RoleService;
import com.graduate.system.user.service.UserAndRoleService;
import com.graduate.system.user.service.UserService;
import com.graduate.utils.UserUtil;
import com.graduate.utils.wecat.WecatService;
import io.swagger.annotations.*;
import me.chanjar.weixin.cp.bean.WxCpUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Created by konglinghai on 2017/4/4.
 */
@RestController
@RequestMapping("api/account")     // 通过这里配置使下面的映射都在/users下，可去除
@Api(value = "api/account", description = "用户接口")
public class UserController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserAndRoleService<UserAndRole> userAndRoleService;

    @Autowired
    private UserService<User> userService;

    @Autowired
    private RoleService<Role> roleService;

    @Autowired
    private  WecatService wecatService;

    @Autowired
    private CollegeService<College> collegeService;


    @ApiOperation(value="获取用户列表", notes="")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_MASTER')")
    @RequestMapping(value={"/list"}, method=RequestMethod.POST)
    public BaseJsonData getUserList(@ApiParam(value = "页数")@RequestParam(value = "pageNo") Integer pageNo,
                                    @ApiParam(value = "页长")@RequestParam(value = "pageSize") int pageSize,
                                    @ApiParam(value = "用户id")@RequestParam(value = "uid",required = false) Long uid,
                                    @ApiParam(value = "角色id")@RequestParam(value = "roleId",required = false) Long roleId,
                                    @ApiParam(value = "用户账号")@RequestParam(value = "username",required = false) String username,
                                    @ApiParam(value = "老师姓名")@RequestParam(value = "name",required = false) String name,
                                    @ApiParam(value = "老师职称")@RequestParam(value = "title",required = false) String title
    ) {
        try{
            User user=userService.findUserByname(UserUtil.getUserName());
            HashMap<String,Object> searchVals = new HashMap<>();
            HashMap<String,String> orderVals = new HashMap<>();
            if(user.getCid()==1){
                searchVals.put("uid",uid);
                searchVals.put("roleId",roleId);
                searchVals.put("username",username);
                searchVals.put("name",name);
                searchVals.put("title",title);
                orderVals.put("uid","ASC");
            }else{
                searchVals.put("uid",uid);
                searchVals.put("roleId",roleId);
                searchVals.put("cid",user.getCid());
                searchVals.put("username",username);
                searchVals.put("name",name);
                searchVals.put("title",title);
                orderVals.put("uid","ASC");
            }
            Page<UserAndRole> userList = userAndRoleService.findAllByField(searchVals,pageNo,pageSize,orderVals);
            return BaseJsonData.ok(JSON.toJSON(userList));
        }catch (Exception e){
            e.printStackTrace();
            logger.error(e.getMessage(),e);
            return BaseJsonData.fail(e.getMessage());
        }
    }


    @ApiOperation(value="创建用户", notes="根据User对象创建用户")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_MASTER')")
    @RequestMapping(value="/create", method=RequestMethod.POST)
    public BaseJsonData createUser(@RequestBody User user, @ApiParam(value = "角色id")@RequestParam(value = "roleId") Long roleId) {
        BaseJsonData data = new BaseJsonData();
        try {
            user.setLastPasswordResetDate(new Date());
            userService.save(user);
            UserAndRole userAndRole=new UserAndRole();
            userAndRole.setUid(user.getId());
            userAndRole.setRoleId(roleId);
            userAndRoleService.save(userAndRole);
            try{
                WxCpUser wxCpUseruser = new WxCpUser();
                wxCpUseruser.setName(user.getUsername());
                wxCpUseruser.setUserId(user.getId().toString());
                wxCpUseruser.setWeiXinId(user.getWecat());
                College college=collegeService.findCollegeByid(user.getCid());
                Integer [] departIds = new Integer[]{Integer.parseInt(college.getWecatid())};
                wxCpUseruser.setDepartIds(departIds);
                wxCpUseruser.setEmail(user.getEmail());
                wxCpUseruser.setMobile(user.getPhone());
                wecatService.addUser(wxCpUseruser);
                user.setIsSynchro(1);//同步完成
                user.setWecatid(user.getId());
                userService.save(user);
            }catch (Exception e){
                e.printStackTrace();
                logger.error(e.getMessage(),e);
                JSONObject j=JSON.parseObject(e.getMessage());
                if(j.get("errcode").toString().trim().equals("60108")){
                    user.setIsSynchro(1);//同步完成
                    String wecatmsg=j.get("errmsg").toString().trim();
                    String wecatid=wecatmsg.substring(wecatmsg.indexOf(":")+1).trim();
                    user.setWecatid(Long.valueOf(wecatid));
                    userService.save(user);
                }
            }
            return data.ok();
        }catch (Exception e){
            e.printStackTrace();
            logger.error(e.getMessage(),e);
            return data.fail(e.getMessage());
        }

    }

    @ApiOperation(value="删除用户", notes="")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_MASTER')")
    @RequestMapping(value={"/delete"}, method=RequestMethod.POST)
    public BaseJsonData deleteUserList(@RequestBody List<User> users) {
        BaseJsonData data = new BaseJsonData();
        try{
            List<UserAndRole> list=new ArrayList<UserAndRole>();
            for (User u: users) {
                if(u.getWecatid()==u.getId()){
                    try {
                        wecatService.deleteUser(u.getId().toString());
                    }catch (Exception e){
                        e.printStackTrace();
                        logger.error(e.getMessage(),e);
                    }
                    UserAndRole userAndRole=userAndRoleService.findRoleByUid(u.getId());
                    list.add(userAndRole);
                }else{
                    UserAndRole userAndRole=userAndRoleService.findRoleByUid(u.getId());
                    list.add(userAndRole);
                }

            }
            userAndRoleService.delete(list);
            return data.ok();
        }catch (Exception e){
            e.printStackTrace();
            logger.error(e.getMessage(),e);
            return data.fail(e.getMessage());
        }
    }


    @ApiOperation(value="修改用户", notes="")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_MASTER')")
    @RequestMapping(value={"/update"}, method=RequestMethod.POST)
    public BaseJsonData updateUsereList(@RequestBody UserAndRole userAndRole) {
        BaseJsonData data = new BaseJsonData();
        try{
            userAndRole.getUser().setLastPasswordResetDate(new Date());
            User user=userService.findOne(userAndRole.getUid());
            if(user.getWecat().trim().equals(userAndRole.getUser().getWecat().trim())){
                try {
                    userAndRole.getUser().setWecatid(user.getId());
                    userAndRoleService.save(userAndRole);
                }catch (Exception e){
                    e.printStackTrace();
                    logger.error(e.getMessage(),e);
                }
            }else{
                try {
                     WxCpUser wxCpUseruser = new WxCpUser();
                     wxCpUseruser.setName(user.getUsername());
                     wxCpUseruser.setUserId(user.getId().toString());
                     wxCpUseruser.setWeiXinId(userAndRole.getUser().getWecat().trim());
                     College college=collegeService.findCollegeByid(userAndRole.getUser().getCid());
                     Integer [] departIds = new Integer[]{Integer.parseInt(college.getWecatid())};
                     wxCpUseruser.setDepartIds(departIds);
                     wxCpUseruser.setEmail(userAndRole.getUser().getEmail());
                     wxCpUseruser.setMobile(userAndRole.getUser().getPhone());
                     wecatService.addUser(wxCpUseruser);
                     userAndRole.getUser().setIsSynchro(1);
                     userAndRole.getUser().setWecatid(user.getId());
                     userAndRoleService.save(userAndRole);
                }catch (Exception e){
                    e.printStackTrace();
                    logger.error(e.getMessage(),e);
                    JSONObject j=JSON.parseObject(e.getMessage());
                    if(j.get("errcode").toString().trim().equals("60108")){
                        user.setIsSynchro(1);//同步完成
                        String wecatmsg=j.get("errmsg").toString().trim();
                        String wecatid=wecatmsg.substring(wecatmsg.indexOf(":")+1).trim();
                        user.setWecatid(Long.valueOf(wecatid));
                        user.setWecat(userAndRole.getUser().getWecat().trim());
                        userService.save(user);
                    }
                }
            }

            return data.ok();
        }catch (Exception e){
            e.printStackTrace();
            logger.error(e.getMessage(),e);
            return data.fail(e.getMessage());
        }
    }

    @ApiOperation(value="角色列表", notes="")
    @RequestMapping(value={"/role"}, method=RequestMethod.POST)
    public BaseJsonData getRoleList() {
        BaseJsonData data = new BaseJsonData();
        try{
            User user=userService.findUserByname(UserUtil.getUserName());
            List<Role> r;
            if(user.getUsername().equals("admin")){
                r=roleService.findAll();
            }else {
                r=roleService.findAll();
                r.remove(0);
            }
            return data.ok(r);
        }catch (Exception e){
            e.printStackTrace();
            logger.error(e.getMessage(),e);
            return data.fail(e.getMessage());
        }
    }


    @ApiOperation(value="获取用户(不分页)", notes="")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_MASTER')")
    @RequestMapping(value={"/list/all"}, method=RequestMethod.POST)
    public BaseJsonData getUserList(@ApiParam(value = "用户id")@RequestParam(value = "uid",required = false) Long uid,
                                    @ApiParam(value = "角色id")@RequestParam(value = "roleId",required = false) Long roleId,
                                    @ApiParam(value = "用户账号")@RequestParam(value = "username",required = false) String username,
                                    @ApiParam(value = "老师姓名")@RequestParam(value = "name",required = false) String name,
                                    @ApiParam(value = "老师职称")@RequestParam(value = "title",required = false) String title
    ) {
        try{
            User user=userService.findUserByname(UserUtil.getUserName());
            HashMap<String,Object> searchVals = new HashMap<>();
            if(user.getCid()==1){
                searchVals.put("uid",uid);
                searchVals.put("roleId",roleId);
                searchVals.put("username",username);
                searchVals.put("name",name);
                searchVals.put("title",title);
            }else{
                searchVals.put("uid",uid);
                searchVals.put("roleId",roleId);
                searchVals.put("cid",user.getCid());
                searchVals.put("username",username);
                searchVals.put("name",name);
                searchVals.put("title",title);
            }
            List<UserAndRole> userList = userAndRoleService.findAllByField(searchVals);
            return BaseJsonData.ok(JSON.toJSON(userList));
        }catch (Exception e){
            e.printStackTrace();
            logger.error(e.getMessage(),e);
            return BaseJsonData.fail(e.getMessage());
        }
    }


}
