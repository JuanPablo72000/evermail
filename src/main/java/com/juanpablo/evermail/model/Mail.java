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
public class Mail {

    private Integer idMail;
    private Integer idAccount;
    private Integer idSenderAddress;
    private Integer idReplyToMail;
    private String serverMessageId;
    private String subject;
    private String bodyPlainText;
    private String bodyHTML;
    private LocalDate dateReceived;
}