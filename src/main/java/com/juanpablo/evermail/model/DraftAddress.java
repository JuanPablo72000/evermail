package com.juanpablo.evermail.model;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DraftAddress {

    private Integer idDraft;
    private Integer idAddress;
    private String recipientType;
}