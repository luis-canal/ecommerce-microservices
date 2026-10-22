package br.edu.atitus.commons.web;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}