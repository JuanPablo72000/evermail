package com.juanpablo.evermail.model;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Draft {

    private Integer idDraft;
    private Integer idAccount;
    private String subject;
    private String bodyPlainText;
    private LocalDate lastEdited;
}