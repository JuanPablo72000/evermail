package com.juanpablo.evermail.model;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

    private Integer idAttachment;
    private Integer idMail;
    private String fileName;
    private String mimeType;
    private int sizeBytes;
    private String filePath;
}