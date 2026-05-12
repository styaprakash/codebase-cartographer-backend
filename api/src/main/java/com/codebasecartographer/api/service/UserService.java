package com.codebasecartographer.api.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codebasecartographer.api.dto.response.UserResponse;
import com.codebasecartographer.api.entity.User;
import com.codebasecartographer.api.repository.UserRepository;

@Service
public class UserService {
    // We need UserRepository to talk to the database
    private final UserRepository userRepository;

    // Constructor injection
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Method 1: called right after Github Oauth Login succeeds
    @Transactional
    public UserResponse findOrCreateUser (String githubId, String name, String email, String accessToken){
        //Check if the user already exists
        User user = userRepository.findByGithubId(githubId)
            .map(existingUser -> {
                //User exists - just update their token
                //Token changes everytime when they login via Github
                existingUser.setAccessToken(accessToken);
                return userRepository.save(existingUser);
            }) 
            .orElseGet(() -> {
                // User doesn't exist - create a new user
                User newUser = User.builder()
                    .githubId(githubId)
                    .name(name)
                    .email(email)
                    .accessToken(accessToken)
                    .dailyQueryCount(0)
                    .build();
                
                return userRepository.save(newUser);
            });

        return toResponse(user);
    }

    //Method 2: Called by GET /api/me, return logged in user's info as DTO
    public UserResponse getUserById (String id){
        User user = userRepository.findById(id)
            // orElseThrow — if user not found, throw exception
            .orElseThrow( () -> new RuntimeException("User not found: " + id));

        return toResponse(user);
    }
        
    //Method 3: Query limit is Reached, called BEFORE every AI query to check the 20/day cap
    public boolean isQueryLimitReached (String userId){
        //if we can't find the user
        User user = userRepository.findById(userId)
            .orElseThrow( () -> new RuntimeException("User not found: " + userId));

        // Check if reset is needed first
        // If queryResetAt is null (never reset) OR last reset was yesterday
        // → reset the counter before checking
        if(shouldResetCount(user)){
            resetQueryCount(userId);
        }

        //20 or more queries used today -> limit reached
        return user.getDailyQueryCount() >= 20;
    }

    //Method 4: Called after every successfull AI query and adds 1 to the user's daily count
    @Transactional
    public void incrementQueryCount(String userId){
        userRepository.incrementQueryCount(userId);
    }

    //Method 5: Called at midnight OR when shouldResetCount() returns true, Sets dailyQueryCount back to 0
    @Transactional
    public void resetQueryCount(String userId){
        userRepository.resetDailyQueryCount(userId);
    }

    // Private Helper : Checks if 24 hours have passed since last reset
    private boolean shouldResetCount(User user){
        if(user.getQueryResetAt() == null) return true; // Never been reset before 

        return user.getQueryResetAt().isBefore(
            LocalDateTime.now().minusHours(24)
        );
    }

    // Private Helper : Converts User Entity -> UserResponse
    private UserResponse toResponse(User user){
            return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .dailyQueryCount(user.getDailyQueryCount())
                .queryResetAt(user.getQueryResetAt())
                .createdAt(user.getCreatedAt())
                .build();
    }

}
