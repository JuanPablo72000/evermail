package com.juanpablo.evermail.model;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppProfile {

    private Integer idProfile;
    private String theme;
    private int syncIntervalMinutes;
    private String language;
    private boolean notificationsEnabled;
}