// Copyright 2014, Kevin Ko <kevin@faveset.com>

package com.faveset.khttp;

class InvalidRequestException extends Exception {
    public InvalidRequestException(String reason) {
        super(reason);
    }
}
