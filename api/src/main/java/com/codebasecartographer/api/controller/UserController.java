package com.codebasecartographer.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codebasecartographer.api.dto.response.UserResponse;
import com.codebasecartographer.api.service.UserService;

@RestController
@RequestMapping("/api")
public class UserController extends BaseController {
    // controller calls service - never calls repository directly\
    private final UserService userService;

    public UserController(UserService userService){
        this.userService = userService;
    }

    //GET - /api/me
    // Returns logged in user's info (id, githubId, email)
    // Called when : dashboard loads, setting page loads

    // userId extracted from JWT token automatically
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(){
        String userId = getCurrentUserId(); //From base controller
        //call service 
        //Service will handle all logic  +  exception throwing
        UserResponse response = userService.getUserById(userId);

        // Return 200 OK + user info in body
        return ResponseEntity.ok(response);
    }

    //Delete - /api/me
    // Deletes the user account entirely
    //Called when: user clicks "Delete Account" in settings and confirms
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(){
        String userId = getCurrentUserId();
        // TODO Week 2: implement deleteAccount in UserService
        // userService.deleteAccount(userId);

        // Return 204 No Content (success but no body needed)
        return ResponseEntity.noContent().build();
    }

    //Delete /api/me/data
    //Dleete all indexed data (repositories + chunks) but keep the user's account
    //Called when: user clicks "Delete All Data" in settings and confirms
    @DeleteMapping("/me/data")
    public ResponseEntity<Void> deleteAllData(){
        String userId = getCurrentUserId();
        // TODO Week 2: implement deleteAllData in UserService
        // userService.deleteAllData(userId);

        // Return 204 No Content (success but no body needed)
        return ResponseEntity.noContent().build();
    }
}
