package com.springai.knowledge;

/** An uploaded document was refused. The message is safe to show: it never contains the document's text. */
public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}
