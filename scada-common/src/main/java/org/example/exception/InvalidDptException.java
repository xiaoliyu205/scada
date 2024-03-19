package org.example.exception;

public class InvalidDptException extends RuntimeException {

    public InvalidDptException(String message) {
        super(message);
    }


    public InvalidDptException(Throwable cause) {
        super(cause);
    }
}
