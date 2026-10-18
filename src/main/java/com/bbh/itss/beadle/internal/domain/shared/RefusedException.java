package com.bbh.itss.beadle.internal.domain.shared;

/**
 * A request a system refuses for what it holds: a {@code 4xx} answer other than a failed sign-in, a time-out or a
 * throttle, a stale version, a title already taken. The API answers it {@code 409} with this message; any other
 * {@link IllegalStateException} is a failure of Beadle-Internal or its framework, answered {@code 500} without one.
 */
public class RefusedException extends IllegalStateException {

    public RefusedException(String message) {
        super(message);
    }
}
