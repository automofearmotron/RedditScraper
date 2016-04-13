package com.github.jreddit.comment;

/**
 * Enum to represent comment sorts on Reddit. You see these on a page that lists comments.
 *
 * @author Evin Ugur
 * @author Raul Rene Lepsa
 */
public enum CommentSort {

    NEW("new"), HOT("hot"), TOP("top"), CONTROVERSIAL("controversial"), OLD("old"), RANDOM("random"), CONFIDENCE("confidence");

    private final String value;

    CommentSort(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }
}
