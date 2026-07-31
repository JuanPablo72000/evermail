package com.juanpablo.evermail.model;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    private Integer idAccount;
    private Integer idProfile;
    private Integer idAddress;
    private String signature;
    private String accountName;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime tokenExpiresAt;
}