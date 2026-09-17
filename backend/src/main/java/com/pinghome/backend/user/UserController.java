package com.pinghome.backend.user;

import com.pinghome.backend.security.CurrentUser;
import com.pinghome.backend.user.dto.FcmTokenRequest;
import com.pinghome.backend.user.dto.UpdateProfileRequest;
import com.pinghome.backend.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me() {
        return ResponseEntity.ok(UserProfileResponse.from(userService.getById(CurrentUser.id())));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(CurrentUser.id(), request));
    }

    /** The Android app calls this after obtaining an FCM registration token so the backend
     * knows where to deliver escalation/nudge/watcher-alert pushes for this user. */
    @PutMapping("/me/fcm-token")
    public ResponseEntity<Void> updateFcmToken(@Valid @RequestBody FcmTokenRequest request) {
        userService.updateFcmToken(CurrentUser.id(), request.fcmToken());
        return ResponseEntity.noContent().build();
    }
}
