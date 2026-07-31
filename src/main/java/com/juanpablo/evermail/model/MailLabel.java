package com.juanpablo.evermail.model;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MailLabel {

    private Integer idMail;
    private Integer idLabel;
    private boolean isRead;
}