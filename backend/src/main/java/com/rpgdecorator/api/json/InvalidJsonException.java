package com.rpgdecorator.api.json;

/** Text that is not valid JSON. {@code position} is the 0-based index of the offending character. */
public class InvalidJsonException extends RuntimeException {

    private final int position;

    public InvalidJsonException(String message, int position) {
        super(message + " (position " + position + ")");
        this.position = position;
    }

    public int position() {
        return position;
    }
}
