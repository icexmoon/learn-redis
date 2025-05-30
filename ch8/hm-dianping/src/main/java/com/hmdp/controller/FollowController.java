package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Autowired
    private IFollowService followService;

    @PutMapping("/{id}/{flag}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("flag") Boolean flag) {
        return followService.follow(id, flag);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollowed(@PathVariable("id") Long followUserId){
        return followService.isFollowed(followUserId);
    }

    @GetMapping("/common/{id}")
    public Result commonFollows(@PathVariable("id") Long uid){
        return followService.commonFollows(uid);
    }
}
